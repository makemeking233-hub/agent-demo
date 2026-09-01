import { useEffect, useRef, useState } from "react";
import { ChatApi } from "../api/chat";
import { SseClient } from "../lib/sse-client";
import { SseEvent } from "../lib/event-types";
import styles from "./ChatPanel.module.css";
import { Composer } from "./Composer";
import { MessageBubble } from "./MessageBubble";
import { PermissionCard } from "./PermissionCard";
import { ToolCallCard } from "./ToolCallCard";

type Item =
  | {
      kind: "text";
      id: string;
      role: "user" | "assistant";
      text: string;
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

export function ChatPanel() {
  const [busy, setBusy] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [streamId, setStreamId] = useState<string | null>(null);
  // streamIdRef: 始终持有最新 streamId，避免 submitPermission/abortStream 读闭包里的陈旧值
  // （React 闭包捕获的是函数创建时的值；SSE 异步到达时闭包里的 streamId 可能仍是 null → 权限提交被跳过）。
  const streamIdRef = useRef<string | null>(null);
  // sessionIdRef: 跨轮次复用同一会话 id，使后端按 session_id 复用 history → 多轮对话有记忆。
  const sessionIdRef = useRef<string | null>(null);
  const clientRef = useRef<SseClient | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [items]);

  const api = new ChatApi();

  function appendItem(it: Item) {
    setItems((prev) => [...prev, it]);
  }
  function updateItem(id: string, patch: Partial<Item>) {
    setItems((prev) => prev.map((it) => (it.id === id ? ({ ...it, ...patch } as Item) : it)));
  }

  // 关键修复: 用函数式 setItems 追加文本到"最后一条 assistant 文本项"。
  // 不能用闭包里的 items(陈旧), 否则 message_delta 永远匹配不到 last 项 → 界面空白。
  function appendTextToLastAssistant(text: string) {
    setItems((prev) => {
      // 从后向前找最后一条 assistant 文本项
      for (let i = prev.length - 1; i >= 0; i--) {
        const it = prev[i];
        if (it.kind === "text" && it.role === "assistant") {
          const updated = [...prev];
          updated[i] = { ...it, text: (it as { text: string }).text + text };
          return updated;
        }
      }
      // 没有 assistant 文本项则追加一条
      return [...prev, { kind: "text", id: "a-" + Date.now(), role: "assistant", text }];
    });
  }

  async function startStream(content: string) {
    if (busy) return;
    setBusy(true);
    appendItem({ kind: "text", id: "u-" + Date.now(), role: "user", text: content });

    if (content.trim() === "/clear") {
      setItems([]);
      setBusy(false);
      sessionIdRef.current = null; // /clear 开新会话，重置 session_id（下轮从新会话开始）
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
      const resp = await api.send({ content, session_id: sessionIdRef.current ?? undefined });
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
    } else if (ev.type === "message_stop") {
      setBusy(false);
      setStreamId(null);
      streamIdRef.current = null;
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

  // 把工具调用内联到最近一条 assistant 消息项（无则创建一条空 assistant 承载）
  function addToolToLastAssistant(tool: InlineTool) {
    setItems((prev) => {
      for (let i = prev.length - 1; i >= 0; i--) {
        const it = prev[i];
        if (it.kind === "text" && it.role === "assistant") {
          const updated = [...prev];
          const tools = [...(it.tools ?? []), tool];
          updated[i] = { ...it, tools } as Item;
          return updated;
        }
      }
      // 无 assistant 文本项 → 新建一条空的 assistant 承载工具
      return [...prev, { kind: "text", id: "a-tool-" + Date.now(), role: "assistant", text: "", tools: [tool] }];
    });
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
    updateItem(itemId, { choices: [] } as Partial<Item>);
  }

  async function abortStream() {
    const sid = streamIdRef.current;
    if (sid) await api.abort(sid);
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
          if (it.kind === "text") return <MessageBubble key={it.id} role={it.role} text={it.text} tools={it.tools} />;
          if (it.kind === "tool") return <ToolCallCard key={it.id} name={it.name} status={it.status} text={it.text} durationMs={it.durationMs} />;
          if (it.kind === "perm") return <PermissionCard key={it.id} toolName={it.toolName} reason={it.reason} choices={it.choices} onChoose={(d) => submitPermission(it.permissionId, d, it.id)} />;
          return null;
        })}
      </div>
      <Composer busy={busy} onSend={startStream} onAbort={abortStream} />
    </div>
  );
}
