import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MessageBubble } from "./MessageBubble";

/**
 * 富 Markdown 渲染（add-rich-markdown-rendering）。
 *
 * 按关注点分组：GFM 基础 / 代码高亮 / 公式 / 图片 / 安全基线 / 流式时序。
 * 这里全部走**真渲染**（不 mock 插件）——本 change 的价值就在"渲染结果对不对"。
 */
describe("MessageBubble 富 Markdown 渲染", () => {
  describe("GFM 表格", () => {
    const TABLE = [
      "| 维度 | 2PC | TCC |",
      "|---|---|---|",
      "| 一致性 | 强一致 | 最终一致 |",
      "| 隔离性 | 有 | 无 |",
    ].join("\n");

    it("把表格渲染为 table 元素", () => {
      const { container } = render(<MessageBubble role="assistant" text={TABLE} />);

      const table = container.querySelector("table");
      expect(table).not.toBeNull();
      // 表头 3 列
      expect(table!.querySelectorAll("thead th")).toHaveLength(3);
      expect(table!.querySelector("thead")!.textContent).toBe("维度2PCTCC");
      // 2 行数据
      expect(table!.querySelectorAll("tbody tr")).toHaveLength(2);
    });

    it("不把分隔行原样显示出来", () => {
      const { container } = render(<MessageBubble role="assistant" text={TABLE} />);

      expect(container.textContent).not.toContain("|---|");
      expect(container.textContent).not.toContain("| 维度 |");
    });

    it("表格外包一层横向滚动容器", () => {
      const { container } = render(<MessageBubble role="assistant" text={TABLE} />);

      const table = container.querySelector("table")!;
      const wrapper = table.parentElement!;
      expect(wrapper.tagName).toBe("DIV");
      expect(wrapper.className).toMatch(/tableWrap/);
    });

    it("未闭合的表格按普通段落渲染且不抛错", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"| a | b |\n\n后续段落正常"} />,
      );

      expect(container.querySelector("table")).toBeNull();
      expect(screen.getByText("后续段落正常")).toBeInTheDocument();
    });
  });

  describe("GFM 其他扩展", () => {
    it("渲染删除线为 del", () => {
      const { container } = render(<MessageBubble role="assistant" text="~~已废弃~~" />);

      const del = container.querySelector("del");
      expect(del).not.toBeNull();
      expect(del!.textContent).toBe("已废弃");
    });

    it("渲染任务列表为勾选框", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"- [x] 已完成\n- [ ] 待办"} />,
      );

      const boxes = container.querySelectorAll('input[type="checkbox"]');
      expect(boxes).toHaveLength(2);
      expect((boxes[0] as HTMLInputElement).checked).toBe(true);
      expect((boxes[1] as HTMLInputElement).checked).toBe(false);
    });

    it("把裸 URL 渲染为链接", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="见 https://example.com/path 说明" />,
      );

      const a = container.querySelector("a");
      expect(a).not.toBeNull();
      expect(a!.getAttribute("href")).toBe("https://example.com/path");
    });
  });

  describe("代码块语法高亮", () => {
    it("对带语言标记的代码块做高亮", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"```java\npublic class A {}\n```"} />,
      );

      const code = container.querySelector("pre code");
      expect(code).not.toBeNull();
      expect(code!.className).toMatch(/hljs/);
      // 真正的信号是 token span：关键字被包成 span 才算高亮成功
      expect(code!.querySelectorAll("span").length).toBeGreaterThan(0);
      expect(code!.querySelector("span.hljs-keyword")).not.toBeNull();
    });

    it("未注册语言降级为纯代码块且不抛错", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"```brainfuck\n+++\n```"} />,
      );

      const code = container.querySelector("pre code");
      expect(code).not.toBeNull();
      expect(code!.textContent).toContain("+++");
      // 降级判据是"没有 token span"，不是"没有 hljs 类"——
      // rehype-highlight 对每个 pre>code 都会无条件加 hljs 类，需显式 no-highlight 才不加。
      expect(code!.querySelectorAll("span")).toHaveLength(0);
    });
  });

  describe("数学公式", () => {
    it("把块级公式渲染为 KaTeX", async () => {
      const { container } = render(<MessageBubble role="assistant" text={"$$E = mc^2$$"} />);

      await waitFor(() => {
        expect(container.querySelector(".katex")).not.toBeNull();
      });
      // 定界符不该留在页面上
      expect(container.textContent).not.toContain("$$");
    });

    it("把行内公式渲染为 KaTeX", async () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"质能方程 $E = mc^2$ 成立"} />,
      );

      await waitFor(() => {
        expect(container.querySelector(".katex")).not.toBeNull();
      });
      // 公式周围的普通文本不受影响
      expect(container.textContent).toContain("质能方程");
      expect(container.textContent).toContain("成立");
    });

    it("非法公式降级为原文且不中断其余渲染", async () => {
      const text = ["$$\\frac{1}{$$", "", "| a | b |", "|---|---|", "| 1 | 2 |"].join("\n");
      const { container } = render(<MessageBubble role="assistant" text={text} />);

      // 关键：整条消息不白屏——同一消息里的表格照常渲染
      expect(container.querySelector("table")).not.toBeNull();
      // 公式处降级显示原始文本，而不是抛错炸掉整条消息
      await waitFor(() => {
        expect(container.textContent).toContain("frac");
      });
    });
  });
});
