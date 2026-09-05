/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
package org.lineageos.internal.util;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.Context;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import lineageos.hardware.LineageHardwareManager;

public final class ReadingModeApps {

    public enum Mode {
        OFF,
        PER_APP,
        OVERRIDE
    }

    private static final String KEY_SELECTED_APPS = "reading_mode_selected_apps";
    private static final String KEY_MODE = "reading_mode_mode";

    private static TaskStackListener sTaskStackListener;
    private static boolean sListening = false;

    private ReadingModeApps() {}

    public static Set<String> getSelectedApps(Context context) {
        final String raw = Settings.Secure.getString(
                context.getContentResolver(), KEY_SELECTED_APPS);
        if (TextUtils.isEmpty(raw)) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(",")).collect(Collectors.toSet());
    }

    public static void setSelectedApps(Context context, Set<String> apps) {
        Settings.Secure.putString(context.getContentResolver(),
                KEY_SELECTED_APPS, TextUtils.join(",", apps));
        applyForCurrentForegroundApp(context);
    }

    public static Mode getMode(Context context) {
        final int ordinal = Settings.Secure.getInt(context.getContentResolver(),
                KEY_MODE, Mode.OFF.ordinal());
        final Mode[] values = Mode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Mode.OFF;
    }

    public static void setMode(Context context, Mode mode) {
        Settings.Secure.putInt(context.getContentResolver(), KEY_MODE, mode.ordinal());
        switch (mode) {
            case OFF:
                stopListening();
                setHardwareState(context, false);
                break;
            case OVERRIDE:
                stopListening();
                setHardwareState(context, true);
                break;
            case PER_APP:
                startListening(context);
                break;
        }
    }

    public static Mode cycleMode(Context context) {
        final Mode next;
        switch (getMode(context)) {
            case OFF:
                next = Mode.PER_APP;
                break;
            case PER_APP:
                next = Mode.OVERRIDE;
                break;
            case OVERRIDE:
            default:
                next = Mode.OFF;
                break;
        }
        setMode(context, next);
        return next;
    }

    public static boolean isOverrideEnabled(Context context) {
        return getMode(context) == Mode.OVERRIDE;
    }

    public static void setOverrideEnabled(Context context, boolean enabled) {
        if (enabled) {
            setMode(context, Mode.OVERRIDE);
        } else {
            setMode(context, getMode(context) == Mode.OFF ? Mode.OFF : Mode.PER_APP);
        }
    }

    public static void onTileInit(Context context) {
        switch (getMode(context)) {
            case OVERRIDE:
                setHardwareState(context, true);
                break;
            case PER_APP:
                startListening(context);
                break;
            case OFF:
            default:
                break;
        }
    }

    private static void setHardwareState(Context context, boolean enabled) {
        LineageHardwareManager.getInstance(context)
                .set(LineageHardwareManager.FEATURE_READING_ENHANCEMENT, enabled);
    }

    private static String getTopPackageName() {
        try {
            ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (info != null && info.topActivity != null) {
                return info.topActivity.getPackageName();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    private static void applyForCurrentForegroundApp(Context context) {
        if (getMode(context) != Mode.PER_APP) {
            return;
        }
        final String top = getTopPackageName();
        setHardwareState(context, top != null && getSelectedApps(context).contains(top));
    }

    private static void startListening(Context context) {
        if (sListening) {
            applyForCurrentForegroundApp(context);
            return;
        }
        final Context appContext = context.getApplicationContext();
        sTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                applyForCurrentForegroundApp(appContext);
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(sTaskStackListener);
            sListening = true;
            applyForCurrentForegroundApp(appContext);
        } catch (RemoteException e) {
            sTaskStackListener = null;
        }
    }

    private static void stopListening() {
        if (!sListening || sTaskStackListener == null) {
            return;
        }
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(sTaskStackListener);
        } catch (RemoteException e) {
            // tata. bye bye
        } finally {
            sTaskStackListener = null;
            sListening = false;
        }
    }
}
