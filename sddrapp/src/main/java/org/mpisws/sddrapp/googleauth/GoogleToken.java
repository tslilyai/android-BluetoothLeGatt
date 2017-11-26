package org.mpisws.sddrapp.googleauth;

import android.util.Log;

/**
 * Created by tslilyai on 11/14/17.
 */

public class GoogleToken {
    private static String token;
    private static final String TAG = GoogleToken.class.getSimpleName();

    public static String getToken() {
        return token;
    }
    public static void setToken(String newtoken) {
        Log.d(TAG, "Set access token " + newtoken);
        token = newtoken;
    }
}