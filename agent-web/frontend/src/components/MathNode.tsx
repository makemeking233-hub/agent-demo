import { useEffect, useState } from "react";
import styles from "./MarkdownContent.module.css";

/**
 * 懒加载 KaTeX 的公式节点（add-rich-markdown-rendering，design.md D2）。
 *
 * <p>刻意不用 `rehype-katex`：它在模块顶层 `import katex`，会把约 270 KB 的 JS 焊进主 bundle，
 * 而绝大多数回答里根本没有公式。这里把 katex 与它的 CSS 都推迟到"真的出现公式时"才加载。
 *
 * <p>用 `renderToString` 而不是 `katex.render`：后者直接改 DOM，而这段 DOM 归 React 管——
 * 流式追加时 React 的 virtual DOM 会与实际 DOM 不一致。
 *
 * <p>`trust: false`（KaTeX 默认值，这里显式写出）禁止 `\href`/`\url` 生成可点击链接，
 * 避免公式成为新的注入面。
 */
export function MathNode(props: { tex: string; display: boolean }) {
  const { tex, display } = props;
  const [html, setHtml] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const [katex] = await Promise.all([
          import("katex"),
          import("katex/dist/katex.min.css"),
        ]);
        if (cancelled) return;
        // throwOnError:false —— 非法公式由 KaTeX 就地渲染为原文，不抛异常
        setHtml(
          katex.default.renderToString(tex, {
            displayMode: display,
            throwOnError: false,
            trust: false,
          }),
        );
      } catch {
        // 加载失败：保持 html=null，下面会显示 TeX 原文，不炸掉整条消息
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [tex, display]);

  if (html === null) {
    // 未就绪或加载失败：先显示 TeX 原文，既不白屏也不产生布局跳空
    return display ? (
      <div className={styles.mathBlock}>{tex}</div>
    ) : (
      <span className={styles.mathInline}>{tex}</span>
    );
  }
  return display ? (
    <div className={styles.mathBlock} dangerouslySetInnerHTML={{ __html: html }} />
  ) : (
    <span className={styles.mathInline} dangerouslySetInnerHTML={{ __html: html }} />
  );
}
