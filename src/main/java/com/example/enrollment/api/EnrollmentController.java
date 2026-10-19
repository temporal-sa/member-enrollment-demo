package com.example.enrollment.api;

import com.example.enrollment.EnrollmentConstants;
import com.example.enrollment.mock.ChaosState;
import com.example.enrollment.model.EnrollmentRequest;
import com.example.enrollment.workflow.EnrollmentWorkflow;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The part an enrollment service would own: a plain Spring controller that starts workflows
 * through the injected WorkflowClient. Status comes from DescribeWorkflowExecution, so it keeps
 * working while the worker is stopped.
 */
@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private static final String[] FIRST = {"Avery", "Jordan", "Riley", "Morgan", "Casey", "Taylor"};
    private static final String[] LAST = {"Nguyen", "Okafor", "Lindqvist", "Hartman", "Delgado", "Brennan"};
    private static final String[] PLANS = {"MA-GOLD-2027", "MA-SILVER-2027", "MA-BRONZE-2027"};

    private final WorkflowClient client;
    private final WorkflowServiceStubs service;
    private final ChaosState chaos;
    private final String namespace;
    private final String temporalUiBase;
    private final boolean encrypted;

    public EnrollmentController(
            WorkflowClient client,
            WorkflowServiceStubs service,
            ChaosState chaos,
            @Value("${spring.temporal.namespace:default}") String namespace,
            @Value("${enrollment.temporal-ui-base}") String temporalUiBase,
            @Value("${enrollment.encrypt:false}") boolean encrypted) {
        this.client = client;
        this.service = service;
        this.chaos = chaos;
        this.namespace = namespace;
        this.temporalUiBase = temporalUiBase;
        this.encrypted = encrypted;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("temporalUiBase", temporalUiBase, "namespace", namespace, "encrypted", encrypted);
    }

    @PostMapping("/enrollments")
    public Map<String, String> start(@RequestBody(required = false) Map<String, String> body) {
        String scenario = body == null ? "STANDARD" : body.getOrDefault("scenario", "STANDARD");
        String memberRef = "M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Synthetic PHI. Only memberRef is allowed to leave the encrypted payloads.
        EnrollmentRequest request = new EnrollmentRequest(
                memberRef,
                FIRST[r.nextInt(FIRST.length)],
                LAST[r.nextInt(LAST.length)],
                (1950 + r.nextInt(50)) + "-0" + (1 + r.nextInt(9)) + "-1" + r.nextInt(10),
                (100 + r.nextInt(900)) + " Maple Street, Springfield, IL 627" + (10 + r.nextInt(90)),
                PLANS[r.nextInt(PLANS.length)],
                scenario);

        String workflowId = "enroll-" + memberRef;
        EnrollmentWorkflow workflow = client.newWorkflowStub(
                EnrollmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(EnrollmentConstants.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .build());
        WorkflowClient.start(workflow::enroll, request);
        return Map.of("workflowId", workflowId, "memberRef", memberRef);
    }

    @GetMapping("/enrollments/{workflowId}")
    public Map<String, Object> status(@PathVariable String workflowId) {
        DescribeWorkflowExecutionResponse response = service.blockingStub()
                .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(namespace)
                        .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId).build())
                        .build());
        WorkflowExecutionInfo info = response.getWorkflowExecutionInfo();

        // Search attributes are stored unencrypted, which is why they must never carry PHI.
        Payload stepPayload = info.getSearchAttributes().getIndexedFieldsMap().get(EnrollmentConstants.STEP_ATTRIBUTE);
        String step = stepPayload == null ? "" : stepPayload.getData().toStringUtf8().replace("\"", "");

        List<Map<String, Object>> pending = response.getPendingActivitiesList().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("activity", p.getActivityType().getName());
                    m.put("attempt", p.getAttempt());
                    m.put("state", p.getState().name());
                    return m;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workflowId", workflowId);
        out.put("status", info.getStatus().name().replace("WORKFLOW_EXECUTION_STATUS_", ""));
        out.put("step", step);
        out.put("pendingActivities", pending);
        out.put("workflowTaskAttempt",
                response.hasPendingWorkflowTask() ? response.getPendingWorkflowTask().getAttempt() : 0);
        out.put("historyLength", info.getHistoryLength());
        return out;
    }

    @GetMapping("/systems")
    public Map<String, String> systems() {
        return chaos.snapshot();
    }

    @PutMapping("/systems/{system}")
    public Map<String, String> setSystem(@PathVariable String system, @RequestParam("mode") String mode) {
        if (!ChaosState.SYSTEMS.contains(system)) {
            throw new IllegalArgumentException("Unknown system: " + system);
        }
        chaos.set(system, ChaosState.Mode.valueOf(mode.toUpperCase()));
        return chaos.snapshot();
    }
}
