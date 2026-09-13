import type { Element, ElementContent, Root } from "hast";
import { textOf } from "./hast-text";

const MERMAID_LANGUAGE_CLASS = "language-mermaid";

/**
 * rehype 插件拿到的 `file`（结构上只需有 `value`）。
 *
 * <p>react-markdown 会把原始 markdown 写进 `file.value` 再交给插件，因此这里能读到"未经解析的
 * 原文"——判断围栏有没有闭合必须靠它，见 {@link fenceCount}。
 */
type MarkdownFile = { value?: unknown };

/** 统计原文里围栏标记行的数量；奇数表示最后一段围栏还没闭合。 */
function fenceCount(raw: string): number {
  let count = 0;
  for (const line of raw.split("\n")) {
    if (/^\s*```/.test(line)) count += 1;
  }
  return count;
}

function mermaidSourceOf(pre: Element): string | null {
  if (pre.tagName !== "pre") return null;
  const code = pre.children.find(
    (child): child is Element => child.type === "element" && child.tagName === "code",
  );
  const classes = code?.properties?.className;
  if (!code || !Array.isArray(classes) || !classes.includes(MERMAID_LANGUAGE_CLASS)) return null;
  return textOf(code);
}

function transformElement(node: Element, allowConvert: boolean): ElementContent {
  const source = allowConvert ? mermaidSourceOf(node) : null;
  if (source !== null) {
    return {
      type: "element",
      tagName: "mermaid-block",
      properties: { source },
      children: [],
    };
  }
  node.children = node.children.map((child) =>
    child.type === "element" ? transformElement(child, true) : child,
  );
  return node;
}

/**
 * 把 ```` ```mermaid ```` 围栏换成自定义标签 `mermaid-block`（add-mermaid-diagrams，design.md D1）。
 *
 * <p>为什么不在 `components.code` 里按 className 判别：那个映射会命中**所有** `code` 元素
 * （含行内代码与高亮后的每个 token 容器），要在其中判别并返回一个块级组件既别扭，又容易破坏
 * 行内代码的既有行为。换成自定义标签后就能在 `components` 里精确命中，与既有的
 * `rehype-math-placeholder` 完全同构。
 *
 * <p><strong>未闭合的围栏不接管</strong>：CommonMark 里未闭合的 fenced code block 会一路延伸到
 * 文档结尾，**仍然是一个合法的 code 节点**——所以"等围栏闭合再渲染"不能指望解析器天然给出区别，
 * 必须自己比对原文的围栏计数。流式输出时最后一段围栏计数为奇数，此时对它不做接管，按代码块显示
 * 源码；否则会拿半张图反复送去渲染、反复报错。
 */
export function rehypeMermaid() {
  return (tree: Root, file?: MarkdownFile): undefined => {
    const raw = typeof file?.value === "string" ? file.value : "";
    // 计数为偶数（含空文档）时不存在未闭合围栏。
    const closed = fenceCount(raw) % 2 === 0;
    // 未闭合的那一段必然是文档里的最后一段。
    const lastIndex = tree.children.length - 1;

    tree.children = tree.children.map((child, index) => {
      if (child.type !== "element") return child;
      return transformElement(child, closed || index !== lastIndex);
    });
    return undefined;
  };
}
