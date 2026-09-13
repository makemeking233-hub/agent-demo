import type { Element } from "hast";

/** 取出一个 hast 元素下所有文本（含嵌套元素），用于还原围栏/公式里的原始源码。 */
export function textOf(node: Element): string {
  let out = "";
  for (const child of node.children) {
    if (child.type === "text") out += child.value;
    else if (child.type === "element") out += textOf(child);
  }
  return out;
}
