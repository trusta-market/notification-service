package com.trustamarket.notificationservice.alert.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

// Cloud Monitoring webhook payload — incident 한 건당 한 번 호출.
// 전체 스펙: https://cloud.google.com/monitoring/support/notification-options#webhooks
//
// AlertManager 와 달리 incident 단건 (alerts 리스트 X). state 는 "OPEN" / "CLOSED" 두 가지.
// severity 는 alert policy 의 user_labels 에 별도로 박아둬야 함 (e.g. severity=critical / warning).
@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudMonitoringWebhookRequest(
        String version,
        Incident incident
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Incident(
            String incident_id,
            String policy_name,
            String condition_name,
            String state,                       // "OPEN" / "CLOSED"
            Long started_at,                    // epoch seconds
            Long ended_at,                      // epoch seconds, null 가능
            String summary,
            String url,                         // Cloud Monitoring incident page URL
            Resource resource,
            Metric metric,
            String threshold_value,
            String observed_value,
            Map<String, String> policy_user_labels
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Resource(
                String type,                    // e.g. "k8s_container"
                Map<String, String> labels      // e.g. {container_name, pod_name, namespace_name, ...}
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Metric(
                String type,
                Map<String, String> labels
        ) {}
    }
}
