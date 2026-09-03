package com.example.agent.web.security;

/**
 * 路径安全校验失败异常（add-workspace-picker-modal）。
 *
 * <p>{@link #code()} 是稳定的 {@code snake_case} 字符串，前端按 code 渲染对应中文错误条，不依赖
 * message 内容。后端在 controller 层把此异常映射为 {@code 400} / {@code 403} / {@code 404} / {@code 409}。
 */
public class HomePathException extends RuntimeException {

    private final String code;

    public HomePathException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
