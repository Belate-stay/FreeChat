# Companion Humanity Implementation Plan

> **For agentic workers:** Use subagent-driven-development for isolated tasks and reviews, and dispatching-parallel-agents only for independent read-only audits. One production implementer at a time; root alone controls Gradle, evaluation and APK output. Preserve the current dirty checkout, no commit/push/deployment. The user already authorized execution; do not stop to ask whether to continue between tasks.

**Goal:** Deliver Beta 1.2.1.3 with lossless, context-grounded companion conversation, stronger character/prose fidelity and source-safe narrative continuity.

**Architecture:** Reuse the pure Kotlin core for reply policy, memory and prompts. Wire Android and the JVM brain to the same results; Node transports optional delivery timing without changing commitment/cancellation. Mirror the corresponding Web source policy without deploying it. Optional memory fields keep JSON and storage backwards compatible.

**Tech Stack:** Kotlin/JVM/Compose, existing Gson/OkHttp/coroutines, existing TypeScript/React and Node test runners; no new dependencies.

## 0. Baseline and evidence

- [x] Read current memory/code and preserve working changes. Snapshot 25 relevant pre-change files under `_verify/humanity/baseline/`.
- [x] Baseline `:app:testDebugUnitTest :freechat-core:test :freechat-companion:test`: successful (existing 757 app passes / 3 skipped, 47 core, 29 companion).
- [x] Disclose approved mechanism changes and obtain version/live-evaluation authorization. Synthetic requests: at most 12, no private chats; version 262/1.2.1.3.
- [x] Preserve synthetic pre-change request payloads before modifying shared prompt behavior. Use the old source snapshot for comparison, not HEAD, which omits Claude's work.

## 1. Mode-first prompts and original-source use

**Files:** core `CompanionPrompts.kt`, `data/CharacterOriginalLearning.kt`; add focused `CompanionHumanityPromptTest.kt` and original-excerpt tests; update only intentional old prompt assertions in `CompanionGoldenTest.kt`.

- [x] Write failing assertions that action/plot no longer start with the WeChat task, the prose checklist is absent, derived subtext is not labelled known fact, user constraints remain verbatim, and chat does not require slang/emotion performance.
- [x] Run `:freechat-core:test --tests '*CompanionHumanityPromptTest'` and retain RED evidence.
- [x] Implement mode-first identity, remove contradictory generic warm/cold directives, use concrete causal empathy and original-style prose guidance, preserve all mode/quote/knowledge/voice/emoji boundaries. Add a reserved explicit chat-silence control `[回应:不回复]`; narrative output cannot use it. Treat any written body as content, not silent output.
- [x] Keep the existing persona-learning integration but improve its language/behavior evidence instructions. Preserve short original learning text; bound long reply-time excerpts through a pure deterministic contiguous selector, never edit the saved source. Export `CharacterOriginalLearning.replyContext(character, history)` for root to place in the dynamic tail.
- [x] Run focused contracts and self-review against user constraints. A separate read-only spec review precedes quality review; do not trust the implementer's report alone.

## 2. Lossless reply protocol and delivery timing

**Files:** core `CompanionReply.kt`, `CompanionRhythm.kt`; Android `CompanionApiReply.kt`, `ChatViewModel.kt`; JVM `CompanionBrain.kt`; Node `src/wechat.js`, tests; core rhythm/parser tests.

- [x] Test old failure directly: three meaningful lines must all survive; anger must not randomly delete written text; a voice/text/voice sequence retains all carriers. Test explicit full control-only silence separately from empty/malformed text and from a quoted control token.
- [x] Run the focused RED tests before implementation.
- [x] Add defaulted `silence` to parsed/shared reply results; handle intentional silence before empty-response retry. Regeneration forces content. Remove obsolete `.take(maxCount)` callers and the unreachable empty-response early-return path. Use one lossless shared segment preparation function.
- [x] Implement a bounded content-sensitive schedule with network elapsed deducted from first-message minimum. Add optional defaulted `delaysMs` to brain results; Android thin client/Node validate and consume it, falling back compatibly for old responses. Keep source IDs, normal persistence, cancellation and sleep semantics.
- [x] Verify empty-result retry, deliberate silence, text/voice preservation, and send/cancel behavior through the real brain with local test completers/store stubs. Test Node invalid/missing schedules and committed sends. Run tests without extending timeouts to hide problems.

## 3. Narrative state, knowledge and deletion-safe memory

**Files:** core model `Conversation.kt` (MemoryEntry), `data/AppJson.kt`, `MemoryExtractor.kt`, `MemoryLogic.kt`; Android `ModelManagedMemory.kt`, `MemoryManager.kt`, `ChatViewModel.kt`; JVM `CompanionStore.kt`, `CompanionBrain.kt`; corresponding memory/sync/brain tests.

- [x] Test that important accepted AI events after character 200 reach extraction, legacy/null JSON heals, world facts are labelled independently of role knowledge, optional state survives merging, and deleted message sources cannot return during a delayed write. Test auto-memory-off does not write entries.
- [x] Run focused RED tests.
- [x] Add defaulted `storyState`, `roleKnowledge`, `openLoops` strings to MemoryEntry and MemoryDraft, with null healing and bounded extraction. Keep stable factual summary, chronology, old JSON and existing source deletion policy. Use the target role name and transcript evidence when extracting narrative knowledge, not a blanket ban on all narration/action.
- [x] Use the shared model-managed memory formatter from Android and JVM. Preserve typed fields on upsert and source filtering, guard delayed writes against latest deletion/auto-memory state, and do not make extractor failure block reply delivery.
- [x] Add dynamic original-reference material to `CompanionRequestBuilder` after history/state, without changing the stable system+history prefix or the last mode-format rule. Align deep-prep notes with fact versus interpretation.
- [x] Run memory, healing, deletion, sync and brain race tests; inspect every copy/JSON consumer instead of assuming new fields round-trip.

## 4. On-demand search and cross-platform source parity

**Files:** core existing search intent policy if reusable, JVM `BrainSearch.kt` / `CompanionBrain.kt`, Node transport; Web `core/prompts/{companion,persona,deep,memory}.ts`, `core/memory/manager.ts`, `core/chat/engine.ts`, existing model types; focused tests/QA script.

- [x] Test greetings, emotional disclosures, private role scenes and acknowledgements do not call external search; explicit current/factual requests do. Search failure remains a normal reply with no invented evidence.
- [x] Implement the same search-need semantics as App without adding an unconditional extra model request per chat. Keep enabled/search-provider settings meaningful and do not silently disable requested deep thinking.
- [x] Port the approved shared behaviors to existing Web source, including lossless grouping, explicit silence, content timing, narrative memory metadata, original-reference boundary and prompt parity. No website deployment/version release side effects.
- [x] Run focused Node tests, Web `tsc -b` / build, and existing parity checks. Document any unrelated baseline failures precisely; never delete tests to get green.

## 5. Bounded quality evaluation and delivery

**Files:** `_verify/humanity/` synthetic fixtures, local evaluator and report; developer changelog; app version and existing version assertions.

- [x] Evaluate six matched old/new synthetic requests (maximum 12 total HTTP calls, failures count; no automatic retry), using the same built-in model and sampling settings. Capture actual outputs, prompt sizes and latency without secrets. Use old assembled payloads, not a guessed reconstruction of old behavior.
- [x] Review role fidelity, concrete empathy, causal continuity, knowledge boundary, prose and redundant AI mannerisms. Record regressions and amend only the causal changes, then use offline checks if the paid-call cap is exhausted. Do not promise subjective success from source-string tests.
- [x] Set versionCode 262/versionName 1.2.1.3, preserving the current version naming convention. Update only developer notes, not the user-authored formal release changelog body.
- [x] Run fresh app/core/companion suites, Node checks, Web build/parity, Debug and Release build/vital lint. Perform independent spec then code-quality review and resolve important findings.
- [x] Verify APK version/signature/Debug-QA isolation and deliver `D:/Desktop/FreeChat_Beta_1.2.1.3.apk` with a matching checksum. Clearly distinguish APK changes from local server/Web changes pending deployment. Preserve all unrelated edits and temporary-device ownership boundaries.
