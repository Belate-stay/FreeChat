# Companion Request Fix Implementation Plan

> **For agentic workers:** Execute each task with test-first checks; use focused parallel server work and independent review, without deployment or Git mutations.

**Goal:** Deliver Beta 1.2.1.4 without null-tool crashes or loss of an already generated narrative reply.

**Architecture:** Keep current request protocol and companion mechanisms. Extract the existing tool-array read and optional reply refinement into small runtime-testable functions, demonstrate their existing failure behavior, then fix them. Use a cancellable OkHttp response reader and stage-specific duration logging. Independently scope server model-route deadlines without relaxing ordinary routes.

**Tech Stack:** Kotlin, Gson 2.11.0, kotlinx.coroutines, OkHttp 4.12, JUnit 4, MockWebServer, Node built-in test runner.

## Tasks

- [x] Snapshot `app/src/main/java/com/freechat/viewmodel/ChatViewModel.kt`, `data/CompanionApiReply.kt` and `app/build.gradle.kts` under `_verify/companion-fix/baseline`; do not use Git HEAD as the baseline for Claude's existing edits.
- [x] Add `app/src/test/java/com/freechat/data/CompanionRequestRegressionTest.kt`. Cover explicit null, absent, empty and populated tool calls, reasoning/content preservation, optional refinement success/failure/empty result and cancellation. Run `:app:testDebugUnitTest --tests com.freechat.data.CompanionRequestRegressionTest`, capture runtime RED against the extracted current behavior, then implement type guards and refinement fallback and rerun GREEN.
- [x] Add `app/src/main/java/com/freechat/data/CompanionHttp.kt` and focused MockWebServer tests for actual status/body, delayed-body cancellation and read failures. Wire only `executeCompanionCall`, retaining the existing connection-only retry rule; label call sites `deep_prep`, `reply`, `length_retry`, `format_repair` for duration logs.
- [x] In `ChatViewModel.kt` replace all three tool-array reads with the shared reader; wrap only optional narrative length refinement. Keep original first-call errors visible and cancellation propagating. Existing source-wiring tests must remain valid or be updated only for the equivalent new wiring, never weakened to hide behavior failures.
- [x] Server task: scoped model-route deadlines, local test RED/GREEN, matching Nginx templates; agent owns server baseline and evidence. Review before integration.
- [x] Set `versionName = "1.2.1.4"`, `versionCode = 263`; run `:app:testDebugUnitTest :freechat-core:test :freechat-companion:test :app:assembleRelease` using JBR 17 and existing 4GB Gradle setting. Verify package name/version/signature and no debug-only QA activity in Release.
- [x] Review exact changes against task snapshots, run focused and full relevant verification again, and write `_verify/companion-fix/VERIFICATION.md` with commands, counts, delivery path, no-deployment caveat and real-device/live-model limitations.
