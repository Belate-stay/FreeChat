# Character Library and Group Chat Implementation Plan

> **For agentic workers:** Use subagent-driven-development with focused ownership, test-first work and spec/quality review. Work in the existing dirty workspace without commits, resets, pushes or deployment. Shared contracts below are authoritative; coordinate changes rather than editing another owner's files.

**Goal:** Deliver Beta 1.2.1.6 / 265 with a Part-1-only character library, one-level folders, independent conversation snapshots and a user-triggered group of up to four AI members plus the user.

**Architecture:** New pure persona/library/group models distinguish persona from mode settings. Library storage and cloud objects use existing atomic storage and versioned synchronization. A finite group planner selects speakers; per-speaker generation receives only its persona, role-scoped memory and public group events, while every speaker uses the same group-selected language/vision models.

**Tech Stack:** Existing Kotlin/JUnit/Gson, app coroutines/OkHttp, Compose components and motion/material tokens, Node's existing server/tests, Web TypeScript compatibility. No group-agent framework dependency.

**Execution status (2026-10-26):** Implementation, final verification and packaging are complete in the existing worktree; evidence and test limits are recorded in `_verify/v1216/VERIFICATION.md`. Historical checklists below describe the planned checks, not additional product scope. Read-only review found four editor-navigation/snapshot issues and a media-release issue; these were fixed before delivery. Local-server deployment remains explicitly excluded.

## Confirmed corrections to the earlier spec

- Character cards contain only Part 1. Models, proactive behavior, reply buffering, thinking/search and mode options are never serialized into cards.
- Group language and vision models always come from group settings, defaulting to live global choices. Do not inherit member/private-chat model settings.
- Group member names beside messages are optional, avatars remain; long press mentions by ID and double tap nudges. Neither forces a reply.
- Group avatar is user-set or a derived composite of role avatars. Announcement is shared public data, group title is the user's remark, and user nickname is group-local.
- Library and folders sync; old server capability absence defers new-data sync safely. Server/Web are edited locally only.

## Shared contracts (define first, do not make each layer invent names)

Core models live under `freechat-core/src/main/kotlin/com/freechat/model/`:

```kotlin
data class CharacterCard(
    val id: String,
    val persona: CharacterPersona,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val sourceConversationId: String? = null,
    val sourceMemberId: String? = null,
)
data class CharacterFolder(val id: String, val name: String,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
data class CharacterLibraryState(val cards: List<CharacterCard> = emptyList(),
    val folders: List<CharacterFolder> = emptyList(),
    val deletedCardIds: Set<String> = emptySet(), val deletedFolderIds: Set<String> = emptySet())
data class GroupMember(val id: String, val persona: CharacterPersona, val sourceCardId: String = "")
data class GroupChatProfile(
    val members: List<GroupMember> = emptyList(),
    val userNickname: String = "我", val announcement: String = "", val announcementAt: Long = 0,
    val avatarPath: String = "", val avatarHash: String = "", val showMemberNames: Boolean = true,
    val languageModelId: String = "", val visionModelId: String = "",
    val replyBufferEnabled: Boolean = true, val replyBufferSeconds: Int = 3,
    val timePerception: Boolean = true, val highQualityMemory: Boolean = false,
    val deepThinking: Boolean = false, val aiCreativity: Float = 6f,
    val updatedAt: Long = 0,
)
```

`CharacterPersona` is a dedicated data class, not a wrapper around `CharacterProfile`. Fields are those that remain when `CharacterPresentationPolicy.withSimulationSettings` copies Part 2: name/gender/age, avatar/hash, MBTI values, personality presets/text, memoryPerception, referencePrototype, appearance fields/descriptions, opening lines as character examples, relationship fields, personaPrompt, supportingCast, worldRules, userPersona and originalLearningText. It offers `CharacterPersona.from(profile)` and `toProfile(mode: Int = DialogueMode.WECHAT)`. The latter creates fresh mode defaults and never restores a private chat's simulation flags. Group creation ignores opening lines for automatic sending.

Add backward-compatible defaults:

```kotlin
// Conversation
val groupChat: GroupChatProfile? = null
// Message; assistant sender identity, trigger provenance and explicit public events
val groupMemberId: String? = null
val groupSenderName: String? = null
val groupSenderAvatar: String? = null
val groupTurnId: String? = null
val groupStepId: String? = null
val groupTriggerMessageIds: List<String> = emptyList()
val mentionedMemberIds: List<String> = emptyList()
val groupEventType: String? = null
val groupTargetMemberId: String? = null
// MemoryEntry: empty = existing single-chat/shared legacy entry; groups filter explicitly
val groupMemberId: String? = null
```

Repository API in `app/.../data/CharacterLibraryStore.kt`:

```kotlin
object CharacterLibraryStore {
    val state: StateFlow<CharacterLibraryState>
    fun reload()
    fun add(persona: CharacterPersona, folderId: String? = null,
        sourceConversationId: String? = null, sourceMemberId: String? = null): CharacterCard
    fun update(card: CharacterCard): Boolean
    fun deleteCard(id: String)
    fun createFolder(name: String): CharacterFolder
    fun renameFolder(id: String, name: String)
    fun deleteFolder(id: String)
    fun moveCard(id: String, folderId: String?)
}
```

No write to global memory. Filesystem test fixtures use task-owned temp directories only. No new paid requests without separate approval.

## Task 1 — Persona/library/group data and storage (data owner)

**Files:** create `model/CharacterLibrary.kt`, `model/GroupChat.kt`, `app/.../data/CharacterLibraryStore.kt`, pure policy tests and repository tests; modify `Conversation.kt`, `Message.kt`, `LocalStore.kt` and deletion/media reachability only as needed.

- [ ] Snapshot exact existing files under `_verify/v1216/baseline`.
- [ ] Write tests demonstrating Part 2 exclusion and clean mode defaults:

```kotlin
val source = CharacterProfile(name = "角色", languageModelId = "private", proactiveEnabled = true,
    replyBufferEnabled = false, dialogueMode = DialogueMode.PLOT, deepThinking = true)
val persona = CharacterPersona.from(source)
val json = Gson().toJson(CharacterCard("c1", persona))
assertFalse(json.contains("languageModelId"))
assertFalse(json.contains("proactiveEnabled"))
assertFalse(json.contains("replyBufferEnabled"))
val fresh = persona.toProfile(DialogueMode.WECHAT)
assertEquals("", fresh.languageModelId)
assertFalse(fresh.proactiveEnabled)
assertTrue(fresh.replyBufferEnabled)
```

- [ ] Add behavior tests for copy independence, folder moves, duplicate source import, deleting a folder to unclassified, card deletion not deleting a conversation, stable IDs and 0/1/4/5 AI member validation. Capture RED using `:freechat-core:test --tests '*CharacterLibrary*' --tests '*GroupChat*'` (new API absence is recorded separately from behavioral failures).
- [ ] Implement minimal pure models/policies, repository atomic mutations and reload. Preserve old JSON defaults and existing fields. Card/folder content is not embedded as a fake hidden conversation.
- [ ] Publish exact model/repository contracts before UI integration; rerun the new pure/storage tests GREEN.

## Task 2 — Library cloud synchronization and media ownership (data owner)

**Files:** `sync/SyncTypes.kt`, `Adapter.kt`, `SyncEngine.kt`, `Wire.kt`, `Relay.kt`, `Merge.kt`, image/deletion helpers; local `FreeChatServer/src/sync.js` and `routes.js`, related tests; Web types/wire/sync preservation and a group-operation guard.

- [ ] Use per-card/per-folder sync objects and existing revision conflict handling. Define constants once (`card`, `cardfolder`), keep per-ID deletion records, preserve edits made while synchronization runs, and include library state in account switching/reset behavior.
- [ ] Write round-trip and temporary-DB tests: delete cannot resurrect; concurrent different card edits coexist; same-card divergent persona preserves a conflict copy; deleted folder targets become unclassified without losing cards; a temporarily not-yet-downloaded folder is not treated as deleted.
- [ ] Extend `/api/me` with additive capability `characterLibrary: 1` and, if needed, `groupChat: 1`; no database schema migration. Old/missing capabilities defer relevant new data and media push while retaining local dirty records; normal existing sync continues.
- [ ] Extend server and local media reachability to cards, group snapshots, member-message fallback avatars and custom group avatars. Test that deleting one owner retains a still-referenced image and final-owner deletion releases payload bytes.
- [ ] Ensure Web preserves new fields/objects and does not answer/save a group as a single character. Full Web character-library UI is outside the Android page scope. Record compatibility/deployment boundary explicitly.
- [ ] Run relevant Node tests with `D:/NodeJS/node.exe --test` using fake fetch and temporary/in-memory databases only; run Web local checks/build. Parent controls all Gradle invocations.

## Task 3 — Character library pages and two creation exits (library UI owner)

**Files:** new `ui/screens/CharacterLibraryScreen.kt`, `CharacterPickerScreen.kt`, library localization extensions; modify `SettingsScreen.kt`, `CharacterSetupScreen.kt`, `DialogueModeScreen.kt`, related source/runtime navigation tests. MainActivity is parent-owned.

- [ ] Add “我的角色” immediately after the model-selection section/category while preserving existing persisted category ordinal meanings.
- [ ] Implement folders and unclassified cards, preview/edit, move, delete, new-card and existing-conversation import. Existing conversation imports use `CharacterPersona.from`; no history/memory/Part 2 is copied.
- [ ] Expose page callbacks for the parent: create persona-only card, edit card by ID, start chat from card and back. New picker contract:

```kotlin
@Composable fun CharacterPickerScreen(isDark: Boolean,
    initialSelectedIds: Set<String> = emptySet(), selectionLimit: Int = 4,
    onConfirm: (List<CharacterCard>) -> Unit, onBack: () -> Unit)
```

- [ ] Picker uses folder navigation, atomic whole-folder selection, deduplication, selected count and explicit over-limit guidance; never silently take the first four.
- [ ] Character setup adds `personaOnly` and a separate `onStoreInPool` callback. Persona-only editing hides mode/Part 2; new creation exposes both “开始对话” and “存入角色池”. Saving just a card must not call conversation creation. Starting a chat must not add a card.
- [ ] Add WeChat-only “多角色群聊” child with existing expand/collapse motion in both phone mode selection and tablet creation mode panel; parent `onCreateGroup` callback opens the picker. Hide it in existing private-chat/card editing.
- [ ] Keep existing validation, unsaved-change confirmation and fullscreen learning behavior. Publish navigation parameter names to the parent before editing shared call sites.
- [ ] Tests distinguish create-card/create-chat actions, Part-1-only UI, folder selection, new category ordering and retained old navigation. Parent runs RED/GREEN and device checks.

## Task 4 — Group settings, identity, avatars and gestures (group UI owner)

**Files:** new `GroupSettingsScreen.kt`, `GroupAvatar.kt` and group localization; modify `ChatScreen.kt`, `ChatBubble.kt`, `DrawerContent.kt`, supporting tests. MainActivity and ViewModel remain parent-owned.

- [ ] Group header rightmost tune callback opens Group Settings, group title remains the user's remark. Group Settings includes avatar grid/user + roles, member addition/removal, group remark, announcement editor/publish, group-local user nickname, model selectors, group reply settings, pin, current-group search and show-member-name switch.
- [ ] Click a member to open its group-local persona editor; never edit its library source. Group models default to actual global models and apply to planner plus every actor. Member editors expose Part 1 only; all group Part 2 lives here.
- [ ] Group avatar uses custom image/crop or a derived 1–4-role composite, reused in drawer/header/settings. Clearing custom avatar restores the composite; role avatars are not mutated.
- [ ] Group message avatar/name display resolves stable member IDs, with saved fallback identity for removed members. Hiding names retains avatars. Use shared spacing/material/motion and scale with existing display presets.
- [ ] Long press avatar calls parent VM `mentionGroupMember(memberId)`; double tap calls `nudgeGroupMember(memberId)`. Single taps must not accidentally nudge. System nudge/announcement events render as subdued system rows, not actor bubbles.
- [ ] Shared compact/full composer callbacks use group-effective model/thinking/search settings; no per-member settings mutation. No group external-WeChat/proactive/scene-generation entry.
- [ ] Add source-wiring/gesture policy tests plus actual phone/tablet, dark/light, large-font and reduced-motion checks with parent-controlled isolated emulator.

## Task 5 — Group prompts, finite execution and cancellation (parent)

**Files:** new core `GroupChatPrompts.kt` / `GroupChatPolicy.kt`; app `GroupChatEngine.kt` / group-memory helper; minimal integration in `ChatViewModel.kt`, `MemoryManager.kt`, `MainActivity.kt`. Test fake provider paths before real network.

- [ ] Plan result has stable step/member/target IDs only. The planner can choose zero or some members, repeated members and a finite order; empty plan is legitimate silence. No fixed round-robin, random online status or forced global response.
- [ ] Write tests for offline/silent personas, @ as attention only, nudge events, unknown IDs, duplicate names, same-person continued speech and 100 naturally generated bubbles. Generation request includes only that member's persona, own memory, public roster/nickname/announcement/rules and correctly attributed public history.
- [ ] Implement a suspend engine with injected planner/generator/delivery/sleep callbacks so tests can assert all effects without HTTP. Each delivered message carries group member/turn/step/trigger IDs. Adjacent same-member plan entries can share a generation call without merging returned natural bubbles.
- [ ] User input creates one finite turn; no role reply schedules a new background turn. Group creation sends no opening lines. Preserve explicit silence and prevent empty-body fallback from overriding intentional no-reply.
- [ ] New user input cancels only unfinished calls/queued output and reads already delivered messages into the next turn. Stop/delete/switch cannot attach output to another conversation or revive deleted rows. Partial success stays; retry only a failed step, without replaying successful steps.
- [ ] Mention draft bindings track text changes and IDs (including same-name members), clear after send/cancel, and remain scoped to the conversation. Nudge is a user-triggered public event and does not force a response.
- [ ] Scope memory by conversation + member ID. One member's private knowledge never enters another member request. Each source deletion invalidates its derived member memories; existing single-chat memory behavior stays unchanged.
- [ ] Use existing model endpoint/protocol/cancellation/error helpers. Language/vision resolve from group settings or live global values; image observation is performed once per user batch and reused as public data, without replacing role language/persona context.

## Task 6 — Main navigation, creation and integration (parent)

**Files:** `MainActivity.kt`, small ViewModel public methods, required i18n wiring and existing contract tests.

- [ ] Add distinct destinations/state for library, card editor, group member picker, group settings and current-group search. Each retained editor holds explicit target IDs rather than the currently selected chat.
- [ ] Store-card flow learns then writes only a Persona card (folder selection), while create-chat flow learns then creates only a conversation. Existing source imports copy only persona. Pure card editing does not open a chat; card-start asks mode and creates fresh Part 2.
- [ ] Group picker snapshots selected personas and creates a group conversation, with no opening messages or proactive job. Group/member settings have explicit saves and wait guards when relevant work is in flight; note/pin remain display-only.
- [ ] Filter group search results to the target conversation. Publish announcements as system data/rows without triggering background role replies. User nickname does not change global account identity.
- [ ] Apply embedded/floating pane routing, retained drafts, in-session history positions, reverse Back and learning exclusivity consistent with existing app. No extra full-screen buttons beyond existing Chat behavior.

## Task 7 — Review, final regression and delivery (parent + fresh review)

- [ ] Read-only spec review, then quality review of snapshot-relative changes. Fix blocking issues and rerun affected tests; preserve unrelated dirty work.
- [ ] Run `:freechat-core:test :freechat-companion:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`, related local Node checks and Web checks/build. Use JBR17 and current pinned dependencies. Do not label source-string tests as runtime UI tests.
- [ ] Exercise isolated offline phone/tablet flows: save-card with zero conversations; direct-chat with zero new cards; import existing; folder moves/delete/whole selection; four-member limit; default/custom group avatar; names on/off; @/nudge; nickname/announcement/model saves; chat/persona/library separation; cancel/retry and old-server pending sync.
- [ ] Set 265 / 1.2.1.6, synchronize version assertions, build and verify APK signer/package/version/no QA components. Deliver `D:/Desktop/FreeChat/FreeChat_Beta_1.2.1.6.apk` without unchecked overwrite.
- [ ] Record all test counts, finite-model limits and deployment dependency in `_verify/v1216/VERIFICATION.md`; update developer changelog and plan status. Do not deploy, commit/push, touch real DB or modify global memory.
