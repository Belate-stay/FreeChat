# Beta 1.2.1.2: tablet composer reachability and titles

The user authorizes a focused bug fix, using the current Beta 1.2.1.1 workspace and preserving Claude's unpublished changes. Version 1.2.1 is already released. This delivery is Beta 1.2.1.2, code 261; do not edit servers, deployment state, official release notes, or unrelated voice/proactive/model work.

## Scope and decisions

- The persistent tablet Chat preserves `inputHidden` across conversation changes. Only consumed list gestures currently reveal it. A short or empty conversation cannot supply that gesture, leaving the composer unreachable.
- Resetting every conversation's hidden state would fix reachability but discard the existing long-conversation behavior. Instead, reveal the composer when the current conversation is empty or its restored, measured list cannot scroll in either direction. Do not change the list position, draft, keyboard focus, or ordinary hide/show animation. Never classify a nonempty, unmeasured list as short.
- The fixed left brand stays `FreeChat`, in the existing phone Aurora family with its existing brand size/tracking. It must not change with selection or multiselect.
- The tablet right header uses the selected conversation title in standard mode and the character name in companion mode, falling back to the conversation title and then FreeChat. Keep normal UI typography, ellipsis, multiselect count and action buttons. Phone shared-title behavior is unchanged.

## Verification

First observe failing regression contracts against the current code. Verify an isolated tablet with automatic hiding: hide in a long conversation, enter a short standard conversation, a short role conversation, an empty existing conversation, and a new conversation. Each must expose the input without opening the keyboard, losing drafts or moving history. Long-to-long switching must keep hidden state; a real reveal gesture and PINNED must still work. Check portrait/landscape, both input styles, left Aurora brand, changing right titles, and phone regression. Run app/core tests and Debug/Release builds, verify the signed version/QA isolation, then deliver a Beta APK and local evidence. No paid API calls or real cloud mutations.
