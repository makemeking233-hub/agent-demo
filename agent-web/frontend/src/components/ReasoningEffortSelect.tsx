import type { ModelEntry } from "../api/chat";
import { Dropdown } from "./Dropdown";

/**
 * 思考强度下拉框（add-models-dropdown-v0）。
 *
 * <p>仅当 {@code model.supportsReasoning=true} 且 {@code model.reasoningEfforts.length > 0} 时渲染；
 * 否则返回 {@code null}(从 DOM 移除)。
 */
export interface ReasoningEffortSelectProps {
  model: ModelEntry | null;
  value: string;
  onChange: (effort: string) => void;
}

const LABELS: Record<string, string> = {
  low: "低",
  medium: "中",
  high: "高",
};

export function ReasoningEffortSelect({ model, value, onChange }: ReasoningEffortSelectProps) {
  if (!model || !model.supportsReasoning || model.reasoningEfforts.length === 0) {
    return null;
  }

  const options = model.reasoningEfforts.map((e) => ({
    label: `思考 ${LABELS[e] ?? e}`,
    value: e,
  }));

  return (
    <Dropdown
      options={options}
      value={value}
      onChange={onChange}
      placeholder="思考强度"
      ariaLabel="思考强度"
    />
  );
}