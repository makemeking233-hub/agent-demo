import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

// mermaid 在 jsdom 里无法真实渲染（依赖 getBBox 等未实现的 SVG 测量 API），
// 本文件只关心「围栏有没有被图组件接管」，故 mock 掉它以保持确定性。
vi.mock("mermaid", () => ({
  default: {
    initialize: vi.fn(),
    render: vi.fn().mockResolvedValue({ svg: "<svg></svg>" }),
  },
}));

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

  describe("图片", () => {
    it("远程图片直连", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="![架构图](https://example.com/a.png)" />,
      );

      const img = container.querySelector("img");
      expect(img).not.toBeNull();
      expect(img!.getAttribute("src")).toBe("https://example.com/a.png");
      expect(img!.getAttribute("alt")).toBe("架构图");
    });

    it("Windows 绝对路径转 /api/fs/raw", () => {
      const local = "C:\\Users\\me\\arch.png";
      const { container } = render(
        <MessageBubble role="assistant" text={`![图](${local})`} />,
      );

      const img = container.querySelector("img");
      expect(img).not.toBeNull();
      expect(img!.getAttribute("src")).toBe("/api/fs/raw?path=" + encodeURIComponent(local));
    });

    it("含中文与空格的路径经 URI 编码后转 /api/fs/raw", () => {
      const local = "C:\\Users\\me\\我的 文档\\a.png";
      // CommonMark 的链接目标含空格时必须用尖括号形式
      const { container } = render(
        <MessageBubble role="assistant" text={`![图](<${local}>)`} />,
      );

      const img = container.querySelector("img");
      expect(img).not.toBeNull();
      expect(img!.getAttribute("src")).toBe("/api/fs/raw?path=" + encodeURIComponent(local));
    });

    it("加载失败时显示 alt 占位而非碎图", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="![架构图](https://example.com/a.png)" />,
      );

      fireEvent.error(container.querySelector("img")!);

      expect(container.querySelector("img")).toBeNull();
      expect(container.textContent).toContain("架构图");
    });

    it("无法定位的相对路径直接降级为占位，不产生碎图", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="![相对图](./arch.png)" />,
      );

      expect(container.querySelector("img")).toBeNull();
      expect(container.textContent).toContain("相对图");
    });
  });

  describe("安全基线", () => {
    it("原始 script 标签不注入 DOM、不执行", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"<script>alert(1)</script>"} />,
      );

      // 标签被当成纯文本展示（不含任何可执行元素）
      expect(container.querySelector("script")).toBeNull();
      expect(container.textContent).toContain("<script>alert(1)</script>");
    });

    it("原始 HTML 的事件属性不产生可执行元素", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={'<img src=x onerror="alert(1)">'} />,
      );

      expect(container.querySelector("img")).toBeNull();
      expect(container.textContent).toContain("onerror");
    });

    it("javascript: 协议不产生可点击链接", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="[点我](javascript:alert(1))" />,
      );

      // 连 <a> 都不该有：空的 href="" 仍是可点击元素，点下去会整页重载
      expect(container.querySelector("a")).toBeNull();
      expect(container.textContent).toContain("点我");
    });

    it("外链带 target 与 rel 安全属性", () => {
      const { container } = render(
        <MessageBubble role="assistant" text="[文档](https://example.com)" />,
      );

      const a = container.querySelector("a");
      expect(a).not.toBeNull();
      expect(a!.getAttribute("href")).toBe("https://example.com");
      expect(a!.getAttribute("target")).toBe("_blank");
      expect(a!.getAttribute("rel")).toBe("noopener noreferrer");
    });
  });

  /**
   * 流式渲染时序。
   *
   * 注意：这里**不是**红→绿驱动的测试。`useDeferredValue` 的作用是降低 Markdown 重解析的调度
   * 优先级，而 jsdom 里没有可观测的调度差异（真实收益要靠浏览器里的大消息实测）。
   * 因此本组是**回归护栏**：把"增量即时可见、快速追加不丢内容、长文混排不崩"钉死，
   * 防止后续为了性能优化而引入卡顿或内容丢失。
   */
  describe("流式渲染时序", () => {
    it("单次追加的文本立即出现在 DOM 中", () => {
      const { container, rerender } = render(<MessageBubble role="assistant" text="第一段" />);
      expect(container.textContent).toContain("第一段");

      rerender(<MessageBubble role="assistant" text="第一段，第二段" />);
      expect(container.textContent).toContain("第一段，第二段");
    });

    it("快速连续追加后内容完整，取最后一次的值", () => {
      const { container, rerender } = render(<MessageBubble role="assistant" text="A" />);
      for (const t of ["AB", "ABC", "ABCD", "ABCDE", "ABCDEF"]) {
        rerender(<MessageBubble role="assistant" text={t} />);
      }
      expect(container.textContent).toContain("ABCDEF");
    });

    it("长文本与表格、代码块混排时完整渲染", () => {
      const text = [
        ...Array.from({ length: 40 }, (_, i) => `第 ${i} 段落文字。`),
        "",
        "| 维度 | 2PC | TCC |",
        "|---|---|---|",
        "| 一致性 | 强一致 | 最终一致 |",
        "",
        "```java",
        "public class A {}",
        "```",
      ].join("\n");

      const { container } = render(<MessageBubble role="assistant" text={text} />);

      expect(container.querySelector("table")).not.toBeNull();
      expect(container.querySelector("pre code")).not.toBeNull();
      expect(container.textContent).toContain("第 39 段落文字。");
      expect(container.textContent).toContain("一致性");
    });
  });

  describe("mermaid 图", () => {
    it("mermaid 围栏交给图组件接管，不再渲染为代码块", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"```mermaid\nflowchart TD\n  A --> B\n```"} />,
      );

      expect(container.querySelector("[data-mermaid-block]")).not.toBeNull();
      // 不再是被高亮处理过的代码块
      expect(container.querySelector("pre code.hljs")).toBeNull();
    });

    it("其他语言的围栏仍按代码块渲染", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"```java\npublic class A {}\n```"} />,
      );

      expect(container.querySelector("[data-mermaid-block]")).toBeNull();
      expect(container.querySelector("pre code")).not.toBeNull();
    });

    it("未闭合的 mermaid 围栏不被接管", () => {
      const { container } = render(
        <MessageBubble role="assistant" text={"```mermaid\nflowchart TD\n  A --> B"} />,
      );

      // 围栏未闭合时在 markdown 层面就不是 code 节点，自然不产生图块
      expect(container.querySelector("[data-mermaid-block]")).toBeNull();
    });
  });
});
