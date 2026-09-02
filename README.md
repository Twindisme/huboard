# huBoard

**Your keyboard, exactly your way.**

huBoard is a privacy-focused Android keyboard based on
[HeliBoard](https://github.com/HeliBorg/HeliBoard). It adds portable visual themes that can replace
key shapes and states, previews, animations, keyboard backgrounds, toolbar artwork, clipboard
surfaces, and color palettes.

[Download the latest huBoard APK](https://github.com/Twindisme/huboard/releases/latest/download/huBoard-latest.apk)

## Highlights

- Offline typing, suggestions, dictionaries, gesture typing, and clipboard history inherited from
  HeliBoard.
- A built-in theme browser with four bundled themes: **Classic**, **Hu Tao**,
  **Nocturne Wisp**, and **Astral Weave**.
- Importable `.hbtheme` packs with PNG, WebP, SVG, Lottie JSON, sprite-atlas, and sandboxed Luau
  animation support.
- Per-theme keyboard, key, popup, toolbar, suggestion, and clipboard styling.
- No account, ads, analytics, or transmission of typed text.

## Install

huBoard supports Android 5.0 and newer.

1. Download `huBoard-latest.apk` from the button above.
2. Allow your browser or file manager to install unknown apps when Android asks.
3. Open huBoard and follow the setup screen to enable it and make it your keyboard.

The app checks GitHub for new releases once per day after a successful request; a failed request
may be retried after one hour. Automatic checks are enabled by default and can be disabled under
**Settings → About → Automatically check for updates**. APKs are only downloaded after you tap
**Download and install**. See [Privacy](PRIVACY.md) for the exact network behavior.

## Themes

Open **Settings → Appearance → Visual theme** to switch themes. Use **Import visual theme** to
install a `.hbtheme` pack and **Remove visual theme** to delete imported packs. Bundled themes
cannot be removed.

An `.hbtheme` is a ZIP archive containing a versioned `manifest.json` and its assets. Imported
packs are checked for path traversal, archive size, entry count, image dimensions, schema validity,
and missing assets. SVGs and Lottie files are restricted to safe, local content. Luau animation
scripts execute in an isolated, resource-limited runtime with no Android, network, filesystem,
clipboard, microphone, or typed-text access.

The bundled Hu Tao directory is a complete example and is also attached to releases as
`hu_tao.hbtheme`. See the [theme authoring guide](docs/theme-packs/README.md),
[manifest schema](docs/theme-packs/manifest.schema.json), and
[huBoard Motion API](docs/theme-packs/motion-api.md). Package a theme with:

```sh
tools/package-hbtheme.sh path/to/theme-directory output/theme-name.hbtheme
```

## Build and contribute

Development requires JDK 21, Android SDK 36, and NDK `28.0.13004108`. Open the project in Android
Studio or run the Gradle wrapper directly. Pull requests run the unit-test and Android-lint gate.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Security issues should follow
[SECURITY.md](SECURITY.md). Maintainer release steps are documented in
[RELEASING.md](RELEASING.md).

## Project status

huBoard is an independent, early-stage fork under active development. Its Java/Kotlin package
structure intentionally stays close to HeliBoard so upstream changes remain practical to merge,
while its application ID, release feed, branding, and APK are separate.

## Credits and license

huBoard preserves the HeliBoard/OpenBoard/AOSP history and is licensed under the
[GNU General Public License v3.0](LICENSE).

The bundled Hu Tao theme is an unofficial fan work and is not affiliated with or endorsed by
HoYoverse or OnePlus. Character and product artwork belongs to its respective rights holders.
