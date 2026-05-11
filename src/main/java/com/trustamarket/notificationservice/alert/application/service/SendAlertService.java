package com.trustamarket.notificationservice.alert.application.service;

import com.trustamarket.notificationservice.alert.application.port.in.SendAlertUseCase;
import com.trustamarket.notificationservice.alert.application.port.out.AlertChannelPort;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

// alert 처리 흐름 — 채널로 발송. 한 alert 발송 실패가 다른 alert 를 막지 않게 per-alert try/catch.
@Slf4j
@Service
@RequiredArgsConstructor
public class SendAlertService implements SendAlertUseCase {

    private final AlertChannelPort channel;

    @Override
    public void send(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) return;
        for (Alert alert : alerts) {
            try {
                channel.deliver(alert);
            } catch (Exception e) {
                log.error("[Alert] 발송 실패 — name={}, status={}", alert.name(), alert.status(), e);
                // 다음 alert 계속 진행 (한 건 실패가 batch 전체 막지 않게)
            }
        }
    }
}
