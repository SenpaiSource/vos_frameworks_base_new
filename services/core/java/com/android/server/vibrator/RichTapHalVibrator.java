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
import android.os.Handler;
import android.os.IVibratorStateListener;
import android.os.RichTapVibrationEffect;
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

/**
 * HalVibrator decorator for AAC RichTap hardware extensions.
 * Intercepts predefined vibration effect playback to trigger native AAC/AW8697
 * waveform effects directly via IRichtapVibrator AIDL.
 */
final class RichTapHalVibrator implements HalVibrator {
    private static final String TAG = "RichTapHalVibrator";

    private final HalVibrator mDelegate;
    private final Handler mHandler;
    private final RichTapVibratorService mRichTap;
    private Callbacks mCallbacks;

    private final int mVibratorId;
    private volatile VibratorInfo mCachedInfo;
    private volatile VibratorInfo mCachedBaseInfo;
    private volatile boolean mLastEffectWasShortRam = false;

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
        builder.setCapabilities(baseInfo.getCapabilities());

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
        builder.setSupportedEffects(effects.toArray());

        SparseIntArray primitives = baseInfo.getSupportedPrimitives();
        if (primitives != null) {
            for (int i = 0; i < primitives.size(); i++) {
                builder.setSupportedPrimitive(primitives.keyAt(i), primitives.valueAt(i));
            }
        }

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
        return mDelegate.isVibrating();
    }

    @Override
    public boolean registerVibratorStateListener(@NonNull IVibratorStateListener listener) {
        return mDelegate.registerVibratorStateListener(listener);
    }

    @Override
    public boolean unregisterVibratorStateListener(@NonNull IVibratorStateListener listener) {
        return mDelegate.unregisterVibratorStateListener(listener);
    }

    @Override
    public float getCurrentAmplitude() {
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
        mRichTap.richTapVibratorSetAmplitude((int) (amplitude * 255));
        return mDelegate.setAmplitude(amplitude);
    }

    @Override
    public long on(long vibrationId, long stepId, long milliseconds) {
        mLastEffectWasShortRam = false;
        return mDelegate.on(vibrationId, stepId, milliseconds);
    }

    @Override
    public long on(long vibrationId, long stepId, VibrationEffect.VendorEffect vendorEffect) {
        mLastEffectWasShortRam = false;
        return mDelegate.on(vibrationId, stepId, vendorEffect);
    }

    @Override
    public long on(long vibrationId, long stepId, PrebakedSegment prebaked) {
        int effectId = prebaked.getEffectId();
        if (RichTapVibrationEffect.isOosEffect(effectId)) {
            byte strength = (byte) RichTapVibrationEffect.getInnerEffectStrength(
                    prebaked.getEffectStrength());
            int result = mRichTap.richTapPerform(effectId, strength);
            long duration = RichTapVibrationEffect.getInnerEffectDuration(effectId);
            if (result >= 0) {
                mLastEffectWasShortRam = (duration <= 50);
                if (mCallbacks != null) {
                    mHandler.postDelayed(() -> {
                        if (mCallbacks != null) {
                            mCallbacks.onVibrationStepComplete(mVibratorId,
                                    vibrationId, stepId);
                        }
                    }, duration);
                }
                return duration;
            }
        }
        mLastEffectWasShortRam = false;
        return mDelegate.on(vibrationId, stepId, prebaked);
    }

    @Override
    public long on(long vibrationId, long stepId, PrimitiveSegment[] primitives) {
        mLastEffectWasShortRam = false;
        return mDelegate.on(vibrationId, stepId, primitives);
    }

    @Override
    public long on(long vibrationId, long stepId, PwlePoint[] pwlePoints) {
        mLastEffectWasShortRam = false;
        return mDelegate.on(vibrationId, stepId, pwlePoints);
    }

    @Override
    public boolean off() {
        if (mLastEffectWasShortRam) {
            // Omit active braking off() call for short SRAM primitives (<= 50ms).
            // The AW8697 hardware on-die back-EMF tracking handles deceleration cleanly.
            mLastEffectWasShortRam = false;
            return true;
        }
        mRichTap.richTapVibratorOff();
        return mDelegate.off();
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        pw.println("RichTapHalVibrator:");
        pw.increaseIndent();
        pw.println("richTapAvailable = " + mRichTap.isAvailable());
        pw.println("lastEffectWasShortRam = " + mLastEffectWasShortRam);
        mDelegate.dump(pw);
        pw.decreaseIndent();
    }
}
