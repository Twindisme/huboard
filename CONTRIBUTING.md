# Contributing to huBoard

Thanks for helping improve huBoard. The project is an independent fork of
[HeliBoard](https://github.com/HeliBorg/HeliBoard), and upstream fixes should still be proposed to
HeliBoard when they are not specific to huBoard.

## Before opening a change

- Search existing issues and discussions first.
- Keep each issue or pull request focused on one problem.
- Discuss large features before implementing them.
- Make user-facing behavior optional when reasonable.
- Keep keyboard hot paths responsive and avoid unnecessary complexity.
- Include the source and license for code or assets you did not create.

Do not include secrets, signing keys, proprietary executable code, or assets you are not permitted
to redistribute.

## Development setup

You need JDK 21, Android SDK 36, NDK `28.0.13004108`, and a compatible Android Studio version.
Import this repository as a Gradle project. The internal package layout remains close to HeliBoard,
so some older source and comments still use upstream terminology.

Before submitting a pull request, run:

```sh
./gradlew :app:testRunTestsUnitTest :app:lintRunTests
```

Include tests for new parsing, validation, data, or rendering behavior where practical. For visual
changes, attach before-and-after screenshots or a short recording.

## Theme contributions

Theme-only work should use the `.hbtheme` format instead of app-specific rendering code whenever
possible. Read the [authoring guide](docs/theme-packs/README.md) and validate the archive with:

```sh
tools/package-hbtheme.sh path/to/theme-directory output/theme-name.hbtheme
unzip -t output/theme-name.hbtheme
```

The theme engine accepts raster art, restricted SVG, restricted Lottie JSON, sprite atlases, and
sandboxed huBoard Motion scripts. Theme scripts must remain general-purpose theme code and respect
the documented memory, execution-time, drawing, and lifetime limits.

## Pull requests

Explain the user-facing goal, how you tested it, and any compatibility or privacy impact. If the
change fixes a bug, include exact reproduction steps and the affected app, Android version, and
huBoard version.
