package com.example.agent.tools;

import com.example.agent.tools.file.ReadFileTool;
import com.example.agent.tools.file.WriteFileTool;
import com.example.agent.tools.file.EditFileTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（按 name 索引）。
 *
 * <p>v0.1 简化：list() 返回所有工具；get(name) / getRaw(name) 按名字查。 M5 Memory 自动注入通过 {@link
 * #registerMemoryTools} 完成。
 */
public class ToolRegistry {
  /** 工具表（按 {@link Tool#name()} 索引；插入顺序 = 列表顺序） */
  private final Map<String, Tool<?, ?>> tools = new LinkedHashMap<>();

  /** 注册一个工具（按 {@link Tool#name()} 索引，重复注册会覆盖）。 */
  public void register(Tool<?, ?> tool) {
    tools.put(tool.name(), tool);
  }

  /** 按名字查询，返回强类型 {@code Tool<I,O>}。 */
  @SuppressWarnings("unchecked")
  public <I, O> Tool<I, O> get(String name) {
    return (Tool<I, O>) tools.get(name);
  }

  /** 按名字查询，返回原始通配符类型（AgentLoop 调用 execute 时用）。 */
  public Tool<?, ?> getRaw(String name) {
    return tools.get(name);
  }

  /** 列出所有注册的工具（不可变快照） */
  public List<Tool<?, ?>> list() {
    return List.copyOf(tools.values());
  }

  /** M5 Memory 自动注入：Agent 默认拥有读写 memory 的工具。 */
  public static void registerMemoryTools(ToolRegistry registry) {
    registry.register(new ReadFileTool());
    registry.register(new WriteFileTool());
    registry.register(new EditFileTool());
  }
}
