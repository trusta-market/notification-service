# notification-service

운영 알림을 Discord 로 전송하는 서비스. AlertManager / GCP Cloud Monitoring 의 webhook 을 받아서 알림 타입별로 다른 메시지 포맷으로 Discord 채널에 전송.

## Endpoint

| Path | 용도 | 인증 |
|---|---|---|
| `POST /api/v1/alerts/webhook` | Prometheus AlertManager 포맷 수신 | permitAll (내부 webhook) |
| `POST /api/v1/alerts/cloud-monitoring` | GCP Cloud Monitoring incident 포맷 수신 | permitAll (내부 webhook) |

## 흐름

```
AlertManager / Cloud Monitoring
       ↓ webhook POST
api-gateway (/api/v1/alerts/** → lb://notification-service)
       ↓
AlertWebhookController (DTO → Alert 도메인)
       ↓
SendAlertService → AlertChannelPort (Discord)
       ↓
DiscordAlertChannelAdapter
       ↓ DiscordStrategyDispatcher.dispatch(alert)
DiscordPayloadStrategy 구현체 (알림 타입별)
       ↓
RestClient POST → Discord webhook URL
```

## Strategy Pattern (알림 타입별 메시지)

알림마다 다른 메시지 포맷을 사용하기 위해 Strategy pattern 적용:

```
DiscordPayloadStrategy (interface)
  ├─ supports(Alert)   — 이 알림 처리할 수 있나
  └─ toPayload(Alert)  — Discord webhook payload 생성

구현체:
  └─ DefaultDiscordPayloadStrategy   — fallback (@Order MAX_VALUE)
  (추후 알림 타입별 strategy 추가 예정)

DiscordStrategyDispatcher
  └─ @Order 순서로 strategy 순회 → 첫 supports==true 매칭
  └─ Default 가 항상 true 라 최종 fallback 보장
```

### 새 알림 타입 추가 방법

기존 코드 수정 X, 신규 strategy 1개만 추가 (OCP):

```java
@Order(100)   // Default(MAX_VALUE) 보다 먼저 평가되도록
@Component
public class OomAlertDiscordStrategy implements DiscordPayloadStrategy {
    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("oom-");
    }

    @Override
    public DiscordWebhookPayload toPayload(Alert alert) {
        // OOM 알림 전용 메시지 (예: 💥 emoji + restart count + 권장 action)
    }
}
```

@Order 값이 작을수록 우선순위 높음. 보통 100, 200, ... 식으로 부여.

### 구현 예정 strategy

| Strategy | 매칭 (alert.name()) | 메시지 차이 |
|---|---|---|
| `CpuAlertDiscordStrategy` | `cpu-*` | 🔥 CPU% + 메트릭 link |
| `MemoryAlertDiscordStrategy` | `memory-*` | 💾 메모리% + JVM heap hint |
| `OomAlertDiscordStrategy` | `oom-*` | 💥 OOM Killed + restart count + 권장 |
| `PodRestartAlertDiscordStrategy` | `pod-restart-*` | 🔄 재시작 횟수 + log link |
| `DeployFailureAlertDiscordStrategy` | `deploy-failure` | 🚧 GitHub Actions URL + commit/author |
| `DefaultDiscordPayloadStrategy` | (모두) | 현재 포맷 (fallback) |

## 환경 변수

| 키 | 의미 | Secret |
|---|---|---|
| `DISCORD_WEBHOOK_URL` | Discord 채널 webhook URL | `notification-secrets.DISCORD_WEBHOOK_URL` |

## 관련 PRD

- `ignoredocs/prd-notification-alerts.md` — 알림 종류 / 임계 / 구조 정의 (10개)
