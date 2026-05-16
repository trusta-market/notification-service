package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;

// Discord 전송용 payload 를 알림 종류별로 다르게 만드는 strategy.
// 새 알림 타입 추가 시 새 구현체 1개 추가 (기존 코드 수정 X — OCP).
//
// 매칭 규칙: DiscordStrategyDispatcher 가 등록된 strategy 들에 대해 supports() 를 순회하며
// 가장 먼저 true 반환하는 것을 사용. 모두 false 면 DefaultDiscordPayloadStrategy 가 fallback.
public interface DiscordPayloadStrategy {

    boolean supports(Alert alert);

    DiscordWebhookPayload toPayload(Alert alert);
}
