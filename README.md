# HeliBoard Theme Engine

An experimental [HeliBoard](https://github.com/HeliBorg/HeliBoard) fork with installable visual
theme packs. It turns the Hu Tao keyboard work into a reusable engine: themes can replace key
frames, pressed states, special-key shapes, key previews, keypress animations, the keyboard
wallpaper, toolbar artwork, and clipboard suggestion chrome without adding executable code.

The existing Hu Tao design is the first bundled reference theme. A Classic HeliBoard pack is
included as a safe fallback.

## Current theme workflow

1. Open **Settings → Appearance → Import visual theme** and choose a `.hbtheme` file.
2. Choose any installed pack under **Visual theme**. The keyboard reloads immediately.
3. Use **Remove visual theme** to delete an imported pack. Bundled packs cannot be removed.

Theme packs are ZIP archives containing `manifest.json` and PNG/WebP files under `assets/`.
They cannot contain code, XML, fonts, or arbitrary files. Imports enforce path, file-count,
archive-size, image-dimension, schema, and asset-reference limits.

See [the theme-pack guide](docs/theme-packs/README.md) and
[the JSON schema](docs/theme-packs/manifest.schema.json) to make a pack. The repository also has a
small packaging helper:

```sh
tools/package-hbtheme.sh path/to/theme-directory output/theme-name.hbtheme
```

## Development status

The engine is intentionally schema-versioned and starts conservatively with raster assets. The
next useful extensions are an in-app preview/gallery, pack export, optional per-pack color
presets, and more configurable toolbar geometry.

This branch still uses the Hu Tao Board application identity and updater while the theme engine is
being separated and tested. Do not publish it as a replacement for the existing Hu Tao Board app
without choosing a new application ID, signing key, update feed, and project branding.

## Credits and license

This project preserves the HeliBoard/OpenBoard/AOSP history and is licensed under the
[GNU General Public License v3.0](LICENSE). The bundled Hu Tao theme is an unofficial fan work and
is not affiliated with or endorsed by HoYoverse or OnePlus. Character and product artwork belongs
to its respective rights holders.
