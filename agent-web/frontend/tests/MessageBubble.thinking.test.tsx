/**
 * MessageBubble thinking 集成测试（add-reasoning-thinking-streaming）。
 */
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MessageBubble } from "../src/components/MessageBubble";

describe("MessageBubble thinking 集成", () => {
  afterEach(() => cleanup());

  it("assistant 消息带 thinking 显示 ThinkingCollapse", () => {
    render(
      <MessageBubble
        role="assistant"
        text="最终答案"
        thinking="我先思考了用户的问题"
      />,
    );
    expect(screen.getByText("我先思考了用户的问题")).toBeInTheDocument();
    expect(screen.getByText("最终答案")).toBeInTheDocument();
    expect(screen.getByText(/思考过程 \(/)).toBeInTheDocument();
  });

  it("user 消息忽略 thinking 字段", () => {
    render(<MessageBubble role="user" text="hi" thinking="不应该显示" />);
    expect(screen.queryByText("不应该显示")).toBeNull();
  });

  it("无 thinking 时不显示 ThinkingCollapse（但仍渲染文本）", () => {
    const { container } = render(<MessageBubble role="assistant" text="仅答案" />);
    expect(container.textContent).toContain("仅答案");
    expect(container.querySelector("details")).toBeNull();
  });

  it("thinking 与 tools 并存时 thinking 在 tools 之前", () => {
    const { container } = render(
      <MessageBubble
        role="assistant"
        text="调完工具后回答"
        thinking="我先思考"
        tools={[
          { id: "t1", name: "Read", status: "ok", text: "file content" },
        ]}
      />,
    );
    // 用 querySelector 找 thinking details 元素 + 工具 card 元素
    const collapse = container.querySelector("details");
    expect(collapse).not.toBeNull();
    expect(collapse?.textContent).toContain("我先思考");
    expect(container.textContent).toContain("Read");
    expect(container.textContent).toContain("调完工具后回答");
  });
});