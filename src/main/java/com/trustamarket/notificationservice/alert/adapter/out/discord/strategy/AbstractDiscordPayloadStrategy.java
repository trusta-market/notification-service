package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 공통 helper — title/description/fields 의 기본 구조 + severity emoji + 시각 포맷.
// 각 알림 타입 strategy 는 buildTitle()/buildExtraFields() 만 override.
public abstract class AbstractDiscordPayloadStrategy implements DiscordPayloadStrategy {

    protected static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    @Override
    public DiscordWebhookPayload toPayload(Alert alert) {
        Map<String, String> labels = alert.labels() != null ? alert.labels() : Map.of();
        String service = labels.getOrDefault("service", "-");
        String occurredAt = alert.occurredAt() != null ? TS_FORMAT.format(alert.occurredAt()) : "-";
        String statusKr = alert.isFiring() ? "발생" : "복구";

        List<DiscordWebhookPayload.Field> fields = new ArrayList<>();
        fields.add(new DiscordWebhookPayload.Field("알림명", alert.name(), true));
        fields.add(new DiscordWebhookPayload.Field("중요도", severityEmoji(alert) + " " + alert.severity().name(), true));
        fields.add(new DiscordWebhookPayload.Field("서비스", service, true));
        fields.add(new DiscordWebhookPayload.Field("발생시각", occurredAt, true));
        fields.addAll(buildExtraFields(alert));
        // Cloud Monitoring incident URL — strategy 무관하게 항상 마지막에 link (있을 때만).
        // markdown link 로 노출 → Discord 에서 "메트릭 + log 보러 가기" 클릭 가능.
        String url = labels.get("incident_url");
        if (url != null && !url.isBlank()) {
            fields.add(new DiscordWebhookPayload.Field(
                    "원본",
                    "[Cloud Monitoring 에서 보기 (메트릭 + log)](" + url + ")",
                    false));
        }

        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                buildTitle(alert, service, statusKr),
                buildBody(alert),
                alert.severity().color(),
                fields,
                null
        );
        return new DiscordWebhookPayload(null, List.of(embed));
    }

    /** 알림 타입 이모지 — strategy 별로 override. */
    protected abstract String typeEmoji();

    /** 알림 타입 이름 (한글) — strategy 별로 override. */
    protected abstract String typeKr();

    /** title 기본 포맷: "{typeEmoji} {typeKr} — {service} {발생|복구}". 필요 시 override. */
    protected String buildTitle(Alert alert, String service, String statusKr) {
        return typeEmoji() + " " + typeKr() + " — " + service + " " + statusKr;
    }

    /** description body — 기본은 alert.description() 또는 summary. 필요 시 override (예: 권장 액션 추가). */
    protected String buildBody(Alert alert) {
        if (alert.description() != null && !alert.description().isBlank()) {
            return alert.description();
        }
        if (alert.summary() != null && !alert.summary().isBlank()) {
            return alert.summary();
        }
        return null;
    }

    /** 알림 타입별 추가 필드 (observed/threshold/권장 등). 기본 빈 list. */
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        return List.of();
    }

    /** severity 별 이모지. */
    protected static String severityEmoji(Alert alert) {
        return switch (alert.severity()) {
            case CRITICAL -> "🚨";
            case WARNING  -> "⚠️";
            case INFO     -> "ℹ️";
        };
    }

    /** Cloud Monitoring 의 observed/threshold label 을 fields 로 변환하는 헬퍼 (있을 때만). */
    protected static List<DiscordWebhookPayload.Field> observedFields(Alert alert) {
        Map<String, String> labels = alert.labels() != null ? alert.labels() : Map.of();
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>();
        if (labels.containsKey("observed")) {
            fields.add(new DiscordWebhookPayload.Field("관측값", labels.get("observed"), true));
        }
        if (labels.containsKey("threshold")) {
            fields.add(new DiscordWebhookPayload.Field("임계값", labels.get("threshold"), true));
        }
        return fields;
    }
}
