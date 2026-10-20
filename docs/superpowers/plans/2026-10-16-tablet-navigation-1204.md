# Tablet Navigation 1.2.0.4 Implementation Plan

> For agentic workers: use subagent-driven-development for isolated implementation/review tasks. No commits, pushes or deployments are requested. Reuse the current dirty checkout so unpublished private sources and prior changes remain available.

**Goal:** implement the user's precise Back/cover hierarchy, embedded About aggregation and floating elevation corrections.

**Architecture:** extend the existing tablet containers with explicit Back versus cover intent, not a new navigation framework. Keep SettingsScreen mounted while swapping its right detail. Retain the single Chat composition and approved camera/divider logic.

**Tech Stack:** Kotlin, Jetpack Compose 1.8, JUnit, Gradle, isolated Android emulator via ADB.

## 1. Reproduce and specify

- [x] Trace the root and settings routing; identify incorrect detail exit sign and full-window detours.
- [x] Add failing behavior tests: right exit reverses right entry; cover holds old content; settings detours retain categories; embedded About directly reuses AboutFreeChatPage.
- [x] Run focused tests and record expected failures before implementation.

## 2. Settings hierarchy and About (isolated SettingsScreen task)

Files: SettingsScreen.kt; TabletSettings1204ContractTest.kt.

- [x] Add optional embedded right-detail key/content parameters; render detours inside the existing detail pane, retaining categories and local navigation state.
- [x] Use tablet cover/back transitions for nested settings, with Back shared by header and system handlers. Parent category changes cover from the right.
- [x] Render AboutFreeChatPage directly for embedded About, append feedback/shares inside its existing entry card, and return author/developer routes to merged About.
- [x] Preserve phone/floating layout and existing update/rating behavior. Verify focused tests and have changes independently reviewed.

## 3. Root intent, account and motion

Files: MainActivity.kt; TabletNavigationPolicy.kt; TabletDestinationLayer.kt; TabletChatCanvas.kt; TabletPageMotion.kt; FreeChatAnimation.kt; TabletDisplayNavigationTest.kt; TabletNavigation1204ContractTest.kt.

- [x] Add intent-aware pane transition policy: cover incoming from own edge while holding old content; Back outgoing to the same edge while revealing parent underneath.
- [x] Route both header and system Back through the same Back intent.
- [x] Keep embedded account in the right Chat pane. Conversation selection covers account with incoming Chat and keeps sidebar interactive.
- [x] Keep settings detours inside persistent SettingsScreen using the optional right-detail API; do not duplicate the settings workspace during internal navigation.
- [x] Apply decelerating tablet timing to pane/divider motion; preserve reduced motion, touch/focus isolation and character learning exclusivity.
- [x] Strengthen floating shadow/elevated surface using existing theme tokens, with no scrim or background blur.
- [x] Run focused tests, review integration and check phone route isolation.

## 4. Verification and handoff

- [x] App/core full unit suite and Debug/Release builds; increment to code 253 / Beta 1.2.0.4; update developer log only, not formal in-app changelog.
- [x] Isolated emulator: embedded landscape/portrait, account to Chat cover, voice/log/agreement right-pane retention, exact-reverse Back, merged About and elevated floating light/dark.
- [x] Inspect screenshots and animation frames; fix observed defects and rerun affected checks.
- [x] Verify release signature/manifest, copy APK and calculate SHA256; record evidence and any real-device limits.

## 5. Follow-up: moderate centered actions

- [x] Add failing contracts for tablet-only capped, centered page actions and all selected callers.
- [x] Apply one shared sizing modifier only to standalone primary actions; preserve phone/floating widths and all existing save/submit behavior.
- [x] Verify rendered action bounds in landscape/portrait and rerun the full tests and builds before replacing the delivery APK.
