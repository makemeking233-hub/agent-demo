import { Loader2, Send, Square } from "lucide-react";
import { KeyboardEvent, useState } from "react";
import { type PermissionMode } from "../api/chat";
import styles from "./Composer.module.css";

interface ComposerProps {
  busy: boolean;
  onSend: (text: string) => void;
  onAbort?: () => void;
  placeholder?: string;
  permissionMode?: PermissionMode;
  onPermissionModeChange?: (mode: PermissionMode) => void;
}

const SLASH_COMMANDS = ["/help", "/clear", "/resume", "/history", "/quit"];
const PERMISSION_LABELS: Record<PermissionMode, string> = {
  read_only: "Read Only",
  workspace_write: "Workspace Write",
  full_access: "Full access",
};

export function Composer({
  busy,
  onSend,
  onAbort,
  placeholder = "输入消息或 /help...",
  permissionMode = "read_only",
  onPermissionModeChange,
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
          placeholder={placeholder}
          disabled={busy}
        />
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
            disabled={!trimmed}
          >
            <Send size={16} />
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
          >
            {(Object.keys(PERMISSION_LABELS) as PermissionMode[]).map((m) => (
              <option key={m} value={m}>
                {PERMISSION_LABELS[m]}
              </option>
            ))}
          </select>
        </span>
        <span>{trimmed.length} 字符</span>
        <span>Ctrl+Enter 发送 / Shift+Enter 换行</span>
      </div>
    </div>
  );
}
