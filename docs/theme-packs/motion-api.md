# huBoard Motion API 1

huBoard Motion runs stateful Luau source from an `.hbtheme` without granting the script access to
Android. It is intended for procedural keypress effects whose behavior cannot be represented well
as frames, a sprite atlas, or Lottie.

## Module contract

The script must return a table containing `onPress` and `frame` functions:

```luau
return {
    onPress = function(centerX, centerY, keyWidth, keyHeight, viewportWidth, viewportHeight, seed, nowSeconds)
        return { x = centerX, y = centerY, age = 0, seed = seed, startedAt = nowSeconds }
    end,

    frame = function(state, dt, elapsed, viewportWidth, viewportHeight)
        state.age += dt
        motion.circle(state.x, state.y, 8, 0xFFFFFFFF, 1)
        return state.age < 0.5
    end,
}
```

`onPress` is invoked once and must return a table containing that effect's private state. Coordinates
and dimensions are physical pixels in the keyboard canvas. The origin is at the keyboard's top-left;
positive Y points downward. `seed` is an unsigned 32-bit value. `nowSeconds` is monotonic process
time, not wall-clock time; it is intended for expiring shared animation state between typing
sessions. Scripts may omit the argument when they do not need it. huBoard intentionally does not
pass the character, key code, surrounding text, clipboard, or editor identity to a theme.

`nowSeconds` is available in theme engine 4. A theme that uses it must declare
`"minimumEngineVersion": 4`; Motion scripts that only use the first seven arguments remain
compatible with engine 3.

`frame` receives the state table, a delta in seconds clamped to 0–0.05, elapsed seconds, and the
current keyboard viewport. It issues drawing commands and returns `true` to remain active or `false`
to finish. huBoard also stops it at the manifest's `durationMs` limit.

## Drawing and utility functions

Colors are numeric `0xAARRGGBB` values. Optional opacity values range from 0 to 1. Images are centered
on `(x, y)` and must refer to a key declared in the manifest's `assets` map.

```luau
motion.circle(x, y, radius, color, opacity?)
motion.glow(x, y, radius, innerColor, outerColor, opacity?)
motion.image(assetKey, x, y, width, height, rotationDegrees?, opacity?)
motion.line(x1, y1, x2, y2, width, color, opacity?)
motion.roundedRect(left, top, right, bottom, radius, color, opacity?)

local value, nextSeed = motion.random(seed, minimum, maximum)
```

`motion.random` is a deterministic generator. Store the returned seed in the effect state if more
random values are needed. Normal Luau control flow, tables, functions, types, and the safe portions
of its standard library are available, so paths, springs, trails, emitters, collisions, and noise can
all be implemented by a theme rather than becoming huBoard engine types.

## Manifest limits

```json
"keyPressAnimation": {
  "script": {
    "asset": "animation.key_press",
    "apiVersion": 1,
    "memoryLimitKb": 2048,
    "frameTimeLimitMs": 2.0,
    "maxDrawCommandsPerFrame": 128
  },
  "durationMs": 2000,
  "maxSimultaneousEffects": 8
}
```

- Script source: at most 128 KiB, UTF-8, compiled locally by huBoard. Imported bytecode is never
  accepted.
- VM memory: 512–4096 KiB, including all active effect state.
- Execution: 0.25–4 ms for a complete animation frame. The same interrupt protects module loading
  and `onPress`.
- Drawing: 1–256 commands total per rendered frame across active effects.
- Lifetime: 16–5000 ms per effect and at most 16 simultaneous effects.

When a script exceeds a limit or raises an error, huBoard disables that animation instead of taking
down the keyboard.

## Sandbox

Each active theme owns an isolated Luau VM. Built-in libraries and the `motion` API are read-only.
huBoard removes filesystem, process, module-loading, debug, environment-mutation, and dynamic-code
globals. Scripts receive no network, Android, Java/JNI, microphone, clipboard, preferences, or input
connection API. Assets can only be addressed by names already declared in the installed theme.

The complete Nocturne Wisp example is
[`nocturne_wisp.luau`](../../app/src/main/assets/visual-themes/nocturne_wisp/assets/nocturne_wisp.luau).
