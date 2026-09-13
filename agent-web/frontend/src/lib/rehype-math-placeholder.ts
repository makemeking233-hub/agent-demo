import type { Element, ElementContent, Root } from "hast";

const MATH_INLINE_CLASS = "math-inline";
const MATH_DISPLAY_CLASS = "math-display";

/** 取出元素下所有文本（remark-math 产出的 math 元素里就是原始 TeX）。 */
function textOf(node: Element): string {
  let out = "";
  for (const child of node.children) {
    if (child.type === "text") out += child.value;
    else if (child.type === "element") out += textOf(child);
  }
  return out;
}

function transformElement(node: Element): ElementContent {
  const classes = node.properties?.className;
  if (
    Array.isArray(classes) &&
    (classes.includes(MATH_INLINE_CLASS) || classes.includes(MATH_DISPLAY_CLASS))
  ) {
    const display = classes.includes(MATH_DISPLAY_CLASS);
    return {
      type: "element",
      tagName: display ? "math-block" : "math-inline",
      properties: { tex: textOf(node) },
      children: [],
    };
  }
  node.children = node.children.map((child) =>
    child.type === "element" ? transformElement(child) : child,
  );
  return node;
}

/**
 * 把 remark-math 产出的公式元素换成自定义标签（add-rich-markdown-rendering，design.md D2）。
 *
 * <p>remark-math 经 remark-rehype 后留下的是 `<span class="math math-inline">` /
 * `<div class="math math-display">`。若直接映射 React 的 `span`/`div`，会把所有普通 span
 * （含代码高亮的 token span）一起截走。换成 `math-inline` / `math-block` 自定义标签后，
 * 就能在 `components` 里精确命中，交给 {@link MathNode} 懒加载渲染 KaTeX。
 */
export function rehypeMathPlaceholder() {
  return (tree: Root): undefined => {
    tree.children = tree.children.map((child) =>
      child.type === "element" ? transformElement(child) : child,
    );
    return undefined;
  };
}
