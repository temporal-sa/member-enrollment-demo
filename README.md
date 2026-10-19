# Member enrollment demo (Temporal, Java, Spring Boot)

A Temporal demo of health insurance member enrollment. A Spring Boot service starts a workflow that writes to several systems of record, waits out outages, and unwinds earlier writes when a later one is permanently rejected. Payloads can be encrypted end to end so the Temporal service never sees PHI. All member data is synthetic.

Use it to show:

- **Saga pattern / compensation**: a rejection at a later step undoes earlier writes, in reverse order.
- **Durable retries through outages**: a system of record can be down for minutes and the enrollment resumes on its own.
- **Worker crash recovery**: kill the worker mid-enrollment, restart it, and the workflow carries on from where it was.
- **PHI / PII protection**: AES-256-GCM payload codec, custom data converter, and a codec server for the Temporal UI.
- **Visibility**: a `Step` search attribute and a control panel that shows live progress and retry attempts.
- **Spring Boot integration**: `temporal-spring-boot-starter`, `@WorkflowImpl` / `@ActivityImpl` auto-discovery, and an injected `WorkflowClient`.
- **Fixing a workflow bug in production**: an optional scenario where the workflow code fails, gets fixed, and the same execution completes.

It fits healthcare, health insurance, and benefits conversations, and works for any audience that cares about multi-system writes with rollback. [DEMO-RUNBOOK.md](DEMO-RUNBOOK.md) has a 40 to 45 minute presenter script.

## How it runs

Two processes from the same codebase, so the worker can be killed and restarted live without losing the control panel.

| Process | Profile | What it does |
|---|---|---|
| api | `api` | Control panel at http://localhost:8080, mock systems of record, codec server, workflow starter. No worker. |
| worker | `worker` | Temporal worker only. Calls the mock systems over HTTP. |

Workflow: Eligibility, then Billing, then ID Card, then Welcome Kit. The first two register compensations. A business rejection (HTTP 4xx) runs them in reverse and fails the workflow. A system outage (5xx or no connection) is retried with no attempt limit.

In the control panel, each system of record can be set to **Healthy**, **Flaky** (fails four times, then succeeds), **Down**, or **Rejecting**. The workflow code never changes between scenarios.

## Prerequisites

- Java 21 (the Gradle toolchain targets 21, and `.sdkmanrc` pins a Temurin 21 build for SDKMAN users). The Gradle wrapper is included.
- [Temporal CLI](https://docs.temporal.io/cli) for the local dev server

## Run locally

```bash
# 1. Temporal dev server, with the codec endpoint wired into the UI (terminal 1)
./starttemporal.sh codec

# 2. Step search attribute, once per dev server
./addsearchattrib.sh

# 3. api process (terminal 2)
./startapi.sh

# 4. worker process (terminal 3)
./startworker.sh
```

`./starttemporal.sh` with no argument starts the dev server without a codec endpoint, and `./starttemporal.sh http://host:port` points the UI at a different codec server.

The api and worker scripts encrypt payloads by default. See [Encryption and the codec server](#encryption-and-the-codec-server) to turn it off.

Open http://localhost:8080, select **Enroll member**, then open the workflow in the Temporal UI at http://localhost:8233.

## Run on Temporal Cloud

Set these environment variables and add the `cloud` profile to both processes. The start scripts only set the `api` or `worker` profile, so run Gradle directly. Encryption is off unless you set `ENCRYPT_PAYLOADS=true`.

```bash
export TEMPORAL_ADDRESS=<namespace>.<accountId>.tmprl.cloud:7233
export TEMPORAL_NAMESPACE=<namespace>.<accountId>
export TEMPORAL_CERT_PATH=/path/to/cert.pem
export TEMPORAL_KEY_PATH=/path/to/key.key
export TEMPORAL_UI_BASE=https://cloud.temporal.io/namespaces/<namespace>.<accountId>/workflows/

./gradlew bootRun --args='--spring.profiles.active=api,cloud'
./gradlew bootRun --args='--spring.profiles.active=worker,cloud'
```

Create the `Step` keyword search attribute in the namespace first (Cloud UI or `tcld`).

## Encryption and the codec server

`startapi.sh` and `startworker.sh` set `ENCRYPT_PAYLOADS=true` unless you override it. To start with plain payloads (for example, the opening of the demo), set it to `false` on both processes:

```bash
ENCRYPT_PAYLOADS=false ./startapi.sh
ENCRYPT_PAYLOADS=false ./startworker.sh
```

Always use the same value on both processes, and restart both when you change it. If you run Gradle directly instead of the scripts, encryption is off unless you set `ENCRYPT_PAYLOADS=true`.

- Local: `./starttemporal.sh codec` points the Temporal UI at `http://localhost:8080/codec`.
- Cloud: in the namespace settings, set the codec server to `http://localhost:8080/codec` and switch on "Pass the user access token". Also set `CODEC_REQUIRE_AUTH=true` on the api process so the codec server rejects calls with no token.

The key comes from `CODEC_KEY` (32 characters) and its label from `CODEC_KEY_ID`. The defaults are demo values.

## Where things are

All paths are under `src/main/java/com/example/enrollment/` unless noted.

| Path | Purpose |
|---|---|
| `workflow/EnrollmentWorkflowImpl.java` | The saga. Sets the `Step` search attribute at each stage. Contains the simulated bug used by the Workflow bug scenario. |
| `activities/EnrollmentActivitiesImpl.java` | REST calls, idempotency key, mapping of 4xx to non-retryable and 5xx to retryable. |
| `mock/` | Mock systems of record and the Healthy, Flaky, Down, Rejecting switch. |
| `api/EnrollmentController.java` | Starts workflows, reports status without needing a worker. |
| `security/` | AES-GCM payload codec, data converter wiring, codec server endpoint. |
| `src/main/resources/static/index.html` | Control panel. |

## Adapting it

- **Different industry**: rename the steps in `EnrollmentWorkflowImpl`, the systems in `index.html` (`STEPS` and `SYSTEMS`), and the synthetic data in `EnrollmentController`. The saga, retry, and codec code does not depend on the domain.
- **Real key management**: `PhiCodec` takes the key lookup as a function. Swap the config-based lookup in `CodecConfig` for your vault or KMS.
- **API key auth on Cloud**: `application-cloud.yml` uses mTLS. Check the Spring Boot integration page in the Temporal docs for the API key property names in your starter version.

## License

MIT. See [LICENSE](LICENSE).
