package com.trustamarket.notificationservice.alert.application.port.in;

import com.trustamarket.notificationservice.alert.domain.model.Alert;

import java.util.List;

// alert 한 건 또는 batch 전송. 입력 측은 AlertManager webhook 또는 Kafka consumer 등 다양.
public interface SendAlertUseCase {
    void send(List<Alert> alerts);
}
