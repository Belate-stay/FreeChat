# Group Chat Refinements Implementation Plan

**Goal:** Beta 1.2.1.7 / 266 implements the user's four corrections to the delivered group chat.

**Architecture:** Keep the existing user-triggered finite planner and sequential role generation. Accepted role messages remain shared public history. Group settings distinguish reply-affecting edits from member-name visibility; no autonomous chat implementation or new storage fields are introduced.

**Tech stack:** Existing Kotlin/JUnit, Compose settings components, shared motion/material tokens and task-owned offline emulator.

- [x] Add and observe regression tests: busy member-name display updates are allowed; language/vision/reply/roster changes are rejected; A→B→A generation sees actual prior accepted messages and retains persona privacy; latest-public-message anchoring ignores failed/visual rows.
- [x] Remove only the group avatar and stop action from Chat's top bar, update the shared phone title's available width, and preserve group avatars in messages, the sidebar and group settings.
- [x] Reuse a pure group-update policy from the ViewModel. Feedback on blocked model/reply entry and already-open model selection uses one localized message. Allow name visibility during generation; remove group-settings pin row and its unused callbacks.
- [x] Add the disabled grey “主动智能 / 内测” card immediately below group reply settings with “允许群成员之间自主对话聊天。” No value, switch persistence, scheduler or model call is added.
- [x] Strengthen planner/actor prompts for public member-to-member replies and actual recent speaker attribution, without exposing other members' private persona or planning guesses. Do not change private chats.
- [x] Update version assertions, run core/app/companion checks and signed builds; verify phone/tablet busy feedback, display toggle, header and preview card using isolated synthetic fixtures. Preserve the previous APK and deliver a separate 1.2.1.7 APK. Record limits; no paid live requests, server deployment, commits or pushes.
