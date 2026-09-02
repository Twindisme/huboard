# Astral Weave

Astral Weave is a bundled huBoard Motion theme that builds a temporary,
session-scoped constellation from recent character-key presses. It uses only
the public Motion API: the theme script owns press history, candidate scoring,
crossing avoidance, line reveals, glints, and fading.

The implementation is in
`app/src/main/assets/visual-themes/astral_weave/assets/astral_constellation.luau`.

## Art direction

- A press creates one crisp star instead of a continuously orbiting emitter.
- The primary connection favors a nearby recent node and strongly penalizes
  crossings with visible segments.
- A dimmer second branch is rare and only appears when its angle is clearly
  separated from the primary branch.
- Connections reveal from the previous star toward the new press, with a single
  traveling highlight that visually explains the relationship. The completed
  connection holds briefly, then uses a 900-millisecond linear fade in place.
  Its stable halo and brighter core keep that fade perceptible until teardown.
- Shared history expires after a short typing pause, so a new session begins
  with a clean constellation.
- The 1.7-second lifetime, four-millisecond script budget, and 256-command
  ceiling keep transparent overdraw and per-frame work bounded during fast
  typing. Up to 16 lightweight effects can finish fading instead of being
  abruptly evicted during normal fast typing.

`onPress` uses the additive Motion API 1 `nowSeconds` argument. It is monotonic
process time, exposes no wall-clock or editor information, and is ignored by
themes whose function signatures omit it. Astral Weave therefore requires
theme engine 4, while existing Motion API 1 themes remain compatible with
engine 3.
