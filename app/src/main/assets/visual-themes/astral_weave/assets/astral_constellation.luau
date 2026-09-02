--!strict
-- Astral Weave is entirely theme-owned. huBoard supplies only the generic
-- Motion canvas; this script owns topology, timing, easing, and art direction.

type Point = {
    x: number,
    y: number,
    time: number,
}

type Segment = {
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    expiresAt: number,
}

type Candidate = {
    point: Point,
    dx: number,
    dy: number,
    distance: number,
    score: number,
    crossings: number,
}

type Link = {
    x: number,
    y: number,
    delay: number,
    intensity: number,
}

type Twinkle = {
    angle: number,
    distance: number,
    size: number,
    delay: number,
}

type ConstellationState = {
    x: number,
    y: number,
    radius: number,
    angle: number,
    starColor: number,
    lineColor: number,
    edgeColor: number,
    links: {Link},
    twinkles: {Twinkle},
}

local SESSION_GAP_SECONDS = 1.15
local HISTORY_LIFETIME_SECONDS = 1.45
local SEGMENT_LIFETIME_SECONDS = 1.55
local EFFECT_LIFETIME_SECONDS = 1.7
local LINE_FADE_DURATION_SECONDS = 0.9
local MAX_RECENT_POINTS = 6

local STAR_COLORS: {number} = {
    0xFFF5FCFF,
    0xFFF2F0FF,
    0xFFEAF5FF,
}

local LINE_COLORS: {number} = {
    0xFFB9E4FF,
    0xFFD0C7FF,
    0xFFAFCFFF,
}

local EDGE_COLORS: {number} = {
    0x001C91D8,
    0x006D4BC7,
    0x003B67D6,
}

local recent: {Point} = {}
local segments: {Segment} = {}
local lastPressTime = -1

local function clamp(value: number, minimum: number, maximum: number): number
    return math.max(minimum, math.min(maximum, value))
end

local function smoothstep(value: number): number
    local t = clamp(value, 0, 1)
    return t * t * (3 - 2 * t)
end

local function easeOutCubic(value: number): number
    local t = clamp(value, 0, 1)
    return 1 - (1 - t) ^ 3
end

local function random(seed: number, minimum: number, maximum: number): (number, number)
    local value: number
    value, seed = motion.random(seed, minimum, maximum)
    return value, seed
end

local function squaredDistance(x1: number, y1: number, x2: number, y2: number): number
    local dx = x2 - x1
    local dy = y2 - y1
    return dx * dx + dy * dy
end

local function cross(
    ax: number,
    ay: number,
    bx: number,
    by: number,
    cx: number,
    cy: number
): number
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
end

local function segmentsCross(
    ax: number,
    ay: number,
    bx: number,
    by: number,
    cx: number,
    cy: number,
    dx: number,
    dy: number
): boolean
    -- Shared constellation nodes are intentional joins, not visual crossings.
    local sharedEndpointDistance = 16
    if squaredDistance(ax, ay, cx, cy) <= sharedEndpointDistance
        or squaredDistance(ax, ay, dx, dy) <= sharedEndpointDistance
        or squaredDistance(bx, by, cx, cy) <= sharedEndpointDistance
        or squaredDistance(bx, by, dx, dy) <= sharedEndpointDistance
    then
        return false
    end

    local abC = cross(ax, ay, bx, by, cx, cy)
    local abD = cross(ax, ay, bx, by, dx, dy)
    local cdA = cross(cx, cy, dx, dy, ax, ay)
    local cdB = cross(cx, cy, dx, dy, bx, by)
    return ((abC > 0 and abD < 0) or (abC < 0 and abD > 0))
        and ((cdA > 0 and cdB < 0) or (cdA < 0 and cdB > 0))
end

local function pruneHistory(nowSeconds: number)
    if lastPressTime < 0
        or nowSeconds < lastPressTime
        or nowSeconds - lastPressTime > SESSION_GAP_SECONDS
    then
        table.clear(recent)
        table.clear(segments)
    else
        while #recent > 0 and nowSeconds - recent[1].time > HISTORY_LIFETIME_SECONDS do
            table.remove(recent, 1)
        end
        local activeSegments: {Segment} = {}
        for _, segment in segments do
            if segment.expiresAt > nowSeconds then
                table.insert(activeSegments, segment)
            end
        end
        segments = activeSegments
    end
    lastPressTime = nowSeconds
end

local function countCrossings(x: number, y: number, point: Point): number
    local result = 0
    for _, segment in segments do
        if segmentsCross(x, y, point.x, point.y, segment.x1, segment.y1, segment.x2, segment.y2) then
            result += 1
        end
    end
    return result
end

local function onPress(
    centerX: number,
    centerY: number,
    keyWidth: number,
    keyHeight: number,
    viewportWidth: number,
    viewportHeight: number,
    seed: number,
    nowSeconds: number
): ConstellationState
    pruneHistory(nowSeconds)

    -- A tiny offset keeps the result organic without disconnecting it from the key.
    local jitterX: number
    jitterX, seed = random(seed, -keyWidth * 0.025, keyWidth * 0.025)
    local jitterY: number
    jitterY, seed = random(seed, -keyHeight * 0.02, keyHeight * 0.02)
    local x = clamp(centerX + jitterX, 3, viewportWidth - 3)
    local y = clamp(centerY + jitterY, 3, viewportHeight - 3)

    local candidates: {Candidate} = {}
    local minimumDistance = math.min(keyWidth, keyHeight) * 0.38
    local maximumDistance = math.sqrt(viewportWidth ^ 2 + viewportHeight ^ 2) * 0.58
    for _, point in recent do
        local dx = point.x - x
        local dy = point.y - y
        local distance = math.sqrt(dx * dx + dy * dy)
        if distance >= minimumDistance and distance <= maximumDistance then
            local crossings = countCrossings(x, y, point)
            local age = math.max(0, nowSeconds - point.time)
            table.insert(candidates, {
                point = point,
                dx = dx,
                dy = dy,
                distance = distance,
                -- Prefer a nearby recent node, and strongly avoid line crossings.
                score = distance + age * keyHeight * 0.55 + crossings * viewportWidth * 0.8,
                crossings = crossings,
            })
        end
    end
    table.sort(candidates, function(a: Candidate, b: Candidate): boolean
        return a.score < b.score
    end)

    local links: {Link} = {}
    local primary = candidates[1]
    if primary ~= nil then
        table.insert(links, {
            x = primary.point.x,
            y = primary.point.y,
            delay = 0.04,
            intensity = 1,
        })
    end

    -- A restrained secondary branch occasionally adds a constellation shape. It
    -- must be crossing-free, close to the primary, and separated by at least 50°.
    local branchRoll: number
    branchRoll, seed = random(seed, 0, 1)
    if primary ~= nil and branchRoll < 0.24 and #candidates >= 3 then
        local bestBranch: Candidate? = nil
        for index = 2, #candidates do
            local candidate = candidates[index]
            local directionDot = (primary.dx * candidate.dx + primary.dy * candidate.dy)
                / (primary.distance * candidate.distance)
            if candidate.crossings == 0
                and candidate.distance <= primary.distance * 1.55
                and directionDot <= 0.643
                and (bestBranch == nil or candidate.score < bestBranch.score)
            then
                bestBranch = candidate
            end
        end
        if bestBranch ~= nil then
            table.insert(links, {
                x = bestBranch.point.x,
                y = bestBranch.point.y,
                delay = 0.1,
                intensity = 0.62,
            })
        end
    end

    for _, link in links do
        table.insert(segments, {
            x1 = link.x,
            y1 = link.y,
            x2 = x,
            y2 = y,
            expiresAt = nowSeconds + SEGMENT_LIFETIME_SECONDS,
        })
    end
    table.insert(recent, { x = x, y = y, time = nowSeconds })
    if #recent > MAX_RECENT_POINTS then
        table.remove(recent, 1)
    end

    local twinkles: {Twinkle} = {}
    for index = 1, 2 do
        local angle: number
        angle, seed = random(seed, 0, math.pi * 2)
        local distance: number
        distance, seed = random(seed, keyHeight * 0.2, keyHeight * 0.36)
        local size: number
        size, seed = random(seed, keyHeight * 0.009, keyHeight * 0.015)
        local delay: number
        delay, seed = random(seed, 0.11 + index * 0.035, 0.25 + index * 0.055)
        table.insert(twinkles, {
            angle = angle,
            distance = distance,
            size = size,
            delay = delay,
        })
    end

    local angle: number
    angle, seed = random(seed, -0.22, 0.22)
    local radius: number
    radius, seed = random(seed, keyHeight * 0.052, keyHeight * 0.062)
    local paletteValue: number
    paletteValue, seed = random(seed, 1, #STAR_COLORS + 0.999)
    local paletteIndex = math.min(#STAR_COLORS, math.floor(paletteValue))

    return {
        x = x,
        y = y,
        radius = radius,
        angle = angle,
        starColor = STAR_COLORS[paletteIndex],
        lineColor = LINE_COLORS[paletteIndex],
        edgeColor = EDGE_COLORS[paletteIndex],
        links = links,
        twinkles = twinkles,
    }
end

local function frame(
    state: ConstellationState,
    _dt: number,
    elapsed: number,
    _viewportWidth: number,
    _viewportHeight: number
): boolean
    local birth = easeOutCubic(elapsed / 0.12)
    local fade = 1 - smoothstep((elapsed - 1.02) / 0.64)
    local opacity = birth * fade

    for _, link in state.links do
        local reveal = smoothstep((elapsed - link.delay) / 0.29)
        if reveal > 0 then
            -- The light travels from the previous node into the newly pressed key.
            local endX = link.x + (state.x - link.x) * reveal
            local endY = link.y + (state.y - link.y) * reveal
            -- Hold after the reveal, then use a long linear fade. The former
            -- low-alpha smoothstep became imperceptible well before it ended.
            local lineFade = clamp(
                (EFFECT_LIFETIME_SECONDS - elapsed) / LINE_FADE_DURATION_SECONDS,
                0,
                1
            )
            local lineOpacity = lineFade * link.intensity
            local travel = math.sin(math.pi * reveal)
            -- Keep both layers after the reveal so the finished connection
            -- retains the same luminance until its final fade begins.
            motion.line(
                link.x,
                link.y,
                endX,
                endY,
                state.radius * 0.34,
                state.lineColor,
                lineOpacity * 0.13
            )
            motion.line(
                link.x,
                link.y,
                endX,
                endY,
                state.radius * 0.105,
                state.lineColor,
                lineOpacity * 0.5
            )

            local photon = travel * lineOpacity
            if photon > 0.015 then
                motion.glow(
                    endX,
                    endY,
                    state.radius * 0.72,
                    state.starColor,
                    state.edgeColor,
                    photon * 0.5
                )
                motion.circle(
                    endX,
                    endY,
                    state.radius * 0.075,
                    0xFFFFFFFF,
                    photon * 0.9
                )
            end
        end
    end

    local haloScale = 0.68 + birth * 0.32
    motion.glow(
        state.x,
        state.y,
        state.radius * 2.55 * haloScale,
        state.starColor,
        state.edgeColor,
        opacity * 0.3
    )
    motion.glow(
        state.x,
        state.y,
        state.radius * 0.92 * haloScale,
        0xFFFFFFFF,
        state.edgeColor,
        opacity * 0.82
    )
    motion.circle(state.x, state.y, state.radius * 0.15, 0xFFFFFFFF, opacity)

    -- A single non-looping glint gives the star a crisp birth without constant motion.
    local glintProgress = clamp((elapsed - 0.025) / 0.39, 0, 1)
    local glint = math.sin(math.pi * glintProgress) ^ 1.6 * fade
    if glint > 0.01 then
        local cosine = math.cos(state.angle)
        local sine = math.sin(state.angle)
        local longRay = state.radius * (0.72 + glint * 1.38)
        local shortRay = state.radius * (0.48 + glint * 0.82)
        motion.line(
            state.x - cosine * longRay,
            state.y - sine * longRay,
            state.x + cosine * longRay,
            state.y + sine * longRay,
            state.radius * 0.075,
            state.starColor,
            glint * 0.72
        )
        motion.line(
            state.x + sine * shortRay,
            state.y - cosine * shortRay,
            state.x - sine * shortRay,
            state.y + cosine * shortRay,
            state.radius * 0.065,
            state.starColor,
            glint * 0.58
        )
    end

    for _, twinkle in state.twinkles do
        local progress = clamp((elapsed - twinkle.delay) / 0.34, 0, 1)
        local twinkleOpacity = math.sin(math.pi * progress) ^ 2 * fade
        if twinkleOpacity > 0.01 then
            local x = state.x + math.cos(twinkle.angle) * twinkle.distance
            local y = state.y + math.sin(twinkle.angle) * twinkle.distance
            motion.circle(x, y, twinkle.size, state.starColor, twinkleOpacity * 0.78)
        end
    end

    return elapsed < EFFECT_LIFETIME_SECONDS
end

return {
    onPress = onPress,
    frame = frame,
}
