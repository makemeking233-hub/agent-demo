/**
 * ModelsSection (add-settings-menu-placeholders M3).
 *
 * 在"模型"菜单复刻 TopBar 的 ModelSelect + ReasoningEffortSelect，共享 App store。
 */

import { ChatApi, type ModelEntry } from "../api/chat";
import { ModelSelect } from "./ModelSelect";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";
import styles from "./SettingsRows.module.css";

interface ModelsSectionProps {
  api: ChatApi;
  model: string;
  currentModelEntry: ModelEntry | null;
  reasoningEffort: string;
  onModelChange: (modelId: string) => void;
  onReasoningEffortChange: (effort: string) => void;
}

export function ModelsSection({
  api,
  model,
  currentModelEntry,
  reasoningEffort,
  onModelChange,
  onReasoningEffortChange,
}: ModelsSectionProps) {
  return (
    <div data-testid="settings-content-models">
      <div className={styles.group}>
        <div className={styles.title}>默认模型</div>
        <div className={styles.row}>
          <div className={styles.rowControl}>
            <ModelSelect api={api} value={model} onChange={onModelChange} />
          </div>
        </div>
      </div>
      {currentModelEntry?.supportsReasoning && (
        <div className={styles.group}>
          <div className={styles.title}>默认推理强度</div>
          <div className={styles.row}>
            <div className={styles.rowControl}>
              <ReasoningEffortSelect
                model={currentModelEntry}
                value={reasoningEffort}
                onChange={onReasoningEffortChange}
              />
              <div className={styles.hint}>下次发送生效</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
