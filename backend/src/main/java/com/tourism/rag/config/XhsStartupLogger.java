package com.tourism.rag.config;

import com.tourism.rag.agent.provider.xhs.XiaohongshuGuideProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时打印小红书配置状态，便于排查 .env 未生效问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XhsStartupLogger {

    private final XiaohongshuGuideProvider xiaohongshuGuide;

    @Value("${agent.xhs.enabled:false}")
    private boolean enabled;

    @Value("${agent.xhs.mode:cookie}")
    private String mode;

    @Value("${agent.xhs.cookie:}")
    private String cookie;

    @Value("${agent.xhs.sign-url:}")
    private String signUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        if (!enabled) {
            log.info("[XHS] 未启用 (XHS_ENABLED=false)");
            return;
        }
        boolean hasCookie = cookie != null && !cookie.isBlank();
        if (hasCookie && cookie.length() < 200) {
            log.warn("[XHS] Cookie 仅 {} 字符，疑似被 .env 截断！请用双引号包裹 XHS_COOKIE=\"a1=...; web_session=...\"",
                    cookie.length());
        }
        log.info("[XHS] 已启用 mode={} cookie={} signUrl={} ready={}",
                mode,
                hasCookie ? "已配置(" + cookie.length() + "字符)" : "未配置",
                signUrl == null || signUrl.isBlank() ? "未配置" : signUrl,
                xiaohongshuGuide.isConfigured());
    }
}
