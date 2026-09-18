package com.example.agent.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * settings 视图（add-settings-foundation M1）。
 *
 * <p>用 {@code Map<String, Object>} 承载各 section，避免硬编码每个字段（M2 接入具体项时仍然 Map）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SettingsView {

    @JsonProperty("version")
    private int version = 1;

    @JsonProperty("general")
    private Map<String, Object> general;

    @JsonProperty("revision")
    private long revision = 0L;

    public SettingsView() {
        this.general = defaultGeneral();
    }

    public SettingsView(int version, Map<String, Object> general, long revision) {
        this.version = version;
        this.general = general; // 允许 null；validator 兜底
        this.revision = revision;
    }

    public static Map<String, Object> defaultGeneral() {
        Map<String, Object> g = new LinkedHashMap<>();
        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("preference", "system");
        g.put("appearance", appearance);
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("mode", "ask");
        g.put("permission", permission);
        Map<String, Object> enterBehavior = new LinkedHashMap<>();
        enterBehavior.put("mode", "send");
        g.put("enterBehavior", enterBehavior);
        return g;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Map<String, Object> getGeneral() {
        return general == null ? defaultGeneral() : general;
    }
    public void setGeneral(Map<String, Object> general) { this.general = general; }

    @JsonIgnore
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
}
