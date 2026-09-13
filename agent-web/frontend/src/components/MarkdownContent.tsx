import { useDeferredValue, type ComponentPropsWithoutRef } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import "highlight.js/styles/github-dark.css";
import { markdownUrlTransform } from "../lib/image-src";
import { rehypeMathPlaceholder } from "../lib/rehype-math-placeholder";
import { rehypeMermaid } from "../lib/rehype-mermaid";
import { MarkdownImage } from "./MarkdownImage";
import { MarkdownLink } from "./MarkdownLink";
import { MathNode } from "./MathNode";
import { MermaidBlock } from "./MermaidBlock";
import styles from "./MarkdownContent.module.css";

/**
 * 对话区正文的 Markdown 渲染（add-rich-markdown-rendering）。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>方言用 GFM（remark-gfm）：表格 / 删除线 / 任务列表 / 自动链接；
 *   <li>公式：remark-math 只负责解析，KaTeX 由 {@link MathNode} 懒加载（design.md D2）；
 *   <li>mermaid：围栏由 rehype-mermaid 换成自定义标签，运行时由 {@link MermaidBlock} 懒加载
 *       （add-mermaid-diagrams）；该插件排在代码高亮之前，避免图源码先被高亮处理一遍；
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
  img: MarkdownImage,
  a: MarkdownLink,
  // math-inline / math-block / mermaid-block 是自定义 rehype 插件产出的标签。
  // react-markdown 运行时按 tagName 查表，能命中；但这些标签不在 JSX.IntrinsicElements 里，
  // 与 Components 的映射类型对不上，故在此收口处做一次类型断言。
  "math-inline": (props: { tex?: string }) => <MathNode tex={props.tex ?? ""} display={false} />,
  "math-block": (props: { tex?: string }) => <MathNode tex={props.tex ?? ""} display />,
  "mermaid-block": (props: { source?: string }) => <MermaidBlock source={props.source ?? ""} />,
} as unknown as Components;

export function MarkdownContent(props: { text: string }) {
  // 流式追加时每个 message_delta 都会推一次 text，而 Markdown 重解析（表格 / 高亮 / 公式）
  // 相对昂贵。useDeferredValue 把解析降到低优先级由 React 调度，文本写入本身仍是即时的。
  // 相比手写 150ms 定时器：无需在流式结束或组件卸载时清理 timer（不会漏 / 不会竞态），
  // 也不会把"文本追加"一起延迟——延迟的只是解析。
  const deferredText = useDeferredValue(props.text);
  return (
    <div className={styles.markdown}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeMermaid, rehypeHighlight, rehypeMathPlaceholder]}
        components={markdownComponents}
        urlTransform={markdownUrlTransform}
      >
        {deferredText}
      </ReactMarkdown>
    </div>
  );
}
