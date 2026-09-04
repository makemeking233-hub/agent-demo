import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Sidebar, type SidebarSession } from "./Sidebar";

// 嵌入的 WorkspacePickerModal 会调 /api/fs/*；mock 掉避免真实 fetch
vi.mock("../api/fs", () => ({
  getHome: vi.fn().mockResolvedValue({ path: "/home/user", platform: "linux" }),
  listDir: vi.fn().mockResolvedValue({ path: "/home/user", parent: null, entries: [] }),
  mkdir: vi.fn().mockResolvedValue({ path: "/home/user/new" }),
  getDrives: vi.fn().mockResolvedValue({ drives: [] }),
}));

function sess(id: string, title: string, time: number, workspace = "agent-demo"): SidebarSession {
  return { id, title, preview: "", workspace, time };
}

const sessions: SidebarSession[] = [
  sess("s1", "一", 1),
  sess("s2", "二", 2),
  sess("s3", "三", 3),
  sess("s4", "四", 4),
  sess("s5", "五", 5),
  sess("s6", "六", 6),
  sess("s7", "七", 7),
  sess("s8", "八", 8),
];

describe("Sidebar 会话管理", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });
  afterEach(() => cleanup());

  function renderSidebar(overrides: Partial<Parameters<typeof Sidebar>[0]> = {}) {
    const props = {
      sessions,
      archived: [] as SidebarSession[],
      workspaces: [] as { name: string; dir: string; sessionCount: number }[],
      activeWorkspace: "agent-demo",
      currentSessionId: null as string | null,
      onSelect: vi.fn(),
      onNewSession: vi.fn(),
      onWorkspaceChange: vi.fn(),
      onRename: vi.fn(),
      onCreateWorkspace: vi.fn(),
      onArchive: vi.fn(),
      onRestore: vi.fn(),
      onCollapseToggle: vi.fn(),
      ...overrides,
    };
    render(<Sidebar {...props} />);
    return props;
  }

  it("默认每工作区只显示前 5 个，其余收进展开按钮", () => {
    renderSidebar();
    expect(screen.getByText("一")).toBeInTheDocument();
    expect(screen.getByText("五")).toBeInTheDocument();
    expect(screen.queryByText("六")).not.toBeInTheDocument();
    expect(screen.getByText(/展开其余 3 个会话/)).toBeInTheDocument();
  });

  it("点击展开显示全部", () => {
    renderSidebar();
    fireEvent.click(screen.getByText(/展开其余 3 个会话/));
    expect(screen.getByText("六")).toBeInTheDocument();
    expect(screen.getByText("八")).toBeInTheDocument();
    expect(screen.getByText("收起")).toBeInTheDocument();
  });

  it("新会话按钮触发 onNewSession", () => {
    const p = renderSidebar();
    fireEvent.click(screen.getByText("新会话"));
    expect(p.onNewSession).toHaveBeenCalledTimes(1);
  });

  it("会话行 ... 菜单归档后调 onArchive", () => {
    const p = renderSidebar();
    fireEvent.click(screen.getAllByLabelText("会话操作")[0]);
    fireEvent.click(screen.getByText("归档"));
    expect(p.onArchive).toHaveBeenCalled();
  });

  it("会话行 ... 菜单重命名提交后调 onRename", () => {
    const p = renderSidebar();
    fireEvent.click(screen.getAllByLabelText("会话操作")[0]);
    fireEvent.click(screen.getByText("重命名"));
    const input = screen.getByDisplayValue("一");
    fireEvent.change(input, { target: { value: "改标题" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(p.onRename).toHaveBeenCalledWith("s1", "改标题");
  });

  it("点击新建工作区 + 弹出 WorkspacePickerModal", async () => {
    const p = renderSidebar();
    fireEvent.click(screen.getByLabelText("新建工作区"));
    // Modal 出现，含 "选择工作区目录" 标题
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "选择工作区目录" })).toBeInTheDocument(),
    );
    // 关闭 Modal → dialog 消失
    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "选择工作区目录" })).not.toBeInTheDocument(),
    );
    expect(p.onCreateWorkspace).not.toHaveBeenCalled();
  });

  it("归档视图列出归档会话并可恢复", () => {
    const archived = [sess("a1", "归档甲", 10)];
    const p = renderSidebar({ archived, sessions: [] });
    fireEvent.click(screen.getByLabelText("归档"));
    expect(screen.getByText("归档甲")).toBeInTheDocument();
    fireEvent.click(screen.getAllByLabelText("会话操作")[0]);
    fireEvent.click(screen.getByText("恢复"));
    expect(p.onRestore).toHaveBeenCalled();
  });

  it("会话行显示相对时间", () => {
    renderSidebar({
      sessions: [sess("s1", "标题", Date.now() - 8 * 60_000)],
    });
    expect(screen.getByText("8分钟")).toBeInTheDocument();
  });
});
