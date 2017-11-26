package org.mpisws.sddrapp.googleauth;

import android.util.Log;

import org.mpisws.sddrservice.SDDR_API;

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
        Log.d(TAG, "Registering with SDDR with new token");
        SDDR_API.register_user(GoogleToken.getToken(), "Lily", "Tsai");
    }
}