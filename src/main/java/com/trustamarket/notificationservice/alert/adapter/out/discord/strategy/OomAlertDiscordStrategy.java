package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// alert.name() 이 "oom-" 으로 시작 (oom-killer 등). 컨테이너가 OOM Killed 된 상황.
// 현재 Cloud Monitoring 의 log-based metric 으로 정책 추가 예정.
@Order(300)
@Component
public class OomAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("oom-");
    }

    @Override
    protected String typeEmoji() {
        return "💥";
    }

    @Override
    protected String typeKr() {
        return "OOM Killed";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        return List.of(new DiscordWebhookPayload.Field("권장",
                "1) memory limit 증액 (가장 빠름)  2) JVM heap 옵션 (-Xmx) 명시  3) heap dump 떠서 leak 의심 분석",
                false));
    }
}
