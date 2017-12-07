package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.SessionsOperations;
import com.microsoft.embeddedsocial.autorest.SessionsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.UsersOperations;
import com.microsoft.embeddedsocial.autorest.UsersOperationsImpl;
import com.microsoft.embeddedsocial.autorest.models.PostSessionRequest;
import com.microsoft.embeddedsocial.autorest.models.PostSessionResponse;
import com.microsoft.embeddedsocial.autorest.models.PostUserRequest;
import com.microsoft.embeddedsocial.autorest.models.PostUserResponse;
import com.microsoft.embeddedsocial.autorest.models.UserProfileView;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import java.util.Date;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import static org.mpisws.sddrservice.embeddedsocial.ESTask.ESAPI_KEY;
import static org.mpisws.sddrservice.embeddedsocial.ESTask.OAUTH_TEMPLATE;
import static org.mpisws.sddrservice.embeddedsocial.ESTask.RETRIES;
import static org.mpisws.sddrservice.embeddedsocial.ESTask.SDDR_ID;
import static org.mpisws.sddrservice.embeddedsocial.ESTask.SESSION_TEMPLATE;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESUser {
    private final String TAG = ESUser.class.getSimpleName();

    /* User-specific login and authentication information */
    private String USER_HANDLE;
    private String SESSION;
    private Date SESSION_DATE;
    protected String GOOGLETOKEN;

    private UsersOperations ES_USEROPS;
    private SessionsOperations ES_SESSION;

    /* Public auth string (reset every time a new session is created */
    public String AUTH;

    public ESUser(Retrofit retrofit, EmbeddedSocialClient esclient) {
        ES_USEROPS = new UsersOperationsImpl(retrofit, esclient);
        ES_SESSION = new SessionsOperationsImpl(retrofit, esclient);
    }

    protected void loginGoogle(String googletoken) {
        if (googletoken == null) {
            Log.v(TAG, "Not registered with Google yet");
        } else {
            GOOGLETOKEN = googletoken;
        }
    }

    protected void registerUser(final String firstname, final String lastname, final int retries) {
        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GOOGLETOKEN);
        Log.v(TAG, "esAuth is " + esAuth);

        final PostUserRequest req = new PostUserRequest();
        req.setFirstName(firstname);
        req.setLastName(lastname);
        req.setInstanceId(SDDR_ID);

        ServiceCallback<PostUserResponse> serviceCallback = new ServiceCallback<PostUserResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed to register user, " + t.getMessage());
                if (retries < RETRIES) {
                    registerUser(firstname, lastname, retries+1);
                }
            }
            @Override
            public void success(ServiceResponse<PostUserResponse> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                Log.v(TAG, "Registered user!");
                USER_HANDLE = result.getBody().getUserHandle();
                SESSION = result.getBody().getSessionToken();
                SESSION_DATE = new Date();
            }
        };
        ES_USEROPS.postUserAsync(req, esAuth, serviceCallback);
    }

    protected void getnewsession(final int retries) {
        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GOOGLETOKEN);
        Log.v(TAG, "esAuth is " + esAuth);

        // find the user handle if we haven't set it yet
        if (USER_HANDLE == null) {
            ServiceCallback<UserProfileView> serviceCallback = new ServiceCallback<UserProfileView>() {
                @Override
                public void failure(Throwable t) {
                    Log.v(TAG, "Failed to register user, " + t.getMessage());
                    if (retries < RETRIES) {
                        getnewsession(retries + 1);
                    }
                }
                @Override
                public void success(ServiceResponse<UserProfileView> sResp) {
                    if (!sResp.getResponse().isSuccess()) {
                        failure(new Throwable());
                        return;
                    }
                    Log.v(TAG, "Got user info");
                    USER_HANDLE = sResp.getBody().getUserHandle();
                }
            };
            ES_USEROPS.getMyProfileAsync(esAuth, serviceCallback);
        }

        // create a new session for this user
        final PostSessionRequest req = new PostSessionRequest();
        req.setUserHandle(USER_HANDLE);
        req.setInstanceId(SDDR_ID);

        ServiceCallback<PostSessionResponse> serviceCallback = new ServiceCallback<PostSessionResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Getting new session failed");
                if (retries < RETRIES) {
                    getnewsession(retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<PostSessionResponse> sResp) {
                if (!sResp.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                USER_HANDLE = sResp.getBody().getUserHandle();
                SESSION = sResp.getBody().getSessionToken();
                SESSION_DATE = new Date();
                AUTH = String.format(SESSION_TEMPLATE, SESSION);
                Log.v(TAG, "New session! Auth set to " + AUTH);
            }
        };
        ES_SESSION.postSessionAsync(req, esAuth, serviceCallback);
    }

    protected boolean checkLoginStatus() {
        // TODO deal with session expiration
       if (AUTH == null) {
            Log.v(TAG, "User not logged in");
            getnewsession(0);
            return false;
       }
       Log.v(TAG, "User logged in");
       return true;
    }
}
