# `.hbtheme` authoring guide

An `.hbtheme` file is a normal ZIP archive with this exact root layout:

```text
my_theme/
├── manifest.json
└── assets/
    ├── key_normal.png
    ├── key_pressed.png
    ├── backspace.svg
    └── …
```

Zip the *contents* of `my_theme`, not the `my_theme` folder itself. Packaged artwork may be PNG,
WebP, SVG, or a Lottie JSON animation. A keypress animation may also include a sandboxed Luau
source file. Images are limited to 4096 pixels on either side, 8 million
pixels, and 8 MiB. Lottie files are limited to 2 MiB. The whole extracted pack is limited to 30 MiB and
256 entries. Theme IDs use lowercase letters, numbers, and underscores and cannot exceed 48
characters.

SVGs are rendered at runtime and remain sharp at every density. For predictable and safe imports,
huBoard accepts static vector elements (`svg`, groups, paths, basic shapes, gradients, and clip
paths) but rejects scripts, images, text, event handlers, external URLs, embedded data, entities,
and document types. Flatten reusable objects and convert text to paths before packaging.

Use `file:assets/...` references in distributable packs. `res:...` references are intended for
themes bundled into the Android project and refer to compiled drawable resource names.

## Smallest key theme

This pack changes ordinary key backgrounds and falls back to those two images for special-key
backgrounds:

```json
{
  "schemaVersion": 1,
  "id": "my_theme",
  "displayName": "My Theme",
  "author": "Your name",
  "capabilities": { "keys": true },
  "assets": {
    "key.normal": "file:assets/key_normal.png",
    "key.pressed": "file:assets/key_pressed.png",
    "icon.backspace": "file:assets/backspace.svg",
    "icon.action": "file:assets/action.svg",
    "icon.space.glyph": "file:assets/space_glyph.svg",
    "icon.space.language": "file:assets/language.svg"
  },
  "keyRenderer": {
    "centerSpecialKeyArtworkHorizontally": true,
    "regularLeftCapPx": 20,
    "regularRightCapPx": 20,
    "iconGradientStart": "#FFFFFF",
    "iconGradientEnd": "#FFFFFF"
  }
}
```

The cap values describe non-stretching pixels at the left and right edges of the source image.
The center is stretched horizontally, which keeps ornamental corners intact.
`centerSpecialKeyArtworkHorizontally` centers the visible pixels in special-key and spacebar
artwork rather than centering an asymmetrically padded image canvas. It defaults to `false`.

## Capabilities

- `keys`: key backgrounds, special shapes, and top icons. Requires `key.normal`, `key.pressed`,
  `icon.backspace`, `icon.action`, `icon.space.glyph`, and `icon.space.language`.
- `keyPreview`: the popup shown above a pressed key. Requires `preview.background` and accurate
  face bounds in `keyPreview`.
- `keyPressAnimation`: animation drawn over a pressed key. Supports a frame list, Lottie asset,
  PNG/WebP sprite atlas, or huBoard Motion script.
- `keyboardBackground`: keyboard wallpaper. Requires `keyboard.background`.
- `toolbar`: toolbar background/start/end artwork. Requires `toolbar.background`,
  `toolbar.start`, and `toolbar.end`; per-action toolbar icon assets are optional.
- `clipboard`: clipboard suggestion and optional clipboard-history cards. Requires
  `clipboard.suggestion.background`; suggestion icons and entry assets are optional.

Capabilities are independent. Omit any capability and huBoard’s regular renderer remains in
charge of that area.

## Rendering and versions

huBoard currently has one theme format: schema 1. It includes theme versions, minimum engine
versions, and reusable rendering rules.

```json
{
  "schemaVersion": 1,
  "versionCode": 3,
  "versionName": "1.2",
  "minimumEngineVersion": 3,
  "rendering": {
    "clipboard.suggestion.background": {
      "mode": "nineSlice",
      "insets": { "leftPx": 20, "topPx": 12, "rightPx": 20, "bottomPx": 12 },
      "cornerScale": 0.75
    },
    "toolbar.end": { "mode": "fit" }
  }
}
```

Rendering modes are `stretch` (the default), `fit`, `crop`, and `nineSlice`. A nine-slice rule
preserves the four corners and stretches the edges and center. Rules apply both to drawable-based
surfaces and direct-canvas key backgrounds (`key.normal`, space, shift, delete, action, and both
function shapes). A declared key rule replaces its legacy horizontal-slice or aspect-fit behavior.
If a special key asset is omitted, the regular key asset and rendering rule remain the fallback.
Clipboard entry rules also override the older clipboard-specific slice settings.

### Toolbar actions, geometry, and previews

Themes can size and align their toolbar artwork independently from the keyboard keys:

```json
{
  "assets": {
    "theme.thumbnail": "file:assets/cover.png",
    "toolbar.icon.undo": "file:assets/undo.svg",
    "toolbar.icon.select_word": "file:assets/select_word.svg"
  },
  "toolbarRenderer": {
    "heightDp": 58,
    "startWidthDp": 62,
    "endWidthDp": 66,
    "keyWidthDp": 52,
    "contentPaddingStartDp": 2,
    "contentPaddingEndDp": 2,
    "iconSizeDp": 24,
    "iconOffsetXDp": 0,
    "iconOffsetYDp": -1,
    "startArtworkOffsetYDp": -2,
    "endArtworkOffsetXDp": -3
  }
}
```

Toolbar action assets use `toolbar.icon.<action>`, where `<action>` is the lowercase huBoard
action name, such as `undo`, `settings`, `select_word`, `clipboard`, or `emoji`. huBoard preserves
the toolbar actions selected by the user and falls back to its normal icon whenever the theme does
not supply one. The older `toolbar.icon.keyboard` and `toolbar.icon.cursor` aliases remain as
fallbacks for Settings and Select Word.

Toolbar height and button-width values are density-independent pixels. huBoard keeps the actual
interactive targets at least 48 dp even when a smaller visual value is requested. Artwork offsets
range from -48 to 48 dp. `theme.thumbnail` is displayed in the theme browser; when it is omitted,
the browser tries the keyboard, toolbar, and key backgrounds in that order.

### Key content and state icons

Themes can keep functional glyphs separate from their backgrounds:

```json
{
  "assets": {
    "icon.shift.off": "file:assets/shift_off.svg",
    "icon.shift.on": "file:assets/shift_on.svg",
    "icon.shift.locked": "file:assets/shift_locked.svg"
  },
  "keyRenderer": {
    "content": {
      "regular": { "centerY": 0.48 },
      "shift": {
        "iconMode": "overlay",
        "centerX": 0.5,
        "centerY": 0.5,
        "iconWidth": 0.5,
        "iconHeight": 0.5
      },
      "action": { "iconMode": "embedded" },
      "diamondFunction": { "iconMode": "hidden" }
    }
  }
}
```

`centerX` and `centerY` place the key's top content in normalized key coordinates. Icon width and
height are fractions of the key's shorter edge; setting only one preserves the icon's default aspect
ratio. The icon modes mean:

- `overlay`: draw the theme icon when supplied, otherwise use huBoard's normal icon.
- `embedded`: suppress the overlay because the dedicated normal/pressed background contains it.
- `hidden`: deliberately suppress the overlay without claiming that it is baked into the art.

When any shift-state icon is supplied, `icon.shift.off` is required and acts as the fallback for an
omitted on or locked state. huBoard selects the three states from the actual keyboard shift state,
not the key's transient pressed state. `embedded` requires dedicated normal and pressed backgrounds
for that key class. Dedicated shift backgrounds do not implicitly suppress the shift glyph; use
`"iconMode": "embedded"` when the artwork already contains it.

## Key-preview face bounds

The theme format locates the visible key face with normalized coordinates, so the manifest remains
correct if the preview artwork is regenerated at another resolution:

```json
{
  "keyPreview": {
    "faceBounds": {
      "left": 0.1892,
      "top": 0.1127,
      "right": 0.8243,
      "bottom": 0.8291
    },
    "verticalOverscan": 0.03,
    "gapDp": 2,
    "textColor": "#FDECD2"
  }
}
```

Each bound ranges from 0 to 1 relative to the preview image. Open a theme's **Theme Lab** action in
the in-app theme browser to drag the face and its corner handles, preview label placement live, and
copy a ready-to-paste JSON block. Source-pixel fields are also accepted when migrating existing
artwork.

## Keypress animations

For procedural behavior, use a huBoard Motion script. It is ordinary Luau source executed in an
isolated VM and can keep state, perform arbitrary math, and issue bounded drawing commands:

```json
{
  "minimumEngineVersion": 3,
  "capabilities": { "keyPressAnimation": true },
  "assets": {
    "animation.key_press": "file:assets/key_press.luau"
  },
  "keyPressAnimation": {
    "script": {
      "asset": "animation.key_press",
      "apiVersion": 1
    },
    "durationMs": 2000,
    "maxSimultaneousEffects": 8,
    "characterKeysOnly": true
  }
}
```

Motion scripts are the open-ended animation path; frame, Lottie, and atlas modes remain useful for
authored deterministic artwork. See [huBoard Motion API 1](motion-api.md) for callbacks, drawing
commands, limits, and a complete example. A script animation must provide an explicit bounded
`durationMs` between 16 and 5000 ms.

Themes can use a vector Lottie animation instead of a directory of PNG frames:

```json
{
  "schemaVersion": 1,
  "capabilities": { "keyPressAnimation": true },
  "assets": {
    "animation.frost": "file:assets/frost.json"
  },
  "keyPressAnimation": {
    "lottieAsset": "animation.frost",
    "durationMs": 320,
    "heightToKeyHeight": 1.25,
    "maxSimultaneousEffects": 8,
    "characterKeysOnly": true
  }
}
```

`durationMs` may be omitted to use the composition's own duration. To keep imported animations
portable and bounded, huBoard accepts vector-only Lottie JSON with no bitmap image layers, a
maximum composition duration of 30 seconds, and at most 256 top-level layers. Per-key playback
may be overridden to 16–5000 ms.

For raster animation, a sprite atlas avoids decoding and retaining a separate bitmap for every
frame. Cells play from left to right and then top to bottom:

```json
{
  "assets": {
    "animation.spark_atlas": "file:assets/spark_atlas.webp"
  },
  "keyPressAnimation": {
    "spriteAtlas": {
      "asset": "animation.spark_atlas",
      "columns": 4,
      "rows": 3,
      "frameCount": 10
    },
    "frameDurationMs": 26,
    "heightToKeyHeight": 1.25
  }
}
```

The atlas must be PNG or WebP, its width and height must divide evenly into the declared grid, and
the grid may contain at most 120 cells. `frameCount` may omit unused cells at the end and defaults
to the complete grid.

## Color palette

A theme can optionally provide its own keyboard colors. When `colors` is present, huBoard uses
that palette while the theme is active instead of the separately selected day/night color scheme.
The seven base colors are required; suggestion, spacebar-text, and gesture colors fall back to
their corresponding base colors. Colors use `#RRGGBB` or `#AARRGGBB` notation.

```json
{
  "colors": {
    "accent": "#CD563C",
    "background": "#48231F",
    "keyBackground": "#4B302C",
    "functionalKey": "#331E22",
    "spaceBar": "#692D2B",
    "keyText": "#FDECD2",
    "keyHintText": "#D3B9A0"
  }
}
```

Themes that depend on one of huBoard’s base key styles can lock it in with an optional appearance
block. Omitted values continue to follow the user’s Appearance settings.

```json
{
  "appearance": {
    "keyboardStyle": "Rounded",
    "keyBorders": true,
    "showMoreSuggestionsHint": false,
    "clipboardSuggestionContentOffsetYDp": -2
  }
}
```

`showMoreSuggestionsHint` controls the small ellipsis below the center suggestion when more
suggestions are available. It defaults to `true` when omitted.
`clipboardSuggestionContentOffsetYDp` moves the clipboard card's contents without moving its
background; negative values move the contents upward. It defaults to `0`.

Clipboard-history cards can use normal, pressed, and pinned artwork. Normal and pressed assets
must be supplied as a pair; the pinned pair is optional and falls back to the normal pair. Insets
define the fixed corners of each image, while huBoard stretches only the center so cards can grow
for longer clipboard text without distorting their trim.

```json
{
  "assets": {
    "clipboard.entry.normal": "file:assets/clip_normal.png",
    "clipboard.entry.pressed": "file:assets/clip_pressed.png",
    "clipboard.entry.pinned": "file:assets/clip_pinned.png",
    "clipboard.entry.pinned.pressed": "file:assets/clip_pinned_pressed.png",
    "clipboard.icon.pin": "file:assets/pin.svg"
  },
  "clipboardRenderer": {
    "entryInsets": { "leftPx": 32, "topPx": 32, "rightPx": 32, "bottomPx": 32 },
    "pinnedEntryInsets": { "leftPx": 45, "topPx": 58, "rightPx": 75, "bottomPx": 44 },
    "entryCornerScale": 0.62,
    "pinnedEntryCornerScale": 0.62,
    "pinnedContentEndPaddingDp": 9
  }
}
```

## Stable asset keys

| Area | Keys |
|---|---|
| Normal keys | `key.normal`, `key.pressed` |
| Space | `key.space.normal`, `key.space.pressed` |
| Shift | `key.shift.normal`, `key.shift.pressed` |
| Delete | `key.delete.normal`, `key.delete.pressed` |
| Action/enter | `key.action.normal`, `key.action.pressed` |
| Function shapes | `key.function.round.normal`, `key.function.round.pressed`, `key.function.diamond.normal`, `key.function.diamond.pressed` |
| Key icons | `icon.backspace`, `icon.action`, `icon.space.glyph`, `icon.space.language`, `icon.shift.off`, `icon.shift.on`, `icon.shift.locked` |
| Preview/background | `preview.background`, `keyboard.background`, `popup.panel.background` |
| Toolbar | `toolbar.background`, `toolbar.start`, `toolbar.start.pressed`, `toolbar.end`, `toolbar.icon.<action>`, plus legacy aliases `toolbar.icon.keyboard`, `toolbar.icon.cursor`, `toolbar.icon.search`, `toolbar.icon.emoji`, `toolbar.icon.expand` |
| Clipboard | `clipboard.suggestion.background`, `clipboard.icon.paste`, `clipboard.icon.close`, `clipboard.entry.normal`, `clipboard.entry.pressed`, `clipboard.entry.pinned`, `clipboard.entry.pinned.pressed`, `clipboard.icon.pin` |
| Theme metadata | `theme.thumbnail` |

Animation asset keys may use any `animation.*` name. All other keys are rejected if the engine
does not recognize them; this catches typos before a broken theme is installed.

## Packaging

From the repository root:

```sh
tools/package-hbtheme.sh themes/my_theme dist/my_theme.hbtheme
```

The helper checks the expected layout and uses `zip` to create a flat-root pack. Android performs
the authoritative validation during import. For the full contract, consult `manifest.schema.json`
and the complete, directly packageable Hu Tao example at
`app/src/main/assets/visual-themes/hu_tao/`.

Installed themes can be updated from the theme browser by importing the same theme ID with
a strictly higher `versionCode`. huBoard stages and validates the new pack before replacing the old
copy, keeps a temporary backup during replacement, and recovers that backup after an interrupted
update.

To build the bundled example itself:

```sh
tools/package-hbtheme.sh \
  app/src/main/assets/visual-themes/hu_tao \
  dist/hu_tao.hbtheme
```

## Emulator visual-regression suite

Milestone 4 includes a device-rendered golden-test runner for the Hu Tao alphabet, shifted, symbols,
clipboard, and held-key-preview states. Use `record` once to create the checked-in references, then
use `verify` on the same fixed emulator image, resolution, density, and locale:

```sh
tools/visual-theme-goldens.sh record
tools/visual-theme-goldens.sh verify
```

Recording installs the `debugNoMinify` test variant, captures the real IME through AndroidX Test,
and pulls reference PNGs into `app/src/androidTest/assets/visual-theme-goldens`. Verification permits
only a 0.1% changed-pixel fraction with a small per-channel rendering tolerance and saves actual and
magenta diff images to the app's external test directory when a comparison fails.
