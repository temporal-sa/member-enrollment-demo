package com.example.enrollment.model;

public record EnrollmentResult(
        String memberRef,
        String eligibilityId,
        String billingAccountId,
        String idCardOrderId,
        String status) {}
