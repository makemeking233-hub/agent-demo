/**
 * SettingsModal (add-settings-foundation M1 + add-settings-general-items M2 + add-settings-menu-placeholders M3).
 *
 * 居中 modal: 左 nav + 右 content.
 * 关闭路径: ESC / mask / X.
 * 焦点管理: 打开时跳到 close, 关闭时回到触发按钮.
 */

import { X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { ChatApi, type ModelEntry } from '../api/chat';
import { OpenConfigButton } from './OpenConfigButton';
import { SettingsContent } from './SettingsContent';
import { SettingsNav, type SettingsNavItem } from './SettingsNav';
import styles from './SettingsModal.module.css';

interface SettingsModalProps {
  open: boolean;
  onClose: () => void;
  triggerElement?: HTMLElement | null;
  api: ChatApi;
  model: string;
  currentModelEntry: ModelEntry | null;
  reasoningEffort: string;
  onModelChange: (modelId: string) => void;
  onReasoningEffortChange: (effort: string) => void;
}

const NAV_ITEMS: SettingsNavItem[] = [
  { id: 'general', label: '通用设置' },
  { id: 'models', label: '模型' },
  { id: 'plugins', label: '插件' },
  { id: 'agent-presets', label: 'Agent 预设' },
];

export function SettingsModal({
  open,
  onClose,
  triggerElement,
  api,
  model,
  currentModelEntry,
  reasoningEffort,
  onModelChange,
  onReasoningEffortChange,
}: SettingsModalProps) {
  const [activeId, setActiveId] = useState<string>('general');
  const titleId = useId();
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  // ESC 关闭
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  // 打开时焦点管理
  useEffect(() => {
    if (open) {
      previousFocusRef.current = triggerElement ?? (document.activeElement as HTMLElement | null);
      // 让 DOM 先渲染再聚焦
      setTimeout(() => closeButtonRef.current?.focus(), 0);
    } else if (previousFocusRef.current) {
      previousFocusRef.current.focus();
      previousFocusRef.current = null;
    }
  }, [open, triggerElement]);

  if (!open) return null;

  return (
    <div className={styles.overlay} role="presentation">
      <div className={styles.mask} aria-hidden="true" onClick={onClose} />
      <div
        className={styles.panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        data-testid="settings-modal"
      >
        <nav className={styles.nav} aria-label="设置分类">
          <h2 id={titleId} className={styles.navTitle}>设置</h2>
          <SettingsNav
            items={NAV_ITEMS}
            activeId={activeId}
            onSelect={setActiveId}
          />
        </nav>
        <div className={styles.content}>
          <div className={styles.header}>
            <div className={styles.actions}>
              <OpenConfigButton />
            </div>
            <button
              ref={closeButtonRef}
              type="button"
              className={styles.close}
              onClick={onClose}
              aria-label="关闭"
            >
              <X size={14} />
              <span className={styles.closeLabel}>关闭</span>
            </button>
          </div>
          <div className={styles.options}>
            <SettingsContent
              activeId={activeId}
              api={api}
              model={model}
              currentModelEntry={currentModelEntry}
              reasoningEffort={reasoningEffort}
              onModelChange={onModelChange}
              onReasoningEffortChange={onReasoningEffortChange}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
