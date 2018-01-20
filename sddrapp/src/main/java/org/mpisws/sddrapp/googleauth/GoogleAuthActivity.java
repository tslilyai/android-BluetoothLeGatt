package org.mpisws.sddrapp.googleauth;

import android.content.Intent;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.TokenResponse;
import android.support.v7.app.AppCompatActivity;

import org.mpisws.sddrservice.EncountersService;

/**
 * Created by tslilyai on 11/14/17.
 */

public class GoogleAuthActivity extends AppCompatActivity {
    private static final String TAG = GoogleAuthActivity.class.getSimpleName();

    @Override
    protected void onNewIntent(Intent intent) {
        checkIntent(intent);
    }

    private void checkIntent(Intent intent) {
        if (intent != null) {
            AuthorizationResponse resp = AuthorizationResponse.fromIntent(intent);
            AuthorizationException ex = AuthorizationException.fromIntent(intent);
            AuthorizationService service = new AuthorizationService(this);
            if (resp != null) {
                service.performTokenRequest(
                        resp.createTokenExchangeRequest(),
                        new AuthorizationService.TokenResponseCallback() {
                            @Override public void onTokenRequestCompleted(
                                    TokenResponse resp, AuthorizationException ex) {
                                if (ex == null && resp != null) {
                                    Log.v(TAG, "Got access token!!!" + resp.accessToken);
                                    GoogleToken.setToken(resp.accessToken);
                                } else {
                                    Log.v(TAG, ex.getStackTrace().toString());
                                    Log.v(TAG, "Auth failed");
                                }
                                Log.v(TAG, "Finishing activity");
                                finish();
                            }
                        });
                service.dispose();
            } else {
                Log.v(TAG, "Auth failed");
                service.dispose();
            }
       }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkIntent(getIntent());
    }
}
