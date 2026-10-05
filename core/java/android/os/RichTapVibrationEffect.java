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

package android.os;

/**
 * RichTap vibration effect implementation and native OxygenOS/ColorOS waveform catalog.
 * Defines hardware effect IDs mapped directly to driver SRAM (RAM) and RTP bin streams.
 *
 * @hide
 */
public final class RichTapVibrationEffect {

    // Category 1: Navigation & System Touch Interactions (RAM & RTP)
    public static final int EFFECT_OOS_WEAKEST_SHORT = 0;                     // 35ms RAM
    public static final int EFFECT_OOS_WEAK_SHORT = 1;                        // 45ms RAM
    public static final int EFFECT_OOS_MODERATE_SHORT = 2;                    // 110ms RAM
    public static final int EFFECT_OOS_MODERATE_SHORT_TWICE = 3;              // 480ms RTP
    public static final int EFFECT_OOS_BIG_SCALE = 9;                         // 30ms RAM
    public static final int EFFECT_OOS_MICRO_TICK = 10;                       // 15ms RAM
    public static final int EFFECT_OOS_PRESSURE_KEY_CLICK = 11;               // 37ms RAM
    public static final int EFFECT_OOS_PRESSURE_KEY_SLIDE = 12;               // 29ms RAM
    public static final int EFFECT_OOS_PUBG_SNIPER = 13;                      // 50ms RAM
    public static final int EFFECT_OOS_CUSTOMIZED_THREE_DIMENSION_TOUCH = 49; // 80ms RAM (Biometric unlock success)
    public static final int EFFECT_OOS_CUSTOMIZED_SPREAD_OUT = 50;            // 350ms RTP
    public static final int EFFECT_OOS_CUSTOMIZED_RUSH_LEFT_TO_RIGHT = 53;    // 110ms RAM
    public static final int EFFECT_OOS_GRANULAR_WEAK = 68;                    // 30ms RAM (Weak Granular)
    public static final int EFFECT_OOS_GRANULAR_STRONG = 69;                  // 45ms RAM (Signature Ball-Bearing Detent)
    public static final int EFFECT_OOS_LONG_VIBRATE = 70;                     // 350ms RTP
    public static final int EFFECT_OOS_STRONG_POINTFOUR_SECOND = 71;          // 400ms RTP
    public static final int EFFECT_OOS_STRONG_ONE_SECOND = 72;                // 1000ms RTP
    public static final int EFFECT_OOS_ATTACH_TO_MIDDLE = 73;                 // 200ms RTP
    public static final int EFFECT_OOS_BREATHE_SPREAD_OUT = 74;               // 620ms RTP

    // Category 2: System Controls, Sliders & Edges
    public static final int EFFECT_OOS_STEPABLE_REGULATE = 152;               // 66ms RTP (Discrete seekbar steps)
    public static final int EFFECT_OOS_VOICE_LEVEL_EDGE = 153;                // 297ms RTP (Volume boundary thud)
    public static final int EFFECT_OOS_STRENGTH_LEVEL_EDGE = 154;             // 146ms RTP (Slider boundary snap)
    public static final int EFFECT_OOS_FINGERPRINT_CORRECT = 156;             // 709ms RTP (Sensor match confirm)
    public static final int EFFECT_OOS_KEYBOARD_WEAK = 157;                   // 36ms RTP
    public static final int EFFECT_OOS_KEYBOARD_MEDIUM = 158;                 // 36ms RTP
    public static final int EFFECT_OOS_KEYBOARD_STRONG = 159;                 // 36ms RTP
    public static final int EFFECT_OOS_BREATH_SIMULATION = 165;               // 3187ms RTP
    public static final int EFFECT_OOS_CHARGING = 166;                        // 935ms RTP (Charge animation start)
    public static final int EFFECT_OOS_VOICE_ASSISTANT = 167;                 // 151ms RTP (OEM Assistant listener)

    // Category 3: Gestures & UI Affordances
    public static final int EFFECT_OOS_THREEFINGERS_LONG = 270;               // 16ms RTP
    public static final int EFFECT_OOS_THREEFINGERS_UP = 271;                 // 16ms RTP
    public static final int EFFECT_OOS_THREEFINGERS_SCREENSHOT = 272;         // 16ms RTP
    public static final int EFFECT_OOS_UNFOLD = 273;                          // 16ms RTP
    public static final int EFFECT_OOS_CLOSE = 274;                           // 16ms RTP
    public static final int EFFECT_OOS_HALF_LAP = 275;                        // 35ms RTP
    public static final int EFFECT_OOS_TWOFINGERS_DOWN = 276;                 // 16ms RTP
    public static final int EFFECT_OOS_TWOFINGERS_LONG = 277;                 // 16ms RTP
    public static final int EFFECT_OOS_STYLESWITCH_CRISP = 280;               // 6699ms RTP (O-Haptics Crisp demo)
    public static final int EFFECT_OOS_SUSPENDBUTTON_BOTTOMOUT = 282;         // 100ms RTP
    public static final int EFFECT_OOS_SUSPENDBUTTON_MENU = 283;              // 16ms RTP
    public static final int EFFECT_OOS_COMPLETE = 284;                        // 16ms RTP
    public static final int EFFECT_OOS_BULB = 285;                            // 121ms RTP
    public static final int EFFECT_OOS_ELASTICITY = 286;                      // 121ms RTP
    public static final int EFFECT_OOS_STYLESWITCH_SOFT = 287;                // 6699ms RTP (O-Haptics Gentle demo)
    public static final int EFFECT_OOS_RAZER_CLICKY = 288;                    // 14ms RAM (Razer Clicky mechanical key)
    public static final int EFFECT_OOS_RAZER_LINEAR = 289;                    // 14ms RAM (Razer Linear mechanical key)
    public static final int EFFECT_OOS_EMULATION_KEY_DOWN = 302;              // 35ms RTP
    public static final int EFFECT_OOS_EMULATION_KEY_UP = 303;                // 26ms RTP
    public static final int EFFECT_OOS_WEAK_EMULATION_KEY_DOWN = 304;         // 35ms RTP
    public static final int EFFECT_OOS_WEAK_EMULATION_KEY_UP = 305;           // 26ms RTP
    public static final int EFFECT_OOS_ALERTSLIDER_DOWN = 308;                // 330ms RTP (Alert slider switch)
    public static final int EFFECT_OOS_RECENT_TASK = 367;                     // 80ms RTP (Overview task carousel snap)
    public static final int EFFECT_OOS_VIRTUAL_KEY = 368;                     // 80ms RTP (3-button nav virtual keys)
    public static final int EFFECT_OOS_LOCKSCREEN = 406;                      // 115ms RTP (Screen lock feedback)
    public static final int EFFECT_OOS_ENROLL_FINGERPRINT = 407;              // 242ms RTP (Fingerprint enrollment step)
    public static final int EFFECT_OOS_SCREENSHOT = 409;                      // 187ms RTP (Screenshot shutter snap)
    public static final int EFFECT_OOS_THREE_STAGE_KEY = 430;                 // 260ms RTP (Tri-state switch position)
    public static final int EFFECT_OOS_CLEAR_NOTIFICATIONS = 471;             // 350ms RTP (Notification shade Clear All)
    public static final int EFFECT_OOS_CLEAR_RECENTS = 472;                   // 350ms RTP (Recents overview Clear All)
    public static final int EFFECT_OOS_INPUT_METHOD_LONG_PRESS = 490;         // 35ms RAM
    public static final int EFFECT_OOS_CIRCLE_TO_SEARCH = 527;                // 600ms RTP (Circle to Search long press)

    // Notification Overridden Waveforms (Durations synchronized with effect_waveform_override.xml)
    public static final int EFFECT_OOS_NOTIFICATION_ALLAY = 214;              // 590ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_BUBBLE = 220;             // 492ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_CHEERFUL = 221;           // 654ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_CLEAR = 222;              // 457ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_DING = 225;               // 1697ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_QUICKLY = 234;            // 612ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_RHYTHM = 235;             // 1002ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_TWINKLE = 237;            // 2206ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_BLINK = 343;              // 731ms RTP
    public static final int EFFECT_OOS_NOTIFICATION_WHOOPDOOP = 344;          // 636ms RTP
    public static final int EFFECT_OOS_MESSAGE_SYMPHONIC = 396;               // 580ms RTP
    public static final int EFFECT_OOS_MESSAGE_STREAK = 397;                  // 390ms RTP
    public static final int EFFECT_OOS_MESSAGE_HEARTBEAT = 398;               // 470ms RTP
    public static final int EFFECT_OOS_MESSAGE_REMIND = 399;                  // 330ms RTP
    public static final int EFFECT_OOS_MESSAGE_RAPID = 400;                   // 430ms RTP

    private RichTapVibrationEffect() {}

    /**
     * Checks if the given effectId belongs to the OxygenOS hardware catalog.
     */
    public static boolean isOosEffect(int effectId) {
        switch (effectId) {
            case EFFECT_OOS_WEAKEST_SHORT:
            case EFFECT_OOS_WEAK_SHORT:
            case EFFECT_OOS_MODERATE_SHORT:
            case EFFECT_OOS_MODERATE_SHORT_TWICE:
            case EFFECT_OOS_BIG_SCALE:
            case EFFECT_OOS_MICRO_TICK:
            case EFFECT_OOS_PRESSURE_KEY_CLICK:
            case EFFECT_OOS_PRESSURE_KEY_SLIDE:
            case EFFECT_OOS_PUBG_SNIPER:
            case EFFECT_OOS_CUSTOMIZED_THREE_DIMENSION_TOUCH:
            case EFFECT_OOS_CUSTOMIZED_SPREAD_OUT:
            case EFFECT_OOS_CUSTOMIZED_RUSH_LEFT_TO_RIGHT:
            case EFFECT_OOS_GRANULAR_WEAK:
            case EFFECT_OOS_GRANULAR_STRONG:
            case EFFECT_OOS_LONG_VIBRATE:
            case EFFECT_OOS_STRONG_POINTFOUR_SECOND:
            case EFFECT_OOS_STRONG_ONE_SECOND:
            case EFFECT_OOS_ATTACH_TO_MIDDLE:
            case EFFECT_OOS_BREATHE_SPREAD_OUT:
            case EFFECT_OOS_STEPABLE_REGULATE:
            case EFFECT_OOS_VOICE_LEVEL_EDGE:
            case EFFECT_OOS_STRENGTH_LEVEL_EDGE:
            case EFFECT_OOS_FINGERPRINT_CORRECT:
            case EFFECT_OOS_KEYBOARD_WEAK:
            case EFFECT_OOS_KEYBOARD_MEDIUM:
            case EFFECT_OOS_KEYBOARD_STRONG:
            case EFFECT_OOS_BREATH_SIMULATION:
            case EFFECT_OOS_CHARGING:
            case EFFECT_OOS_VOICE_ASSISTANT:
            case EFFECT_OOS_THREEFINGERS_LONG:
            case EFFECT_OOS_THREEFINGERS_UP:
            case EFFECT_OOS_THREEFINGERS_SCREENSHOT:
            case EFFECT_OOS_UNFOLD:
            case EFFECT_OOS_CLOSE:
            case EFFECT_OOS_HALF_LAP:
            case EFFECT_OOS_TWOFINGERS_DOWN:
            case EFFECT_OOS_TWOFINGERS_LONG:
            case EFFECT_OOS_STYLESWITCH_CRISP:
            case EFFECT_OOS_SUSPENDBUTTON_BOTTOMOUT:
            case EFFECT_OOS_SUSPENDBUTTON_MENU:
            case EFFECT_OOS_COMPLETE:
            case EFFECT_OOS_BULB:
            case EFFECT_OOS_ELASTICITY:
            case EFFECT_OOS_STYLESWITCH_SOFT:
            case EFFECT_OOS_RAZER_CLICKY:
            case EFFECT_OOS_RAZER_LINEAR:
            case EFFECT_OOS_EMULATION_KEY_DOWN:
            case EFFECT_OOS_EMULATION_KEY_UP:
            case EFFECT_OOS_WEAK_EMULATION_KEY_DOWN:
            case EFFECT_OOS_WEAK_EMULATION_KEY_UP:
            case EFFECT_OOS_ALERTSLIDER_DOWN:
            case EFFECT_OOS_RECENT_TASK:
            case EFFECT_OOS_VIRTUAL_KEY:
            case EFFECT_OOS_LOCKSCREEN:
            case EFFECT_OOS_ENROLL_FINGERPRINT:
            case EFFECT_OOS_SCREENSHOT:
            case EFFECT_OOS_THREE_STAGE_KEY:
            case EFFECT_OOS_CLEAR_NOTIFICATIONS:
            case EFFECT_OOS_CLEAR_RECENTS:
            case EFFECT_OOS_INPUT_METHOD_LONG_PRESS:
            case EFFECT_OOS_CIRCLE_TO_SEARCH:
            case EFFECT_OOS_NOTIFICATION_ALLAY:
            case EFFECT_OOS_NOTIFICATION_BUBBLE:
            case EFFECT_OOS_NOTIFICATION_CHEERFUL:
            case EFFECT_OOS_NOTIFICATION_CLEAR:
            case EFFECT_OOS_NOTIFICATION_DING:
            case EFFECT_OOS_NOTIFICATION_QUICKLY:
            case EFFECT_OOS_NOTIFICATION_RHYTHM:
            case EFFECT_OOS_NOTIFICATION_TWINKLE:
            case EFFECT_OOS_NOTIFICATION_BLINK:
            case EFFECT_OOS_NOTIFICATION_WHOOPDOOP:
            case EFFECT_OOS_MESSAGE_SYMPHONIC:
            case EFFECT_OOS_MESSAGE_STREAK:
            case EFFECT_OOS_MESSAGE_HEARTBEAT:
            case EFFECT_OOS_MESSAGE_REMIND:
            case EFFECT_OOS_MESSAGE_RAPID:
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if the given effectId represents a touch or gesture interaction eligible
     * for system haptic feedback filtering.
     */
    public static boolean isOosTouchEffect(int effectId) {
        switch (effectId) {
            case EFFECT_OOS_WEAKEST_SHORT:
            case EFFECT_OOS_WEAK_SHORT:
            case EFFECT_OOS_MODERATE_SHORT:
            case EFFECT_OOS_MODERATE_SHORT_TWICE:
            case EFFECT_OOS_BIG_SCALE:
            case EFFECT_OOS_MICRO_TICK:
            case EFFECT_OOS_CUSTOMIZED_THREE_DIMENSION_TOUCH:
            case EFFECT_OOS_CUSTOMIZED_SPREAD_OUT:
            case EFFECT_OOS_CUSTOMIZED_RUSH_LEFT_TO_RIGHT:
            case EFFECT_OOS_GRANULAR_WEAK:
            case EFFECT_OOS_GRANULAR_STRONG:
            case EFFECT_OOS_STEPABLE_REGULATE:
            case EFFECT_OOS_VOICE_LEVEL_EDGE:
            case EFFECT_OOS_STRENGTH_LEVEL_EDGE:
            case EFFECT_OOS_FINGERPRINT_CORRECT:
            case EFFECT_OOS_CHARGING:
            case EFFECT_OOS_VOICE_ASSISTANT:
            case EFFECT_OOS_THREEFINGERS_SCREENSHOT:
            case EFFECT_OOS_RAZER_CLICKY:
            case EFFECT_OOS_RAZER_LINEAR:
            case EFFECT_OOS_ALERTSLIDER_DOWN:
            case EFFECT_OOS_RECENT_TASK:
            case EFFECT_OOS_VIRTUAL_KEY:
            case EFFECT_OOS_LOCKSCREEN:
            case EFFECT_OOS_ENROLL_FINGERPRINT:
            case EFFECT_OOS_SCREENSHOT:
            case EFFECT_OOS_THREE_STAGE_KEY:
            case EFFECT_OOS_CLEAR_NOTIFICATIONS:
            case EFFECT_OOS_CLEAR_RECENTS:
            case EFFECT_OOS_INPUT_METHOD_LONG_PRESS:
            case EFFECT_OOS_CIRCLE_TO_SEARCH:
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets the playback duration in milliseconds for the given effect id.
     * Incorporates timing overrides from effect_waveform_override.xml.
     */
    public static long getInnerEffectDuration(int id) {
        switch (id) {
            case EFFECT_OOS_WEAKEST_SHORT:
            case EFFECT_OOS_EMULATION_KEY_DOWN:
            case EFFECT_OOS_WEAK_EMULATION_KEY_DOWN:
            case EFFECT_OOS_INPUT_METHOD_LONG_PRESS:
            case EFFECT_OOS_HALF_LAP:
                return 35L;
            case EFFECT_OOS_WEAK_SHORT:
            case EFFECT_OOS_GRANULAR_STRONG:
                return 45L;
            case EFFECT_OOS_MODERATE_SHORT:
            case EFFECT_OOS_CUSTOMIZED_RUSH_LEFT_TO_RIGHT:
                return 110L;
            case EFFECT_OOS_MODERATE_SHORT_TWICE:
                return 480L;
            case EFFECT_OOS_BIG_SCALE:
            case EFFECT_OOS_GRANULAR_WEAK:
                return 30L;
            case EFFECT_OOS_MICRO_TICK:
                return 15L;
            case EFFECT_OOS_PRESSURE_KEY_CLICK:
                return 37L;
            case EFFECT_OOS_PRESSURE_KEY_SLIDE:
                return 29L;
            case EFFECT_OOS_PUBG_SNIPER:
                return 50L;
            case EFFECT_OOS_CUSTOMIZED_THREE_DIMENSION_TOUCH:
            case EFFECT_OOS_RECENT_TASK:
            case EFFECT_OOS_VIRTUAL_KEY:
                return 80L;
            case EFFECT_OOS_CUSTOMIZED_SPREAD_OUT:
            case EFFECT_OOS_LONG_VIBRATE:
            case EFFECT_OOS_CLEAR_NOTIFICATIONS:
            case EFFECT_OOS_CLEAR_RECENTS:
                return 350L;
            case EFFECT_OOS_STRONG_POINTFOUR_SECOND:
                return 400L;
            case EFFECT_OOS_STRONG_ONE_SECOND:
                return 1000L;
            case EFFECT_OOS_ATTACH_TO_MIDDLE:
                return 200L;
            case EFFECT_OOS_BREATHE_SPREAD_OUT:
                return 620L;
            case EFFECT_OOS_STEPABLE_REGULATE:
                return 66L;
            case EFFECT_OOS_VOICE_LEVEL_EDGE:
                return 297L;
            case EFFECT_OOS_STRENGTH_LEVEL_EDGE:
                return 146L;
            case EFFECT_OOS_FINGERPRINT_CORRECT:
                return 709L;
            case EFFECT_OOS_KEYBOARD_WEAK:
            case EFFECT_OOS_KEYBOARD_MEDIUM:
            case EFFECT_OOS_KEYBOARD_STRONG:
                return 36L;
            case EFFECT_OOS_BREATH_SIMULATION:
                return 3187L;
            case EFFECT_OOS_CHARGING:
                return 935L;
            case EFFECT_OOS_VOICE_ASSISTANT:
                return 151L;
            case EFFECT_OOS_THREEFINGERS_LONG:
            case EFFECT_OOS_THREEFINGERS_UP:
            case EFFECT_OOS_THREEFINGERS_SCREENSHOT:
            case EFFECT_OOS_UNFOLD:
            case EFFECT_OOS_CLOSE:
            case EFFECT_OOS_TWOFINGERS_DOWN:
            case EFFECT_OOS_TWOFINGERS_LONG:
            case EFFECT_OOS_SUSPENDBUTTON_MENU:
            case EFFECT_OOS_COMPLETE:
                return 16L;
            case EFFECT_OOS_STYLESWITCH_CRISP:
            case EFFECT_OOS_STYLESWITCH_SOFT:
                return 6699L;
            case EFFECT_OOS_SUSPENDBUTTON_BOTTOMOUT:
                return 100L;
            case EFFECT_OOS_BULB:
            case EFFECT_OOS_ELASTICITY:
                return 121L;
            case EFFECT_OOS_RAZER_CLICKY:
            case EFFECT_OOS_RAZER_LINEAR:
                return 14L;
            case EFFECT_OOS_EMULATION_KEY_UP:
            case EFFECT_OOS_WEAK_EMULATION_KEY_UP:
                return 26L;
            case EFFECT_OOS_ALERTSLIDER_DOWN:
                return 330L;
            case EFFECT_OOS_LOCKSCREEN:
                return 115L;
            case EFFECT_OOS_ENROLL_FINGERPRINT:
                return 242L;
            case EFFECT_OOS_SCREENSHOT:
                return 187L;
            case EFFECT_OOS_THREE_STAGE_KEY:
                return 260L;
            case EFFECT_OOS_CIRCLE_TO_SEARCH:
                return 600L;
            // Overridden Notification Timings
            case EFFECT_OOS_NOTIFICATION_ALLAY:
                return 590L;
            case EFFECT_OOS_NOTIFICATION_BUBBLE:
                return 492L;
            case EFFECT_OOS_NOTIFICATION_CHEERFUL:
                return 654L;
            case EFFECT_OOS_NOTIFICATION_CLEAR:
                return 457L;
            case EFFECT_OOS_NOTIFICATION_DING:
                return 1697L;
            case EFFECT_OOS_NOTIFICATION_QUICKLY:
                return 612L;
            case EFFECT_OOS_NOTIFICATION_RHYTHM:
                return 1002L;
            case EFFECT_OOS_NOTIFICATION_TWINKLE:
                return 2206L;
            case EFFECT_OOS_NOTIFICATION_BLINK:
                return 731L;
            case EFFECT_OOS_NOTIFICATION_WHOOPDOOP:
                return 636L;
            case EFFECT_OOS_MESSAGE_SYMPHONIC:
                return 580L;
            case EFFECT_OOS_MESSAGE_STREAK:
                return 390L;
            case EFFECT_OOS_MESSAGE_HEARTBEAT:
                return 470L;
            case EFFECT_OOS_MESSAGE_REMIND:
                return 330L;
            case EFFECT_OOS_MESSAGE_RAPID:
                return 430L;
            // Standard AOSP effects fallback
            case VibrationEffect.EFFECT_CLICK:
            case VibrationEffect.EFFECT_POP:
                return 40L;
            case VibrationEffect.EFFECT_DOUBLE_CLICK:
                return 110L;
            case VibrationEffect.EFFECT_TICK:
                return 25L;
            case VibrationEffect.EFFECT_THUD:
                return 60L;
            case VibrationEffect.EFFECT_HEAVY_CLICK:
                return 50L;
            case VibrationEffect.EFFECT_TEXTURE_TICK:
                return 45L;
            default:
                return 30L;
        }
    }

    /**
     * Gets the inner effect strength value for a given strength level.
     */
    public static int getInnerEffectStrength(int strength) {
        switch (strength) {
            case VibrationEffect.EFFECT_STRENGTH_LIGHT:
                return 150;
            case VibrationEffect.EFFECT_STRENGTH_MEDIUM:
                return 200;
            case VibrationEffect.EFFECT_STRENGTH_STRONG:
                return 250;
            default:
                return 200;
        }
    }
}
