# Beta 1.2.2.5: separate character identity from conversation settings

The user's explicit design is the implementation contract. Character cards retain character identity, appearance, personality, learning material and relationship, but no user identity, world rules, supporting-cast list or opening lines. A one-to-one CharacterProfile retains every existing field. StoryProfile gains its own optional userPersona; existing world rules/background/opening situation remain story settings.

- [x] Add failing core, persistence/wire/image and UI boundary regressions.
- [x] Remove dialogue fields from CharacterPersona; ignore legacy JSON keys without changing original private profiles; update consumers and old fixtures.
- [x] Hide/save-scope role-only forms and preview; add story user identity with existing shared field styling.
- [x] Separate card learning scope; enforce dialogue-owned context in group/story and scene/memory prompts.
- [x] Align local contribution server whitelist with modern cards while accepting/ignoring legacy dialogue fields; no deployment or database rewrite.
- [x] Run offline suites and UI/persistence checks, then build/install version 1.2.2.5 / 274.

No model calls, production data, real database migration, deployment, commit or unrelated cleanup. No automatic merge of conflicting legacy card user identities into an existing story. Character prompt free text and original learning references remain authored character material; dialogue scope wins over any embedded instructions.
