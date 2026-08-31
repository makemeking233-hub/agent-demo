package com.example.agent.plugin;

/**
 * Plugin 生命周期接口 (v1.0 add-plugin-system).
 *
 * <p>Plugin 在 {@code AgentLoopFactory.buildLoop} 阶段被 {@link PluginManager} 实例化并 init.
 * 一个 Plugin 可同时实现若干 {@link ToolProvider} / {@link LlmProviderExtension} 等
 * ExtensionPoint 接口, PluginManager.init 走完所有 init 后, 由 AgentLoop 调各 ExtensionPoint 钩子.
 *
 * <p>CLI profile 不依赖 Spring, Plugin 不能引入 Spring Bean 注解; 所有依赖通过
 * {@link PluginContext} 显式注入.
 */
public interface Plugin {

    /** Plugin 名字 (默认 class 简单名, 子类可 override). 用于 PluginManager 去重. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** 启动时调一次. 抛异常被 PluginManager 捕获, 不影响其他 plugin. */
    default void init(PluginContext ctx) throws Exception {}

    /** shutdown 钩子. 抛异常被 PluginManager 捕获, 不影响其他 plugin. */
    default void close() throws Exception {}
}
