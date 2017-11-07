package org.mpi_sws.sddr_service.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SharedPrefsManager {

    private static final String TAG = Utils.getTAG(SharedPrefsManager.class);
    private static final String prefsName = "PREFS";

    private static SharedPreferences getPrefs(final Context c) {
        return c.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    public synchronized static <T> void persist(final Context c, final String key, final T value) {
        final SharedPreferences.Editor editor = getPrefs(c).edit();
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else {
            throw new IllegalStateException();
        }
        if (!editor.commit()) {
            throw new AssertionError("Cannot commit " + key + " to persistent storage (?!)");
        }
        Log.d(TAG, "PERSIST " + key + " ==> " + value);
    }

    @SuppressWarnings("unchecked")
    public synchronized static <T> T load(final Context c, final Class<T> type, final String key, final T defaultValue) {
        T result;
        if (type.equals(String.class)) {
            result = (T) getPrefs(c).getString(key, (String) defaultValue);
        } else if (type.equals(Boolean.class)) {
            result = (T) (Boolean) getPrefs(c).getBoolean(key, (Boolean) defaultValue);
        } else if (type.equals(Integer.class)) {
            result = (T) (Integer) getPrefs(c).getInt(key, (Integer) defaultValue);
        } else if (type.equals(Long.class)) {
            result = (T) (Long) getPrefs(c).getLong(key, (Long) defaultValue);
        } else {
            throw new UnsupportedOperationException("Unsupported type parameter: " + type.getName());
        }
        Log.d(TAG, "LOAD " + key + " ==> " + result);
        return result;
    }
}
