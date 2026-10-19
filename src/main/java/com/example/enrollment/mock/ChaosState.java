package com.example.enrollment.mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Holds the behavior of each mock system of record, switchable live from the control panel. */
@Component
public class ChaosState {

    public enum Mode {
        /** Responds normally. */
        OK,
        /** Returns 503 for the first four calls after being set, then recovers. */
        FLAKY,
        /** Returns 503 until switched back to OK. */
        DOWN,
        /** Returns 422, a business rejection that must not be retried. */
        REJECT
    }

    public static final List<String> SYSTEMS = List.of("eligibility", "billing", "fulfillment", "notifications");
    private static final int FLAKY_FAILURES = 4;

    private final Map<String, Mode> modes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    public Mode get(String system) {
        return modes.getOrDefault(system, Mode.OK);
    }

    public void set(String system, Mode mode) {
        modes.put(system, mode);
        calls.remove(system);
    }

    /** True while a FLAKY system should still be failing. */
    public boolean flakyShouldFail(String system) {
        return calls.computeIfAbsent(system, k -> new AtomicInteger()).incrementAndGet() <= FLAKY_FAILURES;
    }

    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String system : SYSTEMS) {
            out.put(system, get(system).name());
        }
        return out;
    }
}
