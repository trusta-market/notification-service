package com.trustamarket.notificationservice.alert.adapter.in.web;

import com.trustamarket.notificationservice.alert.adapter.in.web.dto.AlertManagerWebhookRequest;
import com.trustamarket.notificationservice.alert.adapter.in.web.dto.CloudMonitoringWebhookRequest;
import com.trustamarket.notificationservice.alert.application.port.in.SendAlertUseCase;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import com.trustamarket.notificationservice.alert.domain.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Webhook 수신 endpoint 두 종류:
// - /webhook            : Prometheus AlertManager 포맷 (alerts 리스트)
// - /cloud-monitoring   : GCP Cloud Monitoring 포맷 (incident 단건)
// 둘 다 도메인 Alert 로 변환해서 SendAlertUseCase 로 위임.
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertWebhookController {

    private final SendAlertUseCase sendAlertUseCase;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody AlertManagerWebhookRequest request) {
        // 빈 alerts 는 정상 케이스 ("no active alerts" 통지) — 200 으로 ack 만 하고 발송 X.
        // 400 으로 돌리면 AlertManager 가 webhook 재시도/실패 알림을 띄울 수 있어 시끄러워짐.
        if (request == null || request.alerts() == null || request.alerts().isEmpty()) {
            return ResponseEntity.ok().build();
        }

        // alerts 안에 null 항목이 섞여오는 케이스 방어 — 한 건 때문에 batch 전체 NPE 로 500 가는 일 없게.
        List<Alert> alerts = request.alerts().stream()
                .filter(Objects::nonNull)
                .map(item -> toDomain(item, request.commonLabels()))
                .toList();
        sendAlertUseCase.send(alerts);
        return ResponseEntity.ok().build();
    }

    // Cloud Monitoring 알림 정책의 webhook notification channel 이 호출.
    // 한 incident 가 OPEN 될 때 / CLOSED 될 때 각각 1번씩.
    @PostMapping("/cloud-monitoring")
    public ResponseEntity<Void> receiveCloudMonitoring(@RequestBody CloudMonitoringWebhookRequest request) {
        if (request == null || request.incident() == null) {
            return ResponseEntity.ok().build();
        }
        Alert alert = toDomain(request.incident());
        sendAlertUseCase.send(List.of(alert));
        return ResponseEntity.ok().build();
    }

    private static Alert toDomain(AlertManagerWebhookRequest.AlertItem item, Map<String, String> commonLabels) {
        Map<String, String> labels = item.labels() != null ? item.labels() : Map.of();
        Map<String, String> ann = item.annotations() != null ? item.annotations() : Map.of();
        return new Alert(
                item.status(),
                AlertSeverity.from(labels.getOrDefault("severity", commonLabels == null ? null : commonLabels.get("severity"))),
                labels.getOrDefault("alertname", "unknown"),
                ann.getOrDefault("summary", labels.getOrDefault("alertname", "")),
                ann.getOrDefault("description", ""),
                labels,
                item.startsAt() != null ? item.startsAt().toInstant() : Instant.now()
        );
    }

    // Cloud Monitoring incident → Alert 도메인 매핑.
    // severity 는 알림 정책의 policy_user_labels.severity 에 박아 두기로 약속 (e.g. critical / warning).
    private static Alert toDomain(CloudMonitoringWebhookRequest.Incident incident) {
        Map<String, String> resourceLabels = incident.resource() != null && incident.resource().labels() != null
                ? incident.resource().labels() : Map.of();
        Map<String, String> userLabels = incident.policy_user_labels() != null
                ? incident.policy_user_labels() : Map.of();

        Map<String, String> mergedLabels = new HashMap<>();
        mergedLabels.putAll(resourceLabels);
        mergedLabels.putAll(userLabels);
        // Cloud Monitoring 의 k8s_container resource 에서 container_name 을 service 명으로 매핑.
        String containerName = resourceLabels.get("container_name");
        if (containerName != null) mergedLabels.put("service", containerName);
        if (incident.observed_value() != null) mergedLabels.put("observed", incident.observed_value());
        if (incident.threshold_value() != null) mergedLabels.put("threshold", incident.threshold_value());

        String status = "OPEN".equalsIgnoreCase(incident.state()) ? "firing" : "resolved";
        String name = incident.policy_name() != null ? incident.policy_name() : "unknown";
        String summary = incident.summary() != null ? incident.summary() : name;
        String description = incident.condition_name() != null ? incident.condition_name() : "";

        return new Alert(
                status,
                AlertSeverity.from(userLabels.get("severity")),
                name,
                summary,
                description,
                mergedLabels,
                incident.started_at() != null ? Instant.ofEpochSecond(incident.started_at()) : Instant.now()
        );
    }
}
