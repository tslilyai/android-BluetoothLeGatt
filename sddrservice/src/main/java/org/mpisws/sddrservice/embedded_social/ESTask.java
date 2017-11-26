package org.mpisws.sddrservice.embedded_social;

import android.app.Service;
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
import com.microsoft.rest.ServiceCall;
import com.microsoft.rest.ServiceCallback;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.mpisws.sddrservice.embedded_social.ESTask.Typ.LOGIN_GOOGLE;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESTask {
    private static final String TAG = ESTask.class.getSimpleName();
    private static Context context;
    public static void setContext(Context newcontext) {
        context = newcontext;
    }

    /* Task queue that is queried and emptied every waking cycle */
    private static final int QUEUE_CAP = 1000;
    private static final int RETRIES = 3;
    private static BlockingQueue<ESTask> taskList = new ArrayBlockingQueue<>(QUEUE_CAP);

    /* ES specific objects and constants */
    public static final String OAUTH_TEMPLATE = "Google AK=%s|TK=%s";
    public static final String SESSION_TEMPLATE = "SocialPlus TK=%s";
    private static final String ESAPI_KEY = "2e5a1cc8-5eab-4dbd-8d6d-6a84eab23374";
    private static final String SDDR_ID = "0.0.0";

    /* User-specific login and authentication information */
    private static String USER_HANDLE;
    private static String SESSION;
    private static Date SESSION_DATE;
    private static String GOOGLETOKEN;

    /* Static objects to perform HTTP requests */
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


    /* Flag that indicates whether topics (message-channels) should be created for each encounter formed */
    private static boolean addTopics;
    public static void setAddTopics(boolean bool) {
        addTopics = bool;
    }

    /* Instance variables */
    public Typ typ;
    public String firstname;
    public String lastname;
    public String googletoken;
    public String msg;
    public MEncounter encounter;
    public Identifier encounterID;
    public NotificationCallback notificationCallback;

    public ESTask(Typ typ) {
        this.typ = typ;
    }

    public enum Typ {
        /* Must be called first to register a permanent "Google" identity with each user */
        LOGIN_GOOGLE,
        /* Registers the user with the SDDR service */
        REGISTER_USER,
        /* Creates a topic (used to communicate between encounters) */
        CREATE_TOPIC,
        /* Gets the notifications (comments posted on topics, i.e., messages between
        encounter participants) and returns them as a list of encounter IDs mapping to
        messages from that ID
         */
        GET_NOTIFICATIONS,
        /* Sends a message to a particular encounter */
        SEND_MSG
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
        if (task == null || context == null) {
            return;
        }
        // return if we can't possible authenticate this request
        if (task.typ != LOGIN_GOOGLE && GOOGLETOKEN == null) {
            return;
        }

        switch(task.typ) {
            case LOGIN_GOOGLE: {
                if (task.googletoken == null)
                    return;
                loginGoogle(task.googletoken);
                break;
            }
            case REGISTER_USER: {
                if (task.firstname == null || task.lastname == null)
                    return;
                registerUser(task.firstname, task.lastname, 0);
                break;
            }
            case SEND_MSG: {
                if (task.encounter == null || task.msg == null)
                    return;
                send_msg(task.encounter, task.msg, 0);
                break;
            }
            case CREATE_TOPIC: {
                // don't respond to create topic tasks unless some application wants to create topics
                if (!addTopics)
                    return;
                if (task.encounterID == null)
                    return;
                create_topic(task.encounterID, 0);
                break;
            }
            case GET_NOTIFICATIONS: {
                if (task.notificationCallback == null) {
                    return;
                }
                get_notifications(task.notificationCallback, 0);
                break;
            }
            default:
                Log.d(TAG, "Bad task type");
        }
    }

    private static void loginGoogle(String googletoken) {
        if (googletoken == null) {
            Log.d(TAG, "Not registered with Google yet");
        } else {
            GOOGLETOKEN = googletoken;
        }
    }

    private static void registerUser(final String firstname, final String lastname, final int retries) {
        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GOOGLETOKEN);
        Log.d(TAG, "esAuth is " + esAuth);

        final PostUserRequest req = new PostUserRequest();
        req.setFirstName(firstname);
        req.setLastName(lastname);
        req.setInstanceId(SDDR_ID);

        ServiceCallback<PostUserResponse> serviceCallback = new ServiceCallback<PostUserResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.d(TAG, "Failed to register user, " + t.getMessage());
                if (retries < RETRIES) {
                    registerUser(firstname, lastname, retries+1);
                }
            }
            @Override
            public void success(ServiceResponse<PostUserResponse> result) {
                Response response = result.getResponse();
                Log.d(TAG, "Registered user!");
                USER_HANDLE = result.getBody().getUserHandle();
                SESSION = result.getBody().getSessionToken();
                SESSION_DATE = new Date();
            }
        };
        ES_USEROPS.postUserAsync(req, esAuth, serviceCallback);
    }

    private static void getnewsession(final int retries) {
        final String esAuth = String.format(OAUTH_TEMPLATE, ESAPI_KEY, GOOGLETOKEN);
        Log.d(TAG, "esAuth is " + esAuth);

        // find the user handle if we haven't set it yet
        if (USER_HANDLE == null) {
            ServiceCallback<UserProfileView> serviceCallback = new ServiceCallback<UserProfileView>() {
                @Override
                public void failure(Throwable t) {
                    Log.d(TAG, "Failed to register user, " + t.getMessage());
                    if (retries < RETRIES) {
                        getnewsession(retries + 1);
                    }
                }
                @Override
                public void success(ServiceResponse<UserProfileView> sResp) {
                    Log.d(TAG, "Got user info");
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
                Log.d(TAG, "Getting new session failed");
                if (retries < RETRIES) {
                    getnewsession(retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<PostSessionResponse> sResp) {
                Log.d(TAG, "New session!");
                USER_HANDLE = sResp.getBody().getUserHandle();
                SESSION = sResp.getBody().getSessionToken();
                SESSION_DATE = new Date();
            }
        };
        ES_SESSION.postSessionAsync(req, esAuth, serviceCallback);
    }

    private static void create_topic(final Identifier title, final int retries) {
        checkLoginStatus();

        Log.d(TAG, "Creating topic " + title);
        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title.toString());
        topicReq.setText(USER_HANDLE);
        ServiceCallback<PostTopicResponse> serviceCallback = new ServiceCallback<PostTopicResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.d(TAG, "Posting topic failed");
                if (retries < RETRIES) {
                    create_topic(title, retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<PostTopicResponse> result) {
                Log.d(TAG, "Created topic for EID " + title);
            }
        };
        ES_TOPICS.postTopicAsync(topicReq, String.format(SESSION_TEMPLATE, SESSION), serviceCallback);
    }

    private static void send_msg(final MEncounter encounter, final String msg, final int retries) {
        checkLoginStatus();

        // TODO this sends to all of the different encounterIDs associated with this encounter
        Log.d(TAG, "Sending message to " + encounter.getEncounterIDs(context).size() + " encounters");
        final PostCommentRequest req = new PostCommentRequest();
        req.setText(msg);
        final String auth = String.format(SESSION_TEMPLATE, SESSION);
        for (final Identifier eid : encounter.getEncounterIDs(context)) {
            final ServiceCallback<FeedResponseTopicView> serviceCallback = new ServiceCallback<FeedResponseTopicView>() {
                @Override
                public void failure(Throwable t) {
                    Log.d(TAG, "Topic Error");
                    if (retries < RETRIES) {
                        send_msg(encounter, msg, retries + 1);
                    }
                }
                @Override
                public void success(ServiceResponse<FeedResponseTopicView> result) {
                    List<TopicView> topics = result.getBody().getData();
                    if (topics.size() == 0) {
                        Log.d(TAG, "Too few topics with this EID, need to create " + eid.toString());
                        create_topic(eid, 0);
                    } else if (topics.size() == 1) {
                        if (topics.get(0).getText().compareTo(USER_HANDLE) == 0) {
                        } else {
                            Log.d(TAG, "We don't have a topic for this EID, need to create " + eid.toString());
                            create_topic(eid, 0);
                        }
                    }
                    // send to the topic with text not equal to our own UUID
                    Log.d(TAG, "USER 0: " + ((topics.get(0).getText()) + " " + USER_HANDLE));
                    Log.d(TAG, "USER 1: " + ((topics.get(1).getText()) + " " + USER_HANDLE));
                    String topicHandle = ((topics.get(0).getText()).compareTo(USER_HANDLE) == 0) ?
                            topics.get(1).getTopicHandle() : topics.get(0).getTopicHandle();
                    postComment(eid.toString(), topicHandle, req, auth, 0);
                }
            };
            ES_SEARCH.getTopicsAsync(eid.toString(), auth, serviceCallback);
        }
    }
    private static void postComment(final String eid, final String topicHandle, final PostCommentRequest req, final String ESAuth, final int retries) {
         ServiceCallback<PostCommentResponse> serviceCallback = new ServiceCallback<PostCommentResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.d(TAG, "PostComment to topic failed");
                if (retries < RETRIES) {
                    postComment(eid, topicHandle, req, ESAuth, retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<PostCommentResponse> result) {
                Log.d(TAG, "Messages sent to EID " + eid + ": " + req.getText());
            }
        };
        ES_TOPIC_COMMENTS.postCommentAsync(topicHandle, req, ESAuth, serviceCallback);
    }

    private static void get_notifications(final NotificationCallback notificationCallback, final int retries) {
        checkLoginStatus();

        Log.d(TAG, "Getting notifications");
        final String auth = String.format(SESSION_TEMPLATE, SESSION);
        ServiceCallback<FeedResponseActivityView> serviceCallback = new ServiceCallback<FeedResponseActivityView>() {
           @Override
           public void failure(Throwable t) {
               Log.d(TAG, "Failed to get notifications");
               if (retries < RETRIES) {
                   get_notifications(notificationCallback, retries + 1);
               }
           }
           @Override
           public void success(ServiceResponse<FeedResponseActivityView> result) {
               Map<String, List<String>> messages = new HashMap<>();
               String readActivityHandle = null;
               String commentHandle, topicHandle, msg, encounterID;
               for (ActivityView view : result.getBody().getData()) {
                   // we've seen all the unread messages by now
                   if (!view.getUnread()) {
                       break;
                   }
                   Log.d(TAG, "New unread notification!");
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

                    /* TODO getting messages synchronously for now so that we can return a list of
                         all notification messages
                    */
                   ServiceResponse<CommentView> sResp2 = null;
                   try {
                       sResp2 = ES_COMMENTS.getComment(commentHandle, auth);
                       if (!sResp2.getResponse().isSuccess()) {
                           Log.d(TAG, "Topic Error " + sResp2.getResponse().code());
                           get_notifications(notificationCallback, retries + 1);
                       }
                       msg = sResp2.getBody().getText();
                       Log.d(TAG, "Got message " + msg);

                       // get the encounterID
                       topicHandle = sResp2.getBody().getTopicHandle();
                       ServiceResponse<TopicView> sResp3 = ES_TOPICS.getTopic(topicHandle, auth);
                       if (!sResp3.getResponse().isSuccess()) {
                           Log.d(TAG, "Topic Error " + sResp3.getResponse().code());
                           get_notifications(notificationCallback, retries + 1);
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
                   } catch (ServiceException | IOException e) {
                       e.printStackTrace();
                   }
               }
               notificationCallback.onReceiveMessages(messages);

               if (readActivityHandle != null) {
                   updateReadNotifs(readActivityHandle, auth, 0);
              }
           }
       };
       ES_NOTIFS.getNotificationsAsync(auth, serviceCallback);
    }
    private static void updateReadNotifs(final String readActivityHandle, final String auth, final int retries) {
        PutNotificationsStatusRequest req = new PutNotificationsStatusRequest();
        req.setReadActivityHandle(readActivityHandle);

        ServiceCallback<Object> serviceCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.d(TAG, "Failed to update notification status");
                if (retries < RETRIES) {
                    updateReadNotifs(readActivityHandle, auth, retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<Object> result) {
                Log.d(TAG, "Set read activity to latest notification");
            }
        };
        ES_NOTIFS.putNotificationsStatusAsync(req, auth, serviceCallback);
    }

    private static void checkLoginStatus() {
       if (SESSION == null) {
            Log.d(TAG, "User not logged in");
            getnewsession(0);
        }
    }
}
