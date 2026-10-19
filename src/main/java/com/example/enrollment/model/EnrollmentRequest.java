package com.example.enrollment.model;

/**
 * Everything except memberRef and scenario is treated as PHI/PII. The memberRef is an opaque
 * identifier, safe to use in workflow IDs, search attributes, logs, and compensation payloads.
 * All data here is synthetic.
 */
public record EnrollmentRequest(
        String memberRef,
        String firstName,
        String lastName,
        String dateOfBirth,
        String address,
        String planId,
        String scenario) {}
