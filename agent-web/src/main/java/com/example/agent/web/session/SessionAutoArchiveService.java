package com.example.agent.web.session;

import com.example.agent.session.SessionAutoArchiver;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.web.stream.ChatStreamService;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 超期会话自动归档的调度（auto-archive-stale-sessions）。
 *
 * <p>应用就绪后执行一次，之后按 {@code agent.session.auto-archive.interval-ms} 周期重复；遍历所有
 * 工作区，跳过仍存在活动流的会话。
 *
 * <p>放在 agent-web 而不是 agent-core：内核不应绑定时钟/调度框架，CLI 是短生命周期进程也不需要
 * 后台定时；web 是长驻服务且已有 {@code WebApplication} 作为装配点。
 *
 * <p>调度用 {@code fixedDelay}（上一轮结束后再计时）而非 {@code fixedRate}：整理是低频 IO，
 * 串行更安全。首轮由 {@link ApplicationReadyEvent} 触发，故调度的 {@code initialDelay} 设为同一
 * 间隔，避免启动后立刻重复执行一次。
 */
@Service
@Profile("web")
public class SessionAutoArchiveService {

    private static final Logger log = LoggerFactory.getLogger(SessionAutoArchiveService.class);

    private final WebAgentRuntime runtime;
    private final ChatStreamService streams;
    private final boolean enabled;
    private final int afterDays;

    /**
     * 构造。
     *
     * @param runtime web 运行时（提供数据目录与工作区会话目录）
     * @param streams 活动流服务（用于跳过进行中的会话）
     * @param enabled 是否启用自动归档
     * @param afterDays 保留期天数（超过则归档）
     */
    public SessionAutoArchiveService(
            WebAgentRuntime runtime,
            ChatStreamService streams,
            @Value("${agent.session.auto-archive.enabled:true}") boolean enabled,
            @Value("${agent.session.auto-archive.after-days:7}") int afterDays) {
        this.runtime = runtime;
        this.streams = streams;
        this.enabled = enabled;
        this.afterDays = afterDays;
    }

    /** 应用就绪即整理一次（否则要等第一个调度周期，用户看不到效果）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        archiveAllWorkspaces();
    }

    /**
     * 周期任务。
     *
     * <p>用 {@code fixedDelay}：上一轮结束后再计时，避免慢盘上堆积。首轮延迟与间隔相同，
     * 因为启动那次已由 {@link #onStartup} 完成。
     */
    @Scheduled(
            fixedDelayString = "${agent.session.auto-archive.interval-ms:21600000}",
            initialDelayString = "${agent.session.auto-archive.interval-ms:21600000}")
    public void scheduled() {
        archiveAllWorkspaces();
    }

    /**
     * 遍历所有工作区执行一次整理。
     *
     * @return 本次归档的会话总数（关闭时为 0）
     */
    public synchronized int archiveAllWorkspaces() {
        if (!enabled) {
            log.debug("自动归档已关闭（agent.session.auto-archive.enabled=false）");
            return 0;
        }
        if (afterDays <= 0) {
            log.warn("自动归档保留期非法（after-days={}），本次跳过", afterDays);
            return 0;
        }
        long now = System.currentTimeMillis();
        Duration retention = Duration.ofDays(afterDays);
        int total = 0;
        for (WorkspaceStore.Workspace ws : WorkspaceStore.list(runtime.agentDataDir())) {
            Path sessionsDir = WorkspaceStore.sessionsDirFor(runtime.agentDataDir(), ws.name());
            try {
                SessionAutoArchiver.Result r =
                        SessionAutoArchiver.archiveStale(
                                sessionsDir, retention, now, streams::isSessionActive);
                total += r.archived().size();
                if (!r.archived().isEmpty()) {
                    log.info(
                            "自动归档：workspace={} 归档 {} 个会话（超过 {} 天未活动）",
                            ws.name(),
                            r.archived().size(),
                            afterDays);
                }
            } catch (Exception e) {
                // 单个工作区失败不影响其余
                log.warn("自动归档工作区失败: workspace={}", ws.name(), e);
            }
        }
        return total;
    }
}
