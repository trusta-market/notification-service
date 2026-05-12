package com.trustamarket.notificationservice.alert.application.port.out;

import com.trustamarket.notificationservice.alert.domain.model.Alert;

// 외부 채널 (Discord / Slack / etc) 발송 추상화. 구현체는 adapter/out 에.
public interface AlertChannelPort {
    void deliver(Alert alert);
}
