import { useState, type ComponentPropsWithoutRef } from "react";

/**
 * 消息内图片（add-rich-markdown-rendering，design.md D4）。
 *
 * <p>两种降级都收敛到同一个 alt 占位，避免浏览器默认的碎图图标：
 *
 * <ul>
 *   <li>`src` 为空——地址无法定位（相对路径、被 URL 变换拦掉的协议）；
 *   <li>图片加载失败（404 / 403 / 网络不可达），由 `onError` 触发。
 * </ul>
 */
export function MarkdownImage(props: ComponentPropsWithoutRef<"img">) {
  const [failed, setFailed] = useState(false);
  const alt = props.alt ?? "";
  const src = typeof props.src === "string" ? props.src : "";

  if (failed || !src) {
    return (
      <span
        className="inline-block rounded-sm border border-dashed border-border px-2 py-1 text-[13px] text-muted-foreground"
        role="img"
        aria-label={alt || "图片无法显示"}
      >
        {alt || "图片无法显示"}
      </span>
    );
  }
  return (
    <img {...props} src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
  );
}
