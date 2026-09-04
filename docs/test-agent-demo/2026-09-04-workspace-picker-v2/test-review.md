# `2026-09-04-workspace-picker-v2/` — 测试复盘

## 1. 流程回顾

按 `openspec-explore → openspec-propose → openspec-apply-change` 标准流程：

1. **explore 阶段**：用 `brainstorming` skill（用户已 reject 资源管理器 API + File System Access API 后选 A 方案 Modal 仿 DSH）。
2. **propose 阶段**：开 `polish-workspace-picker-dsh-style` change，铺齐 proposal / design / specs / tasks 四件套。
3. **apply 阶段**：15 个 task 按 TDD + commit + push 节奏逐项实施。
4. **archive 阶段**：本批次后归档。

## 2. 做得好的

- **layout 用 grid 模板**：`.modal { display: grid; grid-template-rows: auto auto auto auto 1fr auto; grid-template-columns: 1fr; }` + 主区域内部 `.main { grid-template-columns: 200px 1fr }`，DSH 双栏布局结构清晰。
- **history 栈纯前端**：reducer 内 `navigate` action 自动 push history，`back / forward` 用 `historyIndex` 索引；栈深 50 上限自动 trim；初始 `historyIndex === -1` 保证 back 按钮 disabled。
- **列头排序 useMemo**：避免每次 render 重排 1000+ 条目；点击列头同字段切换 asc/desc，不同字段默认 asc。
- **test mock 拆分**：`WorkspacePickerModal.test.tsx` 用 `vi.hoisted` 把 `getQuickAccess` 加进 fsMock，避免测试间相互污染。

## 3. 可改进

- **first time mock getDrives/getQuickAccess 缺失**：第一次跑测试 16 失败，因为旧的 beforeEach 没设这两个 mock 的 default resolve。在新 describe block + 旧 describe block 都加上。
- **`listDir` mock 没校验路径越界**：测试"底部路径框非法路径"时，listDir mock 对 `/etc/passwd` 返回空 entries 而非抛 FsError，所以错误条不显示。改为对非 HOME 起始路径 mock 抛 FsError。
- **`within` import 漏掉**：新 describe block 用 `within(dialog)` 但顶部只 import 了 `screen`；扩展用例时漏了，补 import。

## 4. 风险与遗留

- **DSH "显示隐藏" 切换语义**：跟 DSH 资源管理器的"查看 → 显示隐藏项目"位置不一致，但功能等价；未来若用户有明确诉求再调整。
- **左侧导航树 200px 固定宽度**：在 < 1366 宽屏下有点挤；通过 grid `200px 1fr` 让右列表自适应。
- **列头排序图标**：当前用 ` ↑` / ` ↓` 文本字符，未使用 lucide 图标；视觉略简陋，可后续 polish。

## 5. 交付物

- 1 个新后端文件（FsQuickAccessResponse DTO）+ 2 个修改（FsController + FsControllerTest）
- 1 个新前端类型（FsQuickAccessItem / Response）+ 2 个修改（fs.ts + fs.test.ts）
- 2 个修改前端组件（WorkspacePickerModal.tsx 重写 + .module.css 重写）
- 10 个新 vitest 用例
- 3 个 commit 全部 push 到 origin/main（commit 8e8075d / 447955e / TBD）
- 测试文档四件套
- test-guide.md §2.7 追加

## 6. 归档状态

✅ change `polish-workspace-picker-dsh-style` 已 archive 到 `openspec/changes/archive/`。
