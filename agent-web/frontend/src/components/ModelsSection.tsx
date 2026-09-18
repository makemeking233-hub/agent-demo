/**
 * ModelsSection (add-settings-menu-placeholders M3；add-provider-catalog-abstract task 9/10 升级)。
 *
 * 在"模型"菜单复刻 TopBar 的 ModelSelect + ReasoningEffortSelect，共享 App store。
 *
 * <p>v0.2 起 prop 随两个子组件一起升级：
 *
 * <ul>
 *   <li>`ModelSelect`：`value: ModelSelection` + `onChange(next: ModelSelection)`
 *   <li>`ReasoningEffortSelect`：`options: ReasoningEffort[]`（不再传 `model`）
 * </ul>
 */

import { ChatApi, type ModelSelection } from "../api/chat";
import { ModelSelect } from "./ModelSelect";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";
import styles from "./SettingsRows.module.css";

interface ModelsSectionProps {
  api: ChatApi;
  selection: ModelSelection;
  reasoningEfforts: import("../api/chat").ReasoningEffort[];
  onSelectionChange: (next: ModelSelection) => void;
  onReasoningEffortChange: (effort: string) => void;
}

export function ModelsSection({
  api,
  selection,
  reasoningEfforts,
  onSelectionChange,
  onReasoningEffortChange,
}: ModelsSectionProps) {
  return (
    <div data-testid="settings-content-models">
      <div className={styles.group}>
        <div className={styles.title}>默认模型</div>
        <div className={styles.row}>
          <div className={styles.rowControl}>
            <ModelSelect api={api} value={selection} onChange={onSelectionChange} />
          </div>
        </div>
      </div>
      {reasoningEfforts.length > 0 && (
        <div className={styles.group}>
          <div className={styles.title}>默认推理强度</div>
          <div className={styles.row}>
            <div className={styles.rowControl}>
              <ReasoningEffortSelect
                options={reasoningEfforts}
                value={selection.reasoningEffort ?? ""}
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
