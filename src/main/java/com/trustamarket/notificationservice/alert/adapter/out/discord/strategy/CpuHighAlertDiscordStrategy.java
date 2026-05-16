package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// alert.name() 이 "cpu-" 로 시작하는 모든 정책 (현재 cpu-high). CPU 사용률 압박.
@Order(100)
@Component
public class CpuHighAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("cpu-");
    }

    @Override
    protected String typeEmoji() {
        return "🔥";
    }

    @Override
    protected String typeKr() {
        return "CPU 사용률 높음";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        // 관측값 / 임계값 + 권장 액션
        List<DiscordWebhookPayload.Field> fields = new java.util.ArrayList<>(observedFields(alert));
        fields.add(new DiscordWebhookPayload.Field("권장",
                "service 의 CPU limit 또는 replicas 증액 검토. 부하 테스트 중이면 의도된 부하인지 확인.",
                false));
        return fields;
    }
}
