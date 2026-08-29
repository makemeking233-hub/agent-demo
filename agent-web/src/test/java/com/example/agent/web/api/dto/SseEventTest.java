package com.example.agent.web.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.dto.SseEvent.Error;
import com.example.agent.web.api.dto.SseEvent.MessageDelta;
import com.example.agent.web.api.dto.SseEvent.MessageStart;
import com.example.agent.web.api.dto.SseEvent.MessageStop;
import com.example.agent.web.api.dto.SseEvent.PermissionRequest;
import com.example.agent.web.api.dto.SseEvent.ToolCallEnd;
import com.example.agent.web.api.dto.SseEvent.ToolCallStart;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T3.5/T3.6: SseEvent 序列化 / type 字段对应 (spec §Requirement: SSE Event Types). */
class SseEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void typeFieldMatchesSpec() throws Exception {
        assertThat(new MessageStart("s1", "u1", "deepseek-chat", 0L).type()).isEqualTo("message_start");
        assertThat(new MessageDelta("text", "hi").type()).isEqualTo("message_delta");
        assertThat(new ToolCallStart("c1", "read_file", "{}").type()).isEqualTo("tool_call_start");
        assertThat(new ToolCallEnd("c1", "read_file", true, "ok", 5L).type()).isEqualTo("tool_call_end");
        assertThat(new PermissionRequest("p1", "c1", "write_file", "deny?", List.of("yes", "no")).type())
                .isEqualTo("permission_request");
        assertThat(new MessageStop("stop").type()).isEqualTo("message_stop");
        assertThat(new Error("aborted", "by user").type()).isEqualTo("error");
    }

    @Test
    void serializesMessageDeltaAsSpec() throws Exception {
        var event = new MessageDelta("text", "hello");
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"type\":\"message_delta\"");
        assertThat(json).contains("\"delta_type\":\"text\"");
        assertThat(json).contains("\"content\":\"hello\"");
    }

    @Test
    void serializesPermissionRequestChoices() throws Exception {
        var event = new PermissionRequest("p1", "c1", "write_file", "deny?", List.of("yes", "no", "always"));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"choices\":[\"yes\",\"no\",\"always\"]");
    }
}