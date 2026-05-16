package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.stereotype.Component;

import java.util.List;

// 등록된 모든 DiscordPayloadStrategy 를 @Order 순서로 평가.
// 첫 번째 supports(alert)==true 인 strategy 가 payload 생성.
// DefaultDiscordPayloadStrategy 가 Order=Integer.MAX_VALUE + supports=true 라 최종 fallback 보장.
@Component
public class DiscordStrategyDispatcher {

    private final List<DiscordPayloadStrategy> strategies;

    public DiscordStrategyDispatcher(List<DiscordPayloadStrategy> strategies) {
        // Spring 이 @Order 순서로 List 주입.
        this.strategies = strategies;
    }

    public DiscordWebhookPayload dispatch(Alert alert) {
        return strategies.stream()
                .filter(s -> s.supports(alert))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "어떤 DiscordPayloadStrategy 도 매칭되지 않음 (DefaultDiscordPayloadStrategy 가 누락된 듯) — alert=" + alert.name()))
                .toPayload(alert);
    }
}
