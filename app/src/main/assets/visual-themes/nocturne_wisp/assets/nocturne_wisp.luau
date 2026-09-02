--!strict
-- Nocturne Wisp is intentionally ordinary huBoard Motion API configuration.
-- The engine contains no wisp, particle, gravity, or trail implementation.

type TrailPoint = {
    x: number,
    y: number,
}

type WispState = {
    x: number,
    y: number,
    vx: number,
    vy: number,
    gravity: number,
    sway: number,
    frequency: number,
    phase: number,
    radius: number,
    trailClock: number,
    trail: {TrailPoint},
}

local CORE = 0xFFFFFFFF
local HALO = 0x0091D8FF
local TRAIL_CORE = 0xBFD7F5FF
local TRAIL_EDGE = 0x0058BFFF

local function random(seed: number, minimum: number, maximum: number): (number, number)
    local value: number
    value, seed = motion.random(seed, minimum, maximum)
    return value, seed
end

local function onPress(
    centerX: number,
    centerY: number,
    _keyWidth: number,
    keyHeight: number,
    viewportWidth: number,
    _viewportHeight: number,
    seed: number
): WispState
    local angle: number
    if centerX < viewportWidth * 0.25 then
        angle, seed = random(seed, -1.42, -0.55)
    elseif centerX > viewportWidth * 0.75 then
        angle, seed = random(seed, -2.59, -1.72)
    else
        angle, seed = random(seed, -2.40, -0.74)
    end

    local speed: number
    speed, seed = random(seed, keyHeight * 3.4, keyHeight * 4.4)
    local gravity: number
    gravity, seed = random(seed, keyHeight * 4.7, keyHeight * 5.5)
    local sway: number
    sway, seed = random(seed, keyHeight * 0.34, keyHeight * 0.58)
    local frequency: number
    frequency, seed = random(seed, 4.2, 7.0)
    local phase: number
    phase, seed = random(seed, 0, math.pi * 2)
    local radius: number
    radius, seed = random(seed, keyHeight * 0.115, keyHeight * 0.15)

    return {
        x = centerX,
        y = centerY,
        vx = math.cos(angle) * speed,
        vy = math.sin(angle) * speed,
        gravity = gravity,
        sway = sway,
        frequency = frequency,
        phase = phase,
        radius = radius,
        trailClock = 0,
        trail = {},
    }
end

local function frame(
    state: WispState,
    dt: number,
    elapsed: number,
    viewportWidth: number,
    viewportHeight: number
): boolean
    state.vx += math.sin(elapsed * state.frequency + state.phase) * state.sway * dt
    state.vx *= math.exp(-0.12 * dt)
    state.vy += state.gravity * dt
    state.x += state.vx * dt
    state.y += state.vy * dt

    local edge = state.radius * 0.55
    if state.x < edge then
        state.x = edge
        state.vx = math.abs(state.vx) * 0.72
    elseif state.x > viewportWidth - edge then
        state.x = viewportWidth - edge
        state.vx = -math.abs(state.vx) * 0.72
    end

    state.trailClock += dt
    if state.trailClock >= 0.026 then
        state.trailClock = 0
        table.insert(state.trail, { x = state.x, y = state.y })
        if #state.trail > 9 then
            table.remove(state.trail, 1)
        end
    end

    local fadeIn = math.min(1, elapsed / 0.09)
    local fadeOut = math.min(1, math.max(0, (3.5 - elapsed) / 0.42))
    local opacity = fadeIn * fadeOut
    local count = #state.trail
    for index, point in state.trail do
        local progress = index / math.max(1, count)
        motion.glow(
            point.x,
            point.y,
            state.radius * (0.28 + progress * 0.42),
            TRAIL_CORE,
            TRAIL_EDGE,
            opacity * progress * 0.25
        )
    end

    local pulse = 1 + math.sin(elapsed * 10.5 + state.phase) * 0.075
    motion.glow(state.x, state.y, state.radius * 2.35 * pulse, CORE, HALO, opacity * 0.72)
    motion.glow(state.x, state.y, state.radius * pulse, CORE, HALO, opacity)
    motion.circle(state.x, state.y, state.radius * 0.20, CORE, opacity)

    return elapsed < 3.5 and state.y < viewportHeight + state.radius * 2.5
end

return {
    onPress = onPress,
    frame = frame,
}
