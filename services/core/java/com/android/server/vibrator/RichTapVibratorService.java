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
import android.hardware.vibrator.IVibrator;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Slog;

import vendor.aac.hardware.richtap.vibrator.IRichtapCallback;
import vendor.aac.hardware.richtap.vibrator.IRichtapVibrator;

/**
 * Service class managing RichTap vibrator HAL extension functionality.
 * Directly communicates with vendor.aac.hardware.richtap.vibrator.IRichtapVibrator
 * via the IVibrator AIDL extension point.
 */
public class RichTapVibratorService {
    private static final String TAG = "RichTapVibratorService";
    private static final String VIBRATOR_DESCRIPTOR = IVibrator.DESCRIPTOR + "/default";
    private static final boolean DEBUG = false;

    private final IRichtapCallback mCallback;
    private volatile IRichtapVibrator sRichtapVibratorService = null;

    public RichTapVibratorService() {
        this(null);
    }

    public RichTapVibratorService(@Nullable IRichtapCallback callback) {
        mCallback = callback;
    }

    @Nullable
    private synchronized IRichtapVibrator getRichtapService() {
        if (sRichtapVibratorService != null) {
            IBinder binder = sRichtapVibratorService.asBinder();
            if (binder != null && binder.isBinderAlive() && binder.pingBinder()) {
                return sRichtapVibratorService;
            }
            sRichtapVibratorService = null;
        }

        try {
            if (DEBUG) Slog.d(TAG, "Connecting to vibrator HAL service: " + VIBRATOR_DESCRIPTOR);
            IBinder halBinder = Binder.allowBlocking(
                    ServiceManager.waitForDeclaredService(VIBRATOR_DESCRIPTOR));
            if (halBinder == null) {
                Slog.w(TAG, "Failed to get declared vibrator HAL service: " + VIBRATOR_DESCRIPTOR);
                return null;
            }

            IVibrator vibratorHalService = IVibrator.Stub.asInterface(halBinder);
            if (vibratorHalService == null) {
                Slog.w(TAG, "Failed to cast HAL binder to IVibrator");
                return null;
            }

            IBinder extensionBinder = vibratorHalService.asBinder().getExtension();
            if (extensionBinder != null) {
                sRichtapVibratorService = IRichtapVibrator.Stub.asInterface(
                        Binder.allowBlocking(extensionBinder));
                extensionBinder.linkToDeath(new VibHalDeathRecipient(this), 0);
                if (DEBUG) Slog.d(TAG, "Successfully connected to RichTap HAL extension");
            } else {
                Slog.e(TAG, "RichTap extension binder is null");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to connect to RichTap vibrator extension", e);
        }

        return sRichtapVibratorService;
    }

    /**
     * Synchronously perform a native hardware waveform effect on the RichTap HAL.
     *
     * @param effectId The hardware effect ID from effect_waveform.xml
     * @param strength The vibration strength level (1-100 or scale byte)
     * @return Positive status or command ID on success, or -1 on failure
     */
    public int richTapPerform(int effectId, byte strength) {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                if (DEBUG) Slog.d(TAG, "Executing richTapPerform: effectId=" + effectId + ", strength=" + strength);
                return service.perform(effectId, strength, mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to execute richTapPerform effectId=" + effectId, e);
        }
        return -1;
    }

    /**
     * Turn the vibrator on for a timed duration in milliseconds.
     */
    public void richTapVibratorOn(long millis) {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                if (DEBUG) Slog.d(TAG, "Executing richTapVibratorOn: " + millis + " ms");
                service.on((int) millis, mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to execute richTapVibratorOn", e);
        }
    }

    /**
     * Sets the vibration amplitude (0-255).
     */
    public void richTapVibratorSetAmplitude(int amplitude) {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                if (DEBUG) Slog.d(TAG, "Setting amplitude: " + amplitude);
                service.setAmplitude(amplitude, mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set amplitude", e);
        }
    }

    /**
     * Interrupts and halts any active vibration immediately.
     */
    public void richTapVibratorOff() {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                if (DEBUG) Slog.d(TAG, "Executing richTapVibratorOff");
                service.stop(mCallback);
                service.off(mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to execute richTapVibratorOff", e);
        }
    }

    /**
     * Sets dynamic scale parameter (0-100).
     */
    public void setDynamicScale(int scale) {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                service.setDynamicScale(scale, mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set dynamic scale", e);
        }
    }

    /**
     * Sets calibrated resonant frequency parameter.
     */
    public void setF0(int f0) {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                service.setF0(f0, mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set F0", e);
        }
    }

    /**
     * Initializes RichTap driver parameters.
     */
    public void init() {
        try {
            IRichtapVibrator service = getRichtapService();
            if (service != null) {
                service.init(mCallback);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to init RichTap", e);
        }
    }

    /**
     * Returns true if the RichTap HAL service extension is connected and alive.
     */
    public boolean isAvailable() {
        return getRichtapService() != null;
    }

    synchronized void resetHalServiceProxy() {
        sRichtapVibratorService = null;
    }

    private static final class VibHalDeathRecipient implements IBinder.DeathRecipient {
        private final RichTapVibratorService mRichTapService;

        VibHalDeathRecipient(@NonNull RichTapVibratorService richtapService) {
            mRichTapService = richtapService;
        }

        @Override
        public void binderDied() {
            Slog.w(TAG, "RichTap Vibrator HAL binder died, resetting proxy");
            mRichTapService.resetHalServiceProxy();
        }
    }
}
