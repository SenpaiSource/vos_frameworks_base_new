/*
 * Copyright (C) 2024-2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.vibrator;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.vibrator.IVibrator;
import android.os.Handler;
import android.os.IVibratorStateListener;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.RichTapVibrationEffect;
import android.os.Trace;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.util.IndentingPrintWriter;
import android.util.IntArray;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;

/**
 * HalVibrator decorator for AAC RichTap hardware extensions.
 * Intercepts predefined vibration effects and composition primitives to trigger native
 * AAC/AW8697 waveform effects directly via IRichtapVibrator / IOplusVibrator AIDL.
 */
final class RichTapHalVibrator implements HalVibrator {
    private static final String TAG = "RichTapHalVibrator";

    private final HalVibrator mDelegate;
    private final Handler mHandler;
    private final RichTapVibratorService mRichTap;
    private final int mVibratorId;
    private final Object mLock = new Object();

    private final RemoteCallbackList<IVibratorStateListener> mListeners =
            new RemoteCallbackList<>();

    @GuardedBy("mLock")
    private Object mDispatchToken = null;
    @GuardedBy("mLock")
    private boolean mRichTapVibrating = false;
    @GuardedBy("mLock")
    private long mRichTapDuration = 0;
    @GuardedBy("mLock")
    private float mRichTapAmplitude = 1.0f;

    private Callbacks mCallbacks;
    private volatile VibratorInfo mCachedInfo;
    private volatile VibratorInfo mCachedBaseInfo;

    static HalVibrator wrapIfNeeded(
            @Nullable Context context, @NonNull HalVibrator delegate, @NonNull Handler handler) {
        if (context != null) {
            boolean enabled = context.getResources().getBoolean(
                    R.bool.config_usesRichtapVibration);
            if (!enabled) {
                return delegate;
            }
        }
        Slog.i(TAG, "Wrapping HalVibrator with RichTapHalVibrator");
        return new RichTapHalVibrator(delegate, handler, new RichTapVibratorService());
    }

    RichTapHalVibrator(
            @NonNull HalVibrator delegate,
            @NonNull Handler handler,
            @NonNull RichTapVibratorService richTap) {
        mDelegate = delegate;
        mHandler = handler;
        mRichTap = richTap;
        mVibratorId = delegate.getInfo().getId();
    }

    @Override
    public void init(@NonNull Callbacks callbacks) {
        mCallbacks = callbacks;
        mDelegate.init(callbacks);
        mRichTap.init();
    }

    @Override
    public void onSystemReady() {
        mDelegate.onSystemReady();
        mRichTap.init();
    }

    @NonNull
    @Override
    public VibratorInfo getInfo() {
        VibratorInfo baseInfo = mDelegate.getInfo();
        if (mCachedInfo == null || mCachedBaseInfo != baseInfo) {
            mCachedInfo = buildRichTapVibratorInfo(baseInfo);
            mCachedBaseInfo = baseInfo;
        }
        return mCachedInfo;
    }

    private VibratorInfo buildRichTapVibratorInfo(VibratorInfo baseInfo) {
        VibratorInfo.Builder builder = new VibratorInfo.Builder(baseInfo.getId());
        long caps = baseInfo.getCapabilities()
                | IVibrator.CAP_COMPOSE_EFFECTS | IVibrator.CAP_AMPLITUDE_CONTROL;
        builder.setCapabilities(caps);

        IntArray effects = new IntArray();
        SparseBooleanArray baseEffects = baseInfo.getSupportedEffects();
        if (baseEffects != null) {
            for (int i = 0; i < baseEffects.size(); i++) {
                if (baseEffects.valueAt(i)) {
                    effects.add(baseEffects.keyAt(i));
                }
            }
        }
        for (int effectId : RichTapVibrationEffect.ALL_OOS_EFFECTS) {
            effects.add(effectId);
        }
        effects.add(VibrationEffect.EFFECT_CLICK);
        effects.add(VibrationEffect.EFFECT_DOUBLE_CLICK);
        effects.add(VibrationEffect.EFFECT_TICK);
        effects.add(VibrationEffect.EFFECT_THUD);
        effects.add(VibrationEffect.EFFECT_POP);
        effects.add(VibrationEffect.EFFECT_HEAVY_CLICK);
        effects.add(VibrationEffect.EFFECT_TEXTURE_TICK);
        builder.setSupportedEffects(effects.toArray());

        SparseIntArray primitives = baseInfo.getSupportedPrimitives();
        if (primitives != null) {
            for (int i = 0; i < primitives.size(); i++) {
                int key = primitives.keyAt(i);
                if (key != VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                    builder.setSupportedPrimitive(key, primitives.valueAt(i));
                }
            }
        }
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 22);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 30);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 22);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 10);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 40);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 16);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 12);

        builder.setPrimitiveDelayMax(Math.max(baseInfo.getPrimitiveDelayMax(), 5000));
        builder.setCompositionSizeMax(Math.max(baseInfo.getCompositionSizeMax(), 100));
        builder.setQFactor(baseInfo.getQFactor());
        if (baseInfo.getFrequencyProfileLegacy() != null) {
            builder.setFrequencyProfileLegacy(baseInfo.getFrequencyProfileLegacy());
        }
        if (baseInfo.getFrequencyProfile() != null) {
            builder.setFrequencyProfile(baseInfo.getFrequencyProfile());
        }
        builder.setMaxEnvelopeEffectSize(baseInfo.getMaxEnvelopeEffectSize());
        builder.setMinEnvelopeEffectControlPointDurationMillis(
                baseInfo.getMinEnvelopeEffectControlPointDurationMillis());
        builder.setMaxEnvelopeEffectControlPointDurationMillis(
                baseInfo.getMaxEnvelopeEffectControlPointDurationMillis());
        return builder.build();
    }

    @Override
    public boolean isVibrating() {
        synchronized (mLock) {
            return mRichTapVibrating || mDelegate.isVibrating();
        }
    }

    @Override
    public boolean registerVibratorStateListener(@NonNull IVibratorStateListener listener) {
        boolean registered;
        boolean currentlyVibrating;
        synchronized (mLock) {
            registered = mListeners.register(listener);
            currentlyVibrating = isVibrating();
        }
        if (!registered) {
            return false;
        }
        mDelegate.registerVibratorStateListener(listener);
        notifyStateListener(listener, currentlyVibrating);
        return true;
    }

    @Override
    public boolean unregisterVibratorStateListener(@NonNull IVibratorStateListener listener) {
        mDelegate.unregisterVibratorStateListener(listener);
        synchronized (mLock) {
            return mListeners.unregister(listener);
        }
    }

    private void notifyStateListener(IVibratorStateListener listener, boolean isVibrating) {
        try {
            listener.onVibrating(isVibrating);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Vibrator state listener failed to call", e);
        }
    }

    @GuardedBy("mLock")
    private void setRichTapVibratingLocked(boolean vibrating, float amplitude, long duration) {
        mRichTapDuration = duration;
        boolean previousOverall = isVibrating();
        mRichTapVibrating = vibrating;
        mRichTapAmplitude = amplitude;
        boolean currentOverall = isVibrating();
        if (previousOverall != currentOverall) {
            mListeners.broadcast(listener -> notifyStateListener(listener, currentOverall));
        }
    }

    @Override
    public float getCurrentAmplitude() {
        synchronized (mLock) {
            if (mRichTapVibrating) {
                return mRichTapAmplitude;
            }
        }
        return mDelegate.getCurrentAmplitude();
    }

    @Override
    public boolean setExternalControl(boolean externalControl) {
        return mDelegate.setExternalControl(externalControl);
    }

    @Override
    public boolean setAlwaysOn(int id, @Nullable PrebakedSegment prebaked) {
        return mDelegate.setAlwaysOn(id, prebaked);
    }

    @Override
    public boolean setAmplitude(float amplitude) {
        synchronized (mLock) {
            mRichTapAmplitude = amplitude;
            mRichTap.richTapVibratorSetAmplitude((int) (amplitude * 255));
        }
        return mDelegate.setAmplitude(amplitude);
    }

    @Override
    public long on(long vibrationId, long stepId, long milliseconds) {
        if (!mRichTap.isAvailable()) {
            return mDelegate.on(vibrationId, stepId, milliseconds);
        }
        Trace.traceBegin(Trace.TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onTimed");
        try {
            Object token = new Object();
            mRichTap.richTapVibratorOn(milliseconds);
            synchronized (mLock) {
                if (mDispatchToken != null) {
                    mHandler.removeCallbacksAndMessages(mDispatchToken);
                }
                mDispatchToken = token;
                setRichTapVibratingLocked(true, mRichTapAmplitude, milliseconds);
            }
            scheduleCompletion(token, vibrationId, stepId, milliseconds);
            return milliseconds;
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, VibrationEffect.VendorEffect vendorEffect) {
        return mDelegate.on(vibrationId, stepId, vendorEffect);
    }

    @Override
    public long on(long vibrationId, long stepId, PrebakedSegment prebaked) {
        Trace.traceBegin(Trace.TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onPrebaked");
        try {
            int effectId = prebaked.getEffectId();
            int mappedEffectId = mapToRichTapEffect(effectId);
            if (mappedEffectId >= 0 && mRichTap.isAvailable()) {
                byte strength = (byte) RichTapVibrationEffect.getInnerEffectStrength(
                        prebaked.getEffectStrength());
                int result = mRichTap.richTapPerform(mappedEffectId, strength);
                long duration = RichTapVibrationEffect.getInnerEffectDuration(mappedEffectId);
                if (result >= 0) {
                    Object token = new Object();
                    synchronized (mLock) {
                        if (mDispatchToken != null) {
                            mHandler.removeCallbacksAndMessages(mDispatchToken);
                        }
                        mDispatchToken = token;
                        setRichTapVibratingLocked(true, (float) strength / 99f, duration);
                    }
                    scheduleCompletion(token, vibrationId, stepId, duration);
                    return duration;
                }
            }
            return mDelegate.on(vibrationId, stepId, prebaked);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, PrimitiveSegment[] primitives) {
        Trace.traceBegin(Trace.TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onPrimitives");
        try {
            if (primitives == null || primitives.length == 0 || !mRichTap.isAvailable()) {
                return mDelegate.on(vibrationId, stepId, primitives);
            }

            // If all primitives are LOW_TICK (gesture bar touch rumble), drop them cleanly
            // with a 1ms completion callback to prevent conductor stalls and click artifacts.
            boolean hasNonLowTick = false;
            for (PrimitiveSegment p : primitives) {
                if (p.getPrimitiveId() != VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                    hasNonLowTick = true;
                    break;
                }
            }
            if (!hasNonLowTick) {
                Object token = new Object();
                synchronized (mLock) {
                    if (mDispatchToken != null) {
                        mHandler.removeCallbacksAndMessages(mDispatchToken);
                    }
                    mDispatchToken = token;
                    setRichTapVibratingLocked(false, 0f, 1L);
                }
                scheduleCompletion(token, vibrationId, stepId, 1L);
                return 1L;
            }

            if (primitives.length == 1) {
                PrimitiveSegment primitive = primitives[0];
                int primId = primitive.getPrimitiveId();
                if (primId == VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                    Object token = new Object();
                    synchronized (mLock) {
                        if (mDispatchToken != null) {
                            mHandler.removeCallbacksAndMessages(mDispatchToken);
                        }
                        mDispatchToken = token;
                        setRichTapVibratingLocked(false, 0f, 1L);
                    }
                    scheduleCompletion(token, vibrationId, stepId, 1L);
                    return 1L;
                }
                int mappedEffectId = mapPrimitiveToOosEffect(primId);
                float scale = primitive.getScale();
                byte strength = (byte) Math.max(20, (int) (99 * scale));
                int result = mRichTap.richTapPerform(mappedEffectId, strength);
                long duration = RichTapVibrationEffect.getInnerEffectDuration(mappedEffectId);
                if (result >= 0) {
                    Object token = new Object();
                    synchronized (mLock) {
                        if (mDispatchToken != null) {
                            mHandler.removeCallbacksAndMessages(mDispatchToken);
                        }
                        mDispatchToken = token;
                        setRichTapVibratingLocked(true, (float) strength / 99f, duration);
                    }
                    scheduleCompletion(token, vibrationId, stepId, duration);
                    return duration;
                }
            } else {
                boolean hasDelays = false;
                for (PrimitiveSegment p : primitives) {
                    if (p.getDelay() > 0) {
                        hasDelays = true;
                        break;
                    }
                }

                if (!hasDelays) {
                    // Framework composed without inter-segment delays (e.g. button click + tick).
                    // Dispatch the final non-LOW_TICK primitive to avoid clipped double-buzzes on a single LRA.
                    PrimitiveSegment finalPrim = null;
                    for (int i = primitives.length - 1; i >= 0; i--) {
                        if (primitives[i].getPrimitiveId() != VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                            finalPrim = primitives[i];
                            break;
                        }
                    }
                    if (finalPrim != null) {
                        int mappedEffectId = mapPrimitiveToOosEffect(finalPrim.getPrimitiveId());
                        float scale = finalPrim.getScale();
                        byte strength = (byte) Math.max(20, (int) (99 * scale));
                        int result = mRichTap.richTapPerform(mappedEffectId, strength);
                        long duration = RichTapVibrationEffect.getInnerEffectDuration(mappedEffectId);
                        if (result >= 0) {
                            Object token = new Object();
                            synchronized (mLock) {
                                if (mDispatchToken != null) {
                                    mHandler.removeCallbacksAndMessages(mDispatchToken);
                                }
                                mDispatchToken = token;
                                setRichTapVibratingLocked(true, (float) strength / 99f, duration);
                            }
                            scheduleCompletion(token, vibrationId, stepId, duration);
                            return duration;
                        }
                    }
                } else {
                    Object token = new Object();
                    long currentOffset = 0;
                    boolean anyDispatched = false;
                    for (PrimitiveSegment p : primitives) {
                        int primId = p.getPrimitiveId();
                        if (primId == VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                            currentOffset += p.getDelay();
                            continue;
                        }
                        long scheduledTime = currentOffset + p.getDelay();
                        float scale = p.getScale();
                        byte strength = (byte) Math.max(20, (int) (99 * scale));
                        int mappedEffectId = mapPrimitiveToOosEffect(primId);
                        long playDuration = RichTapVibrationEffect.getInnerEffectDuration(mappedEffectId);
                        if (scheduledTime <= 0) {
                            mRichTap.richTapPerform(mappedEffectId, strength);
                        } else {
                            mHandler.postDelayed(() -> {
                                synchronized (mLock) {
                                    if (mDispatchToken != token) return;
                                }
                                mRichTap.richTapPerform(mappedEffectId, strength);
                            }, token, scheduledTime);
                        }
                        currentOffset = scheduledTime + playDuration;
                        anyDispatched = true;
                    }
                    if (anyDispatched) {
                        long totalDuration = Math.max(1, currentOffset);
                        synchronized (mLock) {
                            if (mDispatchToken != null) {
                                mHandler.removeCallbacksAndMessages(mDispatchToken);
                            }
                            mDispatchToken = token;
                            setRichTapVibratingLocked(true, 1f, totalDuration);
                        }
                        scheduleCompletion(token, vibrationId, stepId, totalDuration);
                        return totalDuration;
                    }
                }
            }
            return mDelegate.on(vibrationId, stepId, primitives);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, PwlePoint[] pwlePoints) {
        return mDelegate.on(vibrationId, stepId, pwlePoints);
    }

    @Override
    public boolean off() {
        Object staleToken;
        long duration;
        boolean wasVibrating;
        synchronized (mLock) {
            staleToken = mDispatchToken;
            mDispatchToken = null;
            duration = mRichTapDuration;
            wasVibrating = mRichTapVibrating;
            setRichTapVibratingLocked(false, 1.0f, 0);
        }
        if (staleToken != null) {
            mHandler.removeCallbacksAndMessages(staleToken);
        }
        if (wasVibrating && duration > 50) {
            mRichTap.richTapVibratorOff();
        }
        if (mDelegate.isVibrating()) {
            return mDelegate.off();
        }
        return true;
    }

    private void scheduleCompletion(Object token, long vibrationId, long stepId, long duration) {
        mHandler.postDelayed(() -> {
            synchronized (mLock) {
                if (mDispatchToken != token) {
                    return;
                }
                mDispatchToken = null;
                setRichTapVibratingLocked(false, 1.0f, 0);
            }
            Callbacks callbacks = mCallbacks;
            if (callbacks != null) {
                callbacks.onVibrationStepComplete(mVibratorId, vibrationId, stepId);
            }
        }, token, duration);
    }

    private static int mapToRichTapEffect(int effectId) {
        if (RichTapVibrationEffect.isOosEffect(effectId)) {
            return effectId;
        }
        return switch (effectId) {
            case VibrationEffect.EFFECT_CLICK -> RichTapVibrationEffect.EFFECT_OOS_GRANULAR_STRONG;
            case VibrationEffect.EFFECT_DOUBLE_CLICK -> RichTapVibrationEffect.EFFECT_OOS_MODERATE_SHORT_TWICE;
            case VibrationEffect.EFFECT_TICK -> RichTapVibrationEffect.EFFECT_OOS_MICRO_TICK;
            case VibrationEffect.EFFECT_THUD -> RichTapVibrationEffect.EFFECT_OOS_WEAK_SHORT;
            case VibrationEffect.EFFECT_POP -> RichTapVibrationEffect.EFFECT_OOS_GRANULAR_WEAK;
            case VibrationEffect.EFFECT_HEAVY_CLICK -> RichTapVibrationEffect.EFFECT_OOS_VIRTUAL_KEY;
            case VibrationEffect.EFFECT_TEXTURE_TICK -> RichTapVibrationEffect.EFFECT_OOS_MICRO_TICK;
            default -> -1;
        };
    }

    private static int mapPrimitiveToOosEffect(int primId) {
        return switch (primId) {
            case VibrationEffect.Composition.PRIMITIVE_CLICK -> RichTapVibrationEffect.EFFECT_OOS_GRANULAR_STRONG;
            case VibrationEffect.Composition.PRIMITIVE_THUD -> RichTapVibrationEffect.EFFECT_OOS_WEAK_SHORT;
            case VibrationEffect.Composition.PRIMITIVE_SPIN -> RichTapVibrationEffect.EFFECT_OOS_GRANULAR_WEAK;
            case VibrationEffect.Composition.PRIMITIVE_QUICK_FALL -> RichTapVibrationEffect.EFFECT_OOS_VIRTUAL_KEY;
            case VibrationEffect.Composition.PRIMITIVE_QUICK_RISE -> RichTapVibrationEffect.EFFECT_OOS_WEAKEST_SHORT;
            case VibrationEffect.Composition.PRIMITIVE_SLOW_RISE -> RichTapVibrationEffect.EFFECT_OOS_STEPABLE_REGULATE;
            case VibrationEffect.Composition.PRIMITIVE_TICK -> RichTapVibrationEffect.EFFECT_OOS_MICRO_TICK;
            default -> RichTapVibrationEffect.EFFECT_OOS_MICRO_TICK;
        };
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        boolean richTapVibrating;
        float richTapAmplitude;
        long richTapDuration;
        synchronized (mLock) {
            richTapVibrating = mRichTapVibrating;
            richTapAmplitude = mRichTapAmplitude;
            richTapDuration = mRichTapDuration;
        }
        pw.println("RichTapHalVibrator:");
        pw.increaseIndent();
        pw.println("richTapAvailable = " + mRichTap.isAvailable());
        pw.println("richTapVibrating = " + richTapVibrating);
        pw.println("richTapAmplitude = " + richTapAmplitude);
        pw.println("richTapDuration = " + richTapDuration);
        mDelegate.dump(pw);
        pw.decreaseIndent();
    }
}
