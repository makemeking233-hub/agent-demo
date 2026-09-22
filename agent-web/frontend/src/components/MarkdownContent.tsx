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

/**
 * 对话区正文的 Markdown 渲染（add-rich-markdown-rendering
 * → shadcn-components-p2：从 CSS Modules 迁到 Tailwind utility，详见 index.css 的
 * `.prose-sm` 块）。
 */
const markdownComponents = {
  /** 表格外包横向滚动容器，避免宽表格撑破气泡。 */
  table: (props: ComponentPropsWithoutRef<"table">) => (
    <div className="my-2 max-w-full overflow-x-auto">
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
    <div className="prose-sm">
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
