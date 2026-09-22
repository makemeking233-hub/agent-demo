/**
 * ReasoningEffortSelect a11y 测试（shadcn-frontend-migration §3 C4）。
 *
 * <p>直接断言 axe 结果的 `violations` 为空数组，而不是用 `toHaveNoViolations()`
 * matcher —— 后者需要 `vitest-axe/extend-expect` 的类型增强，而该子路径的
 * `declare module "vitest"` 在 `moduleResolution: Bundler` 下不会被自动加载，
 * 自建 `.d.ts` 增补又会因 `Assertion` 泛型参数不匹配而 merge 失败。
 * 用 `expect(violations).toEqual([])` 语义等价、零类型 hack，且失败时
 * 打印的是 violations 数组本身，比 matcher 的自定义消息更易读。
 */

import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { axe } from "vitest-axe";
import type { ReasoningEffort } from "../api/chat";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";

const OPTIONS: ReasoningEffort[] = [
  { id: "low", name: "Low" },
  { id: "medium", name: "Medium", description: "默认档" },
  { id: "high", name: "High" },
];

/** 对给定元素跑 axe，返回违规列表（空数组 = 通过）。 */
async function scanViolations(el: Element) {
  const results = await axe(el);
  return results.violations;
}

describe("ReasoningEffortSelect a11y（axe）", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("trigger 无 axe 违规", async () => {
    const { container } = render(
      <ReasoningEffortSelect options={OPTIONS} value="medium" onChange={vi.fn()} />,
    );
    expect(await scanViolations(container)).toEqual([]);
  });

  it("只支持单档位时无 axe 违规", async () => {
    const { container } = render(
      <ReasoningEffortSelect
        options={[{ id: "medium", name: "Medium" }]}
        value="medium"
        onChange={vi.fn()}
      />,
    );
    expect(await scanViolations(container)).toEqual([]);
  });

  it("空 options 不渲染时不报错", () => {
    const { container } = render(
      <ReasoningEffortSelect options={[]} value="medium" onChange={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });
});