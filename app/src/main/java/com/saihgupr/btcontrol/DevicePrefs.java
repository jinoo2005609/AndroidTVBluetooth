package com.saihgupr.btcontrol;

import android.content.Context;
import android.content.SharedPreferences;

public class DevicePrefs {
    private static final String PREFS_NAME = "device_activity";
    private static final String KEY_PREFIX = "last_used_";
    private static final String KEY_AUTO_CONNECT = "auto_connect_enabled";
    private static final String KEY_DEFAULT_DEVICE_ADDRESS = "default_device_address";
    private static final String KEY_DEFAULT_DEVICE_NAME = "default_device_name";

    public static void updateLastUsed(Context context, String address) {
        if (address == null || context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_PREFIX + address, System.currentTimeMillis()).apply();
    }

    public static long getLastUsed(Context context, String address) {
        if (address == null || context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_PREFIX + address, 0);
    }

    public static boolean isAutoConnectEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_CONNECT, false);
    }

    public static void setAutoConnectEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply();
    }

    public static String getDefaultDeviceAddress(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEFAULT_DEVICE_ADDRESS, null);
    }

    public static String getDefaultDeviceName(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEFAULT_DEVICE_NAME, null);
    }

    public static void setDefaultDevice(Context context, String address, String name) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DEFAULT_DEVICE_ADDRESS, address)
                .putString(KEY_DEFAULT_DEVICE_NAME, name)
                .apply();
    }

    public static void clearDefaultDevice(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DEFAULT_DEVICE_ADDRESS)
                .remove(KEY_DEFAULT_DEVICE_NAME)
                .apply();
    }
}

