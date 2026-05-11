package com.trustamarket.notificationservice.alert.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// AlertManager webhook payload (v4) 부분 매핑.
// 전체 스펙: https://prometheus.io/docs/alerting/latest/configuration/#webhook_config
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertManagerWebhookRequest(
        String version,
        String groupKey,
        String status,           // group status — "firing" / "resolved"
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        String externalURL,
        List<AlertItem> alerts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertItem(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String generatorURL,
            String fingerprint
    ) {}
}
