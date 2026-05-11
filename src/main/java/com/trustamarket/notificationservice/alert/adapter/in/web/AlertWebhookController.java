package com.trustamarket.notificationservice.alert.adapter.in.web;

import com.trustamarket.notificationservice.alert.adapter.in.web.dto.AlertManagerWebhookRequest;
import com.trustamarket.notificationservice.alert.application.port.in.SendAlertUseCase;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import com.trustamarket.notificationservice.alert.domain.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// AlertManager 의 webhook_configs 가 호출. payload → 도메인 Alert list 변환 → SendAlertUseCase.
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertWebhookController {

    private final SendAlertUseCase sendAlertUseCase;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody AlertManagerWebhookRequest request) {
        if (request == null || request.alerts() == null || request.alerts().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        List<Alert> alerts = request.alerts().stream()
                .map(item -> toDomain(item, request.commonLabels()))
                .toList();
        sendAlertUseCase.send(alerts);
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
}
