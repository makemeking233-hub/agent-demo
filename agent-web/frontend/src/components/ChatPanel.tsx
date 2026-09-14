import { useEffect, useMemo, useRef, useState } from "react";
import { ChatApi, type HistoryMessage, type ModelEntry, type PermissionMode, type SessionStats } from "../api/chat";
import { SseClient } from "../lib/sse-client";
import { SseEvent } from "../lib/event-types";
import { createVoice } from "../lib/voice";
import { createVoskStt } from "../lib/stt";
import { useVoiceChat } from "../lib/useVoiceChat";
import styles from "./ChatPanel.module.css";
import { Composer } from "./Composer";
import { MessageBubble } from "./MessageBubble";
import { PermissionCard } from "./PermissionCard";
import { StatsBar } from "./StatsBar";
import { ToolCallCard } from "./ToolCallCard";

export type Item =
  | {
      kind: "text";
      id: string;
      role: "user" | "assistant";
      text: string;
      // add-reasoning-thinking-streaming: 推理过程（按 token 累加到当前消息）
      thinking?: string;
      reasoningTokens?: number;
      // assistant 消息项可携带内联工具调用（按到达顺序与文本交错展示）
      tools?: InlineTool[];
    }
  | { kind: "tool"; id: string; name: string; toolCallId: string; status: "running" | "ok" | "fail"; text?: string; durationMs?: number }
  | { kind: "perm"; id: string; toolName: string; reason: string; permissionId: string; choices: ("yes" | "no" | "always")[]; toolCallId: string };

type InlineTool = {
  id: string;
  name: string;
  status: "running" | "ok" | "fail";
  text?: string;
  durationMs?: number;
};

// ---------- 会话重进恢复：localStorage 持久化 + 服务端历史回填 ----------

const CHAT_STATE_KEY = "agent-demo.chat.v1";

interface PersistedChatState {
  v: 1;
  sessionId: string | null;
  items: Item[];
}

function readPersisted(): PersistedChatState | null {
  try {
    const raw = localStorage.getItem(CHAT_STATE_KEY);
    if (!raw) return null;
    const state = JSON.parse(raw) as PersistedChatState;
    if (state && state.v === 1) return state;
  } catch {
    /* 解析失败当作无状态，不阻断启动 */
  }
  return null;
}

function writePersisted(sessionId: string | null, items: Item[]) {
  try {
    localStorage.setItem(CHAT_STATE_KEY, JSON.stringify({ v: 1, sessionId, items }));
  } catch {
    /* 写入失败（配额/隐私模式）仅禁用持久化，不阻断对话 */
  }
}

function clearPersisted() {
  try {
    localStorage.removeItem(CHAT_STATE_KEY);
  } catch {
    /* 忽略 */
  }
}

/** 把服务端返回的消息历史重建为渲染 items（仅保留用户/助手文本与内联工具，处于稳定态）。 */
export function mapHistoryToItems(messages: HistoryMessage[]): Item[] {
  const items: Item[] = [];
  // 记录每个 toolCallId 落在哪个 item 的第几个内联工具上，供随后的 tool 结果消息回填
  const toolIndex = new Map<string, { itemIdx: number; toolIdx: number }>();
  for (const m of messages) {
    if (m.role === "user") {
      items.push({ kind: "text", id: "h-u-" + items.length, role: "user", text: m.content });
    } else if (m.role === "assistant") {
      const tools: InlineTool[] = (m.toolCalls ?? []).map((tc) => ({
        id: tc.id,
        name: tc.name,
        status: "ok" as const,
      }));
      items.push({
        kind: "text",
        id: "h-a-" + items.length,
        role: "assistant",
        text: m.content,
        tools: tools.length ? tools : undefined,
      });
      const itemIdx = items.length - 1;
      tools.forEach((t, toolIdx) => toolIndex.set(t.id, { itemIdx, toolIdx }));
    } else if (m.role === "tool") {
      // 同一次调用只渲染一张卡：把结果回填到该 assistant item 的内联工具上，
      // 而不是再单独出一张（此前会重复出卡，且第二张的工具名是字面量 "tool"）。
      const loc = m.toolCallId ? toolIndex.get(m.toolCallId) : undefined;
      if (loc) {
        const item = items[loc.itemIdx] as Extract<Item, { kind: "text" }>;
        const tools = [...(item.tools ?? [])];
        tools[loc.toolIdx] = {
          ...tools[loc.toolIdx],
          status: m.isError ? "fail" : "ok",
          text: m.content,
        };
        items[loc.itemIdx] = { ...item, tools } as Item;
      } else {
        // 孤儿结果（找不到对应调用，例如历史被裁剪）：仍以独立卡渲染，不丢信息
        items.push({
          kind: "tool",
          id: "h-t-" + items.length,
          name: "tool",
          toolCallId: m.toolCallId ?? "",
          status: m.isError ? "fail" : "ok",
          text: m.content,
        });
      }
    }
  }
  return items;
}

/**
 * 当前最新的 assistant 文本项是否还能继续追加**文本/思考**。
 *
 * <p>只认「最后一条」而不是「从后往前第一条」——这是 fix-tool-call-inline-order 的关键：
 * 工具卡渲染在该 item 的文本下方，一旦这一迭代已经挂上工具，它就"收尾"了；后续文本必须新建
 * item 才能落在工具卡**之后**。否则 `MessageBubble` 固定按 text→tools 渲染，文本会跳到所有
 * 工具卡上面，多轮迭代下来工具就全堆到了底部。
 *
 * @returns 可追加的下标；不可追加返回 -1
 */
function openAssistantIndexForText(items: Item[]): number {
  const last = items[items.length - 1];
  if (
    last &&
    last.kind === "text" &&
    last.role === "assistant" &&
    !(last.tools && last.tools.length > 0)
  ) {
    return items.length - 1;
  }
  return -1;
}

/**
 * 当前最新 assistant 文本项是否还能继续追加**工具**。
 *
 * <p>同一次迭代的工具调用是在 `onAssistant` 里**一次性公告**的（结果稍后才到），所以判据是
 * 「已有工具都还是 running」：说明还在同一批公告里，继续追加；一旦结果回来了，下一个
 * `tool_call_start` 必属新的迭代，要新建 item 才能让上一迭代的文本留在工具卡之前。
 *
 * @returns 可追加的下标；不可追加返回 -1
 */
function openAssistantIndexForTool(items: Item[]): number {
  const last = items[items.length - 1];
  if (!last || last.kind !== "text" || last.role !== "assistant") return -1;
  const tools = last.tools ?? [];
  if (tools.length === 0) return items.length - 1;
  return tools.every((t) => t.status === "running") ? items.length - 1 : -1;
}

/**
 * 沿时间线追加一段助手文本（纯函数，便于单测）。
 *
 * <p>落在「当前这条助手 item」之后：若最后一条已挂工具（该迭代已收尾），则新建一条，
 * 使文本位于工具卡之下，恢复「每次迭代 文本→工具」的真实顺序。
 *
 * @param items 现有渲染项
 * @param text 追加的文本增量
 * @param id 新建 item 时使用的 id
 * @returns 新的渲染项数组
 */
export function appendTextToTimeline(items: Item[], text: string, id: string): Item[] {
  const idx = openAssistantIndexForText(items);
  if (idx >= 0) {
    const updated = [...items];
    const it = items[idx] as Extract<Item, { kind: "text" }>;
    updated[idx] = { ...it, text: it.text + text } as Item;
    return updated;
  }
  return [...items, { kind: "text", id, role: "assistant", text }];
}

/**
 * 沿时间线追加助手思考增量（纯函数；判据与文本一致，使同一次迭代的思考与文本落在同一条 item）。
 *
 * @param items 现有渲染项
 * @param chunk 思考增量
 * @param id 新建 item 时使用的 id
 * @returns 新的渲染项数组
 */
export function appendThinkingToTimeline(items: Item[], chunk: string, id: string): Item[] {
  const idx = openAssistantIndexForText(items);
  if (idx >= 0) {
    const updated = [...items];
    const it = items[idx] as Extract<Item, { kind: "text" }>;
    updated[idx] = { ...it, thinking: (it.thinking ?? "") + chunk } as Item;
    return updated;
  }
  return [...items, { kind: "text", id, role: "assistant", text: "", thinking: chunk } as Item];
}

/**
 * 沿时间线追加一个工具调用（纯函数）。
 *
 * <p>同一批公告（工具都还在 running）追加到同一条 item；否则新建——这样上一迭代的文本会留在
 * 自己的工具卡之上，而新迭代的工具卡排在其后。
 *
 * @param items 现有渲染项
 * @param tool 内联工具
 * @param id 新建 item 时使用的 id
 * @returns 新的渲染项数组
 */
export function appendToolToTimeline(items: Item[], tool: InlineTool, id: string): Item[] {
  const idx = openAssistantIndexForTool(items);
  if (idx >= 0) {
    const updated = [...items];
    const it = items[idx] as Extract<Item, { kind: "text" }>;
    updated[idx] = { ...it, tools: [...(it.tools ?? []), tool] } as Item;
    return updated;
  }
  return [
    ...items,
    { kind: "text", id, role: "assistant", text: "", tools: [tool] } as Item,
  ];
}

export function ChatPanel(props: {
  currentSessionId?: string | null;
  workspace?: string;
  // add-models-dropdown-v0：model/reasoningEffort 由 App 持有并传入（避免 ChatPanel 与 TopBar 状态不同步）
  model: string;
  reasoningEffort: string;
  currentModelEntry: ModelEntry | null;
  onReasoningEffortChange: (effort: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [streamId, setStreamId] = useState<string | null>(null);
  // 底部统计状态栏数据（add-session-stats-bar）：首屏拉 stats API，运行时由 turn_stats 事件刷新。
  const [stats, setStats] = useState<SessionStats | null>(null);
  // 权限模式（add-permission-mode-dropdown）：缺省 read_only；切换即调后端 setPermission；随 send 透传初始模式。
  const [permissionMode, setPermissionMode] = useState<PermissionMode>("read_only");
  // add-models-dropdown-v0：model/reasoningEffort/currentModelEntry 由 props 传入（App.tsx 持有，避免双 state 不同步）
  const { model, reasoningEffort, currentModelEntry, onReasoningEffortChange } = props;
  // streamIdRef: 始终持有最新 streamId，避免 submitPermission/abortStream 读闭包里的陈旧值
  // （React 闭包捕获的是函数创建时的值；SSE 异步到达时闭包里的 streamId 可能仍是 null → 权限提交被跳过）。
  const streamIdRef = useRef<string | null>(null);
  // sessionIdRef: 跨轮次复用同一会话 id，使后端按 session_id 复用 history → 多轮对话有记忆。
  const sessionIdRef = useRef<string | null>(null);
  const clientRef = useRef<SseClient | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  // ---------- 自由语音（add-voice-interaction）----------
  const voice = useMemo(() => createVoice(), []);
  const [muted, setMuted] = useState(false);
  const voiceChat = useVoiceChat({
    getStt: () => createVoskStt(),
    voice,
    onSubmit: (t) => startStream(t),
    canSubmit: () => !busy,
  });

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [items]);

  const api = new ChatApi();

  // 会话重进恢复：挂载时从 localStorage 恢复 session_id + 消息快照；无快照但服务端有历史时回填。
  useEffect(() => {
    const saved = readPersisted();
    if (saved && saved.sessionId) {
      sessionIdRef.current = saved.sessionId;
      setItems(saved.items ?? []);
      if ((saved.items ?? []).length === 0) {
        api
          .history(saved.sessionId)
          .then((h) => {
            if (h.messages.length > 0) {
              setItems((prev) => (prev.length === 0 ? mapHistoryToItems(h.messages) : prev));
            }
          })
          .catch(() => {});
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 侧边栏切换会话（add-session-switch）：currentSessionId 变化时清空并加载该会话历史。
  // lastSessionIdRef 记录上次值，避免首次挂载时误清零（首次由上面 localStorage 恢复 effect 处理）。
  const lastSessionIdRef = useRef<string | null>(props.currentSessionId ?? null);
  useEffect(() => {
    const next = props.currentSessionId ?? null;
    if (next === lastSessionIdRef.current) return;
    lastSessionIdRef.current = next;
    // 中止当前流（如有）
    if (clientRef.current) clientRef.current.stop();
    setBusy(false);
    setItems([]);
    setStreamId(null);
    streamIdRef.current = null;
    sessionIdRef.current = next;
    if (!next) {
      setStats(null);
      return;
    }
    new ChatApi()
      .history(next)
      .then((h) => {
        setItems((prev) => (prev.length === 0 ? mapHistoryToItems(h.messages) : prev));
      })
      .catch(() => {});
    // add-session-stats-bar：切换会话时回填该会话累计统计（首屏值）
    new ChatApi()
      .sessionStats(next, props.workspace)
      .then(setStats)
      .catch(() => setStats(null));
  }, [props.currentSessionId, props.workspace]);

  // 会话重进恢复：消息/会话变化时（防抖）写回 localStorage，供下次重进恢复。
  useEffect(() => {
    const t = setTimeout(() => writePersisted(sessionIdRef.current, items), 250);
    return () => clearTimeout(t);
  }, [items]);

  function appendItem(it: Item) {
    setItems((prev) => [...prev, it]);
  }
  function updateItem(id: string, patch: Partial<Item>) {
    setItems((prev) => prev.map((it) => (it.id === id ? ({ ...it, ...patch } as Item) : it)));
  }

  // 关键修复: 用函数式 setItems 追加文本到"当前这条 assistant 文本项"。
  // 不能用闭包里的 items(陈旧), 否则 message_delta 永远匹配不到 last 项 → 界面空白。
  // 排序规则见模块级 appendTextToTimeline（fix-tool-call-inline-order）。
  function appendTextToLastAssistant(text: string) {
    setItems((prev) => appendTextToTimeline(prev, text, "a-" + Date.now()));
  }

  /**
   * 追加 thinking（推理）内容到当前这条 assistant 消息项。
   *
   * <p>修复：add-reasoning-thinking-streaming 提交时只调用了本函数却未定义，收到 thinking delta 会抛
   * ReferenceError（add-session-stats-bar 实施时一并补齐）。
   */
  function appendThinkingToLastAssistant(chunk: string) {
    setItems((prev) => appendThinkingToTimeline(prev, chunk, "a-think-" + Date.now()));
  }

  async function startStream(content: string) {
    if (busy) return;
    setBusy(true);
    appendItem({ kind: "text", id: "u-" + Date.now(), role: "user", text: content });

    if (content.trim() === "/clear") {
      setItems([]);
      setBusy(false);
      sessionIdRef.current = null; // /clear 开新会话，重置 session_id（下轮从新会话开始）
      clearPersisted(); // 清掉本地持久化，避免重进恢复回旧会话
      return;
    }
    if (content.trim() === "/help") {
      appendItem({
        kind: "text", id: "h-" + Date.now(), role: "assistant",
        text: "可用命令:\n  /help    显示本帮助\n  /clear   清空当前 session 的消息历史\n  /resume  恢复最近一次 session\n  /history 显示当前 session 的消息统计\n  /quit    关闭当前 session",
      });
      setBusy(false);
      return;
    }

    try {
      const resp = await api.send({
        content,
        session_id: sessionIdRef.current ?? undefined,
        permission_mode: permissionMode,
        // add-models-dropdown-v0：透传 model + reasoningEffort 到后端
        model,
        reasoning_effort: reasoningEffort,
      });
      setStreamId(resp.stream_id);
      streamIdRef.current = resp.stream_id;
      sessionIdRef.current = resp.session_id; // 记住会话，下轮复用 → 后端按 session_id 复用 history
      appendItem({ kind: "text", id: "a-" + Date.now(), role: "assistant", text: "" });
      const client = new SseClient({
        url: api.streamUrl(resp.stream_id),
        onEvent: (ev: SseEvent) => handleEvent(ev),
        onComplete: () => setBusy(false),
        onError: () => setBusy(false),
      });
      clientRef.current = client;
      client.start();
    } catch (e) {
      setBusy(false);
      appendItem({ kind: "text", id: "e-" + Date.now(), role: "assistant", text: "err: " + (e as Error).message });
    }
  }

  function handleEvent(ev: SseEvent) {
    if (ev.type === "message_start") return;
    if (ev.type === "message_delta" && ev.delta_type === "text") {
      // 函数式追加，避免闭包捕获陈旧 items 导致界面空白
      appendTextToLastAssistant(ev.content);
      voiceChat.onAssistantDelta(ev.content);
    } else if (ev.type === "message_delta" && ev.delta_type === "thinking") {
      // add-reasoning-thinking-streaming: 累加 thinking 到最后一条 assistant
      appendThinkingToLastAssistant(ev.content);
    } else if (ev.type === "turn_stats") {
      // add-session-stats-bar：用累计统计刷新底部状态栏
      setStats({
        turns: ev.turns,
        steps: ev.steps,
        tokens_in: ev.tokens_in,
        tokens_out: ev.tokens_out,
        llm_ms: ev.llm_ms,
        tool_ms: ev.tool_ms,
        avg_ttft_ms: ev.avg_ttft_ms,
        tok_per_sec: ev.tok_per_sec,
        cache_hit_rate: ev.cache_hit_rate,
      });
    } else if (ev.type === "message_stop") {
      setBusy(false);
      setStreamId(null);
      streamIdRef.current = null;
      voiceChat.onTurnEnd();
    } else if (ev.type === "tool_call_start") {
      // 内联到最近一条 assistant 消息项，保持工具调用与生成的文本同一消息块
      addToolToLastAssistant({
        id: ev.tool_call_id,
        name: ev.name,
        status: "running",
      });
    } else if (ev.type === "tool_call_end") {
      const text = typeof ev.result === "string" ? ev.result : JSON.stringify(ev.result);
      updateToolInLastAssistant(ev.tool_call_id, {
        status: ev.ok ? "ok" : "fail",
        text,
        durationMs: ev.duration_ms,
      });
    } else if (ev.type === "permission_request") {
      appendItem({ kind: "perm", id: ev.permission_id, toolName: ev.tool_name, reason: ev.reason, permissionId: ev.permission_id, choices: ev.choices, toolCallId: ev.tool_call_id });
    }
  }

  // 把工具调用沿时间线内联到当前这条 assistant 消息项（无则新建一条承载）
  function addToolToLastAssistant(tool: InlineTool) {
    setItems((prev) => appendToolToTimeline(prev, tool, "a-tool-" + Date.now()));
  }

  // 在最近的 assistant 内联工具里按 toolCallId 更新（找不到则 fallback 到独立 tool item）
  function updateToolInLastAssistant(toolCallId: string, patch: Partial<InlineTool>) {
    setItems((prev) => {
      for (let i = prev.length - 1; i >= 0; i--) {
        const it = prev[i];
        if (it.kind === "text" && it.role === "assistant" && it.tools) {
          const idx = it.tools.findIndex((t) => t.id === toolCallId);
          if (idx >= 0) {
            const updated = [...prev];
            const tools = it.tools.map((t, j) => (j === idx ? ({ ...t, ...patch } as InlineTool) : t));
            updated[i] = { ...it, tools } as Item;
            return updated;
          }
        }
      }
      // fallback：独立 tool item（如旧数据/未内联）
      return prev.map((it) => (it.kind === "tool" && it.toolCallId === toolCallId ? ({ ...it, ...patch } as Item) : it));
    });
  }

  async function submitPermission(permissionId: string, decision: "yes" | "no" | "always", itemId: string) {
    const sid = streamIdRef.current; // 用 ref 拿最新 streamId（避免闭包陈旧导致提交被跳过）
    if (!sid) return;
    await api.submitDecision(sid, permissionId, decision);
    // 决策后移除权限卡：避免同一工具多次授权时残留成排的“权限已处理”重复卡（工具执行卡已体现结果）
    setItems((prev) => prev.filter((it) => it.id !== itemId));
  }

  async function abortStream() {
    const sid = streamIdRef.current;
    if (sid) await api.abort(sid);
  }

  // 切换权限模式：本地状态 + 若有活动流则立即下发后端（add-permission-mode-dropdown）
  function handlePermissionModeChange(mode: PermissionMode) {
    setPermissionMode(mode);
    const sid = streamIdRef.current;
    if (sid) api.setPermission(sid, mode).catch(() => {});
  }

  // 切换自由语音（add-voice-interaction）：开则开始循环（懒加载 Vosk），关则停止
  function handleVoiceToggle() {
    if (voiceChat.state !== "idle") {
      voiceChat.stop();
      return;
    }
    voiceChat
      .start()
      .catch((e) => {
        appendItem({
          kind: "text",
          id: "v-e-" + Date.now(),
          role: "assistant",
          text: "语音初始化失败：" + ((e as Error)?.message ?? String(e)),
        });
      });
  }

  // 切换朗读静音
  function handleMuteToggle() {
    const next = !muted;
    setMuted(next);
    voice.setMuted(next);
  }

  return (
    <div className={styles.panel}>
      <div ref={listRef} className={styles.list}>
        {items.length === 0 && (
          <div className={styles.empty}>
            <p>开始对话，或输入 <code>/help</code> 查看可用命令</p>
          </div>
        )}
        {items.map((it) => {
          if (it.kind === "text") return <MessageBubble key={it.id} role={it.role} text={it.text} tools={it.tools} thinking={it.thinking} reasoningTokens={it.reasoningTokens} />;
          if (it.kind === "tool") return <ToolCallCard key={it.id} name={it.name} status={it.status} text={it.text} durationMs={it.durationMs} />;
          if (it.kind === "perm") return <PermissionCard key={it.id} toolName={it.toolName} reason={it.reason} choices={it.choices} onChoose={(d) => submitPermission(it.permissionId, d, it.id)} />;
          return null;
        })}
      </div>
      <Composer
        busy={busy}
        onSend={startStream}
        onAbort={abortStream}
        permissionMode={permissionMode}
        onPermissionModeChange={handlePermissionModeChange}
        voiceState={voiceChat.state}
        muted={muted}
        onVoiceToggle={handleVoiceToggle}
        onMuteToggle={handleMuteToggle}
        // add-models-dropdown-v0：透传 model/effort
        model={currentModelEntry}
        reasoningEffort={reasoningEffort}
        onReasoningEffortChange={onReasoningEffortChange}
        // improve-voice-accuracy T6：partial result UI
        lastPartial={voiceChat.lastPartial}
      />
      {/* 底部统计状态栏（add-session-stats-bar） */}
      <StatsBar stats={stats} />
    </div>
  );
}
