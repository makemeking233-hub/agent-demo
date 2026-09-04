## Context

agent-demo 当前 Web UI 的 Sidebar 创建工作区走的是「工作区名 + 绝对路径」两个文本框的手动输入模式（见 `Sidebar.tsx L214-245`）。该模式把"找到正确路径"的责任全部推给用户，并且服务端只在校验失败时才暴露错误，UX 较粗糙。

DSH 的 `WorkspacePicker` 用原生文件选择对话框（`showOpenDialog`）做到一键选目录，体验干净。agent-demo 是 Web 应用，无法直接调原生对话框，所以采用"前端 Modal 模拟文件选择器 + 后端 fs 浏览 API"的折中方案。

本设计文档聚焦三件事：
1. 后端 fs API 的安全边界（路径解析与家目录前缀校验）；
2. 前端 Modal 的状态机与组件拆分；
3. 持久化与错误处理的统一约定。

## Goals / Non-Goals

**Goals：**

- 后端 fs API 全部锁 `$HOME` 子树内，无法通过 `..` 或符号链接逃逸；
- 前端 Modal 提供"可点选 / 可手输路径 / 可新建空目录 / 可面包屑跳转 / 可显示隐藏文件"五项能力；
- 选完后自动创建工作区并切换，无须用户再做额外操作；
- `mvn verify` 门禁（LINE ≥ 80% / BRANCH ≥ 70%）不受影响。

**Non-Goals：**

- 不引入真正的 OS 原生文件选择对话框（web 端做不到，保持 Modal 即可）；
- 不实现文件/目录的"复制/移动/删除/重命名"等文件管理器级能力；
- 不暴露 `Path.toRealPath()` 之外的任何敏感系统信息（不返回 inode / 权限位 / 所有者）；
- 不跨平台家目录以外的"自定义根"配置（v0.x 内部 UX 改进，不做 YAML 暴露）；
- 不引入文件预览缩略图、不做虚拟滚动（首批实现规模小，无需）。

## Decisions

### D1：浏览根锁定 `$HOME`，不做 YAML 配置

**理由**：家目录是普通开发者最高频的工作区存放位置（`~/projects/`、`~/work/` 等），且权限边界天然清晰——`user.home` 是 JVM 启动时已知的常量，无需新配置。

**实现**：
- 后端 `FsController` 注入 `System.getProperty("user.home")` 作为 `homeRoot`；
- 所有 `/api/fs/list` / `/api/fs/mkdir` 请求把传入路径 `Path.of(p).toRealPath().normalize()` 后判 `startsWith(homeRealPath)`；不在则 403 `path_outside_home`。
- `/api/fs/drives` 仅 Windows 返回盘符列表（`FileSystems.getDefault().getFileStores()`），不参与家目录校验。

**考虑过**：`agent.web.fs.allowed-paths` YAML 白名单。否决：v0.x 范围内极少用户需要把工作区放在家目录以外；增加 YAML 字段会让首次部署复杂，违背"锁家目录就是简化配置"的初衷。

### D2：路径安全用 `toRealPath()` + 前缀校验，不靠字符串匹配

**理由**：单纯 `String.startsWith("/home/user")` 会被 `..`、符号链接、Unicode 归一化绕过。`Path.toRealPath()` 会跟随符号链接并规范化路径，配合前缀校验才能保证真实路径在白名单内。

**实现**：
```java
Path real = Path.of(inputPath).toRealPath();
Path homeReal = Path.of(System.getProperty("user.home")).toRealPath();
if (!real.startsWith(homeReal)) throw new ForbiddenException("path_outside_home");
```
- `toRealPath()` 在路径不存在时会抛 `NoSuchFileException` → 404；
- 解析失败 / 权限拒绝 → 500（带稳定错误码 `path_unresolvable`）；
- `toRealPath()` 自动解符号链接，等于"硬封堵 symlink 逃逸"。

**考虑过**：用 `Path.normalize()` 代替 `toRealPath()`。否决：`normalize()` 只处理 `.` / `..`，不解符号链接，无法挡 symlink。

### D3：前端 Modal 状态用 `useState` 分片，不用 Redux/Zustand

**理由**：Modal 生命周期短（开/选/关），无需跨组件共享；引入新状态库成本 > 收益。

**实现**：
```ts
interface State {
  currentPath: string;        // 当前浏览路径
  entries: FsEntry[];          // 当前目录条目
  loading: boolean;
  error: string | null;        // 行内错误
  selectedPath: string;        // 用户当前选中的路径（双击/点选）
  workspaceName: string;       // 提交用 name（默认 basename(selectedPath)）
  includeHidden: boolean;      // 显示隐藏文件开关
  isCreatingWs: boolean;       // POST /api/workspaces 进行中
}
```
- 用单一 `useReducer` 而非多个 `useState` 拼接，避免状态更新竞态；
- 路径变化触发 `useEffect` 拉 `/api/fs/list`；
- 关闭 Modal 时不卸载组件（保持 localStorage 写入），仅重置 selection。

**考虑过**：引入 Zustand。否决：Modal 内的状态不需要跨组件共享；用 `useReducer` 足够。

### D4：错误反馈走内联（行内错误条），不弹 toast/modal

**理由**：与现有 Sidebar 的 `workspaceError` 内联风格一致；toast 会和 Modal 抢视觉焦点，对话区用户正专注时容易漏看。

**实现**：
- Modal 顶部留一条 `error` 行（非 modal），红字短文案；
- 路径不合法、创建失败、目录不存在等都展示一行人类可读信息；
- 同时调后端错误码做埋点（`console.warn` 即可，不上报服务端）。

**考虑过**：用 react-hot-toast。否决：Modal 内错误应该就近显示，跨组件 toast 会和 Modal 关闭动画冲突。

### D5：localStorage 记忆以"路径字符串"为粒度

**理由**：跨 session 记住上次定位路径，下次开 Modal 直达。

**实现**：
- key：`agent-demo.workspace-picker.last-path`；
- value：上次的 `currentPath`（绝对路径）；
- Modal 关闭前（`onClose`）写入；
- 打开时优先读 localStorage，解析失败 / 路径已不存在 / 不在家目录内时回退 `$HOME`。

**考虑过**：用 sessionStorage（关浏览器即清）。否决：用户每天开关浏览器都重新定位太烦；localStorage 更友好。

### D6：双击/回车进目录，点选为选中

**理由**：与 OS 文件管理器一致（macOS Finder / Windows Explorer 都是双击进）；但同时给"点选 + 底部选择按钮"提供单选路径。

**实现**：
- 单击条目 → 高亮 + 更新 `selectedPath`，底部"选择此目录"按钮变为可用；
- 双击条目 → 若是目录则 `setCurrentPath(entry.path)` 重新拉列表；
- 路径输入框 + 回车 → 直接跳转到输入路径（路径合法且在家目录内才生效）。

**考虑过**：单击进目录、双击选中。否决：与 OS 习惯相反，且与底部"选择此目录"按钮语义冲突。

### D7：路径大小写策略与平台一致

**理由**：Windows 文件系统大小写不敏感，Linux 敏感；强行归一化会让 Linux 上的大小写敏感目录被错误识别为同一路径。

**实现**：
- 后端不做大小写归一化，直接用 `Path.toRealPath()` 解析后的路径返回；
- 前端条目排序按文件名 `localeCompare`，不区分大小写（`sensitivity: 'base'`）；
- 路径前缀校验严格用 `startsWith`，由 OS 文件系统自身决定大小写语义。

**考虑过**：后端统一小写比较。否决：会破坏 Linux 上的目录区分语义，且返回路径与用户实际路径不一致。

## Risks / Trade-offs

### R1：家目录范围太小，多盘符用户无法把 D:/E:/ 工作区纳入

[Mitigation] v0.x 接受限制；后续若用户反馈强需求再加 `agent.web.fs.allowed-paths` YAML 白名单（不在本次 scope）。

### R2：localStorage 可能被禁用或被同源其他应用覆盖

[Mitigation] 读写都 try/catch 静默失败；fallback 到 `$HOME`，不影响主流程。

### R3：Modal 内嵌套 Modal（新建文件夹的输入框）与现有 z-index 体系冲突

[Mitigation] 新建文件夹的内嵌输入框不走 Modal，而是 inline 展开在工具栏下方，避免 z-index 嵌套。

### R4：路径前缀校验在符号链接场景下可能误判（链接指向家目录外但通过校验，因为 `toRealPath()` 已解链接）

[Accepted] 这正是 D2 设计的目的：解链接后判前缀 = 一律拒绝逃逸行为；用户想用家目录外的目录需走显式 mount / copy。

### R5：后端 fs API 没有 Rate Limit，恶意客户端可高频列目录吃 IO

[Mitigation] trusted-host 限 LAN 内网访问已是第一道闸；v0.x 不引入 Rate Limit，v0.x 后若发现滥用再加 token bucket。

### R6：前端 vitest jsdom 不支持 `Path.toRealPath`，Mock 后测试覆盖不全

[Mitigation] 前端测试聚焦 Modal 交互（打开 / 浏览 / 双击 / 选择 / 提交）；真实路径解析由后端测试覆盖。两者职责分清。

## Migration Plan

- 无破坏性变更（proposal 已说明）。
- 部署步骤：随 v0.x 下一次发版集成；`mvn package` 一个命令出 jar，无需额外配置。
- 回滚策略：`git revert` 单 commit 即可；`WorkspaceController` 不动，仅 `FsController` + `Sidebar.tsx` + 新增 Modal；前端 Sidebar 内联表单回滚方案可保留 1 个 release 作为紧急逃生通道。

## Open Questions

无（所有探索阶段决策已敲定；下个里程碑若发现需要 `allowed-paths` 白名单或 Rate Limit 再开新 change）。
