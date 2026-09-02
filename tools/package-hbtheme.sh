#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 THEME_DIRECTORY OUTPUT.hbtheme" >&2
  exit 2
fi

theme_directory=$1
output_file=$2

if [[ ! -f "$theme_directory/manifest.json" ]]; then
  echo "error: $theme_directory/manifest.json does not exist" >&2
  exit 1
fi
if [[ -e "$theme_directory/assets" && ! -d "$theme_directory/assets" ]]; then
  echo "error: $theme_directory/assets is not a directory" >&2
  exit 1
fi
if find "$theme_directory" -type f ! -name manifest.json ! -path '*/assets/*.png' ! -path '*/assets/*.webp' ! -path '*/assets/*.svg' ! -path '*/assets/*.json' ! -path '*/assets/*.luau' -print -quit | grep -q .; then
  echo "error: packs may contain only manifest.json and PNG, WebP, SVG, Lottie JSON, or Luau files under assets/" >&2
  exit 1
fi

mkdir -p "$(dirname "$output_file")"
output_file=$(cd "$(dirname "$output_file")" && pwd)/$(basename "$output_file")
rm -f "$output_file"
(
  cd "$theme_directory"
  if [[ -d assets ]]; then
    zip -q -X -r "$output_file" manifest.json assets
  else
    zip -q -X "$output_file" manifest.json
  fi
)
echo "created $output_file"
