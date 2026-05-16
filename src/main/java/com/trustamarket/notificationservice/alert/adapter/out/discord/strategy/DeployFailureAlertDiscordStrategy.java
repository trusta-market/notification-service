package com.trustamarket.notificationservice.alert.adapter.out.discord.strategy;

import com.trustamarket.notificationservice.alert.adapter.out.discord.DiscordWebhookPayload;
import com.trustamarket.notificationservice.alert.domain.model.Alert;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// alert.name() == "deploy-failure". GitHub Actions deploy workflow 실패 시.
// extra fields: commit / actor / branch / run URL / log preview.
// log 는 Discord embed field value 1024자 제한 — 마지막 30줄로 truncate + 전체는 run URL 에서.
@Order(600)
@Component
public class DeployFailureAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    private static final int LOG_PREVIEW_LINES = 30;
    private static final int LOG_FIELD_MAX_CHARS = 950;   // Discord 1024 한도 보수적으로 (코드 블록 ``` 포함)

    @Override
    public boolean supports(Alert alert) {
        return "deploy-failure".equals(alert.name());
    }

    @Override
    protected String typeEmoji() {
        return "🚧";
    }

    @Override
    protected String typeKr() {
        return "배포 실패";
    }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        Map<String, String> labels = alert.labels() != null ? alert.labels() : Map.of();
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>();

        if (labels.containsKey("commit")) {
            String commit = labels.get("commit");
            String shortSha = commit.length() > 7 ? commit.substring(0, 7) : commit;
            fields.add(new DiscordWebhookPayload.Field("commit", "`" + shortSha + "`", true));
        }
        if (labels.containsKey("actor")) {
            fields.add(new DiscordWebhookPayload.Field("actor", "@" + labels.get("actor"), true));
        }
        if (labels.containsKey("branch")) {
            fields.add(new DiscordWebhookPayload.Field("branch", labels.get("branch"), true));
        }
        if (labels.containsKey("run_url")) {
            fields.add(new DiscordWebhookPayload.Field(
                    "GitHub Actions",
                    "[전체 log + step 보기](" + labels.get("run_url") + ")",
                    false));
        }
        if (labels.containsKey("logs")) {
            String preview = lastNLines(labels.get("logs"), LOG_PREVIEW_LINES);
            if (preview.length() > LOG_FIELD_MAX_CHARS) {
                preview = preview.substring(preview.length() - LOG_FIELD_MAX_CHARS);
            }
            fields.add(new DiscordWebhookPayload.Field(
                    "log preview (마지막 " + LOG_PREVIEW_LINES + "줄)",
                    "```\n" + preview + "\n```",
                    false));
        }
        fields.add(new DiscordWebhookPayload.Field("권장",
                "1) log 의 실패 단계 확인  2) rollback (`kubectl rollout undo deployment/{service}`) 또는 fix 후 재배포  3) Liquibase checksum mismatch 면 DATABASECHANGELOG md5sum NULL 처리",
                false));
        return fields;
    }

    private static String lastNLines(String text, int n) {
        String[] lines = text.split("\\R");
        int start = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (i > start) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
