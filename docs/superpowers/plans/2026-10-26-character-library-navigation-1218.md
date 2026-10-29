# Character Library Navigation Refinements

**Goal:** Group personal and built-in pools under a separate settings category, correct one-level folder navigation, apply source-based Start chat eligibility, and keep compact previews separate from immediate full editing.

**Architecture:** Reuse settings category ordinal 6 with the new “角色库” title and a local built-in-pool placeholder subpage. Keep the selected folder in the host so system Back and editor return share the same state. Start-chat eligibility derives from existing source IDs, with no new serialized fields. Existing card snapshots, memory and group selection remain independent.

- [x] Write source-origin round-trip and UI wiring regressions; record failures against 1.2.1.7. Reproduce folder system Back on the isolated emulator.
- [x] Move “我的角色” out of models, add the “内置角色池” placeholder using existing settings/page motion and back navigation; preserve category ordinals.
- [x] Remove library card creation callbacks, hide new-folder inside a folder, retain import/move functionality, and centralize folder state for one-level Back/editor return.
- [x] Gate Start chat by source on both the preview and host action. Limit each preview value to two displayed lines; preserve full stored text and open the persona editor already editable.
- [x] Update the user-confirmed version to 1.2.1.8 / 267, run all core/app/companion tests, build signed APK, and verify phone/tablet behavior with synthetic data. No paid model calls, server changes/deployment, commits or pushes.
- [x] Document verification and preserve the previous APK. Evidence: `_verify/v1218/VERIFICATION.md`.
