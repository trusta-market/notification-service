package com.trustamarket.notificationservice.alert.domain.model;

import java.time.Instant;
import java.util.Map;

// 도메인 입력 — AlertManager / 자체 발생 알림의 공통 표현. record 로 불변.
public record Alert(
        String status,             // "firing" / "resolved"
        AlertSeverity severity,
        String name,               // alertname (예: "OutboxFailedHigh")
        String summary,            // 사람이 읽을 수 있는 한 줄
        String description,        // 자세한 설명
        Map<String, String> labels,// 추가 메타 (service, instance 등)
        Instant occurredAt
) {
    public boolean isFiring() {
        return "firing".equalsIgnoreCase(status);
    }
}
