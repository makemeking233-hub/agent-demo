/**
 * /api/fs 类型与错误类（native-folder-picker）。
 *
 * <p>WorkspacePickerModal 改为调 POST /api/workspaces/pick-folder，
 * 此处仅保留 FsError 类型供其他模块复用；listDir/mkdir/getDrives/getQuickAccess/getHome
 * 客户端函数已删除（FsController 仍保留端点供其他场景）。
 */

export class FsError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "FsError";
  }
}
