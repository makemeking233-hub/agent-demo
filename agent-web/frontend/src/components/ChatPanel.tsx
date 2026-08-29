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
  | { kind: "text"; id: string; role: "user" | "assistant"; text: string }
  | { kind: "tool"; id: string; name: string; toolCallId: string; status: "running" | "ok" | "fail"; text?: string; durationMs?: number }
  | { kind: "perm"; id: string; toolName: string; reason: string; permissionId: string; choices: ("yes" | "no" | "always")[]; toolCallId: string };

export function ChatPanel() {
  const [busy, setBusy] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [streamId, setStreamId] = useState<string | null>(null);
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

  async function startStream(content: string) {
    if (busy) return;
    setBusy(true);
    appendItem({ kind: "text", id: "u-" + Date.now(), role: "user", text: content });

    if (content.trim() === "/clear") {
      setItems([]);
      setBusy(false);
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
      const resp = await api.send({ content });
      setStreamId(resp.stream_id);
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
    const last = items[items.length - 1];
    if (ev.type === "message_delta" && ev.delta_type === "text" && last?.kind === "text" && last.role === "assistant") {
      updateItem(last.id, { text: (last as { text: string }).text + ev.content });
    } else if (ev.type === "message_stop") {
      setBusy(false);
      setStreamId(null);
    } else if (ev.type === "tool_call_start") {
      appendItem({ kind: "tool", id: ev.tool_call_id, name: ev.name, toolCallId: ev.tool_call_id, status: "running" });
    } else if (ev.type === "tool_call_end") {
      const text = typeof ev.result === "string" ? ev.result : JSON.stringify(ev.result);
      updateItem(ev.tool_call_id, { status: ev.ok ? "ok" : "fail", text, durationMs: ev.duration_ms });
    } else if (ev.type === "permission_request") {
      appendItem({ kind: "perm", id: ev.permission_id, toolName: ev.tool_name, reason: ev.reason, permissionId: ev.permission_id, choices: ev.choices, toolCallId: ev.tool_call_id });
    }
  }

  async function submitPermission(permissionId: string, decision: "yes" | "no" | "always", itemId: string) {
    const sid = streamId;
    if (!sid) return;
    await api.submitDecision(sid, permissionId, decision);
    updateItem(itemId, { choices: [] } as Partial<Item>);
  }

  async function abortStream() {
    if (streamId) await api.abort(streamId);
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
          if (it.kind === "text") return <MessageBubble key={it.id} role={it.role} text={it.text} />;
          if (it.kind === "tool") return <ToolCallCard key={it.id} name={it.name} status={it.status} text={it.text} durationMs={it.durationMs} />;
          if (it.kind === "perm") return <PermissionCard key={it.id} toolName={it.toolName} reason={it.reason} choices={it.choices} onChoose={(d) => submitPermission(it.permissionId, d, it.id)} />;
          return null;
        })}
      </div>
      <Composer busy={busy} onSend={startStream} onAbort={abortStream} />
    </div>
  );
}
