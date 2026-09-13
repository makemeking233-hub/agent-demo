import { Loader2, Mic, MicOff, Send, Square, Volume2, VolumeX, WifiOff } from "lucide-react";
import { KeyboardEvent, useState } from "react";
import { type PermissionMode } from "../api/chat";
import { OnlineProvider, useOnline } from "../hooks/useOnline";
import styles from "./Composer.module.css";

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
}: ComposerProps) {
  const [value, setValue] = useState("");
  const [showSlashHint, setShowSlashHint] = useState(false);

  function submit() {
    const text = value.trim();
    if (!text || busy) return;
    onSend(text);
    setValue("");
    setShowSlashHint(false);
  }

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
  const { isOnline } = useOnline();
  const offline = !isOnline;

  return (
    <div className={styles.composer}>
      {showSlashHint && (
        <div className={styles.slashHint}>
          {SLASH_COMMANDS.filter((c) => c.startsWith(trimmed)).map((c) => (
            <span key={c} className={styles.slashHintItem}>
              {c}
            </span>
          ))}
        </div>
      )}
      <div className={styles.row}>
        <textarea
          className={styles.input}
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
            className={`${styles.button} ${voiceActive ? styles.voiceOn : ""}`}
            onClick={onVoiceToggle}
            disabled={offline}
            aria-label={voiceActive ? "关闭自由语音" : "开启自由语音"}
            title={voiceActive ? "关闭自由语音" : "开启自由语音"}
          >
            {voiceState === "loading" ? (
              <Loader2 size={16} className={styles.spin} />
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
            className={styles.button}
            onClick={onMuteToggle}
            disabled={offline}
            aria-label={muted ? "开启朗读" : "静音朗读"}
            title={muted ? "开启朗读" : "静音朗读"}
          >
            {muted ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </button>
        )}
        {busy && onAbort ? (
          <button type="button" className={`${styles.button} ${styles.abort}`} onClick={onAbort}>
            <Square size={16} />
            <Loader2 size={16} className={styles.spin} />
          </button>
        ) : (
          <button
            type="button"
            className={styles.button}
            onClick={submit}
            disabled={!trimmed || offline}
            title={offline ? "网络已断开，无法发送" : undefined}
          >
            {offline ? <WifiOff size={16} /> : <Send size={16} />}
          </button>
        )}
      </div>
      <div className={styles.statusBar}>
        <span className={styles.permission}>
          <select
            className={styles.permissionSelect}
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
        <span>{trimmed.length} 字符</span>
        <span>{offline ? "网络已断开" : "Ctrl+Enter 发送 / Shift+Enter 换行"}</span>
      </div>
    </div>
  );
}
