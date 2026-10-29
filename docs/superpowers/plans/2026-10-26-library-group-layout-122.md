# FreeChat Beta 1.2.2 UI refinement plan

**Goal:** Make the role library, destination sheet, group-member management and conversation-mode picker readable, spatially coherent and consistent with existing navigation and materials.

**Architecture:** Keep existing library/group data and host navigation. Use shared page transitions for folder entry/back, a real shared haze source for storing a persona, equal-width member cells and existing confirmation sheets. Centralize the initial content clearance below the existing header blur without changing its visual curve or scroll-time behavior.

**Tech stack:** Existing Kotlin/Compose, Haze, shared motion/theme primitives, JVM tests and the isolated Android emulator. No new dependency, real model call, server change, commit or deployment.

The user's detailed request is the approved design. Four or five equal columns fit the available member-card width; long press enters edit mode, only role avatars jiggle and have removal badges, an add tile appears last, and Back/Done leaves edit mode. Removing a member confirms first and retains the existing minimum of one role. Group avatars remain below the members. The persona destination sheet reuses the actual editor content as its blur source, rather than making glass more transparent.

## Work and success criteria

- [x] Capture the current source/UI baseline. Add a failing initial-content-clearance regression for short/tall/no status inset and requested gaps.
- [x] Library: distinguish folder and unclassified sections using headings, count, space and a divider; remove the redundant chevron while retaining whole-row navigation. Animate entry/back with shared PageMotion and correct direction; preserve one-level folder and return behavior.
- [x] Destination sheet: share the editor's actual haze source with the folder selector. Device checks cover real blur and readable text on light/dark phones; tablet source coverage is traced through the shared editor and existing sheet window. Ordinary-material mode cards also checked; the unchanged solid-sheet branch remains covered by existing wiring.
- [x] Group settings: members first, group avatar second; full-width four/five-column grid. Long-press, jiggle, per-role close badge, confirm/cancel removal, minimum-member and busy guards, edit-only add tile, accessible labels and Back/Done exits. User refinement: 18dp neutral surface badge with 12dp secondary-ink X, no red fill.
- [x] Mode selection, final user direction: four FULL-WIDTH horizontal settings-style cards, stacked vertically, icon left and title/description beside it. Ordinary display fits four equal-height rows on one page without scrolling. Only a user-selected enlarged display preset allows height growth and scrolling. Keep generous row gaps, settings gutters/material/corners, balanced descriptions and compact short-landscape choices. Check ordinary 360dp, enlarged display and short landscape separately.
- [x] Header: initial body starts at least 16dp below the blur's lower edge; preserve larger existing gaps and the actual header/blur geometry. Chat's direct padding now uses the helper; About and MiMo pass their full old gaps rather than double-adding a workaround.
- [x] Run focused and full tests, Debug and Release builds. Inspect and exercise phone/tablet, light/dark, embedded/floating and larger text; capture key animation frames and resulting data without paid APIs.
- [x] Deliver versionCode 269 / versionName 1.2.2, signed APK, developer-only beta changelog and a verification record. Preserve 1.2.1.9 and unrelated dirty work.

Independent review findings were addressed and rechecked. Exact test/build counts, device artifacts, APK identity and practical limits are documented in `_verify/v122/VERIFICATION.md`.
