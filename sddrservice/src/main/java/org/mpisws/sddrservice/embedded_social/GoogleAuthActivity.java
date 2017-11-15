package org.mpisws.sddrservice.embedded_social;

import android.content.Intent;
import android.util.Log;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.TokenResponse;
import android.support.v7.app.AppCompatActivity;

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
                                    Log.d(TAG, "Got access token!!!" + resp.accessToken);
                                    GoogleToken.setToken(resp.accessToken);
                                } else {
                                    Log.d(TAG, ex.getStackTrace().toString());
                                    Log.d(TAG, "Auth failed");
                                }
                            }
                        });
                service.dispose();
            } else {
                Log.d(TAG, "Auth failed");
            }
            Log.d(TAG, "Finishing activity");
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkIntent(getIntent());
    }
}
