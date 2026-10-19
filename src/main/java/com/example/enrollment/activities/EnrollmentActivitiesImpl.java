package com.example.enrollment.activities;

import com.example.enrollment.EnrollmentConstants;
import com.example.enrollment.model.EnrollmentRequest;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin REST clients for the systems of record. In the demo they all point at the mock systems in
 * the api process, which can be switched between healthy, flaky, down, and rejecting at runtime.
 *
 * Error mapping is the important part: 5xx and connection errors are retried by Temporal, 4xx
 * business rejections are not retried and trigger compensation.
 */
@Component
@ActivityImpl(taskQueues = EnrollmentConstants.TASK_QUEUE)
public class EnrollmentActivitiesImpl implements EnrollmentActivities {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentActivitiesImpl.class);

    private final RestClient http;

    public EnrollmentActivitiesImpl(@Value("${enrollment.systems-base-url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String createEligibility(EnrollmentRequest request) {
        return post("eligibility", "create-record", request);
    }

    @Override
    public String setupBilling(EnrollmentRequest request) {
        return post("billing", "create-account", request);
    }

    @Override
    public String orderIdCard(EnrollmentRequest request) {
        return post("fulfillment", "order-id-card", request);
    }

    @Override
    public void sendWelcome(EnrollmentRequest request) {
        post("notifications", "send-welcome", request);
    }

    @Override
    public void undoEligibility(String memberRef) {
        post("eligibility", "undo-create-record", Map.of("memberRef", memberRef));
    }

    @Override
    public void undoBilling(String memberRef) {
        post("billing", "undo-create-account", Map.of("memberRef", memberRef));
    }

    private String post(String system, String action, Object body) {
        ActivityInfo info = Activity.getExecutionContext().getInfo();
        // Stable across retries of the same activity, so a system of record can de-duplicate writes.
        String idempotencyKey = info.getWorkflowId() + "/" + info.getActivityId();
        try {
            Map<?, ?> response = http.post()
                    .uri("/mock/{system}/{action}", system, action)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response == null ? "" : String.valueOf(response.get("id"));
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            log.warn("{} rejected {} (HTTP {})", system, action, code);
            throw ApplicationFailure.newNonRetryableFailure(
                    system + " rejected the request (HTTP " + code + ")", "BusinessRejection");
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("{} unavailable for {} (attempt {})", system, action, info.getAttempt());
            throw ApplicationFailure.newFailure(
                    system + " is unavailable (attempt " + info.getAttempt() + ")", "SystemUnavailable");
        }
    }
}
