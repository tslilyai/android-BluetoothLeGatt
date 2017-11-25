package org.mpisws.sddrservice.embedded_social;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.CommentsOperations;
import com.microsoft.embeddedsocial.autorest.CommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClientImpl;
import com.microsoft.embeddedsocial.autorest.MyNotificationsOperations;
import com.microsoft.embeddedsocial.autorest.MyNotificationsOperationsImpl;
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
import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.autorest.models.ActivityView;
import com.microsoft.embeddedsocial.autorest.models.CommentView;
import com.microsoft.embeddedsocial.autorest.models.ContentType;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseActivityView;
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
import com.microsoft.embeddedsocial.autorest.models.PutNotificationsStatusRequest;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.embeddedsocial.autorest.models.UserProfileView;
import com.microsoft.rest.ServiceException;
import com.microsoft.rest.ServiceResponse;

import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.lib.Identifier;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.mpisws.sddrservice.embedded_social.ESTask.ResponseTyp.*;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESTask {
    private static final String TAG = ESTask.class.getSimpleName();
    private static Context context;

    /* ES specific objects and constants */
    public static final String OAUTH_TEMPLATE = "Google AK=%s|TK=%s";
    public static final String SESSION_TEMPLATE = "SocialPlus TK=%s";

    private static final String ESAPI_KEY = "2e5a1cc8-5eab-4dbd-8d6d-6a84eab23374";
    private static final String SDDR_ID = "0.0.0";

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
    private static MyNotificationsOperations ES_NOTIFS = new MyNotificationsOperationsImpl(RETROFIT, ESCLIENT);
    private static CommentsOperations ES_COMMENTS = new CommentsOperationsImpl(RETROFIT, ESCLIENT);

    private static BlockingQueue<ESTask> taskList = new ArrayBlockingQueue<>(QUEUE_CAP);
    private static boolean addTopics;

    public static void setAddTopics(boolean bool) {
        addTopics = bool;
    }
    public static void setContext(Context newcontext) {
        context = newcontext;
    }

    /* Instance variables */
    public Typ typ;
    public String firstname;
    public String lastname;
    public String msg;
    public MEncounter encounter;
    public Identifier encounterID;
    public NotificationCallback notificationCallback;
    public int retries = 0;

    public ESTask(Typ typ) {
        this.typ = typ;
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

    public enum Typ {
        GET_NOTIFICATIONS,
        REGISTER_USER,
        SEND_MSG,
        SIGNIN_USER,
        CREATE_TOPIC,
        SIGNOUT_USER;
    }

    public interface NotificationCallback {
        // unread messages are returned with the most recent message first in the list
        public void onReceiveMessages(Map<String, List<String>> messages);
    }

    public static void addTask(ESTask task) {
        try {
            taskList.put(task);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
    }

    public static ESTask getTask() {
        return taskList.poll();
    }

    public static void exec_task(ESTask task) {
        if (task == null) {
            return;
        }
        ResponseTyp resp;
        switch(task.typ) {
            case REGISTER_USER: {
                if (task.retries >= RETRIES)
                    return;

                if (task.firstname == null || task.lastname == null)
                    return;

                resp = registerUser(task.firstname, task.lastname);
                if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                    return;
                }

                Log.d(TAG, "Failed to register");
                task.retries++;
                ESTask.addTask(task);
                break;
            }
            case SIGNIN_USER: {
                if (task.retries >= RETRIES)
                    return;

                resp = getnewsession();
                if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                    return;
                }

                Log.d(TAG, "Failed to sign in");
                task.retries++;
                ESTask.addTask(task);
                break;
            }
            case SIGNOUT_USER: {
                signout_user();
                break;
            }
            case SEND_MSG: {
                if (task.retries >= RETRIES)
                    return;
                if (task.encounter == null || task.msg == null)
                    return;

                resp = send_msg(task.encounter, task.msg);
                if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                    return;
                }

                Log.d(TAG, "Failed to send message");
                task.retries++;
                ESTask.addTask(task);
                break;
            }
            case CREATE_TOPIC: {
                // don't respond to create topic tasks unless some application wants to create topics
                if (!addTopics)
                    return;
                if (task.retries >= RETRIES)
                    return;
                if (task.encounterID == null)
                    return;
                resp = create_topic(task.encounterID);
                if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                    return;
                }
                task.retries++;
                ESTask.addTask(task);
                break;
            }
            case GET_NOTIFICATIONS: {
                if (task.retries >= RETRIES)
                    return;
                if (task.notificationCallback == null) {
                    return;
                }
                resp = get_notifications(task.notificationCallback);
                if (resp == OK || resp == DuplicateFailure || resp == NotFoundFailure) {
                    return;
                }

                Log.d(TAG, "Failed to get notifications");
                task.retries++;
                ESTask.addTask(task);
                break;
            }
            default:
                Log.d(TAG, "Bad task type");
        }
    }

    private static ResponseTyp registerUser(String firstname, String lastname) {
        if (GoogleToken.getToken() == null) {
            Log.d(TAG, "Not registered with Google yet");
            GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, context);
            GNA.makeAuthRequest();
            return AuthFailure;
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
            return ExceptionFailure;
        }
        Response response = serviceResponse.getResponse();
        if (!response.isSuccess()) {
            Log.d(TAG, "Failed to register user");
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
            return AuthFailure;
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
            return ExceptionFailure;
        }
    }

    private static void signout_user() {
        SESSION = null;
        Log.d(TAG, "User signed out");
    }

    private static ResponseTyp create_topic(Identifier title) {
        Log.d(TAG, "Creating topic " + title);
        ResponseTyp resp = checkLoginStatus();
        if (resp != OK) return resp;

        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title.toString());
        topicReq.setText(USER_HANDLE);
        ServiceResponse<PostTopicResponse> sResp;
        try {
            sResp = ES_TOPICS.postTopic(topicReq, String.format(SESSION_TEMPLATE, SESSION));
            if (!sResp.getResponse().isSuccess()) {
                Log.d(TAG, "Posting topic failed");
                return handleFailures(sResp.getResponse());
            } else {
                Log.d(TAG, "Created topic for EID " + title);
                return OK;
            }
        } catch (ServiceException | IOException e) {
            e.printStackTrace();
            return ExceptionFailure;
        }
    }

    private static ResponseTyp send_msg(MEncounter encounter, String msg) {
        ResponseTyp resp = checkLoginStatus();
        if (resp != OK) return resp;

        Log.d(TAG, "Sending message to " + encounter.getEncounterIDs(context).size() + " encounters");
        try {
            for (Identifier eid : encounter.getEncounterIDs(context)) {
                PostCommentRequest req = new PostCommentRequest();
                req.setText(msg);

                ServiceResponse<FeedResponseTopicView> sResp = ES_SEARCH.getTopics(eid.toString(), String.format(SESSION_TEMPLATE, SESSION));
                if (!sResp.getResponse().isSuccess()) {
                    Log.d(TAG, "Topic Error " + sResp.getResponse().code());
                    return handleFailures(sResp.getResponse());
                }

                List<TopicView> topics = sResp.getBody().getData();
                if (topics.size() == 0) {
                    Log.d(TAG, "Too few topics with this EID, need to create " + eid.toString());
                    create_topic(eid);
                    return Unknown;
                } else if (topics.size() == 1) {
                    if (topics.get(0).getText().compareTo(USER_HANDLE) == 0) {
                        return Unknown;
                    } else {
                        Log.d(TAG, "We don't have a topic for this EID, need to create " + eid.toString());
                        create_topic(eid);
                        return Unknown;
                    }
                }

                // send to the topic with text not equal to our own UUID
                Log.d(TAG, "USER 0: " + ((topics.get(0).getText()) + " " + USER_HANDLE));
                Log.d(TAG, "USER 1: " + ((topics.get(1).getText()) + " " + USER_HANDLE));
                String topicHandle = ((topics.get(0).getText()).compareTo(USER_HANDLE) == 0) ?
                        topics.get(1).getTopicHandle() : topics.get(0).getTopicHandle();
                ServiceResponse<PostCommentResponse> sRespTH = ES_TOPIC_COMMENTS.postComment(topicHandle, req, String.format(SESSION_TEMPLATE, SESSION));
                if (!sRespTH.getResponse().isSuccess()) {
                    Log.d(TAG, "Sending to topic failed");
                    return handleFailures(sRespTH.getResponse());
                }
                Log.d(TAG, "Messages sent to EID " + eid + ": " + msg);
            }
            return OK;
            // deal with all the possible exceptions
        } catch (ServiceException | IOException e) {
            e.printStackTrace();
            return ExceptionFailure;
        }
    }

    private static ResponseTyp get_notifications(NotificationCallback notificationCallback) {
        ResponseTyp resp = checkLoginStatus();
        if (resp != OK) return resp;

        String auth = String.format(SESSION_TEMPLATE, SESSION);
        Map<String, List<String>> messages = new HashMap<>();
        String readActivityHandle = null;
        try {
            Log.d(TAG, "Getting notifications");
            ServiceResponse<FeedResponseActivityView> sResp = ES_NOTIFS.getNotifications(auth);
            if (!sResp.getResponse().isSuccess()) {
                Log.d(TAG, "Notif Error " + sResp.getResponse().code());
                return handleFailures(sResp.getResponse());
            }

            String commentHandle, topicHandle, msg, encounterID;
            for (ActivityView view : sResp.getBody().getData()) {
                Log.d(TAG, "New unread notification!");
                // we've seen all the unread messages by now
                if (!view.getUnread()) {
                    break;
                }
                if (view.getActivityType() != ActivityType.COMMENT) {
                    Log.d(TAG, "Not a Comment");
                    continue; // ignore anything that isn't a comment for now
                }
                if (view.getActedOnContent().getContentType() != ContentType.TOPIC) {
                    Log.d(TAG, "Comment not posted on topic");
                    continue; // ignore comments not posted to topics (?)
                }

                // get the unread message
                commentHandle = view.getActivityHandle();
                // set the latest read comment
                if (readActivityHandle == null) {
                    readActivityHandle = commentHandle;
                }
                ServiceResponse<CommentView> sResp2 = ES_COMMENTS.getComment(commentHandle, auth);
                if (!sResp2.getResponse().isSuccess()) {
                    Log.d(TAG, "Topic Error " + sResp2.getResponse().code());
                    return handleFailures(sResp2.getResponse());
                }
                msg = sResp2.getBody().getText();
                Log.d(TAG, "Got message " + msg);

                // get the encounterID
                topicHandle = sResp2.getBody().getTopicHandle();
                ServiceResponse<TopicView> sResp3 = ES_TOPICS.getTopic(topicHandle, auth);
                if (!sResp3.getResponse().isSuccess()) {
                    Log.d(TAG, "Topic Error " + sResp3.getResponse().code());
                    return handleFailures(sResp3.getResponse());
                }
                encounterID = sResp3.getBody().getTitle();
                if (messages.get(encounterID) == null) {
                    List<String> msgs = new LinkedList<String>();
                    msgs.add(msg);
                    messages.put(encounterID, msgs);
                } else {
                    messages.get(encounterID).add(msg);
                }
                Log.d(TAG, "Messages for encounterID " + encounterID + " is size " + messages.get(encounterID).size());
            }
            notificationCallback.onReceiveMessages(messages);

            // set these messages as read if we've read any unread comments
            if (readActivityHandle != null) {
                PutNotificationsStatusRequest req = new PutNotificationsStatusRequest();
                req.setReadActivityHandle(readActivityHandle);

                ServiceResponse<Object> sResp4 = ES_NOTIFS.putNotificationsStatus(req, auth);
                if (!sResp4.getResponse().isSuccess())
                    return handleFailures(sResp4.getResponse());
                return OK;
            }
            Log.d(TAG, "Finished handling all notifications");
            return OK;
        } catch (ServiceException | IOException e) {
            e.printStackTrace();
            return ExceptionFailure;
        }
    }

    private static ResponseTyp checkLoginStatus() {
        if (GoogleToken.getToken() == null) {
            Log.d(TAG, "Not registered with Google yet");
            GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, context);
            GNA.makeAuthRequest();
            return AuthFailure;
        }
        if (SESSION == null) {
            Log.d(TAG, "User not logged in");
            getnewsession();
            return AuthFailure;
        }
        return OK;
    }

    private static ResponseTyp handleFailures(Response response) {
        if (response.code() == 409) {
            Log.d(TAG, "Duplicate Failure");
            return DuplicateFailure;
        } else if (response.code() == 401) {
            return AuthFailure;
        } else if (response.code() == 500) {
            return ServerFailure;
        } else if (response.code() == 404) {
            return NotFoundFailure;
        } else {
            return Unknown;
        }
    }
}
