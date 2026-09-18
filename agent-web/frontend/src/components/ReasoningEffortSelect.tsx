import type { ReasoningEffort } from "../api/chat";
import { Dropdown } from "./Dropdown";

/**
 * 思考强度下拉框（add-models-dropdown-v0；add-provider-catalog-abstract task 10 升级 prop）。
 *
 * <p>prop 从 `model: ModelEntry` 改为 `options: ReasoningEffort[]`（对齐 dsh web
 * `ModelReasoningEffort[]` 数据形态）：调用方只需下发当前模型的档位数组，
 * 组件自己不再感知 `supportsReasoning`。
 *
 * <p>`options` 为空数组时返回 `null`（从 DOM 移除）。
 */
export interface ReasoningEffortSelectProps {
  options: ReasoningEffort[];
  value: string;
  onChange: (effort: string) => void;
}

export function ReasoningEffortSelect({ options, value, onChange }: ReasoningEffortSelectProps) {
  if (!options || options.length === 0) {
    return null;
  }

  const dropdownOptions = options.map((e) => ({
    label: `思考 ${e.name}`,
    value: e.id,
  }));

  return (
    <Dropdown
      options={dropdownOptions}
      value={value}
      onChange={onChange}
      placeholder="思考强度"
      ariaLabel="思考强度"
    />
  );
}
