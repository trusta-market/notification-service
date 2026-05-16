package com.trustamarket.notificationservice.alert.adapter.in.web.dto;

// GitHub Actions deploy workflow 가 fail 시 reusable workflow 가 호출하는 webhook payload.
// service 이름 + commit + actor + run URL + (실패 시점의) pod log 동시 전달.
public record DeployFailureWebhookRequest(
        String service,    // 예: "order-service"
        String commit,     // git sha
        String actor,      // GitHub username (push or merge 사용자)
        String runUrl,     // GitHub Actions run URL
        String branch,     // 머지된 branch (보통 develop)
        String reason,     // 실패 step 또는 한 줄 요약 (선택)
        String logs        // kubectl logs --previous --tail=N 결과 (전체 텍스트)
) {}
