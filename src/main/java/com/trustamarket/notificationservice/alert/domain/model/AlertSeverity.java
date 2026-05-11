package com.trustamarket.notificationservice.alert.domain.model;

// AlertManager 의 alert label `severity` 매핑.
// Discord embed 의 color 값과 1:1 — 채널 라우팅 / 시각 표시에 활용.
public enum AlertSeverity {

    CRITICAL(0xE74C3C),   // 빨강
    WARNING(0xF1C40F),    // 노랑
    INFO(0x3498DB);       // 파랑

    private final int color;

    AlertSeverity(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public static AlertSeverity from(String label) {
        if (label == null) return INFO;
        return switch (label.toLowerCase()) {
            case "critical" -> CRITICAL;
            case "warning"  -> WARNING;
            default          -> INFO;
        };
    }
}
