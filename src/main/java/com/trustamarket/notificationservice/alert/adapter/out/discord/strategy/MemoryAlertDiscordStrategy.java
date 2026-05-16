package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// alert.name() 이 "memory-" 로 시작 (memory-high-warn / memory-critical).
@Order(200)
@Component
public class MemoryAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("memory-");
    }

    @Override
    protected String typeEmoji() {
        return "💾";
    }

    @Override
    protected String typeKr() {
        // CRITICAL 인 경우는 더 강한 표현
        return "메모리 사용률 높음";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>(observedFields(alert));
        fields.add(new DiscordWebhookPayload.Field("권장",
                "JVM heap 사용 확인 (jvm_memory_used_bytes). OOM 위험 시 memory limit 증액 또는 leak 의심.",
                false));
        return fields;
    }
}
