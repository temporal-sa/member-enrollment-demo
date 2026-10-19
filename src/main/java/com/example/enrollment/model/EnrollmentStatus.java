package com.example.enrollment.model;

public record EnrollmentStatus(String step, String state, int completedSteps, int totalSteps) {}
