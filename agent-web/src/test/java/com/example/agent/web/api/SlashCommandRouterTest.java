package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SlashCommandRouterTest {

    private final SlashCommandRouter router = new SlashCommandRouter();

    @Test
    void nonSlashGoesThroughAsNormal() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("hello world");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().consumed()).isFalse();
    }

    @Test
    void emptyContentRejected() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route(null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void helpReturnsOutput() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/help");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().consumed()).isTrue();
        assertThat(r.getBody().command()).isEqualTo("/help");
        assertThat(r.getBody().output()).contains("/help").contains("/quit");
        assertThat(r.getBody().closeStream()).isFalse();
    }

    @Test
    void clearReturnsOk() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/clear");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().output()).contains("清空");
    }

    @Test
    void resumeReturnsPlaceholder() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/resume");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void historyReturnsPlaceholder() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/history");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void quitReturnsCloseStream() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/quit");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().closeStream()).isTrue();
        assertThat(SlashCommandRouter.shouldCloseStream(r.getBody())).isTrue();
    }

    @Test
    void unknownCommandReturns400() {
        ResponseEntity<SlashCommandRouter.Result> r = router.route("/foo");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().consumed()).isFalse();
    }
}
