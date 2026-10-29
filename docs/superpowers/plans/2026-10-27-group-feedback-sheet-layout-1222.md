# FreeChat Beta 1.2.2.2 implementation plan

**Goal:** Coordinate group nudge haptics/avatar motion, prevent sheet/background content collisions, clear initial body content below affected headers, and match character-library multi-selection to Favorites.

**Architecture:** Preserve existing message identity, group generation behavior, persona/card persistence and shared Compose materials. Fix sampling/layout at the actual sheet boundary instead of applying arbitrary z-indexes or opaque global redesigns. Reuse Favorites header and 20dp circular selection vocabulary. No model requests or server/deployment work.

**Success criteria and tasks**

- [x] Capture 1.2.2.1 baseline on the task-owned offline emulator using synthetic fixtures. Identify whether collisions come from missing blur sources, nested card sampling, full-page transitions inside constrained sheets, or simultaneous active sheets. Audit newly added library/contribution/picker/group/story flows and compare working older sheets.
- [x] Add focused regressions before production edits; run and retain their failing output. Add a finite group nudge feedback path for double tap and accessibility, synchronized one short platform haptic with avatar movement, preserving mention and multi-selection/removed-member gating and reduced-motion behavior.
- [x] Match unclassified multi-selection to Favorites: Close/count header with top-right Move/Delete, no Done/footer, same animated circular indicator and final-deselect exit. Verify batch move, delete/cancel, Back, folder navigation and source-conversation retention.
- [x] Repair sheet sampling and content constraints at the narrow shared/call-site boundary found by reproduction. Verify old body controls never show through unblurred; headers/buttons stay distinct; long lists scroll; choosing another sheet does not leave two interactive layers; keyboard, theme/material and tablet floating/embedded remain usable.
- [x] Audit initial header clearances. Repair Chat and proven affected pages only, preserving larger existing gaps and unchanged header/blur geometry. Test initial first content positions, scrolling under the header and large fonts/density; avoid moving every page globally.
- [x] Run serial offline Gradle focused/full tests and Debug/Release builds; inspect phone and tablet light/dark, normal/large text, advanced/basic material and rapid sheet replacement screenshots/interaction. Independent review of changed boundaries; fix findings, recheck affected tests.
- [x] Set version 271 / 1.2.2.2. Verify signed APK version/signature, install/cold launch, checksum and preserve older APK. Update developer-only changelog and verification record, stop only task-owned processes. No commit, Push, live uploads, private-record access or deployment.

Files: `ui/components/ChatBubble.kt` and focused nudge helper/tests; `ui/screens/CharacterLibraryScreen.kt` and its tests; shared `ui/components/SheetPanel.kt` only where required by reproduced layering/measurement; `CharacterContributionCard.kt` for embedded picker composition; `ChatScreen.kt` and confirmed affected top-padding call sites; `HazeSpecTest.kt`/source wiring regressions; `app/build.gradle.kts`; `_verify/v1222/`.

Build command (serial, Java 17): `./gradlew.bat --offline '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1024m' :freechat-core:test :freechat-companion:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --continue --console=plain`.
