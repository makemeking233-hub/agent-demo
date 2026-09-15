package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.settings.SettingsChangeBroadcaster;
import com.example.agent.settings.SettingsFile;
import com.example.agent.settings.SettingsService;
import com.example.agent.settings.SettingsView;
import com.example.agent.settings.exception.SettingsConflictException;
import com.example.agent.settings.exception.SettingsNotFoundException;
import com.example.agent.settings.exception.SettingsValidationException;
import com.example.agent.web.api.dto.SettingsErrorResponse;
import com.example.agent.web.api.dto.SettingsPatchRequest;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

/** SettingsController 单元测试 (add-settings-foundation M1). */
class SettingsControllerTest {

    @TempDir
    Path tmp;

    SettingsController controller;
    SettingsService service;

    @BeforeEach
    void setUp() throws Exception {
        SettingsFile file = new SettingsFile(tmp);
        service = new SettingsService(file, new SettingsChangeBroadcaster());
        controller = new SettingsController(service, file);
    }

    @Test
    void get_returns200WithDefaults() throws Exception {
        ResponseEntity<SettingsView> resp = controller.get();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getGeneral()).isNotNull();
    }

    @Test
    void patch_updatesField() throws Exception {
        SettingsPatchRequest req = new SettingsPatchRequest("dark", null);
        // PATCH URL 用 dot notation（与 SettingsPath.parse 一致）
        var httpReq = MockServerHttpRequest.patch("/api/settings/general.appearance.preference").build();
        ResponseEntity<SettingsView> resp = controller.patch(httpReq, req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> appearance = (Map<String, Object>) resp.getBody().getGeneral().get("appearance");
        assertThat(appearance.get("preference")).isEqualTo("dark");
    }

    @Test
    void patch_unknownPath_throws404() {
        var httpReq = MockServerHttpRequest.patch("/api/settings/general.unknown").build();
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(SettingsNotFoundException.class,
                () -> controller.patch(httpReq, new SettingsPatchRequest("x", null)))).isNotNull();
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<SettingsErrorResponse> resp = controller.handleNotFound(new SettingsNotFoundException("path_not_found"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().error()).isEqualTo("path_not_found");
    }

    @Test
    void handleValidation_returns400() {
        ResponseEntity<SettingsErrorResponse> resp = controller.handleValidation(new SettingsValidationException("value_null"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<SettingsErrorResponse> resp = controller.handleConflict(new SettingsConflictException("revision_mismatch"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void filePath_returns200() throws Exception {
        ResponseEntity<Map<String, String>> resp = controller.filePath();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("path")).endsWith("settings.yaml");
    }

    @Test
    void extractPath_stripsPrefix() throws Exception {
        var httpReq = MockServerHttpRequest.get("/api/settings/general.appearance.preference").build();
        Method m = SettingsController.class.getDeclaredMethod("extractPath",
                org.springframework.http.server.reactive.ServerHttpRequest.class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, httpReq);
        assertThat(result).isEqualTo("general.appearance.preference");
    }

    @Test
    void mockServerHttpResponse_usability() {
        // 防止 unused import 警告
        var response = new MockServerHttpResponse();
        assertThat(response.getStatusCode()).isNull();
    }
}
