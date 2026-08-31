package com.example.agent.plugin.memory;

import com.example.agent.plugin.ExtensionPoints;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;

/**
 * Memory 插件（add-plugin-system v1.0）：把三 scope（USER / PROJECT / LOCAL）记忆说明作为
 * {@code SystemPromptFragment} 拼到 system prompt 尾部。
 *
 * <p>当前实现只承担 {@link SystemPromptFragment}（三 scope 标记），不重复注册记忆工具——记忆工具
 * （读/写 memory 目录）由 {@code AgentLoopFactory.buildTools} 的 {@code registerMemoryTools} 经
 * T5.1 保留注册一次, 避免与 Plugin 重复。真正的三 scope 工具注册（{@code MemoryRecall.init} /
 * 按 scope 的读/写工具）在 {@code add-memory-three-scope} change 落地后再挂到本 Plugin。
 *
 * <p>老 {@code ToolRegistry.registerMemoryTools} 保留为 deprecated wrapper（见 T4.3）。
 */
public class MemoryPlugin implements Plugin, ExtensionPoints.SystemPromptFragment {

    /** 三 scope 记忆说明（拼到 system prompt 尾部）。 */
    @Override
    public String fragment() {
        return "# 长期记忆（三 scope）\n"
                + "- USER：跨项目持久，位于 `~/.agent-demo/memory/`\n"
                + "- PROJECT：随项目仓库，位于 `<cwd>/.agent-demo/memory/`\n"
                + "- LOCAL：本次会话一次性，不入磁盘\n\n"
                + "读取/写入记忆通过文件工具操作对应 memory 目录。";
    }
}
