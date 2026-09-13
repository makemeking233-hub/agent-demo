import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import styles from "./MarkdownContent.module.css";

/**
 * 对话区正文的 Markdown 渲染（add-rich-markdown-rendering）。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>方言用 GFM（remark-gfm）：表格 / 删除线 / 任务列表 / 自动链接；
 *   <li>**不挂 rehype-raw**——原始 HTML 一律按纯文本转义，这是本模块的安全底线；
 *   <li>表格外包 `.tableWrap` 横向滚动容器，宽表格不撑破气泡。
 * </ul>
 */
const markdownComponents: Components = {
  table(props) {
    return (
      <div className={styles.tableWrap}>
        <table>{props.children}</table>
      </div>
    );
  },
};

export function MarkdownContent(props: { text: string }) {
  return (
    <div className={styles.markdown}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {props.text}
      </ReactMarkdown>
    </div>
  );
}
