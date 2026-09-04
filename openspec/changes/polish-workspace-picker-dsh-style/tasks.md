## 1. 后端基础设施

- [x] 1.1 新增 `FsQuickAccessResponse` DTO（`agent-web/.../api/dto/`）：`{items: [{name, path}]}`；含最小字段与文档注释；`mvn -pl agent-web compile` 通过
- [x] 1.2 `FsController` 新增 `GET /api/fs/quick-access` 端点：探测 `Home + Desktop + Documents + Downloads`（按平台 `System.getProperty("user.home")` 拼接 + `Files.isDirectory()` 跳过不存在的）；复用 `guard.homeRealPath()` 校验；`mvn -pl agent-web compile` 通过
- [x] 1.3 扩 `FsControllerTest` 4 个用例（正常返回全部 / Desktop 不存在跳过 / 路径越界跳过 / 空目录时只返回 Home）；`mvn -pl agent-web test` 全绿

## 2. 前端 Modal 重写

- [ ] 2.1 `frontend/src/api/fs.ts` 新增 `getQuickAccess()` + `FsQuickAccessItem` 类型；`api/fs.test.ts` 加 4 个 vitest 用例（200 / 5xx / 部分缺失 / 401）
- [ ] 2.2 `WorkspacePickerModal.tsx` 重写 state：useReducer 13 字段（含 `history: string[]` + `historyIndex: number` + `sortBy: 'name'|'mtime'|'type'` + `sortDir: 'asc'|'desc'` + `quickAccess: FsQuickAccessItem[]`）；导航 actions（`back / forward / up / pushHistory`）实现 history 栈；`navigate` action 改为先 `pushHistory` 再更新 `currentPath`
- [ ] 2.3 `WorkspacePickerModal.tsx` 顶部区：标题改为 "Select Workspace Directory"（`aria-label="选择工作区目录"`）+ ←/→/↑ 三个按钮（按钮根据 history 状态 disabled）+ 面包屑（点击跳转）+ 显示隐藏 Eye 图标（移动到列头右侧）
- [ ] 2.4 `WorkspacePickerModal.tsx` 左侧导航树：标题区分为"快速访问"组（从 `quickAccess` 渲染）+ "此电脑"组（Windows 从 `drives` 渲染，Linux/macOS 隐藏）；每条目点击 → `navigate(item.path)`
- [ ] 2.5 `WorkspacePickerModal.tsx` 右侧文件列表 + 列头排序：渲染 `<thead>` 含 名称/修改时间/类型 列头（点击切换 `sortBy`/`sortDir`）；列表按 `useMemo` 排序（目录优先 + 字段排序）；保留双击进入 + 单击选中 + 文件 disabled
- [ ] 2.6 `WorkspacePickerModal.tsx` 底部分两行：第一行"文件夹：[<path input>]"（DSH 风格，可直接编辑 + Enter 跳转，非法显示行内错误）；第二行"工作区名称：[<name input>]"（紧凑，紧贴路径框下方）+ 取消/选择此目录按钮（右对齐）；保留 name 默认 basename + 校验规则
- [ ] 2.7 `WorkspacePickerModal.module.css` 重写布局：`.modal { display: grid; grid-template-rows: auto auto 1fr auto; grid-template-columns: 220px 1fr; }` 顶部 + 主区域（左右双栏）+ 底部；左导航树 220px 固定、右列表自适应；新增 `.historyBtn / .breadcrumbRow / .treeSection / .treeItem / .listHeader / .sortableHeader / .footerRow / .footerRow + .footerRow` 等类
- [ ] 2.8 `WorkspacePickerModal.test.tsx` 扩 6 个新用例（history 后退/前进/栈底禁用 + 列头点击切换排序 + 左导航树点击 Desktop + 底部路径框 Enter 跳转非法显示错误 + 显示隐藏切换 + 标题 aria-label）；`npx vitest run --no-coverage src/components/WorkspacePickerModal.test.tsx` 全绿

## 3. 集成 + 收尾

- [ ] 3.1 跑 `mvn -pl agent-web verify -DskipNpm=true`（含 jacoco 门禁 LINE ≥ 80% / BRANCH ≥ 70%）BUILD SUCCESS；如有未覆盖分支补针对性单测
- [ ] 3.2 跑 `npx vitest run --no-coverage`（全部 11 个文件 / 72 个用例）全绿 + `Sidebar.test.tsx` 端到端集成测试 SB-07 仍通过（点 + → 弹 Modal → 完整链路）
- [ ] 3.3 写测试文档：`docs/test-agent-demo/2026-09-04-workspace-picker-v2/{test-design,test-cases,test-report,test-review}.md` 四件套 + 更新 `test-guide.md` §1 登记追加一行 + §2 追加 §2.7 详情
- [ ] 3.4 `openspec validate polish-workspace-picker-dsh-style --type change --strict` 通过 + `openspec archive polish-workspace-picker-dsh-style --yes` 自动合并 delta spec 到 `openspec/specs/web-ui/spec.md` + commit + push 到 origin/main
