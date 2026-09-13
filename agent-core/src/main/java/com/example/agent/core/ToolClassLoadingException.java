package com.example.agent.core;

/**
 * 工具类加载失败（harden-tool-error-boundary）。
 *
 * <p>把工具交互过程中逃出的 {@link LinkageError}（{@code NoClassDefFoundError} /
 * {@code ExceptionInInitializerError} / {@code UnsupportedClassVersionError} 等）包装成普通
 * 运行时异常，使它能被 {@code AgentLoop} 既有的错误处理路径转成错误 {@code ToolResult}。
 *
 * <p>为什么必须这么做：Reactor 的 {@code Exceptions.throwIfFatal} 会把 {@code LinkageError}
 * **原样重新抛出**而不是转成 {@code onError} 信号，因此任何下游的 {@code onErrorResume} /
 * {@code onErrorMap} 都看不到它——错误会穿透整条响应式链，最终撕掉整条 SSE 连接。只有在把代码
 * 交给 Reactor **之前**捕获并转换，才有机会降级。
 *
 * <p>语义：工具是可替换的插件式组件，它的类加载失败意味着「这个工具不可用」，而不是「进程不可
 * 用」。{@link VirtualMachineError}（OOM / StackOverflow）不在此列，仍然致命。
 *
 * @see AgentLoop#invokeTool(String, String, java.util.function.Supplier)
 */
public class ToolClassLoadingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 出问题的工具名（用于日志与测试断言）。 */
    private final String toolName;

    /** 失败阶段：入参解析 / 权限检查 / 工具执行。 */
    private final String stage;

    /**
     * 构造降级异常。
     *
     * @param toolName 工具名
     * @param stage 失败阶段（中文短语，直接进错误消息）
     * @param cause 原始 {@link LinkageError}
     */
    public ToolClassLoadingException(String toolName, String stage, LinkageError cause) {
        super("工具类加载失败 [" + toolName + "] 于「" + stage + "」阶段: " + cause, cause);
        this.toolName = toolName;
        this.stage = stage;
    }

    /**
     * @return 工具名
     */
    public String toolName() {
        return toolName;
    }

    /**
     * @return 失败阶段
     */
    public String stage() {
        return stage;
    }
}
