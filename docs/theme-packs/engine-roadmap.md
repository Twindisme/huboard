# Visual theme engine roadmap

This roadmap tracks the implementation proposed after building the Frostbound Moon theme. The
completed work forms huBoard's schema 1 theme format.

## Milestone 1: portable rendering and Lottie

Implemented:

- Theme version metadata and minimum engine version validation.
- Per-asset `stretch`, `fit`, `crop`, and `nineSlice` rendering rules for drawable surfaces.
- Shared nine-slice rendering for clipboard history cards.
- Themeable popup-panel backgrounds.
- Vector-only Lottie JSON keypress animations with PNG frame animation fallback.
- Import limits for Lottie file size, dimensions, duration, top-level layer count, and bitmap layers.
- Packaging, schema documentation, and focused validator/importer/rendering tests.

## Milestone 2: keys and icons

Implemented:

- Bitmap key backgrounds use the generic rendering rules, with their legacy renderers retained as
  the fallback when a rule is absent.
- Separate shift off/on/locked icon assets follow the actual keyboard shift state.
- Explicit `overlay`, `embedded`, and `hidden` icon modes replace background-based inference.
- Per-key-class normalized content alignment and optional icon width/height geometry.

## Milestone 3: toolbar and theme browser

Implemented:

- Replace hardcoded decorated-toolbar actions with the user's configured toolbar actions.
- Resolve `toolbar.icon.<action>` dynamically with normal huBoard icons as fallbacks.
- Centralize toolbar dimensions and apply validated per-theme artwork geometry without shrinking
  accessibility touch targets.
- Add a Compose theme browser with thumbnails, metadata, apply, update, and removal actions.
- Add version-aware staged replacement with backup recovery.

## Milestone 4: authoring tools and regression coverage

Implemented:

- Use normalized key-preview face bounds.
- Add a Compose Theme Lab with draggable preview bounds and live rendering.
- Add a sprite-atlas option for bitmap animations.
- Add emulator golden screenshots for complete keyboard states and retain focused bitmap tests for
  renderer primitives.

## Milestone 5: sandboxed procedural motion

Implemented:

- Vendor the official Luau runtime/compiler and execute source in an isolated, read-only VM.
- Add the versioned huBoard Motion API rather than theme-specific animation types.
- Expose bounded circle, glow, image, line, and rounded-rectangle drawing commands.
- Enforce script-source, VM-memory, frame-time, draw-command, lifetime, and concurrency limits.
- Keep typed characters and all Android, network, filesystem, clipboard, and input APIs outside the
  script boundary.
- Replace Nocturne Wisp's fixed Lottie path with theme-owned physics, randomness, and trail logic.
