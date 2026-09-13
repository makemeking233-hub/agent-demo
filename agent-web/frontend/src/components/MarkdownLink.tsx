import type { ComponentPropsWithoutRef } from "react";
import styles from "./MarkdownContent.module.css";

/**
 * 消息内链接（add-rich-markdown-rendering，design.md D5）。
 *
 * <ul>
 *   <li>外链统一 `target="_blank"` + `rel="noopener noreferrer"`，避免新标签页通过
 *       `window.opener` 反向操作本页；
 *   <li>`href` 为空时**不渲染 `<a>`**——被 `defaultUrlTransform` 拦掉的 `javascript:` 等危险协议
 *       会得到一个 `href=""` 的空链接，那仍是个可点击元素，点下去会整页重载。这里降级为纯文本，
 *       连"可点击"都不给。
 * </ul>
 */
export function MarkdownLink(props: ComponentPropsWithoutRef<"a">) {
  const href = typeof props.href === "string" ? props.href : "";
  if (!href) {
    return <span className={styles.linkDisabled}>{props.children}</span>;
  }
  return <a {...props} href={href} target="_blank" rel="noopener noreferrer" />;
}
