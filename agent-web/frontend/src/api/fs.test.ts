/**
 * /api/fs 错误类测试（native-folder-picker）。
 *
 * <p>客户端函数（listDir/mkdir/getHome/getDrives/getQuickAccess）已被移除；
 * 本文件保留 FsError 类测试以维持覆盖率基线。
 */

import { describe, expect, it } from "vitest";
import { FsError } from "./fs";

describe("FsError", () => {
  it("stores status, code and message", () => {
    const e = new FsError(404, "path_not_found", "路径不存在");
    expect(e.status).toBe(404);
    expect(e.code).toBe("path_not_found");
    expect(e.message).toBe("路径不存在");
    expect(e.name).toBe("FsError");
    expect(e).toBeInstanceOf(Error);
  });
});
