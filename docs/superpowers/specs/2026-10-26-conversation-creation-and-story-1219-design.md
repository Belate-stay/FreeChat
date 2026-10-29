# FreeChat Beta 1.2.1.9 — mentions, background replies and creation separation

## Approved intent

The user requests three related changes: visible, selectable group mentions; uninterrupted in-process background generation with reply notifications; and distinct sidebar actions for creating a conversation versus saving a persona. The user confirmed a unified third-person narrator for multi-character text, with at most eight characters, and version 1.2.1.9. WeChat groups retain their four-character limit.

## 1. Group mentions

- Typing @ in a group composer opens the existing themed member-selection sheet. Picking a member replaces the active @ token at the caret, retains surrounding text and binds the stable member ID. Cancelling leaves ordinary editable text.
- Long-pressing a member avatar continues to insert a bound mention. Both paths use the same draft bindings; edits that cross a mention remove its hidden target. Same-name members remain distinguishable by ID.
- Bound mentions have a theme-aware highlight in compact, complete and expanded composers and in the sent message. Persist the selected label and range so historical messages remain visually correct after a rename or member removal. Literal unselected @ text does not gain a hidden recipient.
- Existing planner behavior still treats @ as added attention rather than a compulsory answer. No @all or autonomous group conversation is added.

## 2. Background execution and notifications

- Keep ordinary, private-role, group, narrative and scene-image generation owned by the ViewModel/request pipeline, independent of the Chat composable's visibility. Switching apps must not cancel, pause delivery or defer persistence until resume.
- Use the existing foreground task service for the whole active request/buffer/delivery lifetime. Add one active-work heartbeat for non-streaming waits and service/lock renewal; stop it and release resources after all work finishes or is explicitly cancelled.
- Group accepted messages use the same background completion-notification path as other replies. Notifications are scoped by conversation; subsequent messages update the same notification quietly. Image-only success has an appropriate completion summary.
- Notification errors or denied permission cannot fail an accepted model reply. Clicking a notification opens its conversation, including cold start, without taking a stale current-conversation ID.
- Use Android system heads-up notifications, not a new draw-over-other-apps permission. OS notification permissions, channel settings and power restrictions remain authoritative. Test app switching with a held local provider response and inspect persisted messages and actual service/notification state.

## 3. Creation entry points

- The sidebar shows 新建对话 followed by 新建角色. Both use the existing fixed frosted header-card material, touch isolation, haze and motion; update list insets for the additional row in both material modes and on tablets.
- 新建对话 offers 标准模式, 拟人模式, 多角色群聊 and 多角色文本. 拟人模式 retains its three private dialogue modes. Remove group creation from the private dialogue-mode picker.
- All new private-conversation forms expose only 开始对话. They do not store a library card. 新建角色 opens only Part 1, has only 存入角色池, supports choosing one-level folders and does not create a conversation. Existing card editing and source-based Start chat eligibility remain intact.
- Multi-character group entry opens the library picker directly and creates the existing group type. An empty role pool shows 请先添加角色卡至角色池 centered in the picker. Existing folders and whole-folder selection remain available, with the correct per-mode limit.

## 4. Multi-character text

- Add a distinct story profile to a conversation; do not encode it as a WeChat group or a fake lead character. It contains one to eight independent persona snapshots plus this story's background, world rules, optional opening situation and generation settings. Conversation title is the story name.
- Creation: choose cards, then configure the world, then 开始对话. No lead/supporting designation or compulsory user-character assignment. Cards and existing private/group conversations are not rewritten when used here.
- One unified narrator model reads all cast profiles, the world and this conversation's history/memory, producing coherent third-person prose with natural dialogue and independent motives. A narrator may know character secrets, while characters' knowledge must follow events and access to information in the story. No per-character WeChat reply loop, emoji picker, online status, buffer or proactive group behavior is inherited.
- The user clarified the long-novel intent: narrative focus can move among any of the selected characters according to user input, independent motives and causal events. Characters can remain off-stage and return; subplots and unresolved threads retain continuity. Do not force every character to appear/speak each turn or permanently privilege the first selected card. Separate established events from each character's actual knowledge.
- Reuse the plot/narrative presentation, progress, model/time metadata, quote coloring, stop/retry/edit protections, context and memory deletion semantics. Narrative reasoning remains hidden. User input advances the story through events, actions or continuation instructions.
- World settings include cast persona editing scoped to this story, background/rules, language and image models following global settings by default, reply length, creativity and existing applicable memory/reasoning controls. Changes commit with Save; reply-affecting saves are blocked while busy. Persona learning remains a full-screen operation.
- Scene images use the world, all selected personas/reference images, recent story and valid memory. Visual-only rows never become plot memory; image regeneration/deletion and visual continuity obey the existing narrative rules.
- Preserve story records and image references through account synchronization and deletion. Server changes, if needed, remain local and undeployed. Old conversations/cards retain their original schema and behavior; no destructive migration or database operation is authorized.

## Boundaries and verification

Use existing Compose controls, typography, adaptive panes, non-linear motion and light/dark/material variants. No new UI or networking library is needed. No paid model calls are authorized for this work; use synthetic personas and a local fake provider for integration checks. Preserve unrelated worktree changes, earlier APKs, existing user data and formal-release changelog text; do not commit, push or deploy.

Verify mention range editing and JSON round-trip, creation action separation, four/eight-member limits, persona/world isolation, narrator context/memory, scene-image exclusion and sync reference cleanup. Then run all module tests and Debug/Release builds, inspect phone/tablet screens, exercise background completion and notification routing, and verify the signed 268 / 1.2.1.9 APK.
