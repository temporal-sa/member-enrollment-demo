package com.example.enrollment.workflow;

import com.example.enrollment.EnrollmentConstants;
import com.example.enrollment.activities.EnrollmentActivities;
import com.example.enrollment.model.EnrollmentRequest;
import com.example.enrollment.model.EnrollmentResult;
import com.example.enrollment.model.EnrollmentStatus;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Enrolls a member across several systems of record. Each write has a compensation, so a permanent
 * failure part way through unwinds the earlier writes in reverse order (saga pattern).
 */
@WorkflowImpl(taskQueues = EnrollmentConstants.TASK_QUEUE)
public class EnrollmentWorkflowImpl implements EnrollmentWorkflow {

    private static final Logger log = Workflow.getLogger(EnrollmentWorkflowImpl.class);
    private static final SearchAttributeKey<String> STEP =
            SearchAttributeKey.forKeyword(EnrollmentConstants.STEP_ATTRIBUTE);
    private static final int TOTAL_STEPS = 4;

    // Retries are capped at 5 seconds so recovery is visible on a call. Production would use a
    // longer cap. There is no maximum attempt count, so an outage never fails the enrollment.
    private final EnrollmentActivities activities = Workflow.newActivityStub(
            EnrollmentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(1.5)
                            .setMaximumInterval(Duration.ofSeconds(5))
                            .build())
                    .build());

    private String step = "Starting";
    private String state = "running";
    private int completedSteps = 0;

    @Override
    public EnrollmentResult enroll(EnrollmentRequest request) {
        String memberRef = request.memberRef();
        log.info("Enrollment started for member {}", memberRef);

        Saga saga = new Saga(new Saga.Options.Builder()
                .setParallelCompensation(false)
                .setContinueWithError(true)
                .build());

        try {
            // 1. Eligibility system. The compensation is registered BEFORE the call because the
            //    undo activities are idempotent and a timeout can leave a partial write behind.
            setStep("Eligibility");
            saga.addCompensation(activities::undoEligibility, memberRef);
            String eligibilityId = activities.createEligibility(request);
            completedSteps++;

            // 2. Billing system
            setStep("Billing");
            saga.addCompensation(activities::undoBilling, memberRef);
            String billingAccountId = activities.setupBilling(request);
            completedSteps++;

            if ("BUG".equals(request.scenario())) {
                // Simulated bug. This fails the workflow task, not the workflow. Comment out this
                // line, restart the worker, and the enrollment continues from here.
                throw new IllegalStateException("Simulated bug - fix me!");
            }

            // 3. ID card fulfillment vendor
            setStep("ID Card");
            String idCardOrderId = activities.orderIdCard(request);
            completedSteps++;

            // 4. Welcome kit notification
            setStep("Welcome Kit");
            activities.sendWelcome(request);
            completedSteps++;

            setStep("Done");
            state = "completed";
            return new EnrollmentResult(memberRef, eligibilityId, billingAccountId, idCardOrderId, "ENROLLED");

        } catch (ActivityFailure e) {
            // A non-retryable business rejection. Retryable errors never reach this point.
            log.info("Enrollment rejected at step {}, running compensations", step);
            state = "rolling back";
            setStep("Rollback");
            saga.compensate();
            state = "rolled back";

            String reason = e.getCause() instanceof ApplicationFailure af
                    ? af.getOriginalMessage()
                    : "Enrollment failed";
            throw ApplicationFailure.newNonRetryableFailure(reason, "EnrollmentRejected");
        }
    }

    @Override
    public EnrollmentStatus getStatus() {
        return new EnrollmentStatus(step, state, completedSteps, TOTAL_STEPS);
    }

    private void setStep(String newStep) {
        step = newStep;
        Workflow.upsertTypedSearchAttributes(STEP.valueSet(newStep));
    }
}
