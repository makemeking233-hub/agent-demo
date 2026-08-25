package com.example.agent.provider;

/**
 * 占位 Message 接口，M2 Task 2.1 替换为 sealed interface。
 * 暂时提供静态工厂方法让 DeepSeekMapper 可以编译通过。
 */
public interface Message {
    String role();
    String content();

    static Message user(String c) {
        return new Message() {
            @Override public String role() { return "user"; }
            @Override public String content() { return c; }
        };
    }

    static Message system(String c) {
        return new Message() {
            @Override public String role() { return "system"; }
            @Override public String content() { return c; }
        };
    }
}