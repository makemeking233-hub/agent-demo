# SMART 重构实施计划

> **For agentic workers:** 按任务顺序执行；每个 R 完成立即 commit + push + 验证 `mvn test` 全绿。

**Goal:** 消除 instanceof 链、类型硬编码、重复骨架；引入 sealed interface + visitor + 策略模式 + 模板方法，使代码符合 SOLID（开闭/单一职责/里氏替换）+ DRY + 易扩展。

**Architecture:**
- **R1/R2/R3**：sealed interface + 多态消 instanceof
- **R4/R5**：模板方法 + 工具类消重复
- **R6/R7/R8**：策略枚举 + 接口消字符串/类型硬编码
- **R9**：Pipeline 模式消嵌套

**Tech Stack:** Java 17（sealed interface 已稳定；switch pattern matching 仅在 preview，本 plan 不用）

---

## R1: ToolInput sealed + PermissionManager 消 instanceof 链

**Files:**
- Create: `src/main/java/com/example/agent/tools/ToolInput.java`
- Modify: `ReadFileTool.java`, `WriteFileTool.java`, `EditFileTool.java`, `LsTool.java`（Input implements ToolInput）
- Modify: `PermissionManager.java:83-90`（extractPath 改 instanceof ToolInput）

**Steps:**
1. 新建 `sealed interface ToolInput permits ReadFileTool.Input, WriteFileTool.Input, EditFileTool.Input, LsTool.Input`，含抽象 `String path()`
2. 4 个 Tool 的 Input 改 `implements ToolInput`
3. PermissionManager.extractPath 改为：
   ```java
   private String extractPath(Object input) {
     if (input instanceof Tool.ToolContext) return null;
     if (input instanceof ToolInput ti) return ti.path();
     return null;
   }
   ```
4. mvn test → 全绿
5. commit `refactor: R1 抽 sealed ToolInput + PermissionManager 消 instanceof 链`

---

## R2: StreamChunk accept visitor + aggregate 重构

**Files:**
- Modify: `StreamChunk.java`（加 visitor 接口 + accept 方法 + 重构 aggregate）

**Steps:**
1. 新建 `interface StreamChunkVisitor`（默认空实现）
2. StreamChunk 加 abstract `void accept(StreamChunkVisitor v)`
3. 7 个 record 各自实现 accept（在 visitor 上调用 visitTextDelta / visitToolCallStart 等）
4. aggregate 改为接受 visitor，由 visitor 累积结果
5. mvn test → 全绿（StreamChunkAggregateTest）
6. commit `refactor: R2 StreamChunk accept visitor 模式重构 aggregate`

---

## R3: ToolResult.toModelContent 多态化

**Files:**
- Modify: `ToolResult.java`（删除 default toModelContent，Ok/Err 各自实现）

**Steps:**
1. 删除 default `toModelContent`
2. Ok 实现：`return String.valueOf(output());`
3. Err 实现：`return "[ERROR] " + message;`
4. mvn test → 全绿
5. commit `refactor: R3 ToolResult.toModelContent 多态化（消 instanceof 强转）`

---

## R4: 抽 AbstractFileTool 模板方法基类

**Files:**
- Create: `src/main/java/com/example/agent/tools/AbstractFileTool.java`
- Modify: `ReadFileTool.java`, `WriteFileTool.java`, `EditFileTool.java`, `LsTool.java`（继承 AbstractFileTool）

**Steps:**
1. 新建 `abstract class AbstractFileTool<I extends ToolInput> implements Tool<I, String>`，提供：
   - `protected final Logger log = LoggerFactory.getLogger(getClass());`
   - `protected final Path normalize(Input input, Path base)`: resolve + normalize
   - `protected final ToolResult<String> boundsError(String inputPath)`: 返回越界错误结果
2. 4 个 Tool 继承 AbstractFileTool
3. execute 中 normalize + boundsError 改调基类方法
4. mvn test → 全绿
5. commit `refactor: R4 抽 AbstractFileTool 模板方法基类消 4 个 Tool 的重复骨架`

---

## R5: 抽 PromptLoader 工具类

**Files:**
- Create: `src/main/java/com/example/agent/util/PromptLoader.java`
- Modify: `ContextCompressor.java`（loadPrompt 改调 PromptLoader）
- Modify: `MemoryPromptBuilder.java`（loadTemplate 改调 PromptLoader）

**Steps:**
1. 新建 `PromptLoader.loadOrFallback(String classpathPath, String fallback)`
2. ContextCompressor.loadPrompt 删除，改调 PromptLoader
3. MemoryPromptBuilder.loadTemplate 删除，改调 PromptLoader
4. mvn test → 全绿
5. commit `refactor: R5 抽 PromptLoader 工具类消 ContextCompressor/MemoryPromptBuilder 的 loadPrompt 重复`

---

## R6: ToolCategory 枚举 + 策略注册表

**Files:**
- Create: `src/main/java/com/example/agent/tools/ToolCategory.java`
- Modify: `Tool.java`（加 `default ToolCategory category() { return ToolCategory.OTHER; }`）
- Modify: `ReadFileTool/LsTool/EditFileTool/WriteFileTool/ShellTool`（override category()）
- Modify: `PermissionManager.java`（用 category 而非 toolName switch）

**Steps:**
1. 新建 `enum ToolCategory { READ, WRITE, SHELL, OTHER }`
2. Tool 加 default category() 返回 OTHER
3. 5 个 Tool override 返回对应 category
4. PermissionManager.decide 改为：
   ```java
   return switch (tool.category()) {
     case READ -> policy.defaultRead() ? allow() : ask();
     case WRITE -> policy.defaultWrite() ? allow() : ask();
     case SHELL -> policy.defaultShell() ? allow() : ask();
     case OTHER -> ask();
   };
   ```
5. mvn test → 全绿（PermissionManagerTest 需更新）
6. commit `refactor: R6 ToolCategory 枚举 + PermissionManager 策略化（消字符串硬编码）`

---

## R7: Message sealed 加 toMap 多态

**Files:**
- Modify: `Message.java`（加 abstract toMap）
- Modify: `DeepSeekRequestMapper.java`（toMessageArray 用多态）

**Steps:**
1. Message sealed 加 `Map<String, Object> toMap()`
2. User: `Map.of("role", role(), "content", content())`
3. Assistant: 加 tool_calls 字段
4. ToolResult: 加 tool_call_id 字段
5. System: 同 User
6. DeepSeekRequestMapper.toMessageArray 改为 `messages.stream().map(Message::toMap).toList()`
7. mvn test → 全绿
8. commit `refactor: R7 Message sealed 加 toMap + DeepSeekRequestMapper 多态化`

---

## R8: DenylistMatcher 策略接口

**Files:**
- Create: `src/main/java/com/example/agent\tools\DenylistMatcher.java`
- Create: `src/main/java/com/example/agent\tools\DefaultDenylistMatcher.java`
- Create: `src/main/java/com/example/agent\tools\ShellDefaults.java`（集中 3 个 Adapter 的默认黑名单）
- Modify: `ShellAdapter.java`（删除 isDenylisted default + defaultDenylist 抽象，改为只 commandLine）
- Modify: `BashAdapter/CmdAdapter/PowerShellAdapter`（只实现 commandLine，denylist 移到 ShellDefaults）
- Modify: `ShellTool.java`（构造加 DenylistMatcher 参数；execute 用 matcher.isDenylisted）

**Steps:**
1. 新建 DenylistMatcher 接口：`boolean matches(String command)`
2. 新建 DefaultDenylistMatcher(List<String> patterns)（含原 flags + glob 匹配逻辑）
3. 新建 ShellDefaults：含 BASH_DENYLIST/CMD_DENYLIST/POWERSHELL_DENYLIST
4. ShellAdapter 简化：只剩 commandLine() + 删除 isDenylisted + 删除 defaultDenylist
5. 3 个 Adapter 删除 defaultDenylist
6. ShellTool 构造加 DenylistMatcher 参数；ChatCommand 装配 ShellTool 时传入
7. mvn test → 全绿（ShellToolTest/BashAdapterTest 需更新）
8. commit `refactor: R8 DenylistMatcher 策略接口 + ShellAdapter 简化`

---

## R9: DeepSeekResponseParser pipeline 模式

**Files:**
- Modify: `DeepSeekResponseParser.java`（抽 List<SsePayloadParser>）

**Steps:**
1. 新建 `interface SsePayloadParser { Optional<StreamChunk> parse(JsonNode root); }`
2. 拆出现有逻辑为 4 个 parser：FirstChoiceParser, FinishReasonParser, TopLevelUsageParser
3. parseSseLine 遍历 parsers
4. mvn test → 全绿
5. commit `refactor: R9 DeepSeekResponseParser 拆 pipeline 消嵌套`

---

## 验收

所有 R 完成后：
- `mvn test` 必须 102+ 测试全绿
- 无新增 instanceof 链（除必要的 PathGuard）
- 新增 ~3 个文件（ToolInput/ToolCategory/DenylistMatcher/DefaultDenylistMatcher/ShellDefaults/PromptLoader/SsePayloadParser/AbstractFileTool）
- 改动 ~15 个文件
- 净增 -50 行（消除重复） / 净增 +200 行（接口 + Javadoc）

---

## 顺序与依赖

R1 → R2 → R3 → R4 → R5 → R6 → R7 → R8 → R9

每个 R 独立可测，可单独回滚。