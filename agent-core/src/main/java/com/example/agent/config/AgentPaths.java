package com.example.agent.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * agent 数据目录的**唯一解析入口**（fix-agent-home-isolation）。
 *
 * <p>此前「agent home 在哪」在全仓有 11 处各自实现，覆盖链还各不相同：
 * {@code WebAgentRuntime} 认系统属性 {@value #HOME_PROPERTY}，其余各处只认环境变量或
 * {@code user.home}。而 {@code @SpringBootTest} 无法设环境变量、只能设系统属性，
 * 于是唯一的测试隔离开关只被 1 处认——测试日志照样写进用户真实
 * {@code ~/.agent-demo/logs/}，与 {@code openspec/specs/observability} 的
 * 「测试日志与运行时日志隔离」直接冲突。
 *
 * <p>优先级：系统属性 {@value #HOME_PROPERTY} → 环境变量 {@link EnvKeys#AGENT_DEMO_HOME}
 * → {@code user.home}；其下拼 {@value #DEFAULT_DIR_NAME}。空白串一律视为未设置。
 * 三者都取不到时按 {@code user.home} 处理（与改造前行为一致）。
 */
public final class AgentPaths {

    /** 覆盖 agent 数据目录的系统属性名（值为「用户主目录」语义，其下仍拼 {@value #DEFAULT_DIR_NAME}）。 */
    public static final String HOME_PROPERTY = "agent.demo.home";

    /** 覆盖 agent 数据目录的环境变量名。 */
    public static final String HOME_ENV = EnvKeys.AGENT_DEMO_HOME;

    /** 数据目录名。 */
    public static final String DEFAULT_DIR_NAME = ".agent-demo";

    private static final String LOGS = "logs";
    private static final String SESSIONS = "sessions";
    private static final String WORKTREES = "worktrees";

    private AgentPaths() {}

    /**
     * 解析「基目录」（即 {@code user.home} 语义的位置，其下才是 {@value #DEFAULT_DIR_NAME}）。
     *
     * <p>存在的理由：多处调用点自己拼 {@code .agent-demo/xxx}（如 skills、memory、sessions、
     * storage 说明）。改造时只统一**基目录解析**、保留它们原有的拼接，语义零变化——
     * 换掉整条路径反而容易在拼错一层目录时静默改掉落盘位置。
     *
     * @return 基目录
     */
    public static String homeBase() {
        return homeBase(
                System.getProperty(HOME_PROPERTY),
                System.getenv(HOME_ENV),
                System.getProperty("user.home"));
    }

    /**
     * 纯函数重载：按给定的三个来源解析基目录。
     *
     * @param propertyOverride 系统属性值（可空/空白 = 未设置）
     * @param envOverride      环境变量值（可空/空白 = 未设置）
     * @param userHome         {@code user.home} 值
     * @return 基目录
     */
    public static String homeBase(String propertyOverride, String envOverride, String userHome) {
        String base = firstNonBlank(propertyOverride, envOverride);
        return base != null ? base : userHome;
    }

    /**
     * 解析 agent 数据目录（{@code <基目录>/.agent-demo}）。
     *
     * @return 数据目录
     */
    public static Path agentHome() {
        return agentHome(
                System.getProperty(HOME_PROPERTY),
                System.getenv(HOME_ENV),
                System.getProperty("user.home"));
    }

    /**
     * 纯函数重载：按给定的三个来源解析数据目录。
     *
     * <p>{@link System#getenv} 无法在进程内注入，故环境变量分支只能通过本重载覆盖。
     *
     * @param propertyOverride 系统属性值（可空/空白 = 未设置）
     * @param envOverride      环境变量值（可空/空白 = 未设置）
     * @param userHome         {@code user.home} 值
     * @return 数据目录
     */
    public static Path agentHome(String propertyOverride, String envOverride, String userHome) {
        return Paths.get(homeBase(propertyOverride, envOverride, userHome), DEFAULT_DIR_NAME);
    }

    /** 日志根目录（{@code <agentHome>/logs}）。 */
    public static String logsDir() {
        return agentHome().resolve(LOGS).toString();
    }

    /** 会话存档目录（{@code <agentHome>/sessions}）。 */
    public static String sessionsDir() {
        return agentHome().resolve(SESSIONS).toString();
    }

    /** 工作树目录（{@code <agentHome>/worktrees}）。 */
    public static String worktreesDir() {
        return agentHome().resolve(WORKTREES).toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
