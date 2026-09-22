import type { ReasoningEffort } from "../api/chat";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";

/**
 * 思考强度下拉框（add-models-dropdown-v0 → add-provider-catalog-abstract task 10 升级 prop
 * → shadcn-prototype 用 Popover + RadioGroup 重写）。
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

  const current = options.find((o) => o.id === value) ?? options[0];

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          aria-label="思考强度"
          className="min-w-[8rem] justify-between font-normal"
        >
          {current ? current.name : "思考强度"}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-56 p-3" align="start">
        <RadioGroup
          className=""
          value={value}
          onValueChange={onChange}
          aria-label="思考强度选项"
        >
          {options.map((o) => (
            <div key={o.id} className="flex items-center gap-2 py-1">
              <RadioGroupItem className="" value={o.id} id={`effort-${o.id}`} />
              <Label htmlFor={`effort-${o.id}`} className="cursor-pointer font-normal">
                思考 {o.name}
                {o.description ? (
                  <span className="ml-1.5 text-xs text-muted-foreground">
                    {o.description}
                  </span>
                ) : null}
              </Label>
            </div>
          ))}
        </RadioGroup>
      </PopoverContent>
    </Popover>
  );
}