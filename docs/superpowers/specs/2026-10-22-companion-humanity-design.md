# Beta 1.2.1.3: role fidelity, human conversation and narrative continuity

## Authorization and boundary

The user approved the preceding evidence-backed proposal, asked for deep implementation, and requires advance notice before changing existing mechanisms. Before edits, three intentional changes were disclosed: lossless reply handling replaces random truncation, context-grounded explicit silence replaces anger dice, and story facts are separated from character knowledge. Version 1.2.1.3 / 262 and at most 12 real built-in-model requests using synthetic material were explicitly approved. No private conversations may be read or uploaded. Server and Web changes remain local; no deployment, commit or push.

The known dirty workspace contains Claude's recent companion/cache/voice work and the previous tablet fix. Preserve it. The task stays in that checkout rather than dropping uncommitted work into a clean old worktree. Pre-change source snapshots are under `_verify/humanity/baseline/` and are the basis for this task's diff and A/B prompts.

Do not change mode permissions, UI settings, the user's explicit personality/negative constraints, plot-length/creativity/deep-thinking choices, accepted-history semantics, scene-image visual-only status, or the save-before-apply character workflow. No new agent framework, vector database, dependencies, mandatory model calls, hidden review pass, or uniform warm/cold persona.

## Intended behavior

### Complete meaning and conversation timing

- Model-generated meaningful content is never discarded by `take(randomCount)`. Usually generate one or two messages, but preserve carrier boundaries and all meaning if a model emits more. Merge adjacent compatible text if needed, never combine distinct voice blocks into a broken audio tag.
- Emotion is a tone/context signal, not a random message quota or silence lottery. Silence is an explicit, unambiguous control result produced only by the model's current-context decision, separate from a malformed/empty response. A deliberate regeneration must still produce content. Narrative modes never use chat silence controls.
- Keep buffering, cancellation gates, send commitment, stable IDs, per-conversation isolation and sleep choices. Use a bounded text-sensitive delivery schedule and count model/network waiting toward the first-message minimum. Do not prepend random waiting before the API call. App and WeChat consume the same optional schedule; old servers/clients remain compatible.

### Distinct people, modes and prose

- Select the mode contract at the start: WeChat is actual chat messages; action is only the controlled character's action/dialogue; plot is third-person fiction. No universal WeChat identity before a narrative task.
- User-authored constraints/facts outrank derived persona and samples. MBTI and personality labels guide, not mechanically dictate. Preserve the user's intended intensity without default heat, default distance or compulsory emotional performance.
- Replies address the concrete situation and person, without a mandatory empathy/diagnosis/advice/question formula. Interpret emotion target and uncertainty correctly; a guess about subtext is not a known fact. Do not force slang, catchphrases, punctuation or physiological reactions.
- Narrative details serve action, motivation and causal movement. Original prose determines density/rhythm; no sensory checklist in every paragraph, mandatory emotional explanation, stock gestures or obligatory closing uplift. Preserve quote/POV/user-agency boundaries.
- Keep short reference texts intact. For large references, retain full source on disk, choose bounded contiguous relevant excerpts with attribution and a stable style anchor, and place variable excerpts after the stable system/history prefix. Do not turn examples into current plot facts or executable instructions. Persona-learning inputs retain the original source.

### Remember the right things without omniscience

- Continue stable factual memory; supplement narrative entries with optional `storyState`, `roleKnowledge`, and `openLoops` strings. They record accepted scene progress, evidence-backed facts available to the controlled character, and pending/fulfilled/cancelled matters. Older JSON remains readable without a database migration.
- The extractor sees a bounded but substantial complete reply rather than its first 200 characters. It must not treat discarded drafts, visual-only rows or hidden analysis as accepted story events.
- Shared formatting must label world facts separately from role knowledge; later explicit updates beat older scene state. Do not erase narrative events just because they were not spoken aloud, and do not grant private narration to the character.
- Optional fields survive healing, upsert, synchronization and deletion. Derived entries retain source IDs. Before any asynchronous write or use, recheck deletion and auto-memory policy; deleted sources cannot be resurrected by a delayed extractor. Memory failure must remain nonfatal to a normal reply.
- App and JVM brain share model-managed association semantics. No user-facing synonym slider is restored. Normal/HQ budgets and the user's switches remain in force.

### Grounding, not unnecessary search

Enabling search grants a capability; it does not send every private utterance to a search engine. Reuse/align current search intent planning and trusted evidence blocks. Ordinary greetings, acknowledgements, role dialogue, emotional disclosure and fictional scene continuation do not search absent a real external-information need. Explicit factual/current-information requests still search. Failed search must not break or force fabricated factual replies.

## Verification and limits

TDD covers lossless grouping, mixed voice/text, explicit silence versus empty response, stable IDs and send commitment, bounded schedules, parser compatibility, memory full-reply coverage, old/null JSON, source deletion races, normal/HQ memory policy, original-source boundaries, all three mode contracts, and cross-platform policy parity. Keep pre-existing tests unless an assertion explicitly encodes one of the newly approved obsolete behaviors; document such replacements.

Run app/core/companion tests, focused Node tests, Web type/build/parity checks, and Debug/Release packaging. Use local fake providers for orchestration, not production data. Live evaluation is capped at 12 HTTP model requests total, no retry loop, 6 old/new synthetic pairs covering fragmented WeChat, empathy/anger target, action knowledge, plot style, long-reply memory and continuity. Report outputs/latency honestly; prompt contracts alone do not prove subjective quality. Never claim a percentage gain or a deployed WeChat improvement from local tests.
