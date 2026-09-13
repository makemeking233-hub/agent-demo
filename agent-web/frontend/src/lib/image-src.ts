import { defaultUrlTransform } from "react-markdown";

const HTTP_URL = /^https?:\/\//i;
const DATA_IMAGE = /^data:image\//i;
const FILE_URL = /^file:\/\//i;
/** Windows 盘符绝对路径，如 `C:\Users\...` 或 `C:/Users/...`。 */
const WINDOWS_DRIVE = /^[a-zA-Z]:[\\/]/;
/** UNC 路径，如 `\\server\share\a.png`。 */
const UNC = /^\\\\/;

function rawEndpoint(path: string): string {
  return `/api/fs/raw?path=${encodeURIComponent(path)}`;
}

/**
 * 把 Markdown 图片地址解析成浏览器可直接加载的 URL（add-rich-markdown-rendering，design.md D4）。
 *
 * <p>返回 `null` 表示"无法定位"——调用方据此降级为 alt 占位，而不是产生碎图。
 *
 * <ul>
 *   <li>`http(s)://` 与 `data:image/` 原样透传；
 *   <li>本地绝对路径（Windows 盘符 / UNC / POSIX 绝对路径）与 `file://` 一律转
 *       `/api/fs/raw?path=...`，交由后端在 `$HOME` 边界内校验后返回字节；
 *   <li>**相对路径返回 null**：前端没有工作区上下文，无法可靠地把它解析成绝对路径。
 * </ul>
 *
 * <p>注意：进来的字符串可能已被 `mdast-util-to-hast` 规范化成 URI——markdown 里写的
 * `C:\Users\me\a.png` 到这一步会变成 `C:%5CUsers%5Cme%5Ca.png`。因此**判类型前必须先解码**，
 * 否则反斜杠路径一律识别不出。远程 URL 不走解码（其 `%20` 等编码本就该保留）。
 */
export function toImageSrc(raw: string): string | null {
  const value = raw.trim();
  if (!value) return null;
  if (HTTP_URL.test(value) || DATA_IMAGE.test(value)) return value;

  const decoded = tryDecode(value);
  if (FILE_URL.test(decoded)) {
    const stripped = decoded.replace(FILE_URL, "").replace(/^\/([a-zA-Z]:)/, "$1");
    return rawEndpoint(stripped);
  }
  if (WINDOWS_DRIVE.test(decoded) || UNC.test(decoded)) return rawEndpoint(decoded);
  // POSIX 绝对路径；`//host/...` 是协议相对地址，不属于本地路径
  if (decoded.startsWith("/") && !decoded.startsWith("//")) return rawEndpoint(decoded);
  return null;
}

/** 解码失败（畸形百分号序列，如 `100%`）时按原样返回，不抛异常。 */
function tryDecode(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

/**
 * react-markdown 的 URL 变换钩子。
 *
 * <p>`src` 走 {@link toImageSrc}——**这一步是必需的**，因为 react-markdown 默认的
 * {@link defaultUrlTransform} 只放行 `http(s)` 等少数协议，会把 `C:\...` 这种 Windows 路径
 * 判成危险协议而清空（它按 `:` 与 `/` 的相对位置判断，看不见反斜杠）。
 *
 * <p>`href` 保持默认行为（拦掉 `javascript:` 等），安全语义不变。
 */
export function markdownUrlTransform(url: string, key: string): string {
  if (key === "src") {
    return toImageSrc(url) ?? "";
  }
  return defaultUrlTransform(url);
}
