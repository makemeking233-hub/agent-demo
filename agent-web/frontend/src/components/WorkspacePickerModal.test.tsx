/**
 * WorkspacePickerModal 总装测试（add-workspace-picker-modal）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>打开 → 默认定位 home / 优先读 localStorage / 失效回退 home；
 *   <li>双击进入子目录、单击选中、文件不可选；
 *   <li>name 输入校验；
 *   <li>提交调 onSubmit(name, dir)，成功后关闭；
 *   <li>Esc 关闭；
 *   <li>关闭时 localStorage 写入；
 *   <li>新建文件夹 inline + 错误处理。
 * </ul>
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";

// 用 hoisted 变量确保 vi.mock factory 拿到正确引用
const mocks = vi.hoisted(() => ({
  getHome: vi.fn(),
  listDir: vi.fn(),
  mkdir: vi.fn(),
  getDrives: vi.fn(),
  getQuickAccess: vi.fn(),
}));

vi.mock("../api/fs", async () => {
  const actual =
    await vi.importActual<typeof import("../api/fs")>("../api/fs");
  return {
    ...actual,
    getHome: mocks.getHome,
    listDir: mocks.listDir,
    mkdir: mocks.mkdir,
    getDrives: mocks.getDrives,
    getQuickAccess: mocks.getQuickAccess,
    FsError: actual.FsError,
  };
});

const HOME = "/home/user";
const PROJECTS = "/home/user/projects";
const AGENT_DEMO = "/home/user/projects/agent-demo";

beforeEach(() => {
  localStorage.clear();
  mocks.getHome.mockReset();
  mocks.listDir.mockReset();
  mocks.mkdir.mockReset();
  mocks.getDrives.mockReset();
  mocks.getQuickAccess.mockReset();
  mocks.getHome.mockResolvedValue({ path: HOME, platform: "linux" });
  mocks.getDrives.mockResolvedValue({ drives: [] });
  mocks.getQuickAccess.mockResolvedValue({
    items: [
      { name: "Home", path: HOME },
      { name: "Documents", path: `${HOME}/Documents` },
    ],
  });
  mocks.listDir.mockImplementation(async (p: string) => ({
    path: p,
    parent: p === HOME ? null : PROJECTS,
    entries:
      p === HOME
        ? [
            { name: "projects", path: PROJECTS, isDir: true, size: 0, mtime: 1 },
            { name: "README.md", path: `${HOME}/README.md`, isDir: false, size: 100, mtime: 2 },
          ]
        : p === PROJECTS
          ? [
              { name: "agent-demo", path: AGENT_DEMO, isDir: true, size: 0, mtime: 3 },
              { name: "md-main", path: `${PROJECTS}/md-main`, isDir: true, size: 0, mtime: 4 },
            ]
          : [],
  }));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function renderModal(overrides: Partial<Parameters<typeof WorkspacePickerModal>[0]> = {}) {
  const props = {
    open: true,
    onClose: vi.fn(),
    onSubmit: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  const view = render(<WorkspacePickerModal {...props} />);
  return { ...props, ...view };
}

describe("WorkspacePickerModal 初始化", () => {
  it("默认定位到 home（无 localStorage）", async () => {
    renderModal();
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalled());
    expect(mocks.listDir).toHaveBeenCalledWith(HOME, false);
    expect(await screen.findByText("projects")).toBeInTheDocument();
  });

  it("读 localStorage 记住的位置", async () => {
    localStorage.setItem("agent-demo.workspace-picker.last-path", PROJECTS);
    renderModal();
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalled());
    expect(mocks.listDir.mock.calls[0][0]).toBe(PROJECTS);
    expect(await screen.findByText("agent-demo")).toBeInTheDocument();
  });

  it("localStorage 路径不在 home 内 → 回退 home", async () => {
    localStorage.setItem("agent-demo.workspace-picker.last-path", "/etc/passwd");
    renderModal();
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalled());
    expect(mocks.listDir.mock.calls[0][0]).toBe(HOME);
  });
});

describe("WorkspacePickerModal 导航", () => {
  it("双击目录进入下级", async () => {
    renderModal();
    const projectsItem = await screen.findByText("projects");
    fireEvent.doubleClick(projectsItem);
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledWith(PROJECTS, false));
    expect(await screen.findByText("agent-demo")).toBeInTheDocument();
  });

  it("底部文件夹路径框 Enter 跳转", async () => {
    renderModal();
    await screen.findByText("projects");
    const input = screen.getByLabelText("文件夹路径");
    fireEvent.change(input, { target: { value: PROJECTS } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledWith(PROJECTS, false));
  });

  it("单击目录选中（footer 显示路径 + 默认 basename）", async () => {
    renderModal();
    const projects = await screen.findByText("projects");
    fireEvent.click(projects);
    const nameInput = screen.getByLabelText("工作区名称") as HTMLInputElement;
    expect(nameInput.value).toBe("projects");
  });

  it("文件不可选（按钮 disabled）", async () => {
    renderModal();
    const readme = await screen.findByText("README.md");
    const btn = readme.closest("button")!;
    expect(btn).toBeDisabled();
  });
});

describe("WorkspacePickerModal 提交", () => {
  it("默认选 directory + 改 name → 提交调 onSubmit(name, dir)", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    renderModal({ onSubmit, onClose });

    const projects = await screen.findByText("projects");
    fireEvent.click(projects);
    const nameInput = screen.getByLabelText("工作区名称") as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: "my-ws" } });
    fireEvent.click(screen.getByText("选择此目录"));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("my-ws", PROJECTS));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("name 含非法字符 → 选择此目录按钮 disabled", async () => {
    renderModal();
    const projects = await screen.findByText("projects");
    fireEvent.click(projects);
    const nameInput = screen.getByLabelText("工作区名称") as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: "bad name" } });
    const btn = screen.getByText("选择此目录") as HTMLButtonElement;
    expect(btn).toBeDisabled();
  });

  it("提交失败 → 显示错误条 + 不关闭", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("workspace_exists"));
    const onClose = vi.fn();
    renderModal({ onSubmit, onClose });
    const projects = await screen.findByText("projects");
    fireEvent.click(projects);
    fireEvent.click(screen.getByText("选择此目录"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText(/workspace_exists/)).toBeInTheDocument());
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe("WorkspacePickerModal 关闭 + 持久化", () => {
  it("Esc 关闭 + localStorage 写入当前路径", async () => {
    const onClose = vi.fn();
    renderModal({ onClose });
    await screen.findByText("projects");
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
    expect(localStorage.getItem("agent-demo.workspace-picker.last-path")).toBe(HOME);
  });

  it("点击 overlay 关闭", async () => {
    const onClose = vi.fn();
    const { container } = renderModal({ onClose });
    await screen.findByText("projects");
    // overlay 是最外层 div
    const overlay = container.firstChild as HTMLElement;
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalled();
  });
});

describe("WorkspacePickerModal 新建文件夹", () => {
  it("工具栏 → 输入名 → 创建 → 调 mkdir + 刷新列表", async () => {
    mocks.mkdir.mockResolvedValue({ path: `${PROJECTS}/fresh` });
    renderModal();
    await screen.findByText("projects");
    // 先双击进 projects 目录
    const projects = await screen.findByText("projects");
    fireEvent.doubleClick(projects);
    await screen.findByText("agent-demo");
    fireEvent.click(screen.getByText("新建文件夹"));
    const input = screen.getByPlaceholderText("新文件夹名");
    fireEvent.change(input, { target: { value: "fresh" } });
    fireEvent.click(screen.getByText("创建"));
    await waitFor(() => expect(mocks.mkdir).toHaveBeenCalledWith(`${PROJECTS}/fresh`));
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledTimes(3)); // home + projects + refresh
  });

  it("名称非法 → 显示错误条 + 不调 mkdir", async () => {
    renderModal();
    await screen.findByText("projects");
    fireEvent.click(screen.getByText("新建文件夹"));
    const input = screen.getByPlaceholderText("新文件夹名");
    fireEvent.change(input, { target: { value: "bad name" } });
    fireEvent.click(screen.getByText("创建"));
    expect(mocks.mkdir).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByText(/名称非法/)).toBeInTheDocument());
  });
});

// ===== polish-workspace-picker-dsh-style 新增用例 =====

describe("WorkspacePickerModal DSH 风格", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    mocks.getHome.mockReset();
    mocks.listDir.mockReset();
    mocks.mkdir.mockReset();
    mocks.getDrives.mockReset();
    mocks.getQuickAccess.mockReset();
    mocks.getHome.mockResolvedValue({ path: HOME, platform: "linux" });
    mocks.getDrives.mockResolvedValue({ drives: [] });
    mocks.getQuickAccess.mockResolvedValue({
      items: [
        { name: "Home", path: HOME },
        { name: "Documents", path: `${HOME}/Documents` },
      ],
    });
    mocks.listDir.mockImplementation(async (p: string) => ({
      path: p,
      parent: p === HOME ? null : PROJECTS,
      entries:
        p === HOME
          ? [
              { name: "projects", path: PROJECTS, isDir: true, size: 0, mtime: 1 },
              { name: "README.md", path: `${HOME}/README.md`, isDir: false, size: 100, mtime: 2 },
            ]
          : p === PROJECTS
            ? [
                { name: "agent-demo", path: AGENT_DEMO, isDir: true, size: 0, mtime: 3 },
                { name: "md-main", path: `${PROJECTS}/md-main`, isDir: true, size: 0, mtime: 4 },
              ]
            : [],
    }));
    mocks.mkdir.mockResolvedValue({ path: `${PROJECTS}/new` });
  });
  afterEach(() => cleanup());

  it("title aria-label 是中文 '选择工作区目录'", async () => {
    renderModal();
    await screen.findByRole("dialog", { name: "选择工作区目录" });
  });

  it("左侧导航树显示 quickAccess 列表（Home + Documents）", async () => {
    renderModal();
    await screen.findByText("projects"); // 等 Modal 渲染完
    // Documents 在左侧 tree 里出现（同时 listDir mock 返回空）
    const tree = screen.getByLabelText("导航树");
    expect(within(tree).getByText("Home")).toBeInTheDocument();
    expect(within(tree).getByText("Documents")).toBeInTheDocument();
  });

  it("history 后退 + 前进 + 边界禁用", async () => {
    renderModal();
    await screen.findByText("projects");
    const back = screen.getByLabelText("后退") as HTMLButtonElement;
    const forward = screen.getByLabelText("前进") as HTMLButtonElement;
    // 初始只有 home 一次记录，无前驱节点
    expect(back).toBeDisabled();
    expect(forward).toBeDisabled();

    // 双击进入 projects 目录 → history 增长
    fireEvent.doubleClick(screen.getByText("projects"));
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledWith(PROJECTS, false));
    // 现在可以后退
    await waitFor(() => expect(back).not.toBeDisabled());
    fireEvent.click(back);
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledWith(HOME, false));
    // 后退后 forward 启用
    await waitFor(() => expect(forward).not.toBeDisabled());
  });

  it("列头点击切换排序（点击 修改时间 后带 ↑）", async () => {
    renderModal();
    await screen.findByText("projects");
    const mtimeHeader = screen.getByRole("button", { name: /^修改时间/ });
    fireEvent.click(mtimeHeader);
    await waitFor(() => expect(mtimeHeader.textContent).toMatch(/↑/));
    // 再点切换降序
    fireEvent.click(mtimeHeader);
    await waitFor(() => expect(mtimeHeader.textContent).toMatch(/↓/));
  });

  it("底部文件夹路径框回车跳转非法路径显示错误", async () => {
    renderModal();
    await screen.findByText("projects");
    // 让 listDir 对 /etc/passwd 抛 path_outside_home
    mocks.listDir.mockImplementation(async (p: string) => {
      if (!p.startsWith(HOME)) {
        const { FsError } = await import("../api/fs");
        throw new FsError(403, "path_outside_home", "路径不在家目录范围内");
      }
      return { path: p, parent: null, entries: [] };
    });
    const input = screen.getByLabelText("文件夹路径") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "/etc/passwd" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() =>
      expect(screen.getByText(/家目录范围内/)).toBeInTheDocument(),
    );
  });

  it("显示隐藏切换调 listDir(path, true)", async () => {
    renderModal();
    await screen.findByText("projects");
    fireEvent.click(screen.getByLabelText("显示隐藏文件"));
    await waitFor(() => expect(mocks.listDir).toHaveBeenCalledWith(HOME, true));
  });
});
