/**
 * SettingsModal (add-settings-foundation M1 + add-settings-general-items M2 + add-settings-menu-placeholders M3
 * → shadcn-components-p1: 用 shadcn Dialog 替换原手搓 <div role="dialog">).
 *
 * <p>用 shadcn Dialog 自动获得：focus trap / Esc 关闭 / 焦点还原 / 外点击关闭
 * / portal 渲染。data-testid="settings-modal" 保留以兼容现有测试。
 */

import { X } from 'lucide-react';
import { useId, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';
import { ChatApi, type ModelSelection, type ReasoningEffort } from '../api/chat';
import { OpenConfigButton } from './OpenConfigButton';
import { SettingsContent } from './SettingsContent';
import { SettingsNav, type SettingsNavItem } from './SettingsNav';

interface SettingsModalProps {
  open: boolean;
  onClose: () => void;
  triggerElement?: HTMLElement | null;
  api: ChatApi;
  /** add-provider-catalog-abstract task 9.1：模型菜单用完整 ModelSelection */
  selection: import('../api/chat').ModelSelection;
  /** 当前 model 的可选思考档位（升级 ReasoningEffortSelect 的 options prop） */
  reasoningEfforts: import('../api/chat').ReasoningEffort[];
  onSelectionChange: (next: import('../api/chat').ModelSelection) => void;
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
  api,
  selection,
  reasoningEfforts,
  onSelectionChange,
  onReasoningEffortChange,
}: SettingsModalProps) {
  const [activeId, setActiveId] = useState<string>('general');
  const titleId = useId();

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        data-testid="settings-modal"
        className="flex max-w-3xl gap-0 p-0"
      >
        <nav
          aria-label="设置分类"
          className="flex w-48 shrink-0 flex-col gap-1 border-r p-4"
        >
          <DialogTitle id={titleId} className="mb-2 px-2 text-base font-semibold">
            设置
          </DialogTitle>
          <DialogDescription className="sr-only">
            应用设置：通用、模型、插件、Agent 预设
          </DialogDescription>
          <SettingsNav
            items={NAV_ITEMS}
            activeId={activeId}
            onSelect={setActiveId}
          />
        </nav>
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex items-center justify-end gap-2 border-b px-4 py-2">
            <OpenConfigButton />
            <button
              type="button"
              className="inline-flex h-7 w-7 items-center justify-center rounded-md text-sm hover:bg-accent"
              onClick={onClose}
              aria-label="关闭"
            >
              <X size={14} />
              <span className="sr-only">关闭</span>
            </button>
          </div>
          <div className="flex-1 overflow-auto p-4">
            <SettingsContent
              activeId={activeId}
              api={api}
              selection={selection}
              reasoningEfforts={reasoningEfforts}
              onSelectionChange={onSelectionChange}
              onReasoningEffortChange={onReasoningEffortChange}
            />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}