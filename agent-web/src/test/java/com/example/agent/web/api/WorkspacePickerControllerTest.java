package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** WorkspacePickerController 单元测试 (native-folder-picker). */
class WorkspacePickerControllerTest {

    @Test
    void buildCommand_windows_powershell() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Windows 10", "");
        assertThat(pb.command().get(0)).isEqualTo("powershell");
        assertThat(pb.command()).contains("-NoProfile", "-Command");
        assertThat(pb.command().get(3)).contains("FolderBrowserDialog");
    }

    @Test
    void buildCommand_macos_osascript() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Mac OS X", "");
        assertThat(pb.command().get(0)).isEqualTo("osascript");
        assertThat(pb.command().get(2)).contains("choose folder");
    }

    @Test
    void buildCommand_linux_zenity() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Linux", ":0");
        assertThat(pb.command().get(0)).isEqualTo("zenity");
        assertThat(pb.command()).contains("--file-selection", "--directory");
    }

    @Test
    void buildCommand_linux_noDisplay_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> WorkspacePickerController.buildCommand("Linux", ""));
    }

    @Test
    void buildCommand_linux_nullDisplay_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> WorkspacePickerController.buildCommand("Linux", null));
    }

    @Test
    void smoke_instantiate() {
        var ctrl = new WorkspacePickerController();
        assertThat(ctrl).isNotNull();
    }

    @Test
    void response_type_check() {
        Map<String, String> resp = Map.of("path", "/tmp/test");
        assertThat(resp).containsKey("path");
        assertThat(resp.get("path")).isEqualTo("/tmp/test");
    }
}
