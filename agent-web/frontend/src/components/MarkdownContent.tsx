import type { ComponentPropsWithoutRef } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import "highlight.js/styles/github-dark.css";
import { rehypeMathPlaceholder } from "../lib/rehype-math-placeholder";
import { MathNode } from "./MathNode";
import styles from "./MarkdownContent.module.css";

/**
 * 对话区正文的 Markdown 渲染（add-rich-markdown-rendering）。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>方言用 GFM（remark-gfm）：表格 / 删除线 / 任务列表 / 自动链接；
 *   <li>公式：remark-math 只负责解析，KaTeX 由 {@link MathNode} 懒加载（design.md D2）；
 *   <li>代码高亮用 rehype-highlight 的**默认** `common` 语言集（34 个）。刻意不传 `languages`
 *       自选子集——`lowlight` 的 `common` 被 rehype-highlight 顶层静态 import，传不传都会进包，
 *       自选只会白白砍掉 go/rust/c/cpp 等语言（design.md D3，2026-09-13 实测纠正）；
 *   <li>**不挂 rehype-raw**——原始 HTML 一律按纯文本转义，这是本模块的安全底线；
 *   <li>表格外包 `.tableWrap` 横向滚动容器，宽表格不撑破气泡。
 * </ul>
 */
const markdownComponents = {
  /** 表格外包横向滚动容器，避免宽表格撑破气泡（样式见 MarkdownContent.module.css）。 */
  table: (props: ComponentPropsWithoutRef<"table">) => (
    <div className={styles.tableWrap}>
      <table>{props.children}</table>
    </div>
  ),
  // math-inline / math-block 是 rehype-math-placeholder 产出的自定义标签。
  // react-markdown 运行时按 tagName 查表，能命中；但这两个标签不在 JSX.IntrinsicElements 里，
  // 与 Components 的映射类型对不上，故在此收口处做一次类型断言。
  "math-inline": (props: { tex?: string }) => <MathNode tex={props.tex ?? ""} display={false} />,
  "math-block": (props: { tex?: string }) => <MathNode tex={props.tex ?? ""} display />,
} as unknown as Components;

export function MarkdownContent(props: { text: string }) {
  return (
    <div className={styles.markdown}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeHighlight, rehypeMathPlaceholder]}
        components={markdownComponents}
      >
        {props.text}
      </ReactMarkdown>
    </div>
  );
}
