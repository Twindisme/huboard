# Security policy

## Supported versions

Security fixes target the latest huBoard release. Please reproduce a suspected issue on the latest
version before reporting it when that can be done safely.

## Reporting a vulnerability

Do not open a public issue for a vulnerability that could expose keyboard data, bypass the theme
sandbox, compromise update installation, or enable code execution. Use GitHub's
[private vulnerability reporting](https://github.com/Twindisme/huboard/security/advisories/new) and
include:

- the affected huBoard version and Android version;
- exact reproduction steps or a minimal malicious `.hbtheme` when applicable;
- the impact and any conditions required to trigger it;
- logs or screenshots with sensitive text removed.

If private reporting is unavailable, open a minimal public issue asking the maintainer to enable a
private contact path; do not publish exploit details.

## Theme boundary

Imported themes are untrusted data. huBoard validates archive paths, sizes, declared assets, raster
dimensions, SVG content, Lottie content, and manifest limits. huBoard Motion runs Luau source in an
isolated VM with memory, execution-time, drawing-command, and lifetime budgets. A theme should never
receive network, filesystem, Android, clipboard, microphone, editor, or typed-text access.
