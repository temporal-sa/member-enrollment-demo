package com.example.enrollment.mock;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-in for the systems of record that a member enrollment touches. Compensation calls (actions
 * that start with "undo") always succeed so the rollback scenario is predictable on a call.
 * The request body is never logged because in a real system it would contain PHI.
 */
@RestController
@RequestMapping("/mock")
public class MockSystemsController {

    private static final Logger log = LoggerFactory.getLogger(MockSystemsController.class);

    private final ChaosState chaos;

    public MockSystemsController(ChaosState chaos) {
        this.chaos = chaos;
    }

    @PostMapping("/{system}/{action}")
    public ResponseEntity<Map<String, String>> handle(
            @PathVariable String system,
            @PathVariable String action,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body)
            throws InterruptedException {

        Thread.sleep(400); // a little latency so the call looks like a real downstream hop

        if (!action.startsWith("undo")) {
            ChaosState.Mode mode = chaos.get(system);
            if (mode == ChaosState.Mode.DOWN
                    || (mode == ChaosState.Mode.FLAKY && chaos.flakyShouldFail(system))) {
                log.info("mock {}/{} -> 503", system, action);
                return ResponseEntity.status(503).body(Map.of("error", system + " is unavailable"));
            }
            if (mode == ChaosState.Mode.REJECT) {
                log.info("mock {}/{} -> 422", system, action);
                return ResponseEntity.status(422).body(Map.of("error", system + " rejected the request"));
            }
        }

        // Same idempotency key gives the same id, which is what a well-behaved system of record does.
        String seed = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        String id = system.substring(0, 3).toUpperCase() + "-"
                + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
        log.info("mock {}/{} -> 200 ({})", system, action, id);
        return ResponseEntity.ok(Map.of("id", id));
    }
}
