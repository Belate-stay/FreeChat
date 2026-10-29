# Beta 1.2.2.4 interaction consistency

The user's corrected interaction design is authoritative: selection for creating a conversation uses shaded cards and an animated avatar strip, without visual check circles. Long press in My Characters enters explicit batch management, both within a folder and at the root. Back always closes a sheet first, then leaves a folder, then leaves the page. Existing-role imports stay open for consecutive additions. No server changes are required.

- [x] Reproduce selection and Back routing, then remove picker checks and verify cross-folder selection persists.
- [x] Extend scoped role management to folders and verify delete/move never affects other folders or established conversations.
- [x] Keep existing-role import sheets open and mark already-added roles, for both root and folder destinations.
- [x] Retain sheet discriminators and visible content through exit animations; audit all sheet call sites for the same null/default fallback.
- [x] Apply one 16dp input-field shape to all ordinary forms/search and card inputs, retaining the distinct chat composer geometry.
- [x] Run regression suites and actual phone/tablet UI checks, inspect dismissal animation frames and dark/light rendering, then package version 1.2.2.4 / 273.

Validation uses synthetic local fixtures and the task-owned offline emulator. No model calls, production data access, deployment, commits, or unrelated cleanup. Existing changes and APKs are preserved. Tests for nontrivial state changes must fail against the previous source before fixes, then pass after implementation.

Actual device verification found and corrected stale local callable-reference capture in picker system Back; see _verify/v1224/VERIFICATION.md for the failed reproduction, correction and final installed Release smoke test.
