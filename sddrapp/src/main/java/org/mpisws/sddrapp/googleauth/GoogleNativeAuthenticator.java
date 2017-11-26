package org.mpisws.sddrapp.googleauth;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

import java.util.Arrays;
import java.util.List;

/**
 * Created by tslilyai on 11/13/17.
 */

/**
 * Implements Google authentication process using Google Play Services SDK.
 */
public class GoogleNativeAuthenticator {
    private static final String TAG = GoogleNativeAuthenticator.class.getSimpleName();

    private final AuthenticationMode authMode;
    private final String clientID = "78223818115-67lqkeeneosu58c91asibp6o2qvsj3bt.apps.googleusercontent.com";
    private final String redirectURI = "org.mpisws.sddrservice";

    private static final Uri ISSUER_URI = Uri.parse("https://accounts.google.com");
    private AuthorizationService service;
    private Context context;

    public GoogleNativeAuthenticator(AuthenticationMode authMode, Context context) {
        service = new AuthorizationService(context);
        this.authMode = authMode;
        this.context = context;
    }

    private class RetrieveServiceConfigurationCallback implements AuthorizationServiceConfiguration.RetrieveConfigurationCallback {
        @Override
        public void onFetchConfigurationCompleted(@Nullable AuthorizationServiceConfiguration authorizationServiceConfiguration, @Nullable AuthorizationException e) {
            if (e != null) {
                service.dispose();
            } else {
                // service configuration retrieved, proceed to authorization...'
                sendAuthRequest(authorizationServiceConfiguration);
            }
        }
    }

    public void makeAuthRequest() {
        AuthorizationServiceConfiguration.fetchFromIssuer(ISSUER_URI, new RetrieveServiceConfigurationCallback());
    }

    private void sendAuthRequest(AuthorizationServiceConfiguration serviceConfiguration) {
        String authRedirect = String.format("%s:/oauth2redirect", redirectURI);
        Uri redirectUri = Uri.parse(authRedirect);
        Log.d(TAG, authRedirect);

        AuthorizationRequest request = new AuthorizationRequest.Builder(
                serviceConfiguration,
                this.clientID,
                ResponseTypeValues.CODE,
                redirectUri)
                .setScopes(authMode.getPermissions())
                .build();

        Log.d(TAG, "Making request!");
        Intent intent = new Intent(context, GoogleAuthActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, request.hashCode(), intent, 0);
        service.performAuthorizationRequest(request, pi);
    }

    /**
     * Google authentication mode.
     */
    public enum AuthenticationMode {

        /**
         * Allow sign-in only.
         */
        SIGN_IN_ONLY(false, "profile"),

        /**
         * Allow sign-in and obtaining friend list.
         */
        OBTAIN_FRIENDS(true, "profile", "email");

        private final List<String> permissions;
        private final boolean allowStoringToken;

        AuthenticationMode(boolean allowStoringToken, String... permissions) {
            this.permissions = Arrays.asList(permissions);
            this.allowStoringToken = allowStoringToken;
        }

        private List<String> getPermissions() {
            return permissions;
        }

        private boolean canStoreToken() {
            return allowStoringToken;
        }
    }
}

