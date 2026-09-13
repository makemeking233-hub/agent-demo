import ReactMarkdown, { type Components } from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
// 按需注册的语言集（design.md D3）：不注册就是 highlight.js 全量 190+ 语言，观感收益为零。
import bash from "highlight.js/lib/languages/bash";
import css from "highlight.js/lib/languages/css";
import diff from "highlight.js/lib/languages/diff";
import dockerfile from "highlight.js/lib/languages/dockerfile";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import json from "highlight.js/lib/languages/json";
import markdown from "highlight.js/lib/languages/markdown";
import properties from "highlight.js/lib/languages/properties";
import python from "highlight.js/lib/languages/python";
import sql from "highlight.js/lib/languages/sql";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import yaml from "highlight.js/lib/languages/yaml";
import "highlight.js/styles/github-dark.css";
import styles from "./MarkdownContent.module.css";

/**
 * 对话区正文的 Markdown 渲染（add-rich-markdown-rendering）。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>方言用 GFM（remark-gfm）：表格 / 删除线 / 任务列表 / 自动链接；
 *   <li>代码高亮用 rehype-highlight，只注册常用语言（`xml` 自带 html 别名，`typescript` 自带 tsx，
 *       `javascript` 自带 jsx），未注册语言自动降级为纯代码块；
 *   <li>**不挂 rehype-raw**——原始 HTML 一律按纯文本转义，这是本模块的安全底线；
 *   <li>表格外包 `.tableWrap` 横向滚动容器，宽表格不撑破气泡。
 * </ul>
 */
const HIGHLIGHT_LANGUAGES = {
  bash,
  css,
  diff,
  dockerfile,
  java,
  javascript,
  json,
  markdown,
  properties,
  python,
  sql,
  typescript,
  xml,
  yaml,
};

/** 表格外包横向滚动容器，避免宽表格撑破气泡（markdown 样式见 MarkdownContent.module.css）。 */
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
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { languages: HIGHLIGHT_LANGUAGES }]]}
        components={markdownComponents}
      >
        {props.text}
      </ReactMarkdown>
    </div>
  );
}
