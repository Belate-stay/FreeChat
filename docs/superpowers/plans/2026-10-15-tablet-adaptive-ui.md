# FreeChat tablet workspace implementation plan

> **For agentic workers:** Use subagent-driven-development for the isolated form task and executing-plans for the shared navigation integration. Follow the test-driven-development and verification-before-completion skills. Do not commit, push, deploy, or discard the user's existing changes.

**Goal:** Deliver Beta 1.2.0.2 with tablet-native two-pane workflows and explicit, race-safe character setting saves, without changing the phone layout.

**Architecture:** Keep the current ViewModel, manual routes, material providers and liquid clock. A tablet-only workspace host keeps both pane compositions alive while animating their bounds, and supplies actual pane dimensions. Existing screens remain the source of truth; local form state is not copied into a second UI implementation. Identify tablets using the device's maximum window's smallest dp dimension, then adapt columns to the current available width.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Material 3, Android WindowMetrics, existing FreeChat motion/theme/Haze components, JUnit and Android emulator screenshots.

## Baseline and constraints

- Baseline `:app:testDebugUnitTest :freechat-core:test` passed before implementation.
- Work in the approved existing checkout because it contains the required private files and Claude's uncommitted work. Do not alter unrelated API, sync, backup or feedback changes.
- Use `apply_patch` for edits. Run Gradle serially with JBR 17, 4 GB heap.
- All new controls use existing semantic colors and minimum 48 dp touch targets. Do not change Density or temporarily disable advanced materials during transitions.

## Task 1: Tablet layout policy and host

Files: create `app/src/main/java/com/freechat/ui/adaptive/TabletLayoutPolicy.kt`, `TabletWorkspace.kt`, `TabletStrings.kt`; test `app/src/test/java/com/freechat/data/TabletLayoutPolicyTest.kt`.

- [x] Write tests before the policy: a 393 dp phone stays phone even at 873 dp landscape width; a 800 dp tablet stays tablet in both rotations; widths 600/800/1280 have two usable panes; a window below 600 temporarily displays the focused pane; full-screen selection returns the requested pane without changing the device class.
- [x] Run `:app:testDebugUnitTest --tests '*TabletLayoutPolicyTest'`, observe the missing implementation, then implement the pure policy.
- [x] Provide `LocalTabletUi` and `LocalPaneSize` (actual `DpSize`). Build a single two-child `Layout` retaining both compositions, clipping and disabling the hidden pane, and animating only bounds with the existing nonlinear motion curve. Expose `TabletPane` and full-screen callbacks to each pane.
- [x] Add locale-aware expand/restore/navigation labels. Use Android maximum WindowMetrics on API 30+, configuration smallest width as fallback; do not infer tablet from landscape width alone.
- [x] Re-run policy tests and compile the app.

## Task 2: Character draft transaction and responsive form

Files: modify `ui/screens/CharacterSetupScreen.kt`, `data/CharacterPresentationPolicy.kt`; create `data/CharacterSavePolicy.kt`; test `data/CharacterSavePolicyTest.kt`.

- [x] Write classification tests with an original `CharacterProfile`: changing language/image/vision model, thinking/search/memory/timing controls is functional-only; changing name, relationship, original-text material or persona is a persona change; mixed changes require learning. Save is rejected for both thinking and typing states.
- [x] Run the new tests red, implement classification using the existing `withSimulationSettings` whitelist, then run green.
- [x] Remove every Back-triggered `onSaveSimulation`. Back with either part changed prompts discard/continue editing; no implicit submission. Route all explicit save entrances through one busy guard, including confirmations.
- [x] Add `isSavingBlocked: Boolean` and `canSubmit: () -> Boolean` inputs supplied by the root for the opened conversation. Display the exact disabled message “请等待角色思考/回复完毕后保存更改” and localized equivalents. Editing remains possible while blocked; no queued save.
- [x] On wide tablet creation, place the existing persona and functional sections in independently scrolling columns without duplicating their state. Auxiliary narrow settings retain one readable form. Preserve the top mode control and clear separation from persona content.
- [x] Respect `LocalPageActive` for Back handling. Confirm discarded drafts never update the saved profile and functional-only save never calls learning.

## Task 3: Root two-pane routing and full-screen learning

Files: modify `MainActivity.kt`, precise generation-state and target-bound save additions to `viewmodel/ChatViewModel.kt`; test `data/TabletNavigationContractTest.kt`.

- [x] Add source-contract tests for a tablet-only host, phone fallback, full-window learning guard, and explicit target conversation save. Run red.
- [x] Extract the existing route renderer, keeping its current business callbacks. Phone still uses the existing PageMotion and drawer.
- [x] Tablet normal workspace: conversation list left, current chat right. Auxiliary rules/character settings replace the list: chat left, editor right. Bind auxiliary settings to the conversation ID captured on entry.
- [x] Use stable movable content for the chat when changing its pane role, never mount it twice. Keep inactive pane compositions on full-screen changes so chat viewports and unsaved drafts survive. Supply expand/restore controls with one shared pane state.
- [x] Expose an observable set of busy conversation IDs from the existing per-conversation loading/typing maps. Apply one final guard to both save callbacks, not just disabled buttons; use target-bound profile updates rather than whichever chat happens to be selected.
- [x] Learning overrides the entire workspace and consumes Back. No drawer or full-screen navigation during learning. Functional-only save bypasses learning; persona changes learn and only then update the saved profile.
- [x] Tablet model editor keeps its model pool on the left. Favorites keep list/filter selection left and selected detail right. Non-master/detail routes get a restrained reading width rather than stretched controls.

## Task 4: Tablet settings and panel-local rendering

Files: modify `ui/screens/SettingsScreen.kt`, `ChatScreen.kt`, `ui/components/ChatInput.kt`, `ChatQuickLocate.kt`, `SheetPanel.kt`; optionally native screen headers touched for pane controls.

- [x] Add tests asserting the settings category/detail path is tablet-only and quick locate uses actual chat-pane width; run red.
- [x] On tablet settings show category navigation and selected content, preserving the existing model, personalization, AI and about controls. Retain existing settings scroll-map semantics and child routes. Keep the phone's full settings list unchanged.
- [x] Limit long reading/form content to 760–840 dp. Keep chat background/header full panel, center the message stream and composer with a comfortable max width, and base input max-height and quick-locate estimates on `LocalPaneSize`.
- [x] Tablet sheet cards use max width 520 dp, 24 dp safe gutters and unified rounded corners. Keep scrim and touch isolation within the invoking pane, while global notices cover the whole app.
- [x] Verify the root liquid coordinate chain remains global and each pane's Haze sampling/touch shield is local. No separate animation clock per pane.

## Task 5: Review and release verification

Files: update `app/build.gradle.kts` version to code 251 / `Beta 1.2.0.2`, `../FreeChat_更新日志.md`, relevant README; add reproducible QA notes and screenshots under `_verify/v1202`.

- [x] Independent spec review, then code-quality review of the actual diff; fix findings and recheck, preserving all prior dirty changes.
- [x] Run `:app:testDebugUnitTest :freechat-core:test :app:assembleDebug :app:assembleRelease --console=plain` with JBR 17. Count current XML test results, do not reuse an old count.
- [x] Use an isolated emulator userdata directory, not a user's live AVD. Check phone portrait/landscape, tablet portrait/landscape and a narrow tablet window; record actual dimensions/density.
- [x] Visually inspect screenshots for normal chat, rules beside chat, character draft editing, settings categories, model management, favorites, expand/restore and full-window learning. Check light/dark, advanced material and liquid theme paths. Report any unavailable hardware-specific verification honestly.
- [x] Verify APK badging/signature and copy the verified release to `D:/Desktop/FreeChat/FreeChat_Beta_1.2.0.2.apk`. Deliver test/visual evidence without claiming physical-device frame rates or paid API validation.
