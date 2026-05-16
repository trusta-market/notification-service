package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// alert.name() 이 "pod-restart" 로 시작 (pod-restart-frequent). pod 가 5분간 N번 이상 재시작.
@Order(400)
@Component
public class PodRestartAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("pod-restart");
    }

    @Override
    protected String typeEmoji() {
        return "🔄";
    }

    @Override
    protected String typeKr() {
        return "Pod 재시작 빈번";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        return List.of(new DiscordWebhookPayload.Field("권장",
                "kubectl logs <pod> --previous 로 crash 직전 로그 확인. OOM / Liveness fail / startup probe timeout 의심.",
                false));
    }
}
