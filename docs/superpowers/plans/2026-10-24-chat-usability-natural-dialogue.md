# Chat Usability and Natural Dialogue Implementation Plan

> **For agentic workers:** Use focused parallel implementation with test-first checks and independent review; preserve existing dirty work, and do not deploy or perform Git mutations.

**Goal:** Deliver Beta 1.2.1.5 with the four requested UI corrections and natural, uncapped-by-message-count companion conversation.

**Architecture:** Reuse existing Compose settings/menu controls, fix the actual tutorial return state, and adjust shared generation-time instructions and visual evidence. Remove forced message-count normalization without deleting generated text or rewriting punctuation after generation. Mirror shared behavior in Web and server call sites where present.

**Tech Stack:** Jetpack Compose, Kotlin/JUnit, existing companion core, TypeScript/Node checks, existing local Android build tools.

## Tasks

- [x] Snapshot touched source files under `_verify/v1215/baseline` before edits. Agent-owned files have their own subfolders; do not replace the task baseline with Git HEAD.
- [x] Settings task: own `SettingsScreen.kt` and, only if needed, `MainActivity.kt`; add a focused tutorial-origin/navigation regression and personalization-order/subtitle check. Capture RED then implement origin-aware back behavior for phone/tablet/floating hosts and existing preview/theme styles.
- [x] Chat UI task: own `ChatScreen.kt`, `ChatInput.kt` and focused regression tests; remove header new-chat rendering, preserve drawer creation, and expose compact-menu reasoning/search controls via existing callbacks and capability state. Coordinate any MainActivity callback cleanup with the settings owner.
- [x] Dialogue task: own `CompanionPrompts.kt`, `CompanionRequestBuilder.kt`, `CompanionRhythm.kt`, the Android vision-call prompt and Web mirrors. First add failing tests for 100 natural segments, ambiguous images/provenance, persona precedence, short punctuation reactions and narrative exclusion; verify old logic fails, then apply minimal changes without content clipping.
- [x] Audit parser, delivery, WeChat forwarding, retry and voice paths for fixed-count truncation/merging; preserve actual platform constraints with complete batching where relevant. Do not change unrelated quotas or add paid calls.
- [x] Run scoped RED/GREEN and complete existing Android/core/brain tests, Web parity/runtime checks and related server forwarding tests. Use only synthetic fixtures for external-model tests if separately authorized; never send the supplied private screenshots.
- [x] Set app version `1.2.1.5` / `264`, synchronize only version-pin test expectations, perform independent scoped review, build Release and verify signer/version/no QA activity.
- [x] Record exact results and limits under `_verify/v1215/VERIFICATION.md`, deliver APK, preserve all unrelated work and no-deployment boundary.
