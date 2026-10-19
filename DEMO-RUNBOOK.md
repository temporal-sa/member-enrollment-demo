# Demo runbook

A presenter script for the member enrollment demo. It suits architects, platform teams, and security reviewers, especially in healthcare or insurance where the data is PHI and PII. Timing assumes about 40 to 45 minutes of live demo. Drop or shorten segments to fit your slot.

## Before the call

- Do a full dry run, including the encrypted run and the codec server toggle.
- Start with plain payloads, then switch to encrypted for the security segment. The start scripts encrypt by default, so open with `ENCRYPT_PAYLOADS=false ./startapi.sh` and `ENCRYPT_PAYLOADS=false ./startworker.sh`. Have both pairs of start commands ready in a text file.
- Two Temporal UI tabs open: the workflow list, and one workflow detail.
- Control panel and Temporal UI side by side on the shared screen.
- Know where you will say "this is a demo key, in production it lives in your vault".
- If you plan to show the workflow bug, confirm the `throw` in `EnrollmentWorkflowImpl` is not commented out from a previous run.

## Suggested flow

### 1. Frame it (3 min)
Explain the pattern: a service receives an enrollment and Temporal orchestrates the writes to the systems of record. Point at the control panel: four systems of record, each can be made to misbehave. The workflow code never changes during the demo.

### 2. Happy path (4 min)
Enroll one member. In the Temporal UI show the event history: one workflow, four activities, one search attribute (`Step`) moving as it goes.
Say: every step, input, and result is recorded. That history is what makes everything after this possible.

### 3. Flaky billing (4 min)
Set Billing to Flaky, enroll. The panel shows retries; it succeeds on the fifth attempt.
Say: the retry policy is declared once, there is no retry code in the activity, and the idempotency key is the same on every attempt so the billing system can de-duplicate.

### 4. Extended outage (6 min)
Set Billing to Down, enroll. It parks on Billing and keeps retrying. Let it sit for a bit, then set Billing to Healthy. It finishes.
Say: the enrollment waits until the downstream system comes back. No attempt limit, no operator action, no lost work.

### 5. Kill the worker (5 min)
Set Billing to Down, enroll, stop the worker (Ctrl-C), set Billing to Healthy. Nothing moves and the panel says it is waiting for a worker. Restart the worker. The enrollment resumes from where it was.
Say: the state lives in the Temporal service, not in the process. A deploy or a crash is not an outage.

### 6. Permanent failure and rollback (6 min)
Set ID card vendor to Rejecting, enroll. Billing and Eligibility succeed, the vendor rejects, and the compensations run in reverse. Show the history: undo Billing, then undo Eligibility, then the workflow fails with a business reason.
Say: this is the saga pattern. The compensation logic is ordinary Java next to the forward logic, and it is durable, so it also survives crashes.

### 7. PHI protection (10 min)
Restart both processes with the plain start scripts (`./startapi.sh` and `./startworker.sh`), which encrypt by default. Enroll a member. In the Temporal UI, show that inputs, results, and failure messages are encrypted blobs. Turn on the codec server in the UI and show the same history decoded.

Note: `./starttemporal.sh codec` sets the codec endpoint for the whole dev server, so the UI decodes payloads straight away and the encrypted blobs never show. Before this segment, open **Workflows**, select **Configure Codec Server** (the glasses icon, top right), choose to use your browser setting instead of the server setting, and leave the endpoint empty. The history now shows ciphertext. To reveal the decoded history, go back to that dialog and switch to the server setting (or enter `http://localhost:8080/codec`), then refresh. Rehearse this in your dry run, because the dialog wording changes between UI versions.
Say:
- The Temporal service stores only ciphertext. You hold the key.
- The codec server is yours, runs in your network, and decides who can decode. In production it validates the caller's token and role before decoding.
- Workflow IDs, search attributes, and the compensation inputs contain only the opaque member reference. Design for that from the start, because search attributes are not encrypted.
- Keys carry an ID in each payload, so rotation works without re-encrypting history.

### 8. Optional: workflow bug (5 min)
Pick the Workflow bug scenario, enroll. The panel reports the workflow code failing. Comment out the simulated bug `throw` in `EnrollmentWorkflowImpl`, restart the worker, and the same enrollment completes. Uncomment it afterwards so the next run works.
Skip if time is short, or if the audience is focused on security rather than development.

### 9. Spring developer experience (5 min)
Walk through the code, not slides: workflow and activity interfaces as plain Java, `@WorkflowImpl` and `@ActivityImpl` auto-discovery, and `EnrollmentController` starting a workflow with an injected `WorkflowClient`. This is what a real enrollment service would look like.

## Questions to be ready for

- **Open source versus Cloud.** Teams not staffed to run infrastructure care about support and operational burden. Have the concrete comparison ready: who carries pager duty, upgrades, scaling, and what support you get.
- **Cloud provider and region.** Know which providers and regions Temporal Cloud currently supports, and how workers running in one cloud connect to a namespace in another.
- **Egress cost.** The data that crosses to Temporal is payloads (workflow, activity, signal, query, timer inputs and return values). Keep them small and pass references to large documents instead.
- **Who can decode PHI.** Expect questions about key custody, access control on the codec server, and audit logging. Be ready to say what you would recommend, and what is the customer's to decide.
- **Compliance posture.** If HIPAA or BAA questions come up, confirm the current position with the Temporal compliance material before answering. Do not answer from memory.
- **Migrating from an integration platform.** If the team is moving off an ESB or iPaaS (MuleSoft, Boomi, and similar), map their current flows to workflows and activities, and offer enablement.
