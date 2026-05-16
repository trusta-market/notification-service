package com.trustamarket.notificationservice.alert.adapter.out.discord;

import com.trustamarket.notificationservice.alert.adapter.out.discord.strategy.DiscordStrategyDispatcher;
import com.trustamarket.notificationservice.alert.application.port.out.AlertChannelPort;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

// AlertChannelPort 의 Discord 구현체. RestClient 로 webhook URL 에 POST.
// URL 은 환경변수 (DISCORD_WEBHOOK_URL) 로만 주입 — 코드 / 로그에 노출 금지.
//
// 메시지 포맷팅은 DiscordStrategyDispatcher 가 알림 종류별로 다른 strategy 선택.
// 알림 타입 추가 시 새 DiscordPayloadStrategy 구현체만 추가 (이 adapter 는 수정 X).
@Slf4j
@Component
public class DiscordAlertChannelAdapter implements AlertChannelPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final String webhookUrl;
    private final RestClient restClient;
    private final DiscordStrategyDispatcher dispatcher;

    public DiscordAlertChannelAdapter(
            @Value("${trusta.notification.discord.webhook-url}") String webhookUrl,
            DiscordStrategyDispatcher dispatcher) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("DISCORD_WEBHOOK_URL 환경변수 미설정 — notification-service 는 webhook URL 없이 시작 불가");
        }
        this.webhookUrl = webhookUrl;
        this.dispatcher = dispatcher;
        // RestClient 기본값은 connect/read timeout 무한 — Discord 가 느리거나 응답 없으면 caller thread 블록.
        // JDK HttpClient + JdkClientHttpRequestFactory 로 명시적 timeout 부여.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void deliver(Alert alert) {
        DiscordWebhookPayload payload = dispatcher.dispatch(alert);
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("[Discord] non-2xx 응답 — alertname={}, status={}", alert.name(), response.getStatusCode());
            }
        } catch (RestClientException e) {
            // 채널로 위임된 알림 자체가 실패. 로그로 흔적 + 호출자 (SendAlertService) 가 다음 alert 계속 진행.
            log.error("[Discord] webhook POST 실패 — alertname={}", alert.name(), e);
            throw e;
        }
    }
}
