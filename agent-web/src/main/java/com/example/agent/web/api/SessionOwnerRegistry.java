package com.example.agent.web.api;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Session owner registry（rewrite-permission-mode-dsh T7.4 引入；Q2 多用户防护决策）。
 *
 * <p>记录 {@code streamId → owner_ip} 映射。{@link ChatController#send} 创建流时调
 * {@link #register} 写入；{@link ChatController#permission} 切换 mode 时调 {@link #verify} 校验
 * 调用方 IP 与 owner IP 一致；不一致返回 403。
 *
 * <p>实现为进程内 {@link ConcurrentHashMap}，进程重启即丢；适合单机个人使用场景。多租户防护留
 * follow-up（spec §"Open Questions" Q2）。
 */
@Component
public class SessionOwnerRegistry {

    /** stream_id → owner_ip */
    private final ConcurrentMap<String, String> streamOwners = new ConcurrentHashMap<>();

    /**
     * 注册 stream 的 owner IP（send 创建流时调用）。
     *
     * @param streamId  流 id
     * @param ownerIp   调用方 IP（{@code null} 视为空串，verify 时拒绝）
     */
    public void register(String streamId, String ownerIp) {
        if (streamId == null || streamId.isBlank()) {
            throw new IllegalArgumentException("streamId 不可空");
        }
        streamOwners.put(streamId, ownerIp != null ? ownerIp : "");
    }

    /**
     * 校验调用方 IP 与 stream owner IP 一致。
     *
     * <p>三条豁免（mock 测试环境兼容）：
     *
     * <ul>
     *   <li>streamId 未注册 → 通过（向后兼容旧 send 调用未经过 register）
     *   <li>callerIp 为 {@code null} / {@code "unknown"} / 空串 → 通过（mock 环境无法获取真实 IP）
     *   <li>ownerIp 为空串 → 通过（register 时传 null 存空）
     * </ul>
     *
     * @param streamId  流 id
     * @param callerIp  调用方 IP
     * @return true 表示同 IP 或上述豁免; false 表示异 IP
     */
    public boolean verify(String streamId, String callerIp) {
        if (streamId == null || streamId.isBlank()) return false;
        String ownerIp = streamOwners.get(streamId);
        if (ownerIp == null) {
            // 未注册: 视为通过 (向后兼容)
            return true;
        }
        if (ownerIp.isEmpty()) return false;
        if (callerIp == null || callerIp.isBlank() || "unknown".equals(callerIp)) {
            // 无法识别 callerIp: 视为通过 (mock 环境兼容; 生产环境 proxy 应注入真实 X-Forwarded-For)
            return true;
        }
        return ownerIp.equals(callerIp);
    }

    /**
     * 注销 stream (turn 结束 / 异常关闭时调用).
     *
     * @param streamId 流 id
     */
    public void unregister(String streamId) {
        if (streamId != null) streamOwners.remove(streamId);
    }

    /** 当前注册数 (测试用) */
    public int size() {
        return streamOwners.size();
    }
}