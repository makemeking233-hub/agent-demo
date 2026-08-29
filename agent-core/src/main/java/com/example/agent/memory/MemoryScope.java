package com.example.agent.memory;

/** Memory scope 枚举（v0.1 仅 USER；v0.2 增加 PROJECT / LOCAL）。 */
public enum MemoryScope {
    /** 用户级（跨项目持久；~/.agent-demo/memory/） */
    USER,
    /** 项目级（随项目仓库；.agent-demo/memory/；v0.2+） */
    PROJECT,
    /** 机器级（一次性会话；不入磁盘；v0.2+） */
    LOCAL
}
