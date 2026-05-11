package com.trustamarket.notificationservice.alert.adapter.out.discord;

import com.trustamarket.notificationservice.alert.application.port.out.AlertChannelPort;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// AlertChannelPort 의 Discord 구현체. RestClient 로 webhook URL 에 POST.
// URL 은 환경변수 (DISCORD_WEBHOOK_URL) 로만 주입 — 코드 / 로그에 노출 금지.
//
// 메시지 템플릿 (고정 헤더 + 메타 필드 + 본문):
//   title       : "{severityEmoji} [{SEVERITY}] 알림 {발생|복구}"
//   description : alert.description (없으면 summary)
//   fields      : 알림명 / 중요도 / 서비스 / 발생시각 (+ 추가 라벨)
//   color       : severity 별
@Slf4j
@Component
public class DiscordAlertChannelAdapter implements AlertChannelPort {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    private static final int MAX_EXTRA_LABEL_FIELDS = 6;

    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordAlertChannelAdapter(@Value("${trusta.notification.discord.webhook-url}") String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("DISCORD_WEBHOOK_URL 환경변수 미설정 — notification-service 는 webhook URL 없이 시작 불가");
        }
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public void deliver(Alert alert) {
        DiscordWebhookPayload payload = toPayload(alert);
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

    private DiscordWebhookPayload toPayload(Alert alert) {
        String emoji = switch (alert.severity()) {
            case CRITICAL -> "🚨";
            case WARNING  -> "⚠️";
            case INFO     -> "ℹ️";
        };
        String statusKr = alert.isFiring() ? "발생" : "복구";
        String title = emoji + " [" + alert.severity().name() + "] 알림 " + statusKr;

        Map<String, String> labels = alert.labels() != null ? alert.labels() : Map.of();
        String service = labels.getOrDefault("service", "-");
        String occurredAt = alert.occurredAt() != null ? TS_FORMAT.format(alert.occurredAt()) : "-";

        List<DiscordWebhookPayload.Field> fields = new ArrayList<>();
        fields.add(new DiscordWebhookPayload.Field("알림명", alert.name(), true));
        fields.add(new DiscordWebhookPayload.Field("중요도", alert.severity().name(), true));
        fields.add(new DiscordWebhookPayload.Field("서비스", service, true));
        fields.add(new DiscordWebhookPayload.Field("발생시각", occurredAt, true));

        labels.entrySet().stream()
                .filter(e -> !"alertname".equals(e.getKey())
                        && !"severity".equals(e.getKey())
                        && !"service".equals(e.getKey()))
                .limit(MAX_EXTRA_LABEL_FIELDS)
                .forEach(e -> fields.add(new DiscordWebhookPayload.Field(e.getKey(), e.getValue(), true)));

        String body = (alert.description() != null && !alert.description().isBlank())
                ? alert.description()
                : (alert.summary() != null && !alert.summary().isBlank() ? alert.summary() : null);

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                title,
                body,
                alert.severity().color(),
                fields,
                null   // 발생시각은 fields 에 두고, embed timestamp 는 사용 X (포맷 일관성)
        );
        return new DiscordWebhookPayload(null, List.of(embed));
    }
}
