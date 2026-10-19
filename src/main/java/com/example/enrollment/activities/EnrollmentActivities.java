package com.example.enrollment.activities;

import com.example.enrollment.model.EnrollmentRequest;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface EnrollmentActivities {

    String createEligibility(EnrollmentRequest request);

    String setupBilling(EnrollmentRequest request);

    String orderIdCard(EnrollmentRequest request);

    void sendWelcome(EnrollmentRequest request);

    // Compensations take only the opaque member reference, never PHI.
    void undoEligibility(String memberRef);

    void undoBilling(String memberRef);
}
