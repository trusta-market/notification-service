package com.trustamarket.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// CI/로컬에서 DISCORD_WEBHOOK_URL 환경변수 미설정이어도 컨텍스트가 로드되도록 더미 URL 주입.
// 실제 webhook 호출은 일어나지 않음 (테스트는 컨텍스트 로딩만 검증).
@SpringBootTest(properties = "trusta.notification.discord.webhook-url=https://example.invalid/webhook")
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
