package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// fallback strategy — 다른 어떤 strategy 도 매칭되지 않을 때.
// @Order MAX_VALUE 라 dispatcher 가 가장 마지막에 평가.
// title 은 기존 일반 알림 포맷 ("{severityEmoji} [{SEVERITY}] 알림 {발생|복구}") 유지.
@Order(Integer.MAX_VALUE)
@Component
public class DefaultDiscordPayloadStrategy extends AbstractDiscordPayloadStrategy {

    private static final int MAX_EXTRA_LABEL_FIELDS = 6;

    @Override
    public boolean supports(Alert alert) {
        return true;
    }

    @Override
    protected String typeEmoji() {
        return ""; // 사용 X — buildTitle 에서 severity emoji 사용
    }

    @Override
    protected String typeKr() {
        return "알림";
    }

    @Override
    protected String buildTitle(Alert alert, String service, String statusKr) {
        return severityEmoji(alert) + " [" + alert.severity().name() + "] 알림 " + statusKr;
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        // 기존 동작: labels 중 alertname / severity / service 제외하고 최대 6개 노출.
        Map<String, String> labels = alert.labels() != null ? alert.labels() : Map.of();
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>();
        labels.entrySet().stream()
                .filter(e -> !"alertname".equals(e.getKey())
                        && !"severity".equals(e.getKey())
                        && !"service".equals(e.getKey())
                        && !"incident_url".equals(e.getKey()))   // base 에서 별도 link 로 처리
                .limit(MAX_EXTRA_LABEL_FIELDS)
                .forEach(e -> fields.add(new DiscordWebhookPayload.Field(e.getKey(), e.getValue(), true)));
        return fields;
    }
}
