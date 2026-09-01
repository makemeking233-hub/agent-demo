# Plugin 插件系统指南（add-plugin-system）

> 适用范围：`agent-core`（CLI 与 web 共用）。Plugin 框架让能力以可插拔方式注入，无需改主循环。

## 1. 是什么

一个 Plugin 是实现了 `com.example.agent.plugin.Plugin` 接口的类。`PluginManager` 在 `AgentLoopFactory.buildLoop` 阶段按 `AgentConfig.plugins` 列表顺序 `init()`，进程退出前反序 `close()`。单个插件 init/close 抛异常会被隔离（记 WARN），不影响其他插件与 agent 启动。

一个 Plugin 可以同时实现若干扩展点接口（`implements` 多个），声明它提供的能力：

| 扩展点接口 | 作用 |
|------|------|
| `ToolProvider` | 提供工具（`tools()` 返回 `List<Tool<?,?>>`） |
| `SystemPromptFragment` | 提供拼到 system prompt 尾部的文本（`fragment()`） |
| `SlashCommandProvider` | 提供 slash 命令（`commands()`） |
| `LlmProviderExtension` | 提供额外 LLM provider（`provider()`） |
| `ChatRequestMapper` | 修改发往 LLM 的请求（`map(req, cfg)`） |

生命周期：

- 启动：`AgentLoopFactory.buildLoop` → `new PluginManager(plugins, cfg, tools)` → `init()`（按列表序）
- 运行：AgentLoop 通过 `collectTools()` / `collectSystemPromptFragment()` 等收集插件产物
- 退出：JVM shutdown hook 调 `PluginManager.close()`（反序）

## 2. 最小示例：hello-world 插件

一个文件、一个类，实现 `Plugin + ToolProvider`，注册一个工具。

```java
import com.example.agent.plugin.ExtensionPoints;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;
import com.example.agent.tools.Tool;

import java.util.List;

public class HelloPlugin implements Plugin, ExtensionPoints.ToolProvider {

    @Override
    public void init(PluginContext ctx) {
        System.out.println("[plugin] hello plugin inited");
    }

    @Override
    public List<Tool<?, ?>> tools() {
        return List.of(new HelloTool());
    }
}
```

配套的自定义工具（`implements Tool<String, String>`）：

```java
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class HelloTool implements Tool<String, String> {

    @Override public String name() { return "hello"; }
    @Override public String description() { return "打招呼，返回一段问候语。"; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }
    @Override public String renderUse(String input) { return "Hello(" + input + ")"; }
    @Override public String renderResult(String output) { return output; }
    @Override public String parseArguments(String argumentsJson) { return argumentsJson; }
    @Override public Mono<ToolResult<String>> execute(String input, Tool.ToolContext ctx) {
        return Mono.just(ToolResult.ok("你好，我是 hello 工具"));
    }
}
```

配置启用（`~/.agent-demo/config.yaml` 或 `application-local.yml` 的 `plugins` 段）：

```yaml
plugins:
  - className: com.example.agent.plugin.HelloPlugin
    config: {}
```

> `className` 必须是带包名的完整类名，且有无参构造。`config` 会传给 `PluginContext.cfg()` 对应的 `PluginConfig.config()`。

## 3. 多扩展点范例

一个 Plugin 同时实现 `ToolProvider + SystemPromptFragment`，既注册工具又注入提示词片段。

```java
public class SkillsPlugin implements Plugin, ExtensionPoints.ToolProvider, ExtensionPoints.SystemPromptFragment {

    private final List<Skill> skills = new ArrayList<>();

    @Override public void init(PluginContext ctx) {
        skills.addAll(SkillCatalog.discover(SkillsPlugin.defaultRoots()));
    }

    @Override public List<Tool<?, ?>> tools() {
        List<Tool<?, ?>> out = new ArrayList<>();
        for (Skill s : skills) out.add(new SkillTool(s));
        return out;
    }

    @Override public String fragment() {
        if (skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("# 可用技能\n");
        for (Skill s : skills) sb.append("- `").append(s.name()).append("`：").append(s.description()).append("\n");
        return sb.toString();
    }
}
```

## 4. ChatRequestMapper 范例

通过 `ChatRequestMapper` 修改发往 LLM 的请求（例如给所有请求加一个参数）。

```java
public class ParamMapperPlugin implements Plugin, ExtensionPoints.ChatRequestMapper {

    @Override
    public com.example.agent.llm.ChatRequest map(com.example.agent.llm.ChatRequest req, AgentConfig cfg) {
        // 示意：按需构造改造后的 ChatRequest（字段随 ChatRequest 定义）
        return req;
    }
}
```

## 5. 关键点与约定

- 插件初始化顺序 = `AgentConfig.plugins` 列表顺序，`close` 反序。后一个插件可用前一个插件注册的工具。
- 插件间有隐式依赖时，按依赖顺序配置 `plugins` 列表。
- 插件不依赖 Spring（CLI profile 不启 Spring），依赖通过 `PluginContext` 显式注入。
- 旧 `ToolRegistry.registerMcpTools` / `registerSkillTools` / `registerMemoryTools` 已标 `@Deprecated`，新路径走插件框架。

## 6. 相关文件

- 框架核心：`agent-core/.../plugin/{Plugin, PluginManager, PluginContext, ExtensionPoints}.java`
- 内置插件：`agent-core/.../plugin/{mcp, skill, memory}/`
- 配置：`AgentConfig.plugins`（`List<PluginConfig>`）+ `ConfigLoader.mergePlugins`
