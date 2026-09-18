import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ModelsResponse, ModelSelection } from "../api/chat";
import { ModelSelect } from "./ModelSelect";

/**
 * ModelSelect 两层菜单（add-provider-catalog-abstract task 9.3）。
 *
 * <p>覆盖：trigger 显示完整 ModelSelection / 打开两层菜单 / 切 provider 刷新右栏 /
 * 选 model 触发 onChange / effort chip 联动 / 空 providers 时禁用。
 */
describe("ModelSelect 两层菜单", () => {
  afterEach(() => cleanup());

  const RESPONSE: ModelsResponse = {
    providers: [
      {
        id: "deepseek",
        name: "DeepSeek",
        models: [
          {
            id: "deepseek-v4-flash",
            name: "DeepSeek-V4-Flash",
            supportsReasoning: false,
            reasoningEfforts: [],
          },
          {
            id: "deepseek-reasoner",
            name: "DeepSeek Reasoner",
            supportsReasoning: true,
            reasoningEfforts: [
              { id: "low", name: "Low" },
              { id: "medium", name: "Medium" },
              { id: "high", name: "High" },
            ],
          },
        ],
      },
      {
        id: "anthropic",
        name: "Anthropic",
        models: [
          {
            id: "claude-opus-4-20250514",
            name: "Claude Opus 4",
            supportsReasoning: true,
            reasoningEfforts: [{ id: "medium", name: "Medium" }],
          },
        ],
      },
    ],
  };

  const api = { listModels: vi.fn(async () => RESPONSE) };

  function renderSelect(value: ModelSelection, onChange = vi.fn()) {
    render(<ModelSelect api={api} value={value} onChange={onChange} />);
    return onChange;
  }

  it("trigger 显示 provider 名 + model 名 + effort 徽标", async () => {
    renderSelect({ provider: "deepseek", model: "deepseek-reasoner", reasoningEffort: "high" });
    const trigger = await screen.findByRole("button", { name: "选择模型" });
    expect(trigger.textContent).toContain("DeepSeek");
    expect(trigger.textContent).toContain("DeepSeek Reasoner");
    expect(trigger.textContent).toContain("High");
  });

  it("点击 trigger 打开两层菜单（左 provider / 右 model）", async () => {
    renderSelect({ provider: "deepseek", model: "deepseek-reasoner", reasoningEffort: "medium" });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    expect(screen.getByRole("menu", { name: "模型两层菜单" })).toBeTruthy();
    // 左栏两个 provider
    expect(screen.getByText("DeepSeek")).toBeTruthy();
    expect(screen.getByText("Anthropic")).toBeTruthy();
    // 右栏当前 provider 的两个 model
    expect(screen.getByText("DeepSeek-V4-Flash")).toBeTruthy();
    expect(screen.getByText("DeepSeek Reasoner")).toBeTruthy();
    // 不显示另一个 provider 的 model
    expect(screen.queryByText("Claude Opus 4")).toBeNull();
  });

  it("切换 provider 刷新右栏 model 列表", async () => {
    renderSelect({ provider: "deepseek", model: "deepseek-reasoner", reasoningEffort: "medium" });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.click(screen.getByText("Anthropic"));
    expect(screen.getByText("Claude Opus 4")).toBeTruthy();
    expect(screen.queryByText("DeepSeek Reasoner")).toBeNull();
  });

  it("选不支持 reasoning 的 model → onChange 带 provider + model + undefined effort，且关闭面板", async () => {
    const onChange = renderSelect({
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "high",
    });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.click(screen.getByText("DeepSeek-V4-Flash"));
    expect(onChange).toHaveBeenCalledWith({
      provider: "deepseek",
      model: "deepseek-v4-flash",
      reasoningEffort: undefined,
    });
    expect(screen.queryByRole("menu", { name: "模型两层菜单" })).toBeNull();
  });

  it("选支持 reasoning 的 model（原 selection 无 effort）→ onChange 取第一档", async () => {
    const onChange = renderSelect({
      provider: "deepseek",
      model: "deepseek-v4-flash",
    });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.click(screen.getByText("DeepSeek Reasoner"));
    expect(onChange).toHaveBeenCalledWith({
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "low",
    });
  });

  it("跨 provider 切到支持 reasoning 的 model 时保留仍有效的 effort", async () => {
    // medium 在 deepseek-reasoner 的档位里 → 保留，不重置为第一档
    const onChange = renderSelect({
      provider: "anthropic",
      model: "claude-opus-4-20250514",
      reasoningEffort: "medium",
    });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.click(screen.getByText("DeepSeek"));
    fireEvent.click(screen.getByText("DeepSeek Reasoner"));
    expect(onChange).toHaveBeenCalledWith({
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "medium",
    });
  });

  it("effort chip 联动：点 High → onChange 带同 provider/model + 新 effort", async () => {
    const onChange = renderSelect({
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "low",
    });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.click(screen.getByText("High"));
    expect(onChange).toHaveBeenCalledWith({
      provider: "deepseek",
      model: "deepseek-reasoner",
      reasoningEffort: "high",
    });
  });

  it("点外部关闭面板", async () => {
    renderSelect({ provider: "deepseek", model: "deepseek-reasoner", reasoningEffort: "low" });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    expect(screen.getByRole("menu", { name: "模型两层菜单" })).toBeTruthy();
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole("menu", { name: "模型两层菜单" })).toBeNull();
  });

  it("Esc 关闭面板", async () => {
    renderSelect({ provider: "deepseek", model: "deepseek-reasoner", reasoningEffort: "low" });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu", { name: "模型两层菜单" })).toBeNull();
  });

  it("providers 为空时 trigger 禁用", async () => {
    const emptyApi = { listModels: vi.fn(async () => ({ providers: [] })) };
    render(
      <ModelSelect
        api={emptyApi}
        value={{ provider: "", model: "deepseek-chat" }}
        onChange={vi.fn()}
      />,
    );
    const trigger = await screen.findByRole("button", { name: "选择模型" });
    expect((trigger as HTMLButtonElement).disabled).toBe(true);
  });

  it("重新打开面板保留当前 provider 高亮", async () => {
    renderSelect({ provider: "anthropic", model: "claude-opus-4-20250514", reasoningEffort: "medium" });
    fireEvent.click(await screen.findByRole("button", { name: "选择模型" }));
    // 右栏应是 anthropic 的 model
    expect(screen.getByText("Claude Opus 4")).toBeTruthy();
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "选择模型" }));
    expect(screen.getByText("Claude Opus 4")).toBeTruthy();
  });
});
