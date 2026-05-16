package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// alert.name() 이 "db-pool" 로 시작 (db-pool-high / db-pool-pending). HikariCP connection 압박.
// 부하 테스트 시 가장 먼저 trigger 될 가능성 높음 — Cloud SQL 마이그레이션 의사결정 근거.
@Order(500)
@Component
public class DbPoolAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("db-pool");
    }

    @Override
    protected String typeEmoji() {
        return "🐘";
    }

    @Override
    protected String typeKr() {
        return "DB Pool 압박";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>(observedFields(alert));
        fields.add(new DiscordWebhookPayload.Field("권장",
                "1) HikariCP maximum-pool-size 증액  2) postgres max_connections 증액  3) Cloud SQL 마이그레이션 (영구)",
                false));
        return fields;
    }
}
