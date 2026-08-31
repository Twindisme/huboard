# `.hbtheme` authoring guide

An `.hbtheme` file is a normal ZIP archive with this exact root layout:

```text
my_theme/
├── manifest.json
└── assets/
    ├── key_normal.png
    ├── key_pressed.png
    └── …
```

Zip the *contents* of `my_theme`, not the `my_theme` folder itself. Every packaged image must be a
PNG or WebP, at most 4096 pixels on either side, 8 million pixels, and 8 MiB. The whole extracted pack is limited to 30 MiB and
256 entries. Theme IDs use lowercase letters, numbers, and underscores and cannot exceed 48
characters.

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
    "icon.backspace": "file:assets/backspace.png",
    "icon.action": "file:assets/action.png",
    "icon.space.glyph": "file:assets/space_glyph.png",
    "icon.space.language": "file:assets/language.png"
  },
  "keyRenderer": {
    "regularLeftCapPx": 20,
    "regularRightCapPx": 20,
    "iconGradientStart": "#FFFFFF",
    "iconGradientEnd": "#FFFFFF"
  }
}
```

The cap values describe non-stretching pixels at the left and right edges of the source image.
The center is stretched horizontally, which keeps ornamental corners intact.

## Capabilities

- `keys`: key backgrounds, special shapes, and top icons. Requires `key.normal`, `key.pressed`,
  `icon.backspace`, `icon.action`, `icon.space.glyph`, and `icon.space.language`.
- `keyPreview`: the popup shown above a pressed key. Requires `preview.background` and accurate
  face bounds in `keyPreview`.
- `keyPressAnimation`: frame animation drawn over a pressed key. Requires a
  `keyPressAnimation.frames` list referencing animation assets declared in `assets`.
- `keyboardBackground`: keyboard wallpaper. Requires `keyboard.background`.
- `toolbar`: toolbar background/start/end artwork. Requires `toolbar.background`,
  `toolbar.start`, and `toolbar.end`; toolbar icon assets are optional.
- `clipboard`: clipboard suggestion background. Requires `clipboard.suggestion.background`;
  paste and close icons are optional.

Capabilities are independent. Omit any capability and HeliBoard’s regular renderer remains in
charge of that area.

## Stable asset keys

| Area | Keys |
|---|---|
| Normal keys | `key.normal`, `key.pressed` |
| Space | `key.space.normal`, `key.space.pressed` |
| Shift | `key.shift.normal`, `key.shift.pressed` |
| Delete | `key.delete.normal`, `key.delete.pressed` |
| Action/enter | `key.action.normal`, `key.action.pressed` |
| Function shapes | `key.function.round.normal`, `key.function.round.pressed`, `key.function.diamond.normal`, `key.function.diamond.pressed` |
| Key icons | `icon.backspace`, `icon.action`, `icon.space.glyph`, `icon.space.language` |
| Preview/background | `preview.background`, `keyboard.background`, `popup.panel.background` |
| Toolbar | `toolbar.background`, `toolbar.start`, `toolbar.start.pressed`, `toolbar.end`, `toolbar.icon.keyboard`, `toolbar.icon.cursor`, `toolbar.icon.search`, `toolbar.icon.emoji`, `toolbar.icon.expand` |
| Clipboard | `clipboard.suggestion.background`, `clipboard.icon.paste`, `clipboard.icon.close` |

Animation asset keys may use any `animation.*` name. All other keys are rejected if the engine
does not recognize them; this catches typos before a broken theme is installed.

## Packaging

From the repository root:

```sh
tools/package-hbtheme.sh themes/my_theme dist/my_theme.hbtheme
```

The helper checks the expected layout and uses `zip` to create a flat-root pack. Android performs
the authoritative validation during import. For the full contract, consult `manifest.schema.json`
and the bundled Hu Tao manifest at
`app/src/main/assets/visual-themes/hu_tao/manifest.json`.
