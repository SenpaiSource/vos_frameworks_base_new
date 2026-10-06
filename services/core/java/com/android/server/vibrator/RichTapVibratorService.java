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
import android.os.Parcel;
import android.os.ServiceManager;
import android.util.Slog;

import vendor.aac.hardware.richtap.vibrator.IRichtapCallback;
import vendor.aac.hardware.richtap.vibrator.IRichtapVibrator;

/**
 * Service class managing RichTap vibrator HAL extension functionality.
 * Directly communicates with vendor.aac.hardware.richtap.vibrator.IRichtapVibrator
 * or vendor.oplus.hardware.oplusvibrator.IOplusVibrator via the IVibrator AIDL extension.
 */
public class RichTapVibratorService {
    private static final String TAG = "RichTapVibratorService";
    private static final String VIBRATOR_DESCRIPTOR = IVibrator.DESCRIPTOR + "/default";
    private static final boolean DEBUG = false;

    private static final int TRANSACTION_INIT = IBinder.FIRST_CALL_TRANSACTION + 0;
    private static final int TRANSACTION_SET_DYNAMIC_SCALE = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_SET_F0 = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int TRANSACTION_STOP = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int TRANSACTION_SET_AMPLITUDE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final int TRANSACTION_OFF = IBinder.FIRST_CALL_TRANSACTION + 6;
    private static final int TRANSACTION_ON = IBinder.FIRST_CALL_TRANSACTION + 7;
    private static final int TRANSACTION_PERFORM = IBinder.FIRST_CALL_TRANSACTION + 8;

    private final IRichtapCallback mCallback;
    private volatile IRichtapVibrator sRichtapVibratorService = null;
    private volatile IBinder mExtensionBinder = null;
    private volatile String mDescriptor = null;

    public RichTapVibratorService() {
        this(null);
    }

    public RichTapVibratorService(@Nullable IRichtapCallback callback) {
        mCallback = callback;
    }

    private synchronized boolean ensureConnectedLocked() {
        if (mExtensionBinder != null && mExtensionBinder.isBinderAlive() && mExtensionBinder.pingBinder()) {
            return true;
        }
        resetHalServiceProxy();

        try {
            if (DEBUG) Slog.d(TAG, "Connecting to vibrator HAL service: " + VIBRATOR_DESCRIPTOR);
            IBinder halBinder = Binder.allowBlocking(ServiceManager.checkService(VIBRATOR_DESCRIPTOR));
            if (halBinder == null) {
                halBinder = Binder.allowBlocking(ServiceManager.getService(VIBRATOR_DESCRIPTOR));
            }
            if (halBinder == null) {
                if (DEBUG) Slog.w(TAG, "Vibrator HAL service not yet available: " + VIBRATOR_DESCRIPTOR);
                return false;
            }

            IVibrator vibratorHalService = IVibrator.Stub.asInterface(halBinder);
            if (vibratorHalService == null) {
                Slog.w(TAG, "Failed to cast HAL binder to IVibrator");
                return false;
            }

            IBinder extensionBinder = vibratorHalService.asBinder().getExtension();
            if (extensionBinder != null) {
                mExtensionBinder = Binder.allowBlocking(extensionBinder);
                mDescriptor = mExtensionBinder.getInterfaceDescriptor();
                if (DEBUG) Slog.d(TAG, "Connected to vibrator HAL extension descriptor: " + mDescriptor);

                if (IRichtapVibrator.DESCRIPTOR.equals(mDescriptor)) {
                    sRichtapVibratorService = IRichtapVibrator.Stub.asInterface(mExtensionBinder);
                } else {
                    sRichtapVibratorService = null;
                }
                mExtensionBinder.linkToDeath(new VibHalDeathRecipient(this), 0);
                initInternalLocked();
                return true;
            } else {
                Slog.e(TAG, "RichTap extension binder is null");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to connect to RichTap vibrator extension", e);
        }
        return false;
    }

    private void initInternalLocked() {
        try {
            if (sRichtapVibratorService != null) {
                sRichtapVibratorService.init(mCallback);
            } else if (mExtensionBinder != null && mDescriptor != null) {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(mDescriptor);
                    data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                    mExtensionBinder.transact(TRANSACTION_INIT, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to initialize RichTap actuator looper", e);
        }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return -1;
                if (sRichtapVibratorService != null) {
                    if (DEBUG) Slog.d(TAG, "Executing richTapPerform: effectId=" + effectId + ", strength=" + strength);
                    return sRichtapVibratorService.perform(effectId, strength, mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    if (DEBUG) Slog.d(TAG, "Executing richTapPerform direct: effectId=" + effectId + ", strength=" + strength);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(mDescriptor);
                        data.writeInt(effectId);
                        data.writeByte(strength);
                        data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_PERFORM, data, reply, 0);
                        reply.readException();
                        return reply.readInt();
                    } finally {
                        reply.recycle();
                        data.recycle();
                    }
                }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return;
                if (sRichtapVibratorService != null) {
                    if (DEBUG) Slog.d(TAG, "Executing richTapVibratorOn: " + millis + " ms");
                    sRichtapVibratorService.on((int) millis, mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    Parcel data = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(mDescriptor);
                        data.writeInt((int) millis);
                        data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_ON, data, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        data.recycle();
                    }
                }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return;
                if (sRichtapVibratorService != null) {
                    if (DEBUG) Slog.d(TAG, "Setting amplitude: " + amplitude);
                    sRichtapVibratorService.setAmplitude(amplitude, mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    Parcel data = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(mDescriptor);
                        data.writeInt(amplitude);
                        data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_SET_AMPLITUDE, data, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        data.recycle();
                    }
                }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return;
                if (sRichtapVibratorService != null) {
                    if (DEBUG) Slog.d(TAG, "Executing richTapVibratorOff");
                    sRichtapVibratorService.stop(mCallback);
                    sRichtapVibratorService.off(mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    Parcel dataStop = Parcel.obtain();
                    try {
                        dataStop.writeInterfaceToken(mDescriptor);
                        dataStop.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_STOP, dataStop, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        dataStop.recycle();
                    }
                    Parcel dataOff = Parcel.obtain();
                    try {
                        dataOff.writeInterfaceToken(mDescriptor);
                        dataOff.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_OFF, dataOff, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        dataOff.recycle();
                    }
                }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return;
                if (sRichtapVibratorService != null) {
                    sRichtapVibratorService.setDynamicScale(scale, mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    Parcel data = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(mDescriptor);
                        data.writeInt(scale);
                        data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_SET_DYNAMIC_SCALE, data, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        data.recycle();
                    }
                }
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
            synchronized (this) {
                if (!ensureConnectedLocked()) return;
                if (sRichtapVibratorService != null) {
                    sRichtapVibratorService.setF0(f0, mCallback);
                } else if (mExtensionBinder != null && mDescriptor != null) {
                    Parcel data = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(mDescriptor);
                        data.writeInt(f0);
                        data.writeStrongBinder(mCallback != null ? mCallback.asBinder() : null);
                        mExtensionBinder.transact(TRANSACTION_SET_F0, data, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        data.recycle();
                    }
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set F0", e);
        }
    }

    /**
     * Initializes RichTap driver parameters.
     */
    public void init() {
        synchronized (this) {
            if (ensureConnectedLocked()) {
                initInternalLocked();
            }
        }
    }

    /**
     * Returns true if the RichTap HAL service extension is connected and alive.
     */
    public synchronized boolean isAvailable() {
        return ensureConnectedLocked();
    }

    synchronized void resetHalServiceProxy() {
        sRichtapVibratorService = null;
        mExtensionBinder = null;
        mDescriptor = null;
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
