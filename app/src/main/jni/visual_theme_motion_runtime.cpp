/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <new>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "lua.h"
#include "lualib.h"
#include "luacode.h"

namespace {

constexpr int kCommandStride = 10;
constexpr float kCircleCommand = 1.0f;
constexpr float kGlowCommand = 2.0f;
constexpr float kImageCommand = 3.0f;
constexpr float kLineCommand = 4.0f;
constexpr float kRoundedRectCommand = 5.0f;

struct MemoryLimit {
    size_t used = 0;
    size_t limit = 0;
};

struct Effect {
    int stateReference = LUA_NOREF;
    int64_t startTimeMs = 0;
    int64_t lastFrameTimeMs = 0;
};

struct MotionRuntime {
    MemoryLimit memory;
    lua_State* state = nullptr;
    int onPressReference = LUA_NOREF;
    int frameReference = LUA_NOREF;
    std::unordered_map<std::string, int> assetIndices;
    std::vector<Effect> effects;
    std::vector<float> commands;
    std::string lastError;
    std::chrono::steady_clock::time_point deadline;
    int maxDrawCommands = 0;
    int frameBudgetMicros = 0;
    int64_t maximumDurationMs = 0;
    int maxEffects = 0;
    bool deadlineActive = false;
    bool disabled = false;
};

void* limitedAllocate(void* userData, void* pointer, size_t oldSize, size_t newSize) {
    auto* memory = static_cast<MemoryLimit*>(userData);
    if (newSize == 0) {
        std::free(pointer);
        memory->used = oldSize <= memory->used ? memory->used - oldSize : 0;
        return nullptr;
    }

    const size_t growth = newSize > oldSize ? newSize - oldSize : 0;
    if (growth > memory->limit - std::min(memory->used, memory->limit)) {
        return nullptr;
    }

    void* result = std::realloc(pointer, newSize);
    if (result != nullptr) {
        if (newSize >= oldSize) {
            memory->used += newSize - oldSize;
        } else {
            memory->used -= std::min(memory->used, oldSize - newSize);
        }
    }
    return result;
}

MotionRuntime* runtime(lua_State* state) {
    return static_cast<MotionRuntime*>(lua_callbacks(state)->userdata);
}

void interruptScript(lua_State* state, int garbageCollectionState) {
    if (garbageCollectionState >= 0) {
        return;
    }
    MotionRuntime* motion = runtime(state);
    if (motion != nullptr && motion->deadlineActive &&
            std::chrono::steady_clock::now() >= motion->deadline) {
        luaL_error(state, "huBoard Motion frame exceeded its execution budget");
    }
}

double finiteNumber(lua_State* state, int argument) {
    const double value = luaL_checknumber(state, argument);
    if (!std::isfinite(value)) {
        luaL_error(state, "huBoard Motion drawing values must be finite");
    }
    return value;
}

float finiteFloat(lua_State* state, int argument) {
    const double value = finiteNumber(state, argument);
    if (std::abs(value) > 1000000.0) {
        luaL_error(state, "huBoard Motion drawing value is outside the supported range");
    }
    return static_cast<float>(value);
}

float optionalFiniteFloat(lua_State* state, int argument, double fallback) {
    const double value = luaL_optnumber(state, argument, fallback);
    if (!std::isfinite(value) || std::abs(value) > 1000000.0) {
        luaL_error(state, "huBoard Motion drawing value is outside the supported range");
    }
    return static_cast<float>(value);
}

float colorBits(lua_State* state, int argument) {
    const uint32_t color = static_cast<uint32_t>(luaL_checkunsigned(state, argument));
    float encoded = 0.0f;
    static_assert(sizeof(encoded) == sizeof(color), "Color command encoding changed");
    std::memcpy(&encoded, &color, sizeof(encoded));
    return encoded;
}

void appendCommand(lua_State* state, const float* command) {
    MotionRuntime* motion = runtime(state);
    if (motion == nullptr || motion->disabled) {
        luaL_error(state, "huBoard Motion runtime is unavailable");
    }
    if (motion->commands.size() / kCommandStride >=
            static_cast<size_t>(motion->maxDrawCommands)) {
        luaL_error(state, "huBoard Motion exceeded its draw-command budget");
    }
    motion->commands.insert(motion->commands.end(), command, command + kCommandStride);
}

int drawCircle(lua_State* state) {
    const float command[kCommandStride] = {
        kCircleCommand,
        finiteFloat(state, 1),
        finiteFloat(state, 2),
        finiteFloat(state, 3),
        0.0f,
        0.0f,
        optionalFiniteFloat(state, 5, 1.0),
        colorBits(state, 4),
        0.0f,
        0.0f,
    };
    appendCommand(state, command);
    return 0;
}

int drawGlow(lua_State* state) {
    const float command[kCommandStride] = {
        kGlowCommand,
        finiteFloat(state, 1),
        finiteFloat(state, 2),
        finiteFloat(state, 3),
        0.0f,
        0.0f,
        optionalFiniteFloat(state, 6, 1.0),
        colorBits(state, 4),
        colorBits(state, 5),
        0.0f,
    };
    appendCommand(state, command);
    return 0;
}

int drawImage(lua_State* state) {
    MotionRuntime* motion = runtime(state);
    const char* asset = luaL_checkstring(state, 1);
    const auto found = motion->assetIndices.find(asset);
    if (found == motion->assetIndices.end()) {
        luaL_error(state, "huBoard Motion image references an undeclared asset");
        return 0;
    }
    const float command[kCommandStride] = {
        kImageCommand,
        finiteFloat(state, 2),
        finiteFloat(state, 3),
        finiteFloat(state, 4),
        finiteFloat(state, 5),
        optionalFiniteFloat(state, 6, 0.0),
        optionalFiniteFloat(state, 7, 1.0),
        static_cast<float>(found->second),
        0.0f,
        0.0f,
    };
    appendCommand(state, command);
    return 0;
}

int drawLine(lua_State* state) {
    const float command[kCommandStride] = {
        kLineCommand,
        finiteFloat(state, 1),
        finiteFloat(state, 2),
        finiteFloat(state, 3),
        finiteFloat(state, 4),
        finiteFloat(state, 5),
        optionalFiniteFloat(state, 7, 1.0),
        colorBits(state, 6),
        0.0f,
        0.0f,
    };
    appendCommand(state, command);
    return 0;
}

int drawRoundedRect(lua_State* state) {
    const float command[kCommandStride] = {
        kRoundedRectCommand,
        finiteFloat(state, 1),
        finiteFloat(state, 2),
        finiteFloat(state, 3),
        finiteFloat(state, 4),
        finiteFloat(state, 5),
        optionalFiniteFloat(state, 7, 1.0),
        colorBits(state, 6),
        0.0f,
        0.0f,
    };
    appendCommand(state, command);
    return 0;
}

int randomRange(lua_State* state) {
    const uint32_t seed = static_cast<uint32_t>(luaL_checkunsigned(state, 1));
    const double minimum = finiteNumber(state, 2);
    const double maximum = finiteNumber(state, 3);
    if (minimum > maximum) {
        luaL_error(state, "huBoard Motion random minimum exceeds maximum");
        return 0;
    }
    const uint32_t next = seed * 1664525u + 1013904223u;
    const double unit = static_cast<double>(next >> 8u) / 16777216.0;
    const double value = minimum + (maximum - minimum) * unit;
    if (!std::isfinite(value)) {
        luaL_error(state, "huBoard Motion random range is too large");
    }
    lua_pushnumber(state, value);
    lua_pushnumber(state, static_cast<double>(next));
    return 2;
}

void setFunction(lua_State* state, const char* name, lua_CFunction function) {
    lua_pushcfunction(state, function, name);
    lua_setfield(state, -2, name);
}

void installMotionApi(lua_State* state) {
    lua_createtable(state, 0, 6);
    setFunction(state, "circle", drawCircle);
    setFunction(state, "glow", drawGlow);
    setFunction(state, "image", drawImage);
    setFunction(state, "line", drawLine);
    setFunction(state, "roundedRect", drawRoundedRect);
    setFunction(state, "random", randomRange);
    lua_setreadonly(state, -1, true);
    lua_setglobal(state, "motion");
}

void removeGlobal(lua_State* state, const char* name) {
    lua_pushnil(state);
    lua_setglobal(state, name);
}

std::string stackError(lua_State* state, const char* fallback) {
    const char* error = lua_tostring(state, -1);
    return error != nullptr ? error : fallback;
}

void beginBudget(MotionRuntime* motion) {
    motion->deadline = std::chrono::steady_clock::now() +
            std::chrono::microseconds(motion->frameBudgetMicros);
    motion->deadlineActive = true;
}

void endBudget(MotionRuntime* motion) {
    motion->deadlineActive = false;
}

void clearEffects(MotionRuntime* motion) {
    if (motion->state != nullptr) {
        for (const Effect& effect : motion->effects) {
            lua_unref(motion->state, effect.stateReference);
        }
    }
    motion->effects.clear();
}

void disableWithStackError(MotionRuntime* motion, const char* fallback) {
    motion->lastError = stackError(motion->state, fallback);
    motion->disabled = true;
    motion->commands.clear();
    clearEffects(motion);
    lua_settop(motion->state, 0);
    endBudget(motion);
}

void throwIllegalArgument(JNIEnv* environment, const std::string& message) {
    jclass exception = environment->FindClass("java/lang/IllegalArgumentException");
    if (exception != nullptr) {
        environment->ThrowNew(exception, message.c_str());
        environment->DeleteLocalRef(exception);
    }
}

MotionRuntime* fromHandle(jlong handle) {
    return reinterpret_cast<MotionRuntime*>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeCreate(
        JNIEnv* environment, jclass, jbyteArray sourceArray, jobjectArray assetNames,
        jint maxMemoryBytes, jint maxDrawCommands, jint frameBudgetMicros,
        jlong maximumDurationMs, jint maxEffects) {
    if (maxMemoryBytes < 512 * 1024 || maxMemoryBytes > 4 * 1024 * 1024 ||
            maxDrawCommands < 1 || maxDrawCommands > 256 ||
            frameBudgetMicros < 250 || frameBudgetMicros > 4000 ||
            maximumDurationMs < 16 || maximumDurationMs > 5000 ||
            maxEffects < 1 || maxEffects > 16) {
        throwIllegalArgument(environment, "Invalid huBoard Motion resource limits");
        return 0;
    }
    auto* motion = new (std::nothrow) MotionRuntime();
    if (motion == nullptr) {
        throwIllegalArgument(environment, "Could not allocate huBoard Motion runtime");
        return 0;
    }
    motion->memory.limit = static_cast<size_t>(maxMemoryBytes);
    motion->maxDrawCommands = maxDrawCommands;
    motion->frameBudgetMicros = frameBudgetMicros;
    motion->maximumDurationMs = maximumDurationMs;
    motion->maxEffects = maxEffects;

    motion->state = lua_newstate(limitedAllocate, &motion->memory);
    if (motion->state == nullptr) {
        delete motion;
        throwIllegalArgument(environment, "Could not initialize huBoard Motion runtime");
        return 0;
    }
    lua_callbacks(motion->state)->userdata = motion;
    lua_callbacks(motion->state)->interrupt = interruptScript;
    luaL_openlibs(motion->state);
    installMotionApi(motion->state);
    for (const char* name : {"io", "package", "os", "debug", "require", "dofile",
            "loadfile", "loadstring", "collectgarbage", "getfenv", "setfenv", "newproxy"}) {
        removeGlobal(motion->state, name);
    }
    luaL_sandbox(motion->state);

    const jsize assetCount = environment->GetArrayLength(assetNames);
    for (jsize index = 0; index < assetCount; ++index) {
        auto name = static_cast<jstring>(environment->GetObjectArrayElement(assetNames, index));
        const char* utfName = environment->GetStringUTFChars(name, nullptr);
        if (utfName != nullptr) {
            motion->assetIndices.emplace(utfName, index);
            environment->ReleaseStringUTFChars(name, utfName);
        }
        environment->DeleteLocalRef(name);
    }

    const jsize sourceLength = environment->GetArrayLength(sourceArray);
    jbyte* source = environment->GetByteArrayElements(sourceArray, nullptr);
    if (source == nullptr) {
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, "Could not read huBoard Motion script");
        return 0;
    }
    size_t bytecodeSize = 0;
    char* bytecode = luau_compile(
            reinterpret_cast<const char*>(source), static_cast<size_t>(sourceLength),
            nullptr, &bytecodeSize);
    environment->ReleaseByteArrayElements(sourceArray, source, JNI_ABORT);
    if (bytecode == nullptr) {
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, "Could not compile huBoard Motion script");
        return 0;
    }

    const int loadStatus = luau_load(
            motion->state, "@theme/key_press.luau", bytecode, bytecodeSize, 0);
    std::free(bytecode);
    if (loadStatus != LUA_OK) {
        const std::string error = stackError(motion->state, "Invalid huBoard Motion script");
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, error);
        return 0;
    }

    beginBudget(motion);
    const int executionStatus = lua_pcall(motion->state, 0, 1, 0);
    endBudget(motion);
    if (executionStatus != LUA_OK || !lua_istable(motion->state, -1)) {
        const std::string error = executionStatus == LUA_OK
                ? "huBoard Motion script must return a table"
                : stackError(motion->state, "Could not initialize huBoard Motion script");
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, error);
        return 0;
    }

    lua_getfield(motion->state, -1, "onPress");
    if (!lua_isfunction(motion->state, -1)) {
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, "huBoard Motion script needs an onPress function");
        return 0;
    }
    motion->onPressReference = lua_ref(motion->state, -1);
    lua_pop(motion->state, 1);

    lua_getfield(motion->state, -1, "frame");
    if (!lua_isfunction(motion->state, -1)) {
        lua_close(motion->state);
        delete motion;
        throwIllegalArgument(environment, "huBoard Motion script needs a frame function");
        return 0;
    }
    motion->frameReference = lua_ref(motion->state, -1);
    lua_settop(motion->state, 0);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(motion));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeStart(
        JNIEnv*, jclass, jlong handle, jfloat centerX, jfloat centerY, jfloat keyWidth,
        jfloat keyHeight, jfloat viewportWidth, jfloat viewportHeight, jlong nowMs, jint seed) {
    MotionRuntime* motion = fromHandle(handle);
    if (motion == nullptr || motion->disabled) {
        return JNI_FALSE;
    }
    if (motion->effects.size() >= static_cast<size_t>(motion->maxEffects)) {
        lua_unref(motion->state, motion->effects.front().stateReference);
        motion->effects.erase(motion->effects.begin());
    }

    lua_settop(motion->state, 0);
    lua_getref(motion->state, motion->onPressReference);
    lua_pushnumber(motion->state, centerX);
    lua_pushnumber(motion->state, centerY);
    lua_pushnumber(motion->state, keyWidth);
    lua_pushnumber(motion->state, keyHeight);
    lua_pushnumber(motion->state, viewportWidth);
    lua_pushnumber(motion->state, viewportHeight);
    lua_pushnumber(motion->state, static_cast<uint32_t>(seed));
    lua_pushnumber(motion->state, static_cast<double>(nowMs) / 1000.0);
    beginBudget(motion);
    const int status = lua_pcall(motion->state, 8, 1, 0);
    endBudget(motion);
    if (status != LUA_OK) {
        disableWithStackError(motion, "huBoard Motion onPress failed");
        return JNI_FALSE;
    }
    if (!lua_istable(motion->state, -1)) {
        motion->lastError = "huBoard Motion onPress must return a state table";
        motion->disabled = true;
        lua_settop(motion->state, 0);
        return JNI_FALSE;
    }
    const int stateReference = lua_ref(motion->state, -1);
    lua_settop(motion->state, 0);
    motion->effects.push_back({stateReference, nowMs, nowMs});
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeFrame(
        JNIEnv* environment, jclass, jlong handle, jlong nowMs,
        jfloat viewportWidth, jfloat viewportHeight) {
    MotionRuntime* motion = fromHandle(handle);
    if (motion == nullptr || motion->disabled) {
        return environment->NewFloatArray(0);
    }

    motion->commands.clear();
    beginBudget(motion);
    auto effect = motion->effects.begin();
    while (effect != motion->effects.end()) {
        const int64_t elapsedMs = std::max<int64_t>(0, nowMs - effect->startTimeMs);
        if (elapsedMs > motion->maximumDurationMs) {
            lua_unref(motion->state, effect->stateReference);
            effect = motion->effects.erase(effect);
            continue;
        }
        const int64_t deltaMs = std::clamp<int64_t>(nowMs - effect->lastFrameTimeMs, 0, 50);
        effect->lastFrameTimeMs = nowMs;

        lua_settop(motion->state, 0);
        lua_getref(motion->state, motion->frameReference);
        lua_getref(motion->state, effect->stateReference);
        lua_pushnumber(motion->state, static_cast<double>(deltaMs) / 1000.0);
        lua_pushnumber(motion->state, static_cast<double>(elapsedMs) / 1000.0);
        lua_pushnumber(motion->state, viewportWidth);
        lua_pushnumber(motion->state, viewportHeight);
        const int status = lua_pcall(motion->state, 5, 1, 0);
        if (status != LUA_OK) {
            disableWithStackError(motion, "huBoard Motion frame failed");
            return environment->NewFloatArray(0);
        }
        if (!lua_isboolean(motion->state, -1)) {
            motion->lastError = "huBoard Motion frame must return a boolean";
            motion->disabled = true;
            motion->commands.clear();
            clearEffects(motion);
            lua_settop(motion->state, 0);
            endBudget(motion);
            return environment->NewFloatArray(0);
        }
        const bool alive = lua_toboolean(motion->state, -1);
        lua_settop(motion->state, 0);
        if (!alive) {
            lua_unref(motion->state, effect->stateReference);
            effect = motion->effects.erase(effect);
        } else {
            ++effect;
        }
    }
    endBudget(motion);

    jfloatArray result = environment->NewFloatArray(
            static_cast<jsize>(motion->commands.size()));
    if (result != nullptr && !motion->commands.empty()) {
        environment->SetFloatArrayRegion(
                result, 0, static_cast<jsize>(motion->commands.size()), motion->commands.data());
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeHasEffects(
        JNIEnv*, jclass, jlong handle) {
    MotionRuntime* motion = fromHandle(handle);
    return motion != nullptr && !motion->disabled && !motion->effects.empty();
}

extern "C" JNIEXPORT void JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeClear(
        JNIEnv*, jclass, jlong handle) {
    MotionRuntime* motion = fromHandle(handle);
    if (motion != nullptr) {
        clearEffects(motion);
        motion->commands.clear();
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeLastError(
        JNIEnv* environment, jclass, jlong handle) {
    MotionRuntime* motion = fromHandle(handle);
    return environment->NewStringUTF(
            motion == nullptr ? "huBoard Motion runtime was closed" : motion->lastError.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_helium314_keyboard_theme_VisualThemeMotionNative_nativeClose(
        JNIEnv*, jclass, jlong handle) {
    MotionRuntime* motion = fromHandle(handle);
    if (motion == nullptr) {
        return;
    }
    clearEffects(motion);
    if (motion->state != nullptr) {
        lua_close(motion->state);
    }
    delete motion;
}
