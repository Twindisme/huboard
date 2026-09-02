# Nocturne Wisp

`Nocturne Wisp` is a bundled, directly packageable schema-1 theme in
`app/src/main/assets/visual-themes/nocturne_wisp`.

Its keypress effect is an ordinary huBoard Motion API 1 Luau script. The script chooses a bounded
random launch direction, integrates velocity, gravity, drag, and sinusoidal sway, steers away from
the side edges, and draws its own glow and trail. No wisp-specific behavior exists in huBoard.

The source is kept in `assets/nocturne_wisp.luau`, making this bundled theme a directly inspectable
example of procedural motion. It receives only key geometry, viewport geometry, time, and a random
seed; it cannot access typed characters or Android services. Unlike the old per-key Lottie box, the
script draws into the complete keyboard viewport and can leave through its real bottom edge.
