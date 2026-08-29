import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MessageBubble } from "./MessageBubble";

describe("MessageBubble", () => {
  it("renders assistant markdown text", () => {
    render(<MessageBubble role="assistant" text="**你好**" />);
    const strong = screen.getByText("你好");
    expect(strong).toBeInTheDocument();
    expect(strong.tagName).toBe("STRONG");
  });

  it("renders inline code from markdown", () => {
    render(<MessageBubble role="assistant" text={"```js\nconst x=1;\n```"} />);
    expect(screen.getByText(/const x=1/)).toBeInTheDocument();
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
});
