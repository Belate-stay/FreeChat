# FreeChat Android

## Register

product

## Users and purpose

FreeChat offers configurable AI conversations, character roleplay and reusable personas on phones and tablets. Standard chat, private role conversations and multi-character groups have distinct behavior and data ownership. User-written personas take priority over generic dialogue styles.

## Established design principles

- Preserve the existing Compose visual language: theme colors, light/dark palettes, optional advanced materials, soft card shadows, typography and shared non-linear motion primitives.
- Use phone and tablet layouts appropriate to the available space; retain embedded/floating pane navigation and existing accessibility/display-size settings.
- Make editable, read-only, busy, unavailable and saved states explicit. Cosmetic controls must not be locked by unrelated generation work.
- Changes to cards and conversations have explicit scope. Do not mix role personas with mode settings, shared public messages with private member memories, or inactive feature previews with working features.
- Reuse existing controls and motion rather than introducing an unrelated design system. Do not turn a small refinement into a global redesign.
