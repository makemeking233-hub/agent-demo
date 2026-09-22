import { Loader2, Mic, MicOff, Send, Square, Volume2, VolumeX, WifiOff } from "lucide-react";
import { KeyboardEvent, useEffect, useState } from "react";
import { type ModelEntry, type PermissionMode } from "../api/chat";
import { OnlineProvider, useOnline } from "../hooks/useOnline";
import { useSettingsStore } from "../hooks/useSettingsStore";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";

interface ComposerProps {
  busy: boolean;
  onSend: (text: string) => void;
  onAbort?: () => void;
  placeholder?: string;
  permissionMode?: PermissionMode;
  onPermissionModeChange?: (mode: PermissionMode) => void;
  /** 自由语音状态（add-voice-interaction）。 */
  voiceState?: "idle" | "loading" | "listening" | "sending";
  muted?: boolean;
  onVoiceToggle?: () => void;
  onMuteToggle?: () => void;
  /** add-models-dropdown-v0: 当前 model 条目（用于控制 ReasoningEffortSelect 是否渲染） */
  model?: ModelEntry | null;
  /** add-models-dropdown-v0: 当前思考强度（low / medium / high） */
  reasoningEffort?: string;
  /** add-models-dropdown-v0: 思考强度切换回调 */
  onReasoningEffortChange?: (effort: string) => void;
  // improve-voice-accuracy T6：partial result UI
  /** Vosk 最新 partial 文本（空串 = 不显示） */
  lastPartial?: string;
  /** 是否正在调 LLM 纠错（contextCorrect fire-and-forget 进行中），显示"纠错中..."占位 */
  isProcessingVoice?: boolean;
}

const SLASH_COMMANDS = ["/help", "/clear", "/resume", "/history", "/quit"];
const PERMISSION_LABELS: Record<PermissionMode, string> = {
  read_only: "Read Only",
  workspace_write: "Workspace Write",
  full_access: "Full access",
};

export function Composer(props: ComposerProps) {
  // Composer 自身用 OnlineProvider 包裹（add-pwa-support）：
  // - App 根已包过，嵌套无害
  // - 测试 render(<Composer />) 自动有 provider，不用每个测试单独包
  return (
    <OnlineProvider>
      <ComposerInner {...props} />
    </OnlineProvider>
  );
}

function ComposerInner({
  busy,
  onSend,
  onAbort,
  placeholder = "输入消息或 /help...",
  permissionMode = "read_only",
  onPermissionModeChange,
  voiceState = "idle",
  muted = false,
  onVoiceToggle,
  onMuteToggle,
  model,
  reasoningEffort,
  onReasoningEffortChange,
  lastPartial = "",
  isProcessingVoice = false,
}: ComposerProps) {
  const [value, setValue] = useState("");
  const [showSlashHint, setShowSlashHint] = useState(false);
  // add-settings-general-items M2: enterBehavior 三种模式 + 内存 queue
  const enterMode =
    (useSettingsStore(
      (s) => (s.snapshot?.general?.enterBehavior as { mode?: "send" | "queue" | "newSession" } | undefined)?.mode,
    ) ?? "send") as "send" | "queue" | "newSession";
  const [queue, setQueue] = useState<string[]>([]);
  const [toast, setToast] = useState<string | null>(null);

  function showToast(msg: string) {
    setToast(msg);
    setTimeout(() => setToast(null), 2000);
  }

  function submit() {
    const text = value.trim();
    if (!text) return;
    if (!busy) {
      onSend(text);
      setValue("");
      setShowSlashHint(false);
      return;
    }
    // 繁忙：根据 enterBehavior 决定
    if (enterMode === "send") {
      showToast("agent 还在跑");
    } else if (enterMode === "queue") {
      setQueue((q) => [...q, text]);
      setValue("");
      setShowSlashHint(false);
      showToast(`已加入队列（${queue.length + 1}）`);
    } else {
      // newSession：弹确认
      if (window.confirm("agent 还在跑。是否新建会话？")) {
        onSend(text);
        setValue("");
        setShowSlashHint(false);
      }
    }
  }

  // 当 busy 从 true 变 false 时，drain queue（仅 queue 模式生效）
  useEffect(() => {
    if (!busy && queue.length > 0 && enterMode === "queue") {
      const [next, ...rest] = queue;
      setQueue(rest);
      onSend(next);
    }
  }, [busy, queue, enterMode, onSend]);

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  }

  function onChange(v: string) {
    setValue(v);
    setShowSlashHint(v.startsWith("/") && !v.includes(" "));
  }

  const trimmed = value.trim();
  const voiceActive = voiceState !== "idle";
  // T6：partial display 仅在语音循环已启动（voiceActive）且有内容时显示
  const showPartial = voiceActive && (isProcessingVoice || lastPartial.length > 0);
  const partialText = isProcessingVoice ? "纠错中..." : lastPartial;
  const { isOnline } = useOnline();
  const offline = !isOnline;

  return (
    <div className="relative flex flex-col border-t border-border bg-card px-4 py-3">
      {/* M2: toast 显示（agent 还在跑 / 已加入队列） */}
      {toast && (
        <div className="mb-1 self-stretch overflow-hidden text-ellipsis whitespace-nowrap px-2 py-1 text-[0.7em] italic text-muted-foreground opacity-70">
          {toast}
        </div>
      )}
      {/* T6：partial display（输入框正上方，半透明灰色，语音循环未启动不渲染） */}
      {showPartial && (
        <div className="mb-1 self-stretch overflow-hidden text-ellipsis whitespace-nowrap px-2 py-1 text-[0.7em] italic text-muted-foreground opacity-70">
          {partialText}
        </div>
      )}
      {showSlashHint && (
        <div className="absolute bottom-full left-4 flex gap-1 rounded-sm border border-border bg-background px-2 py-1 shadow-md">
          {SLASH_COMMANDS.filter((c) => c.startsWith(trimmed)).map((c) => (
            <span key={c} className="rounded-sm bg-secondary px-1.5 py-0.5 font-mono text-xs">
              {c}
            </span>
          ))}
        </div>
      )}
      <div className="flex items-end gap-2">
        <textarea
          className="min-h-10 max-h-[200px] flex-1 resize-none rounded-md border border-border bg-background px-2.5 py-2 text-sm leading-normal text-foreground focus:border-primary focus:outline-none focus:ring-2 focus:ring-accent-subtle disabled:bg-secondary disabled:text-muted-foreground"
          rows={2}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={onKey}
          placeholder={offline ? "网络已断开" : placeholder}
          disabled={busy || offline}
        />
        {onVoiceToggle && (
          <button
            type="button"
            className={
              voiceActive
                ? "inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border-none bg-accent text-primary-foreground outline-2 outline-accent-subtle"
                : "inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border-none bg-accent text-primary-foreground disabled:cursor-not-allowed disabled:bg-neutral-300"
            }
            onClick={onVoiceToggle}
            disabled={offline}
            aria-label={voiceActive ? "关闭自由语音" : "开启自由语音"}
            title={voiceActive ? "关闭自由语音" : "开启自由语音"}
          >
            {voiceState === "loading" ? (
              <Loader2 size={16} className="animate-spin" />
            ) : voiceActive ? (
              <Mic size={16} />
            ) : (
              <MicOff size={16} />
            )}
          </button>
        )}
        {onMuteToggle && (
          <button
            type="button"
            className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border-none bg-accent text-primary-foreground disabled:cursor-not-allowed disabled:bg-neutral-300"
            onClick={onMuteToggle}
            disabled={offline}
            aria-label={muted ? "开启朗读" : "静音朗读"}
            title={muted ? "开启朗读" : "静音朗读"}
          >
            {muted ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </button>
        )}
        {busy && onAbort ? (
          <button
            type="button"
            className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border-none bg-destructive text-primary-foreground"
            onClick={onAbort}
          >
            <Square size={16} />
            <Loader2 size={16} className="animate-spin" />
          </button>
        ) : (
          <button
            type="button"
            className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border-none bg-accent text-primary-foreground disabled:cursor-not-allowed disabled:bg-neutral-300"
            onClick={submit}
            disabled={!trimmed || offline}
            title={offline ? "网络已断开，无法发送" : undefined}
          >
            {offline ? <WifiOff size={16} /> : <Send size={16} />}
          </button>
        )}
      </div>
      <div className="mt-1.5 flex justify-between text-[11px] text-muted-foreground">
        <span className="inline-flex items-center">
          <select
            className="cursor-pointer rounded-sm border border-border bg-background px-1.5 py-0.5 text-[11px] text-foreground focus:border-primary focus:outline-none"
            value={permissionMode}
            onChange={(e) => onPermissionModeChange?.(e.target.value as PermissionMode)}
            aria-label="权限模式"
            disabled={offline}
          >
            {(Object.keys(PERMISSION_LABELS) as PermissionMode[]).map((m) => (
              <option key={m} value={m}>
                {PERMISSION_LABELS[m]}
              </option>
            ))}
          </select>
        </span>
        {/* add-models-dropdown-v0: 思考强度下拉；add-provider-catalog-abstract task 10
            改用 options: ReasoningEffort[]（组件自己判断空数组时返回 null） */}
        {onReasoningEffortChange && reasoningEffort !== undefined && (
          <span className="inline-flex items-center gap-1">
            <ReasoningEffortSelect
              options={model?.reasoningEfforts ?? []}
              value={reasoningEffort}
              onChange={onReasoningEffortChange}
            />
            <span className="text-[11px] text-muted-foreground">下次发送生效</span>
          </span>
        )}
        <span>{trimmed.length} 字符</span>
        <span>{offline ? "网络已断开" : "Ctrl+Enter 发送 / Shift+Enter 换行"}</span>
      </div>
    </div>
  );
}
