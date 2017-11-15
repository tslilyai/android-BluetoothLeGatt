package org.mpisws.sddrservice.embedded_social;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClientImpl;
import com.microsoft.embeddedsocial.autorest.SearchOperations;
import com.microsoft.embeddedsocial.autorest.SearchOperationsImpl;
import com.microsoft.embeddedsocial.autorest.SessionsOperations;
import com.microsoft.embeddedsocial.autorest.SessionsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperations;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicsOperations;
import com.microsoft.embeddedsocial.autorest.TopicsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.UsersOperations;
import com.microsoft.embeddedsocial.autorest.UsersOperationsImpl;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseTopicView;
import com.microsoft.embeddedsocial.autorest.models.PostCommentRequest;
import com.microsoft.embeddedsocial.autorest.models.PostCommentResponse;
import com.microsoft.embeddedsocial.autorest.models.PostSessionRequest;
import com.microsoft.embeddedsocial.autorest.models.PostSessionResponse;
import com.microsoft.embeddedsocial.autorest.models.PostTopicRequest;
import com.microsoft.embeddedsocial.autorest.models.PostTopicResponse;
import com.microsoft.embeddedsocial.autorest.models.PostUserRequest;
import com.microsoft.embeddedsocial.autorest.models.PostUserResponse;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.embeddedsocial.autorest.models.UserProfileView;
import com.microsoft.rest.ServiceException;
import com.microsoft.rest.ServiceResponse;

import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.lib.Identifier;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.mpisws.sddrservice.embedded_social.Tasks.ResponseTyp.AuthFailure;
import static org.mpisws.sddrservice.embedded_social.Tasks.ResponseTyp.DuplicateFailure;
import static org.mpisws.sddrservice.embedded_social.Tasks.ResponseTyp.NotFoundFailure;
import static org.mpisws.sddrservice.embedded_social.Tasks.ResponseTyp.OK;

/**
 * Created by tslilyai on 11/14/17.
 */

public class Tasks {
    private static final String TAG = Tasks.class.getSimpleName();
    private static Context context;

    /* ES specific objects and constants */
    public static final String OAUTH_TEMPLATE = "Google AK=%s|TK=%s";
    public static final String SESSION_TEMPLATE = "SocialPlus TK=%s";

    private static final String ESAPI_KEY = "2e5a1cc8-5eab-4dbd-8d6d-6a84eab23374";
    private static final String SDDR_ID = "0.0.0";
    private static final String TOPIC2_SUFFIX = "sddr";

    private static final int QUEUE_CAP = 1000;
    private static final int RETRIES = 3;

    private static String USER_HANDLE;
    private static String SESSION;
    private static Date SESSION_DATE;

    private static final OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
    private static Retrofit RETROFIT = new Retrofit.Builder()
            .baseUrl("https://ppe.embeddedsocial.microsoft.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient.build())
            .build();;
    private static EmbeddedSocialClient ESCLIENT = new EmbeddedSocialClientImpl("https://ppe.embeddedsocial.microsoft.com/");;
    private static UsersOperations ES_USEROPS = new UsersOperationsImpl(RETROFIT, ESCLIENT);
    private static TopicsOperations ES_TOPICS = new TopicsOperationsImpl(RETROFIT, ESCLIENT);;
    private static TopicCommentsOperations ES_TOPIC_COMMENTS = new TopicCommentsOperationsImpl(RETROFIT, ESCLIENT);
    private static SearchOperations ES_SEARCH = new SearchOperationsImpl(RETROFIT, ESCLIENT);
    private static SessionsOperations ES_SESSION = new SessionsOperationsImpl(RETROFIT, ESCLIENT);

    private static BlockingQueue<TaskTyp> taskTypList = new ArrayBlockingQueue<>(QUEUE_CAP);

    public static void setContext(Context newcontext) {
        context = newcontext;
    }

    public static void addTask(TaskTyp taskTyp) {
        try {
            taskTypList.put(taskTyp);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
    }

    public static TaskTyp getTask() {
        try {
            return taskTypList.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public enum ResponseTyp {
        DuplicateFailure,
        ServerFailure,
        AuthFailure,
        NotFoundFailure,
        ExceptionFailure,
        Unknown,
        OK
    }

    public enum TaskTyp {
        REGISTER_USER,
        SEND_MSG,
        SIGNIN_USER,
        CREATE_TOPIC,
        SIGNOUT_USER;

        public String firstname;
        public String lastname;
        public String msg;
        public MEncounter encounter;
        public String encounterID;
    }

    public static void exec_task(TaskTyp taskTyp) {
        if (taskTyp == null) {
            return;
        }
        ResponseTyp resp;
        switch(taskTyp) {
            case REGISTER_USER:
                if (taskTyp.firstname == null || taskTyp.lastname == null) {
                    return;
                }
                for (int i = 0; i < RETRIES; ++i) {
                    resp = registerUser(taskTyp.firstname, taskTyp.lastname);
                    // success or nonrecoverable error
                    if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                        return;
                    }
                }
                Log.d(TAG, "Failed to register");
                break;
            case SIGNIN_USER:
                for (int i = 0; i < RETRIES; ++i) {
                    resp = getnewsession();
                    // success or nonrecoverable error
                    if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                        return;
                    }
                }
                Log.d(TAG, "Failed to sign in");
                break;
            case SIGNOUT_USER:
                signout_user();
                break;
            case SEND_MSG:
                if (taskTyp.encounter == null || taskTyp.msg == null) {
                    return;
                }
                for (int i = 0; i < RETRIES; ++i) {
                    resp = send_msg(taskTyp.encounter, taskTyp.msg);
                    // success or nonrecoverable error
                    if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                        return;
                    }
                    else if (resp == AuthFailure) {
                        getnewsession();
                    }
                }
                Log.d(TAG, "Failed to send message");
                break;
            case CREATE_TOPIC:
                if (taskTyp.encounterID == null) {
                    return;
                }
                for (int i = 0; i < RETRIES; ++i) {
                    resp = create_topic(taskTyp.encounterID);
                    // success or nonrecoverable error
                    if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                        return;
                    }
                    else if (resp == AuthFailure) {
                        getnewsession();
                    }
                }
                Log.d(TAG, "Failed to create topic");
                break;
        }
    }

    private static ResponseTyp registerUser(String firstname, String lastname) {
        if (GoogleToken.getToken() == null) {
            Log.d(TAG, "Not registered with Google yet");
            GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, context);
            GNA.makeAuthRequest();
            return ResponseTyp.AuthFailure;
        } else if (USER_HANDLE == null || SESSION == null) {
            return getnewsession();
        }

        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GoogleToken.getToken());
        Log.d(TAG, "esAuth is " + esAuth);

        final PostUserRequest req = new PostUserRequest();
        req.setFirstName(firstname);
        req.setLastName(lastname);
        req.setInstanceId(SDDR_ID);

        ServiceResponse<PostUserResponse> serviceResponse;
        try {
            serviceResponse = ES_USEROPS.postUser(req, esAuth);
        } catch (ServiceException | IOException e) {
            return ResponseTyp.ExceptionFailure;
        }
        Response response = serviceResponse.getResponse();
        if (!response.isSuccess()) {
            return handleFailures(response);
        } else {
            Log.d(TAG, "Registered user!");
            USER_HANDLE = serviceResponse.getBody().getUserHandle();
            SESSION = serviceResponse.getBody().getSessionToken();
            SESSION_DATE = new Date();
            return OK;
        }
    }

    private static ResponseTyp getnewsession() {
        if (GoogleToken.getToken() == null) {
            Log.d(TAG, "Not registered with Google yet");
            GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, context);
            GNA.makeAuthRequest();
            return ResponseTyp.AuthFailure;
        }

        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GoogleToken.getToken());
        Log.d(TAG, "esAuth is " + esAuth);

        try {
            // find the user handle if we haven't set it yet
            if (USER_HANDLE == null) {
                ServiceResponse<UserProfileView> sResp = ES_USEROPS.getMyProfile(esAuth);
                if (!sResp.getResponse().isSuccess()) {
                    return handleFailures(sResp.getResponse());
                }
                Log.d(TAG, "Got user info");
                USER_HANDLE = sResp.getBody().getUserHandle();
            }
        
            // create a new session for this user
            final PostSessionRequest req = new PostSessionRequest();
            req.setUserHandle(USER_HANDLE);
            req.setInstanceId(SDDR_ID);
        
            ServiceResponse<PostSessionResponse> sResp2 = ES_SESSION.postSession(req, esAuth);
            Response response = sResp2.getResponse();
            if (!response.isSuccess()) {
                Log.d(TAG, "Getting new session failed");
                return handleFailures(response);
            } else {
                Log.d(TAG, "New session!");
                USER_HANDLE = sResp2.getBody().getUserHandle();
                SESSION = sResp2.getBody().getSessionToken();
                SESSION_DATE = new Date();
                return OK;
            }
        } catch (ServiceException | IOException e) {
            return ResponseTyp.ExceptionFailure;
        }
    }

    private static void signout_user() {
        SESSION = null;
        Log.d(TAG, "User signed out");
    }

    private static ResponseTyp create_topic(String title) {
        if (SESSION == null) {
            Log.d(TAG, "User not logged in");
            return ResponseTyp.AuthFailure;
        }
        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title);
        topicReq.setText("#" + title);
        ServiceResponse<PostTopicResponse> sResp = null;
        try {

            sResp = ES_TOPICS.postTopic(topicReq, String.format(SESSION_TEMPLATE, SESSION));

            // if the post request failed, return false
            if (!sResp.getResponse().isSuccess()) {
                Log.d(TAG, "Sending message failed");

                // someone already created this topic---create the secondary one
                if (sResp.getResponse().code() == 409) {
                    PostTopicRequest topicReq2 = new PostTopicRequest();
                    topicReq2.setPublisherType(PublisherType.USER);
                    topicReq2.setTitle(title + TOPIC2_SUFFIX);
                    topicReq2.setText("#" + title);
                    ServiceResponse<PostTopicResponse> sResp2 = ES_TOPICS.postTopic(topicReq2, String.format(SESSION_TEMPLATE, SESSION));

                    if (!sResp2.getResponse().isSuccess())
                        return handleFailures(sResp.getResponse());
                    else return OK;
                } else return handleFailures(sResp.getResponse());
            } else return OK;

        } catch (ServiceException e) {
            e.printStackTrace();
            return ResponseTyp.ExceptionFailure;
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseTyp.ExceptionFailure;
        }
    }

    private static ResponseTyp send_msg(MEncounter encounter, String msg) {
        if (SESSION == null) {
            Log.d(TAG, "User not logged in");
            return ResponseTyp.AuthFailure;
        }
        try {
            for (Identifier eid : encounter.getEncounterIDs(context)) {
                PostCommentRequest req = new PostCommentRequest();
                req.setText(msg);
                String topicHandle = "";

                ServiceResponse<FeedResponseTopicView> sResp = ES_SEARCH.getTopics("#" + eid.toString(), String.format(SESSION_TEMPLATE, SESSION));
                if (!sResp.getResponse().isSuccess()) {
                    Log.d(TAG, "Topic Error " + sResp.getResponse().code());
                    return handleFailures(sResp.getResponse());
                }

                List<TopicView> topics = sResp.getBody().getData();
                if (topics.size() != 2) {
                    Log.d(TAG, "Wrong number of topics with this EID " + eid.toString());
                    return ResponseTyp.Unknown;
                }
                for (TopicView topic : topics) {
                    topicHandle = topic.getTopicHandle();
                    ServiceResponse<PostCommentResponse> sResp2 = ES_TOPIC_COMMENTS.postComment(topicHandle, req, String.format(SESSION_TEMPLATE, SESSION));
                    if (!sResp2.getResponse().isSuccess()) {
                        Log.d(TAG, "Sending to topic failed");
                        return handleFailures(sResp2.getResponse());
                    }
                }
            }
            Log.d(TAG, "Messages sent ok");
            return OK;
            // deal with all the possible exceptions
        } catch (ServiceException e) {
            e.printStackTrace();
            return ResponseTyp.ExceptionFailure;
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseTyp.ExceptionFailure;
        }
    }

    private static ResponseTyp handleFailures(Response response) {
        if (response.code() == 409) {
            return DuplicateFailure;
        } else if (response.code() == 401) {
            return ResponseTyp.AuthFailure;
        } else if (response.code() == 500) {
            return ResponseTyp.ServerFailure;
        } else if (response.code() == 404) {
            return NotFoundFailure;
        } else {
            return ResponseTyp.Unknown;
        }
    }
}
