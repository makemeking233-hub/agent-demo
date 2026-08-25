package com.example.agent.agent;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import com.example.agent.provider.ToolCall;
import com.example.agent.provider.ToolSpec;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 主循环：单轮对话 → 流式响应 → 工具调度 → 续推（详见 design.md §7）。
 *
 * <p>关键约束：
 * <ul>
 *   <li>{@code maxToolIterations} 强制熔断，防止模型/工具诱导无限循环（§7）</li>
 *   <li>assistant(tool_calls) 必须先入 history，再 append tool_results（§7 消息顺序约束）</li>
 *   <li>流式打印统一在 {@link #printChunk} 内部，processTurn 外层不再打印（避免双打）</li>
 * </ul>
 */
public class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final LlmProvider provider;
    private final ToolRegistry tools;
    private final MessageHistory history;
    private final StreamingPrinter printer;
    private final int maxToolIterations;

    public AgentLoop(LlmProvider provider, ToolRegistry tools,
                     MessageHistory history, StreamingPrinter printer,
                     int maxToolIterations) {
        this.provider = provider;
        this.tools = tools;
        this.history = history;
        this.printer = printer;
        this.maxToolIterations = maxToolIterations;
    }

    public Mono<TurnResult> processTurn(Message.User userMsg) {
        history.append(userMsg);
        return streamUntilStable(0)
            .next()
            .map(this::buildTurnResult);
    }

    private Flux<List<StreamChunk>> streamUntilStable(int iteration) {
        if (iteration >= maxToolIterations) {
            log.warn("hit maxToolIterations={}, stopping turn", iteration);
            return Flux.error(new MaxIterationsExceededException(iteration));
        }
        return provider.streamChat(toRequest())
            .doOnNext(this::printChunk)
            .collectList()
            .flatMapMany(chunks -> {
                Message.Assistant assistant = extractAssistant(chunks);
                history.append(assistant);
                if (assistant.toolCalls() == null || assistant.toolCalls().isEmpty()) {
                    printer.onFinished();
                    return Flux.just(chunks);
                }
                return executeTools(assistant.toolCalls())
                    .flatMap(results -> {
                        history.appendToolResults(toEnvelopes(results));
                        return streamUntilStable(iteration + 1);
                    });
            });
    }

    private ChatRequest toRequest() {
        List<ToolSpec> specs = new ArrayList<>();
        for (var t : tools.list()) {
            specs.add(new ToolSpec(t.name(), t.description(), t.inputSchema()));
        }
        List<com.example.agent.agent.Message> msgs = new ArrayList<>(history.all());
        return new ChatRequest("deepseek-chat", null, msgs, specs, 1.0, 8192, null);
    }

    private void printChunk(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.TextDelta t) {
            printer.onTextDelta(t.text());
        } else if (chunk instanceof StreamChunk.ToolCallStart s) {
            printer.onToolCallStart(s.id(), s.name());
        } else if (chunk instanceof StreamChunk.ToolCallDelta d) {
            printer.onToolCallArgs(d.id(), d.argumentsDelta());
        } else if (chunk instanceof StreamChunk.ToolCallEnd e) {
            printer.onToolCallEnd(e.id(), e.name(), e.arguments());
        } else if (chunk instanceof StreamChunk.Error err) {
            printer.onError(err.message());
        }
        // Finished / Usage 不打印
    }

    private Message.Assistant extractAssistant(List<StreamChunk> chunks) {
        StringBuilder content = new StringBuilder();
        for (StreamChunk c : chunks) {
            if (c instanceof StreamChunk.TextDelta t) content.append(t.text());
        }
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        return new Message.Assistant(content.toString(), calls);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flux<List<ToolResult<Object>>> executeTools(List<ToolCall> calls) {
        return Flux.fromIterable(calls).flatMap(call -> {
            Tool tool = tools.getRaw(call.name());
            if (tool == null) {
                ToolResult<Object> err = ToolResult.<Object>error("工具不存在: " + call.name());
                return Mono.just(List.of(err));
            }
            return tool.execute(call.argumentsJson(), null)
                .map(r -> {
                    @SuppressWarnings("unchecked")
                    ToolResult<Object> typed = (ToolResult<Object>) r;
                    return List.of(typed);
                })
                .flux();
        });
    }

    private List<MessageHistory.ToolResultEnvelope> toEnvelopes(List<ToolResult<Object>> results) {
        return results.stream()
            .map(r -> new MessageHistory.ToolResultEnvelope(
                r.toolCallId(), r.toModelContent(), r.isError()))
            .toList();
    }

    private TurnResult buildTurnResult(List<StreamChunk> chunks) {
        String text = chunks.stream()
            .filter(c -> c instanceof StreamChunk.TextDelta)
            .map(c -> ((StreamChunk.TextDelta) c).text())
            .reduce("", String::concat);
        int prompt = 0, completion = 0;
        for (StreamChunk c : chunks) {
            if (c instanceof StreamChunk.Usage u) {
                prompt += u.promptTokens();
                completion += u.completionTokens();
            } else if (c instanceof StreamChunk.Finished f && f.usage() != null) {
                prompt += f.usage().promptTokens();
                completion += f.usage().completionTokens();
            }
        }
        return new TurnResult(text, prompt, completion, 0);
    }
}