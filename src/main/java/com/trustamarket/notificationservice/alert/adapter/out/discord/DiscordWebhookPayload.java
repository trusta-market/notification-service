package com.trustamarket.notificationservice.alert.adapter.out.discord;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

// Discord webhook 의 message body. content 또는 embeds 필요.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscordWebhookPayload(
        String content,
        List<Embed> embeds
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Embed(
            String title,
            String description,
            int color,
            List<Field> fields,
            Instant timestamp
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Field(
            String name,
            String value,
            Boolean inline
    ) {}
}
