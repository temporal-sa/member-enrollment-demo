package com.example.enrollment.workflow;

import com.example.enrollment.model.EnrollmentRequest;
import com.example.enrollment.model.EnrollmentResult;
import com.example.enrollment.model.EnrollmentStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EnrollmentWorkflow {

    @WorkflowMethod
    EnrollmentResult enroll(EnrollmentRequest request);

    @QueryMethod
    EnrollmentStatus getStatus();
}
