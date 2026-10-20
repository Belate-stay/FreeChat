# FreeChat Beta 1.2.0.4: tablet navigation corrections

The user's detailed page specification is the design authority. This supersedes 1.2.0.3's both-exits-left rule. Phone layouts and phone transitions are unchanged.

## Navigation

- A Back action reverses the entry path. A left page exits left; a right page exits right. Both system Back and header Back use this intent.
- A parent/category/sidebar selection is a cover action, not Back. The incoming page covers the stationary previous page from its own screen edge. Keep the previous page until the incoming page finishes; disable outgoing input.
- Major workspaces (settings, character creation) still replace both panes. Chat auxiliary settings retain the approved continuous camera/divider movement.
- Embedded account opens in the Chat pane with the sidebar retained. Choosing a conversation over account brings Chat in from the right, rather than dismissing account as a Back action.
- Embedded voice debug, changelog, agreement and author details stay in the settings right pane. The settings category pane stays mounted and usable. Editing models also retains a settings parent on the left.
- Use one decelerating curve (fast start, soft settling) for tablet cover, Back and divider motion. Respect disabled/reduced motion.

## About

In embedded settings, the About category directly hosts the existing AboutFreeChatPage. Reuse its implementation, preserving all update, logo, author, agreement, developer and rating behavior. Remove the AboutFreeChat entry level in this presentation only. Add feedback and signed-in shares after agreement/developer rows within the same card, before rating. Phone and phone-shaped floating navigation retain their existing hierarchy.

## Floating elevation

Keep the phone-shaped rounded floating panel, side preferences and undimmed/unblurred blocked background. Strengthen soft directional and ambient separation in light and dark themes, without adding a heavy outline or white glow. The shadow travels with the card and is not clipped at the card boundary.

## Follow-up: page action sizing

The user additionally requests moderate, centered floating-looking buttons rather than stretched phone buttons. In embedded tablet panes, cap standalone primary actions at 320dp and center their actual colored/material surface. Keep existing shadows, rounding, press feedback, touch height and page placement; do not turn settings rows, selection cards, text fields or in-row actions into compact buttons. Cover account submission, feedback, model add/import/fetch/save, favorites open-original, icon save, role creation/save/next and custom-voice create/save. Phone and phone-shaped floating panels retain their existing widths. Validate actual button bounds in landscape and portrait as well as source contracts.

## Verification

Test cover-vs-Back geometry, page hierarchy and unchanged phone behavior. Run app/core unit tests and Debug/Release builds. On an isolated Android emulator, inspect embedded account/sidebar navigation, each settings detour, About aggregation, Back from both edges, floating elevation in light/dark, and portrait/landscape. Preserve existing character save/busy/learning isolation.
