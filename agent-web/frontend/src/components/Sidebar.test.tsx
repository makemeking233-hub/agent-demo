import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Sidebar, type SidebarSession } from "./Sidebar";

// native-folder-picker: WorkspacePickerModal 不再调 /api/fs/*
// 改为 fetch /api/workspaces/pick-folder（在具体测试用例内 stub fetch）

beforeEach(() => {
  localStorage.clear();
  vi.unstubAllGlobals();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

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

  it("点击 + 弹出 menu，含 'Add workspace...' 项；点 Add workspace 触发 WorkspacePickerModal", async () => {
    const p = renderSidebar();
    // 点击 + 弹出 menu
    fireEvent.click(screen.getByTestId("workspace-add-button"));
    await waitFor(() =>
      expect(screen.getByTestId("workspace-add-menu")).toBeInTheDocument(),
    );
    // menu 含 "Add workspace..." 项
    expect(screen.getByTestId("workspace-add-new")).toBeInTheDocument();
    // 点 "Add workspace..." 关闭 menu + 弹 picker modal
    fireEvent.click(screen.getByTestId("workspace-add-new"));
    await waitFor(() =>
      expect(screen.queryByTestId("workspace-add-menu")).toBeNull(),
    );
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "选择工作区目录" })).toBeInTheDocument(),
    );
    // 关闭 picker modal → dialog 消失
    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "选择工作区目录" })).not.toBeInTheDocument(),
    );
    expect(p.onCreateWorkspace).not.toHaveBeenCalled();
  });

  it("menu 默认隐藏，点 + 才打开", () => {
    renderSidebar();
    expect(screen.queryByTestId("workspace-add-menu")).toBeNull();
  });

  it("menu 列已有 workspaces + 选中调 onWorkspaceChange", () => {
    const p = renderSidebar({
      workspaces: [
        { name: "agent-demo", dir: "/x", sessionCount: 1 },
        { name: "md-main", dir: "/y", sessionCount: 2 },
      ],
      activeWorkspace: "agent-demo",
    });
    fireEvent.click(screen.getByTestId("workspace-add-button"));
    const menu = screen.getByTestId("workspace-add-menu");
    expect(within(menu).getByText("agent-demo")).toBeInTheDocument();
    expect(within(menu).getByText("md-main")).toBeInTheDocument();
    // 选 md-main
    fireEvent.click(within(menu).getByText("md-main"));
    expect(p.onWorkspaceChange).toHaveBeenCalledWith("md-main");
  });

  it("点击 menu 外部关闭 menu", () => {
    renderSidebar({
      workspaces: [{ name: "agent-demo", dir: "/x", sessionCount: 1 }],
    });
    fireEvent.click(screen.getByTestId("workspace-add-button"));
    expect(screen.getByTestId("workspace-add-menu")).toBeInTheDocument();
    // 点击 document body 任意位置
    fireEvent.mouseDown(document.body);
    expect(screen.queryByTestId("workspace-add-menu")).toBeNull();
  });

  it("端到端：点 + → menu → Add workspace → picker modal → 输入路径 → 自动 basename → 提交 → 调 onCreateWorkspace", async () => {
    const p = renderSidebar();
    // 1. 点 + 弹 menu
    fireEvent.click(screen.getByTestId("workspace-add-button"));
    // 2. 点 "Add workspace..." 关闭 menu + 弹 picker
    fireEvent.click(screen.getByTestId("workspace-add-new"));
    const dialog = await screen.findByRole("dialog", { name: "选择工作区目录" });

    // 3. 输入路径 → name 自动填 basename
    fireEvent.change(within(dialog).getByTestId("wp-path-input"), {
      target: { value: "/home/user/projects/agent-demo" },
    });
    expect(within(dialog).getByTestId("wp-name-input")).toHaveValue("agent-demo");

    // 4. 改成自定义 name
    fireEvent.change(within(dialog).getByTestId("wp-name-input"), {
      target: { value: "ws-from-picker" },
    });

    // 5. 点击 "选择此目录" 调 onCreateWorkspace
    fireEvent.click(within(dialog).getByTestId("wp-submit"));

    await waitFor(() =>
      expect(p.onCreateWorkspace).toHaveBeenCalledWith(
        "ws-from-picker",
        "/home/user/projects/agent-demo",
      ),
    );
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

  // ---- auto-archive-stale-sessions：归档视图按时间分档分组 ----

  it("归档视图按时间分档分组，空档不显示且档间由近到远", () => {
    const archived: SidebarSession[] = [
      { id: "r", title: "刚归档的", preview: "", workspace: "agent-demo", time: 1, bucket: "recent" },
      { id: "w", title: "一周前", preview: "", workspace: "agent-demo", time: 2, bucket: "last_week" },
      { id: "e", title: "很久以前", preview: "", workspace: "agent-demo", time: 3, bucket: "earlier" },
    ];
    renderSidebar({ archived });

    fireEvent.click(screen.getByLabelText("归档"));

    expect(screen.getByText("最近归档")).toBeInTheDocument();
    expect(screen.getByText("上周")).toBeInTheDocument();
    expect(screen.getByText("更早")).toBeInTheDocument();
    // 该档没有任何归档会话 → 不显示标题
    expect(screen.queryByText("本月")).toBeNull();

    const html = document.body.innerHTML;
    expect(html.indexOf("最近归档")).toBeLessThan(html.indexOf("上周"));
    expect(html.indexOf("上周")).toBeLessThan(html.indexOf("更早"));
  });

  it("归档项缺分档字段时归入「更早」，不丢项", () => {
    // 旧数据 / 后端未返回该字段时的兜底
    const archived: SidebarSession[] = [
      { id: "x", title: "无分档字段", preview: "", workspace: "agent-demo", time: 1 },
    ];
    renderSidebar({ archived });

    fireEvent.click(screen.getByLabelText("归档"));

    expect(screen.getByText("更早")).toBeInTheDocument();
    expect(screen.getByText("无分档字段")).toBeInTheDocument();
  });

  it("普通视图仍按工作区分组（不误用分档）", () => {
    renderSidebar({
      sessions: [
        { id: "a", title: "会话A", preview: "", workspace: "agent-demo", time: 1, bucket: "last_week" },
        { id: "b", title: "会话B", preview: "", workspace: "other-ws", time: 2 },
      ],
    });

    expect(screen.getByText("agent-demo")).toBeInTheDocument();
    expect(screen.getByText("other-ws")).toBeInTheDocument();
    // 普通视图不应出现分档标题
    expect(screen.queryByText("上周")).toBeNull();
  });
});
