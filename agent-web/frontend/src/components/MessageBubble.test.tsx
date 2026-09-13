import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MessageBubble } from "./MessageBubble";

describe("MessageBubble", () => {
  it("renders assistant markdown text", () => {
    render(<MessageBubble role="assistant" text="**你好**" />);
    const strong = screen.getByText("你好");
    expect(strong).toBeInTheDocument();
    expect(strong.tagName).toBe("STRONG");
  });

  it("renders inline code from markdown", () => {
    const { container } = render(
      <MessageBubble role="assistant" text={"```js\nconst x=1;\n```"} />,
    );
    // add-rich-markdown-rendering: 代码块现在带语法高亮，文本被拆进多个 token span，
    // 整串 getByText 不再匹配；改为断言 code 元素的整体文本（内容必须一字不少）。
    const code = container.querySelector("pre code");
    expect(code).not.toBeNull();
    expect(code!.textContent).toContain("const x=1;");
  });

  it("shows placeholder ellipsis when text empty", () => {
    render(<MessageBubble role="assistant" text="" />);
    expect(screen.getByText("…")).toBeInTheDocument();
  });

  it("aligns user bubble right (reverse row direction)", () => {
    const { container } = render(<MessageBubble role="user" text="hi" />);
    const row = container.querySelector("div") as HTMLElement | null;
    expect(row).not.toBeNull();
    // user 用 rowUser CSS Module 类, 跟 rowAssistant 区分
    expect(row!.className).toMatch(/rowUser/);
  });

  it("renders inline tool cards within assistant message", () => {
    render(
      <MessageBubble
        role="assistant"
        text="我检查一下"
        tools={[{ id: "t1", name: "ReadFile", status: "ok", text: "file content", durationMs: 5 }]}
      />,
    );
    expect(screen.getByText(/我检查一下/)).toBeInTheDocument();
    expect(screen.getByText(/ReadFile/)).toBeInTheDocument();
    // 工具卡片默认折叠：点击后显示输出
    const card = screen.getByText(/ReadFile/);
    fireEvent.click(card);
    expect(screen.getByText("file content")).toBeInTheDocument();
  });
});
