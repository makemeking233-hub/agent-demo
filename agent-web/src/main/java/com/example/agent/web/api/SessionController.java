package com.example.agent.web.api;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T5.2: GET /api/sessions/current (spec §Requirement: Current Session).
 *
 * <p>v0.1 简化: 没有"活动 session"概念时返 {session_id: null} (HTTP 200, 不返 404).
 * v0.2 接 SessionStore 后返真实 session 元数据.
 */
@RestController
@RequestMapping("/api/sessions")
@Profile("web")
public class SessionController {

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current() {
        return ResponseEntity.ok(Map.of("session_id", (Object) null));
    }
}
