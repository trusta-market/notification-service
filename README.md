# notification-service

운영 알림을 Discord 로 전송하는 서비스. AlertManager / GCP Cloud Monitoring 의 webhook 을 받아 알림 타입별로 다른 메시지 포맷으로 Discord 채널에 전송. Cloud Monitoring incident URL 도 함께 첨부해 클릭 한 번에 메트릭/로그 확인 가능.

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
AlertWebhookController (DTO → Alert 도메인 — labels.incident_url 포함)
       ↓
SendAlertService → AlertChannelPort (Discord)
       ↓
DiscordAlertChannelAdapter
       ↓ DiscordStrategyDispatcher.dispatch(alert)
DiscordPayloadStrategy 구현체 (알림 타입별 — Cpu/Memory/Oom/PodRestart/DbPool/Default)
       ↓
RestClient POST → Discord webhook URL
```

## Strategy Pattern (알림 타입별 메시지)

알림 종류별로 emoji / 권장 액션 / 추가 field 를 다르게 보내기 위해 Strategy pattern 적용.

```
DiscordPayloadStrategy (interface)
  ├─ supports(Alert)   — 이 알림 처리할 수 있나
  └─ toPayload(Alert)  — Discord webhook payload 생성

AbstractDiscordPayloadStrategy (template)
  ├─ 공통: title / 발생시각 포맷 / severity emoji / 기본 4 fields
  ├─ Cloud Monitoring incident URL (있으면) markdown link field 자동 추가
  └─ 하위 클래스는 typeEmoji() / typeKr() / buildExtraFields() 만 override

구현체:
  ├─ CpuHighAlertDiscordStrategy        🔥  (@Order 100)
  ├─ MemoryAlertDiscordStrategy         💾  (@Order 200)
  ├─ OomAlertDiscordStrategy            💥  (@Order 300)
  ├─ PodRestartAlertDiscordStrategy     🔄  (@Order 400)
  ├─ DbPoolAlertDiscordStrategy         🐘  (@Order 500)
  └─ DefaultDiscordPayloadStrategy           (@Order MAX_VALUE — fallback)

DiscordStrategyDispatcher
  └─ @Order 순서로 strategy 순회 → 첫 supports==true 매칭
  └─ Default 가 항상 true 라 최종 fallback 보장
```

### 구현된 strategy

| Strategy | 매칭 (alert.name()) | emoji | 메시지 특징 |
|---|---|---|---|
| `CpuHighAlertDiscordStrategy` | `cpu-*` | 🔥 | 관측값/임계값 + replica/limit 증액 권장 |
| `MemoryAlertDiscordStrategy` | `memory-*` | 💾 | 관측값/임계값 + JVM heap 확인 권장 |
| `OomAlertDiscordStrategy` | `oom-*` | 💥 | limit 증액 / JVM heap 옵션 / heap dump 분석 권장 |
| `PodRestartAlertDiscordStrategy` | `pod-restart*` | 🔄 | `kubectl logs --previous` + crash 원인 분류 안내 |
| `DbPoolAlertDiscordStrategy` | `db-pool*` | 🐘 | 관측값 + HikariCP/max_connections/Cloud SQL 검토 권장 |
| `DefaultDiscordPayloadStrategy` | (모두) | severity emoji | 기존 일반 포맷 (fallback) |

모든 알림 메시지에 **Cloud Monitoring incident URL** 이 markdown link 로 첨부됨 (Cloud Monitoring webhook 의 `incident.url` → `labels.incident_url`). 클릭 시 메트릭 그래프 + 관련 log 까지 한 화면.

### 새 알림 타입 추가 방법

기존 코드 수정 X, 신규 strategy 1개만 추가 (OCP):

```java
@Order(600)   // Default(MAX_VALUE) 보다 먼저, 기존 strategy 들과 우선순위 겹치지 않게
@Component
public class Http5xxAlertDiscordStrategy extends AbstractDiscordPayloadStrategy {

    @Override
    public boolean supports(Alert alert) {
        return alert.name() != null && alert.name().startsWith("http-5xx");
    }

    @Override
    protected String typeEmoji() { return "🚫"; }

    @Override
    protected String typeKr() { return "5xx 에러율 높음"; }

    @Override
    protected List<DiscordWebhookPayload.Field> buildExtraFields(Alert alert) {
        List<DiscordWebhookPayload.Field> fields = new ArrayList<>(observedFields(alert));
        fields.add(new DiscordWebhookPayload.Field("권장",
                "service log + downstream (DB/Feign) 상태 확인",
                false));
        return fields;
    }
}
```

`@Order` 값이 작을수록 우선순위 ↑. 보통 100, 200, … 단위로.

## Cloud Monitoring incident URL link

`AlertWebhookController.toDomain` 에서 `incident.url` → `labels.incident_url` 매핑. `AbstractDiscordPayloadStrategy.toPayload()` 가 마지막 field 로 markdown link 추가:

```
원본: [Cloud Monitoring 에서 보기 (메트릭 + log)](https://console.cloud.google.com/...)
```

향후 pod log 직접 첨부 (Cloud Logging API 또는 K8s API) 는 별도 이슈로 추적: #9

## 환경 변수

| 키 | 의미 | Secret |
|---|---|---|
| `DISCORD_WEBHOOK_URL` | Discord 채널 webhook URL | `notification-secrets.DISCORD_WEBHOOK_URL` |

## 관련

- Cloud Monitoring alert policies — `trusta-monitoring` repo + GCP Cloud Monitoring (현재 enable: cpu-high / memory-high-warn / memory-critical / db-pool-high / db-pool-pending / pod-restart-frequent)
- PodMonitoring (메트릭 scrape 설정) — `trusta-monitoring` repo

---

# 성능 테스트

## 트래픽 목표

| 구분 | 평상시 | 피크 타임 |
|---|---|---|
| 전체 플랫폼 | ~100 RPS | ~2,000 RPS |

## 서비스별 목표

| 서비스 | 주요 부하 유형 | p95 | p99 | 근거 |
|---|---|---|---|---|
| product-service | 상품 조회 (Read 집중) | < 200ms | < 500ms | 브라우징은 UX에 직결, 가장 많은 요청 수 |
| order-service | 주문 생성 (Write) | < 500ms | < 1,000ms | DB 쓰기 + 상품 유효성 Feign 호출 포함 |
| payment-service | 결제 요청 처리 | < 500ms | < 1,000ms | DB 쓰기 + 외부 결제 의존. 외부 서비스 연결 시 부하 테스트 어려워 요청 단위로 변경 |
| delivery-service | 배송 조회 (Read) | < 300ms | < 700ms | 배송 상태 추적, 소비자 폴링 시나리오 |
| inspection-service | 검수 처리 (내부 오퍼레이션) | < 500ms | < 1,000ms | 검수관 전용, 일반 사용자 트래픽 없음 |

## 서비스별 트래픽 분포 예측 (전체 2,000 RPS 기준)

| 서비스 | 비중 | 비고 |
|---|---|---|
| product-service | ~50% | 상품 조회/검색 |
| order-service | ~20% | 주문 생성/조회 |
| user-service | ~15% | 인증/토큰 검증 |
| delivery-service | ~10% | 배송 상태 조회 |
| payment-service | ~4% | 결제는 구매 전환율에 비례 |
| inspection-service | ~1% | 검수관 내부 오퍼레이션 |

## 성능 테스트 계획

```
1단계: 베이스라인 측정 (제한된 GKE 환경)
  - 환경 조건 (노드 스펙, Pod 수, 리소스 제한값)
  - 테스트 시나리오 (RPS 구성 비율)
  - 측정 지표 (에러율, p95/p99, Kafka lag, Pod 상태)
  - 예상 병목 지점
  - 실측 결과
2단계: 개선 (코드 + 인프라)
  - 코드 레벨: 커넥션 풀 튜닝, 캐싱 도입 등
  - 인프라 레벨: HPA, 노드 풀 확장, Kafka 파티션 조정
  - CQRS
3단계: 검증
  - 동일 시나리오 재실행
  - Before/After 수치 비교
```

## 테스트 시나리오

### 시나리오 1: 판매자 여정 (40%)

1. `POST /api/v1/users` — 로그인
2. `POST /api/products` — 상품 등록
3. `POST /api/products/{id}/inspection` — 검수 요청 → Kafka → inspection-service
4. `POST /api/v1/admin/inspections/{id}/start` — 검수 시작
5. `POST /api/v1/admin/inspections/{id}/complete` — 검수 완료 → Kafka → product-service
6. `GET /api/v1/inspections/me` — 검수 결과 조회 (판매자 확인)
7. `GET /api/products/{id}` — 상품 상태 반영 확인 (ON_SALE 여부)

관측 포인트:
- 로그인 응답 시간, JWT 발급 처리량
- 상품 등록 TPS, 검수 요청 후 상태 전이 지연
- 검수 상태 전이 처리 시간, Kafka 이벤트 전파 lag

### 시나리오 2: 구매자 여정 (60%)

1. `POST /api/v1/users` — 로그인
2. `GET /api/products/latest` — 상품 목록 탐색
3. `GET /api/products/category/{id}` — 카테고리 필터
4. `GET /api/products/{id}` — 상품 상세
5. `POST /api/v1/orders` — 주문 생성 (Feign → product-service 재고 확인)
6. `POST /api/v1/orders/{id}/payments` — 결제 요청 (Kafka → wallet-service, Kafka: order.paid → delivery-service)
7. `GET /api/v1/orders/{id}` — 주문 상태 폴링
8. `POST /api/v1/orders/{id}/confirmations` — 구매 확정 (Kafka → settlement-service)

관측 포인트:
- 인증 처리량
- 상품 조회 응답 시간 (트래픽 가장 집중)
- 주문 생성 → 결제 → 확정 각 단계 응답 시간, 에러율
- 에스크로 처리 TPS, 결제 실패율
- 정산 Kafka 이벤트 수신 lag, 정산 처리 성공률
- `order.paid` 수신 후 delivery 생성까지 지연 시간

### 서비스별 독립 시나리오

| 독립 테스트 대상 | 핵심 확인 지표 |
|---|---|
| 회원 서비스 단독 | 로그인 TPS, JWT 발급 p99 |
| 상품 서비스 단독 | Read p99, DB 쿼리 시간 |
| 주문 서비스 단독 | 주문 생성 TPS |
| 결제 서비스 단독 | 에스크로 처리 TPS, 동시성 안전성 |
| 정산 서비스 단독 | 정산 이벤트 처리 지연 |
| 검수 / 배송 서비스 단독 | 상태 전이 처리 시간 |
