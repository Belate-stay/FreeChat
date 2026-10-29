# FreeChat Beta 1.2.2.1 implementation plan

**Goal:** Add voluntary complete persona contributions and an authenticated admin viewer, confirm story-world compatibility, improve in-character chat/proactive language, and undo the previous global body offset while protecting only new pages that need it.

**Architecture:** Keep role cards as Part 1 snapshots. An explicit contribution flow selects a single library card (folders retained), confirms the transmitted fields and submits only persona plus its actual image bytes to a new bounded, optional-account endpoint. Store a standalone submission snapshot; expose summaries and full detail only behind the current stats authentication. Do not publish contributions automatically or deploy. Character creation, private conversations, group/story settings and existing sync remain independent.

**Design decisions:** Reuse current Compose cards, SheetPanel, haze source, pane/floating hosts and shared transitions. The existing unavailable message stays above one readable invitation card. New story creation gets one compatibility confirmation at the final create action, with cancel preserving the draft. Restore general topContentPaddingDp to its previous title-plus-requested-gap behavior; opt in only affected new role/group/story/mode pages to a safe full-blur clearance. Character priorities and free message length/count remain; improve generation prompts and real proactive context, with no keyword deletion or extra per-response rewrite call.

**Tech stack:** Kotlin/Compose shared core and companion; existing Node/SQLite server and Web stats source. No new dependency, commit, deployment or real data migration.

## Tasks and evidence

- [x] Baseline: retain current dirty work; snapshot only changed files. Inspect persona serialization, feedback/account networking, stats auth, media validation and proactive generation entry points.
- [x] Server/admin: test anonymous/account submission, complete persona/media round trip, owner-bound idempotency, payload/format validation and private list/detail authorization. Implement additive local schema and routes plus stats Character sharing tab, full text/image detail and empty/error states. Test with temporary SQLite only; no live database or production requests.
- [x] Android: add a dedicated contribution payload builder/client and single-card picker retaining folder navigation; errors keep selection and success is reported only after acknowledgement. Disclose device model and optional account with the transmitted persona/images, without secrets, folders or conversation state. Use old-server/network error feedback. Verify exact received local payload with synthetic card/images.
- [x] Story: final create triggers a shared confirmation about one compatible world; cancel keeps edits, confirm rechecks draft and creates only once. Editing an existing story skips this creation-only warning.
- [x] Language: trace shared/core/brain/Web duplicates. Remove prompt incentives for repeated absence-counting, two-option guesses, decorative grievances and coercive follow-ups. Preserve formal personas, distinct characters, ordinary questions, punctuation and unrestricted natural segmentation. Test generation input wiring; use at most 10 newly authorized synthetic real model calls total, never supplied screenshots/private records.
- [x] Layout: failing tests reproduce unwanted global offset; restore unaffected settings/chat/legacy page positions, retain targeted safe clearance on new role library/picker/group/story/mode/contribution pages. Validate phone/tablet, dark/light, large display and embedded/floating screenshots.
- [x] Final: run serial Gradle unit suites and Debug/Release builds plus server/Web checks; independent spec and quality reviews. Set 270 / 1.2.2.1; signed install/cold-launch and checksum. Update developer-only changelog and verification record, preserve previous APKs, stop only task-owned processes.

## Additional user refinements in this same version

- [x] Root unclassified role cards support long-press multi-selection, batch move to one existing folder, and confirmed batch deletion. Batch persistence is atomic, rechecks current unclassified membership, preserves source conversations/shared media, and Back first leaves selection.
- [x] Sidebar group and ensemble-story avatars both use the established rounded-rectangle collage; private-role avatar geometry is unchanged.
- [x] Story save/create is the final scrollable list item, not a pinned screen-wide footer. The creation-only world compatibility confirmation overlays the root, rechecks valid/busy state and preserves the draft on cancel.

Server contract agreed: POST /api/character-contributions with {id,clientId,device,appVersion,persona,images:[{id,mime,data}]}. id/clientId are UUIDs; media id is SHA-256, persona refs are image://hash, no missing refs, deduplicated original bytes. Persona UTF-8 <=1MiB, media <=12MiB and <=64 images, request <=24MiB. Returns {ok:true,id,createdAt,duplicate}. Explicit invalid bearer is 401, never silently anonymous. Stats list and detail are private and use existing stats key. Raw text is preserved, no credentials/memory accepted. The endpoint is tested locally only until the developer deploys it.

Completion evidence: _verify/v1221/VERIFICATION.md and delivery.json; signed Release installed/cold-started, independent review findings rechecked, task emulator stopped (cleanup.txt). Server/Web/companion deployment remains intentionally outside scope.
