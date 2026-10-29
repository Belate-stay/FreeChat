# FreeChat 1.2.1.9 Implementation Plan

**Goal:** Make group mentions visible and usable, keep requested generations working in the background, and separate reusable personas from single/group/story conversation creation.

**Architecture:** Preserve the existing request pipelines, foreground service, library snapshots and adaptive Compose shell. Extend the pure mention draft with caret insertion and persisted spans; add a separate story profile/narrator pipeline, never sharing group scheduling or persona-card mode settings. Execute in this session without commits, deployment or paid provider calls.

**Tech stack:** Kotlin/JVM shared core, Jetpack Compose, StateFlow/coroutines, existing Android foreground service/notifications and JSON sync.

Approved specification: `docs/superpowers/specs/2026-10-26-conversation-creation-and-story-1219-design.md`.

## Phase 1 — mention interaction

- [x] Extend `freechat-core/src/test/kotlin/com/freechat/core/GroupMentionDraftTest.kt` with caret-token insertion/replacement, duplicate names, cancellation/plain text, stale bindings, unrelated edits and persisted range round-trip. Record failures in `_verify/v1219/red/`.
- [x] Extend `GroupMentionDraft.kt` and message metadata with stable ranges; wire `ChatInput.kt`, `ChatScreen.kt`, `ChatBubble.kt` and `ChatViewModel.kt` to the shared member sheet and theme-aware spans. Verify compact, complete and expanded inputs preserve caret/composition and surrounding text.
- [x] Run `:freechat-core:test --tests '*GroupMentionDraftTest*'` plus the relevant app tests and Debug compilation; exercise actual typing, selecting, cancelling and sending in the isolated emulator.

## Phase 2 — background lifecycle

- [x] Add focused regressions for per-conversation notification identity/routing, group accepted-message notification, permission/error isolation and service lifetime while another request remains active; reproduce missing group notification with a local delayed provider.
- [x] Update `ReplyService.kt`, generation keepalive/notification paths in `ChatViewModel.kt` and notification navigation in `MainActivity.kt`. Retain explicit cancellation, per-conversation validity and persistence guards; add one active-work renewal heartbeat for non-streaming waits.
- [x] Verify normal, group, narrative and image completion while another app is foreground. Inspect service, persisted response and notification state before returning to FreeChat; click the notification to its exact conversation. Finish/cancel releases service/locks. Private-role shared wiring and denied-notification isolation are covered by contracts; denied permission was not separately exercised on device. No real provider call.

## Phase 3 — creation ownership

- [x] Add regressions for the two sidebar actions, four conversation choices, removal of group creation from `DialogueModeScreen`, and exclusive Start versus Store exits in `CharacterSetupScreen`.
- [x] Update `DrawerContent.kt` fixed header height, haze/touch isolation and both material layouts; update `NewChatModeScreen.kt` with a scrollable adaptive four-card layout; wire new-role and direct group entry in `MainActivity.kt`.
- [x] Parameterize the existing card picker only for its actual mode limits/labels. Empty library has the exact centered prompt. Existing group-member add, imported card eligibility and single-level folder behavior remain available.

## Phase 4 — story profile, world and narration

- [x] Add pure core tests before production code: valid cast sizes 1–8, unique stable member IDs, no inherited Part 2, isolated snapshots, legacy JSON compatibility, unified world/cast/history prompt, character knowledge constraints and visual/error exclusion from story context.
- [x] Add `StoryProfile` and its separate field on `Conversation`; create story prompt/settings helpers and the world setup/settings screen. Reuse persona editing for a story member with an explicit story target; global settings remain live defaults until overridden.
- [x] Route story sends/retry/stop/edit and shared memory into the unified narrator, with existing narrative display and reasoning visibility rules. Scene-image context includes this world/cast and valid prior visual references while remaining outside plot memory.
- [x] Update Android/server/Web serialization and image reference traversal only where required to preserve/delete this new data safely. Do not deploy or touch a production database. Verify old modes remain unchanged with their existing tests.
- [x] Exercise creation, world save, role snapshots, global-model following, narrative generation, scene images and process-local background completion using synthetic fixtures. Retry/edit/delete are also covered by focused source/core/sync regressions and a temporary server database; not every branch was replayed through touch automation.

## Delivery

- [x] Set versionCode 268 / versionName 1.2.1.9 and update existing version regression expectations.
- [x] Run all core/companion/app tests plus Debug and Release builds with local JBR 17 and offline Gradle caches. Inspect phone and tablet embedded/floating screens, light/dark and larger text; fix observed failures before delivery.
- [x] Verify APK package/signature/production manifest, preserve 1.2.1.8, write developer beta changelog and `_verify/v1219/VERIFICATION.md`, and return the new signed APK. Explicitly distinguish actual device checks from source contracts and fake-provider checks from language-quality evaluation.

## Review and evidence

The independent review's concrete findings were fixed and rechecked: preserve unspaced prose after a mention's caret token, isolate standard-request round state and cancellation, merge story names with their settings revision, let the learning-screen Back action background active work, and persist actual composer edits synchronously without a competing callback effect. Final visual review also moved the empty-pool prompt to the center of the usable content area. See `_verify/v1219/VERIFICATION.md` for logs, screenshots and remaining platform/deployment limits.
