package com.example.agent.web.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * T6.3: web profile 启动 banner (spec/design §D6 启动时打印已注册 trusted-host / §R4).
 *
 * <p>仅 {@code @Profile("web")} 激活, 在 Spring context 就绪后打印单行:
 * <pre>{@code dsh web: http://<host>:<port>}</pre>
 *
 * <p>CLI profile (默认) 不加载本 bean, banner 自然不打印; 行为与 v0.1 一致.
 *
 * <p>{@link #banner(WebProperties)} 是纯函数, 便于单测 (不依赖真实 HTTP server 启动).
 */
@Component
@Profile("web")
public class WebStartupBanner {

    /** 是否真正打印过 (防止热刷新时重复打印; v0.1 单次即可). */
    private boolean printed = false;

    /**
     * Spring 事件监听: context 启动完成 (WEB_SERVER_STARTED 之后) 打印 banner.
     * 用 {@link org.springframework.context.ApplicationListener}{@code <ApplicationReadyEvent>}
     * 保证 HTTP server 已绑定端口, 打印的是真实监听话.
     */
    @org.springframework.context.event.EventListener
    public void onReady(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        if (printed) {
            return;
        }
        WebProperties props = event.getApplicationContext().getBean(WebProperties.class);
        System.out.println(banner(props));
        printed = true;
    }

    /** 纯函数: 生成单行 banner, 无多余换行. */
    static String banner(WebProperties props) {
        return "dsh web: http://" + props.host() + ":" + props.port();
    }
}
