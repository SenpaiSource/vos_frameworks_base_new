/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.os.Trace.TRACE_TAG_VIBRATOR;

import android.annotation.NonNull;
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

import com.android.internal.annotations.GuardedBy;

/**
 * Decorates another {@link HalVibrator} (expected to be a
 * {@link VintfHalVibrator.DefaultHalVibrator}) with RichTap vendor extension support.
 *
 * <p>{@link HalVibrator} is an interface, so RichTap support is layered on top of the real
 * implementation via composition instead of being scattered through AOSP-owned,
 * actively-refactored files such as {@link VintfHalVibrator}/{@link VintfHalVibratorManager}.
 * Those files are only touched at their {@code HalVibrator} construction call sites, wrapping
 * the constructed instance with {@link #wrapIfNeeded}.
 *
 * <p>RichTap's {@code IRichtapVibrator} HAL is entirely {@code oneway} (fire-and-forget): no
 * method returns a duration, and its {@code IRichtapCallback} carries no id to correlate a
 * completion signal back to a specific request. So, exactly like {@link VintfHalVibrator}
 * itself does for HALs without {@code CAP_ON_CALLBACK}/{@code CAP_PERFORM_CALLBACK}, this class
 * reports an estimated duration to the vibration step conductor and uses
 * {@link Handler#postDelayed} both to simulate the completion callback and to pace
 * multi-primitive compositions (which RichTap has no built-in timing for once dispatched
 * one-primitive-per-call).
 */
final class RichTapHalVibrator implements HalVibrator {
    private static final String TAG = "RichTapHalVibrator";

    private final HalVibrator mDelegate;
    private final RichTapVibratorService mRichTap;
    private final Handler mHandler;
    private final Object mLock = new Object();

    // Listeners registered by external callers. Kept separately from mDelegate's own listener
    // bookkeeping (which is private to it) so this class can broadcast RichTap-driven
    // transitions itself; the same listener is also registered directly with mDelegate so it
    // still hears about delegate-driven transitions (setExternalControl, on(millis),
    // on(VendorEffect), on(PwlePoint[]), or any prebaked/primitive call RichTap couldn't handle).
    private final RemoteCallbackList<IVibratorStateListener> mListeners =
            new RemoteCallbackList<>();

    @GuardedBy("mLock")
    private boolean mRichTapVibrating;
    @GuardedBy("mLock")
    private float mRichTapAmplitude;
    @GuardedBy("mLock")
    private long mRichTapDuration;
    @GuardedBy("mLock")
    private Object mDispatchToken;

    private volatile Callbacks mCallbacks;
    private volatile VibratorInfo mInfo;

    /** Wraps {@code delegate} with RichTap support if this device is configured to use it. */
    static HalVibrator wrapIfNeeded(HalVibrator delegate, Handler handler) {
        return RichTapVibrationEffect.isSupported()
                ? new RichTapHalVibrator(delegate, handler)
                : delegate;
    }

    private RichTapHalVibrator(HalVibrator delegate, Handler handler) {
        mDelegate = delegate;
        mHandler = handler;
        mRichTap = new RichTapVibratorService();
        mInfo = delegate.getInfo();
    }

    @Override
    public void init(@NonNull Callbacks callbacks) {
        mCallbacks = callbacks;
        mDelegate.init(callbacks);
        refreshInfo();
    }

    @Override
    public void onSystemReady() {
        mDelegate.onSystemReady();
        refreshInfo();
    }

    @NonNull
    @Override
    public VibratorInfo getInfo() {
        return mInfo;
    }

    @Override
    public boolean isVibrating() {
        synchronized (mLock) {
            return mRichTapVibrating || mDelegate.isVibrating();
        }
    }

    @Override
    public boolean usesRichTap() {
        return true;
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
    public boolean registerVibratorStateListener(@NonNull IVibratorStateListener listener) {
        boolean registered;
        boolean currentlyVibrating;
        synchronized (mLock) {
            registered = mListeners.register(listener);
            currentlyVibrating = mRichTapVibrating || mDelegate.isVibrating();
        }
        if (!registered) {
            return false;
        }
        // Let the delegate notify this listener directly for transitions it drives itself.
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

    @Override
    public boolean setExternalControl(boolean externalControl) {
        return mDelegate.setExternalControl(externalControl);
    }

    @Override
    public boolean setAlwaysOn(int id, PrebakedSegment prebaked) {
        return mDelegate.setAlwaysOn(id, prebaked);
    }

    @Override
    public boolean setAmplitude(float amplitude) {
        synchronized (mLock) {
            if (mRichTap.isAvailable()) {
                int strength = (int) (255.0f * amplitude);
                mRichTap.richTapVibratorSetAmplitude(strength);
                // ALWAYS update the stored amplitude so Prebaked effects read the correct state
                mRichTapAmplitude = amplitude;
                return true;
            }
        }
        return mDelegate.setAmplitude(amplitude);
    }

    @Override
    public long on(long vibrationId, long stepId, long milliseconds) {
        if (!mRichTap.isAvailable()) {
            return mDelegate.on(vibrationId, stepId, milliseconds);
        }
        // Route timed vibrations through the RichTap HAL extension. The standard
        // vibrator HAL on RichTap devices ignores the AIDL on() call, so we use
        // richTapVibratorOn() which calls IRichtapVibrator.on(millis, callback).
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onTimed");
        try {
            Object token = newDispatchToken();
            // Timed vibrations must respect the amplitude set by the framework via setAmplitude().
            // If the framework didn't call setAmplitude(), mRichTapAmplitude will be 1.0f (default max).
            if (mRichTap.isAvailable()) mRichTap.richTapVibratorSetAmplitude((int) (255 * mRichTapAmplitude));
            mRichTap.richTapVibratorOn(milliseconds);
            synchronized (mLock) {
                mDispatchToken = token;
                setRichTapVibratingLocked(true, 1f, milliseconds);
            }
            scheduleCompletion(token, vibrationId, stepId, milliseconds);
            return milliseconds;
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, VibrationEffect.VendorEffect vendorEffect) {
        return mDelegate.on(vibrationId, stepId, vendorEffect);
    }

    @Override
    public long on(long vibrationId, long stepId, PrebakedSegment prebaked) {
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onPrebaked");
        try {
            // The RichTap HAL's IVibrator.perform() returns near-zero duration (3-7ms), causing
            // the motor to cut off before the effect is felt. Use richTapVibratorOn(duration) with
            // the correct effect duration instead - the HAL's on(millis) path works correctly.
            // richTapVibratorOnRawPattern() / performHe() is not used here because this device's
            // AacRichTapPerformer rejects our int[] patterns (logs "perform_cmd reset").
            long duration = RichTapVibrationEffect.getInnerEffectDuration(prebaked.getEffectId());
            if (duration <= 0 || !mRichTap.isAvailable()) {
                return mDelegate.on(vibrationId, stepId, prebaked);
            }
            Object token = newDispatchToken();
            // Prebaked effects (like Back Gesture) do not receive a scale from the framework.
            // They must always fire at full hardware strength (255). Relying on mRichTapAmplitude here
            // causes them to randomly use the amplitude of the last timed waveform (like a faded notification).
            if (mRichTap.isAvailable()) mRichTap.richTapVibratorSetAmplitude(255);
            mRichTap.richTapVibratorOn(duration);
            synchronized (mLock) {
                mDispatchToken = token;
                setRichTapVibratingLocked(true, 1f, duration);
            }
            scheduleCompletion(token, vibrationId, stepId, duration);
            return duration;
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, PrimitiveSegment[] primitives) {
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "RichTapHalVibrator.onPrimitives");
        try {
            if (primitives == null || primitives.length == 0 || !mRichTap.isAvailable()) {
                return mDelegate.on(vibrationId, stepId, primitives);
            }

            Object token = newDispatchToken();
            long totalDuration = 0;
            boolean anyDispatched = false;
            
            // To prevent terrible double-buzzes when the framework sends composed effects
            // (like [TICK, CLICK] for the volume button), we ONLY dispatch the final primitive
            // in the composition. Our richTapVibratorOn(duration) emulation fires at max strength,
            // so playing multiple primitives back-to-back feels broken. 
            // This preserves single primitives (like [TICK] for slider drags) perfectly.
            if (primitives.length > 0) {
                PrimitiveSegment primitive = primitives[primitives.length - 1];
                int mappedEffectId = mapPrimitiveToEffectId(primitive.getPrimitiveId());
                int[] pattern = RichTapVibrationEffect.getInnerEffect(mappedEffectId);
                if (pattern != null) {
                    // Use the exact scale provided by the framework without any boost.
                    // This provides maximum dynamic range between the Low and High settings,
                    // but conservative AOSP default scales (like 0.2) will feel very weak.
                    float scale = primitive.getScale();
                    int strength = (int) (255 * scale);
                    if (strength > 25) {
                        // Force fireAtOffset to 0 so the final effect fires instantly without latency,
                        // ignoring any delays that were meant for the primitives we skipped.
                        long fireAtOffset = 0;
                        long effectDuration = RichTapVibrationEffect.getInnerEffectDuration(
                                mappedEffectId);
                        
                        long reportedDuration = effectDuration;
                        // For rapid texture ticks (slider drags), 25ms is too long and causes
                        // the framework to queue them up, resulting in vibrations continuing
                        // after the finger is lifted. Force the reported duration to be super short (12ms)
                        // without altering the HAL's actual playback time to avoid sharp active braking.
                        if (mappedEffectId == android.os.VibrationEffect.EFFECT_TICK) {
                            reportedDuration = 12;
                        }
                        
                        scheduleDispatch(token, pattern, strength, fireAtOffset, effectDuration);
                        totalDuration = fireAtOffset + reportedDuration;
                        anyDispatched = true;
                    }
                }
            }

            if (!anyDispatched) {
                return mDelegate.on(vibrationId, stepId, primitives);
            }

            synchronized (mLock) {
                mDispatchToken = token;
                setRichTapVibratingLocked(true, 1f, totalDuration);
            }
            scheduleCompletion(token, vibrationId, stepId, totalDuration);
            return totalDuration;
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    public long on(long vibrationId, long stepId, PwlePoint[] pwlePoints) {
        return mDelegate.on(vibrationId, stepId, pwlePoints);
    }

    @Override
    public boolean off() {
        Object staleToken;
        synchronized (mLock) {
            staleToken = mDispatchToken;
            mDispatchToken = null;
            // Only send richTapVibratorOff if we were actively emulating a RichTap timed vibration.
            // If mRichTapVibrating is false, it means mDelegate is playing a native firmware primitive
            // or prebaked effect, and we should NOT send richTapVibratorOff() as it causes a harsh
            // active-braking thump (double vibration feeling).
            // Avoid active braking thump for short primitives by not sending off() if duration <= 50ms
            if (mRichTap.isAvailable() && mRichTapVibrating && mRichTapDuration > 50) {
                mRichTap.richTapVibratorOff();
            }
            setRichTapVibratingLocked(false, 1.0f, 0); // Reset to default max amplitude
        }
        if (staleToken != null) {
            // Cancel any not-yet-fired scheduled primitives/completion callback from this token.
            mHandler.removeCallbacksAndMessages(staleToken);
        }
        // Always also stop the real HAL; harmless no-op if nothing was started through it.
        return mDelegate.off();
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        boolean richTapVibrating;
        float richTapAmplitude;
        synchronized (mLock) {
            richTapVibrating = mRichTapVibrating;
            richTapAmplitude = mRichTapAmplitude;
        }
        pw.println("RichTapHalVibrator:");
        pw.increaseIndent();
        pw.println("richTapAvailable = " + mRichTap.isAvailable());
        pw.println("richTapVibrating = " + richTapVibrating);
        pw.println("richTapAmplitude = " + richTapAmplitude);
        mDelegate.dump(pw);
        pw.decreaseIndent();
    }

    @Override
    public String toString() {
        return "RichTapHalVibrator{mInfo=" + mInfo + ", mDelegate=" + mDelegate + '}';
    }

    /** Maps a {@link VibrationEffect.Composition} primitive id to a RichTap inner effect id. */
    private static int mapPrimitiveToEffectId(int primitiveId) {
        if (primitiveId == VibrationEffect.Composition.PRIMITIVE_CLICK) {
            return VibrationEffect.EFFECT_CLICK;
        } else if (primitiveId == VibrationEffect.Composition.PRIMITIVE_THUD) {
            return VibrationEffect.EFFECT_THUD;
        }
        return VibrationEffect.EFFECT_TICK;
    }

    /**
     * Returns a new, unique token to correlate a dispatch (see {@link #scheduleDispatch}/
     * {@link #scheduleCompletion}) with the {@link #mDispatchToken} that {@link #off} checks to
     * decide whether it needs to cancel any not-yet-fired {@link Handler} callbacks. Does not
     * itself assign {@link #mDispatchToken} - callers must do that under {@link #mLock} once
     * they're ready to commit to this dispatch.
     */
    private static Object newDispatchToken() {
        return new Object();
    }

    private void scheduleDispatch(Object token, int[] pattern, int strength, long delayMillis, long effectDuration) {
        // AacRichTapPerformer rejects inline int[] patterns and logs "perform_cmd reset".
        // Using richTapVibratorOnRawPattern causes the HAL to enter a bad state where subsequent
        // valid vibrations (like keyboard clicks) fail until the screen is turned off and on.
        // Therefore, we emulate primitives by using the simple timed richTapVibratorOn(duration)
        if (delayMillis <= 0) {
            if (mRichTap.isAvailable()) mRichTap.richTapVibratorSetAmplitude(strength);
            mRichTap.richTapVibratorOn(effectDuration);
            return;
        }
        mHandler.postDelayed(() -> {
            if (mRichTap.isAvailable()) mRichTap.richTapVibratorSetAmplitude(strength);
            mRichTap.richTapVibratorOn(effectDuration);
        }, token, delayMillis);
    }

    private void scheduleCompletion(Object token, long vibrationId, long stepId, long duration) {
        mHandler.postDelayed(() -> {
            synchronized (mLock) {
                if (mDispatchToken == token) {
                    mDispatchToken = null;
                    // Only send an explicit off command for longer vibrations (notifications). 
                    // For short primitives (< 50ms), sending off() can trigger a harsh active braking
                    // thump in the linear motor, causing a "double vibration" feeling.
                    if (mRichTap.isAvailable() && duration > 50) {
                        mRichTap.richTapVibratorOff();
                    }
                    setRichTapVibratingLocked(false, 1.0f, 0); // Reset to default max amplitude
                }
            }
            Callbacks callbacks = mCallbacks;
            if (callbacks != null) {
                callbacks.onVibrationStepComplete(mInfo.getId(), vibrationId, stepId);
            }
        }, token, duration);
    }

    @GuardedBy("mLock")
    private void setRichTapVibratingLocked(boolean vibrating, float amplitude, long duration) {
        mRichTapDuration = duration;
        boolean previousOverall = mRichTapVibrating || mDelegate.isVibrating();
        mRichTapVibrating = vibrating;
        mRichTapAmplitude = amplitude;
        boolean currentOverall = mRichTapVibrating || mDelegate.isVibrating();
        if (previousOverall != currentOverall) {
            mListeners.broadcast(listener -> notifyStateListener(listener, currentOverall));
        }
    }

    private void notifyStateListener(IVibratorStateListener listener, boolean isVibrating) {
        try {
            listener.onVibrating(isVibrating);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Vibrator state listener failed to call", e);
        }
    }

    /**
     * Rebuilds {@link #mInfo} as the union of whatever the real HAL ({@link #mDelegate}) already
     * reports and what RichTap adds, so {@link VibratorInfo#isEffectSupported} /
     * {@link VibratorInfo#isPrimitiveSupported} - which now drive fallback substitution and
     * segment validation upstream in {@link DeviceAdapter} - reflect reality.
     */
    private void refreshInfo() {
        VibratorInfo base = mDelegate.getInfo();
        VibratorInfo.Builder builder = new VibratorInfo.Builder(base.getId());

        builder.setCapabilities(base.getCapabilities()
                | IVibrator.CAP_COMPOSE_EFFECTS
                | IVibrator.CAP_AMPLITUDE_CONTROL);

        IntArray supportedEffects = new IntArray();
        supportedEffects.add(VibrationEffect.EFFECT_CLICK);
        supportedEffects.add(VibrationEffect.EFFECT_DOUBLE_CLICK);
        supportedEffects.add(VibrationEffect.EFFECT_TICK);
        supportedEffects.add(VibrationEffect.EFFECT_THUD);
        supportedEffects.add(VibrationEffect.EFFECT_POP);
        supportedEffects.add(VibrationEffect.EFFECT_HEAVY_CLICK);
        supportedEffects.add(VibrationEffect.EFFECT_TEXTURE_TICK);
        SparseBooleanArray baseEffects = base.getSupportedEffects();
        if (baseEffects != null) {
            for (int i = 0; i < baseEffects.size(); i++) {
                if (baseEffects.valueAt(i)) {
                    supportedEffects.add(baseEffects.keyAt(i));
                }
            }
        }
        builder.setSupportedEffects(supportedEffects.toArray());

        // Keep whatever primitives the real HAL already declares...
        for (int primitiveId = VibrationEffect.Composition.PRIMITIVE_NOOP;
                primitiveId <= VibrationEffect.Composition.PRIMITIVE_LOW_TICK; primitiveId++) {
            if (base.isPrimitiveSupported(primitiveId)) {
                builder.setSupportedPrimitive(primitiveId, base.getPrimitiveDuration(primitiveId));
            }
        }
        // ...then layer RichTap's approximated primitives on top (overriding duration for any
        // the real HAL also claimed to support, since RichTap is what will actually play them).
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 10);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 10);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 10);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 10);
        builder.setSupportedPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 10);

        builder.setPrimitiveDelayMax(base.getPrimitiveDelayMax());
        builder.setCompositionSizeMax(base.getCompositionSizeMax());
        builder.setFrequencyProfileLegacy(base.getFrequencyProfileLegacy());
        builder.setFrequencyProfile(base.getFrequencyProfile());
        builder.setQFactor(base.getQFactor());
        builder.setMaxEnvelopeEffectSize(base.getMaxEnvelopeEffectSize());
        builder.setMinEnvelopeEffectControlPointDurationMillis(
                base.getMinEnvelopeEffectControlPointDurationMillis());
        builder.setMaxEnvelopeEffectControlPointDurationMillis(
                base.getMaxEnvelopeEffectControlPointDurationMillis());

        mInfo = builder.build();
    }
}
