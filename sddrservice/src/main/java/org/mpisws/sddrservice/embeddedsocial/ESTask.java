package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClientImpl;

import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESTask {
    private static final String TAG = ESTask.class.getSimpleName();

    /* ES specific objects and constants */
    public static final String OAUTH_TEMPLATE = "Google AK=%s|TK=%s";
    public static final String SESSION_TEMPLATE = "SocialPlus TK=%s";
    protected static final String ESAPI_KEY = "2e5a1cc8-5eab-4dbd-8d6d-6a84eab23374";
    protected static final String SDDR_ID = "0.0.0";

    /* Task queue that is queried and emptied every waking cycle */
    protected static final int RETRIES = 3;
    private static final int QUEUE_CAP = 1000;
    private static BlockingQueue<ESTask> taskList = new ArrayBlockingQueue<>(QUEUE_CAP);

    /* Objects to perform HTTP requests */
    private static OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
    private static Retrofit RETROFIT = new Retrofit.Builder()
            .baseUrl("https://ppe.embeddedsocial.microsoft.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient.build())
            .build();
    private static EmbeddedSocialClient ESCLIENT = new EmbeddedSocialClientImpl("https://ppe.embeddedsocial.microsoft.com/");
    private static ESUser esUser;
    private static ESMsgTopics esMsgTopics;
    private static ESNotifs esNotifs;


    /* Instance variables */
    private Typ typ;
    public String firstname;
    public String lastname;
    public String googletoken;
    public String msg;
    public Identifier encounterID;
    public NotificationCallback notificationCallback;
    public MsgsCallback msgsCallback;

    public enum Typ {
        /* Must be called first to register a permanent "Google" identity with each user */
        LOGIN_GOOGLE,
        /* Registers the user with the SDDR service */
        REGISTER_USER,
        /* Creates a topic (used to communicate between encounters) */
        CREATE_TOPIC,
        /* Gets the notifications (comments posted on topics, i.e., messages between
        encounter participants) and returns them as a queue of Notifs (encounter ID + msg + timestamp)
         */
        GET_NOTIFICATIONS,
        /* Gets the messages associated with the specified encounterID */
        GET_MSGS,
        /* Sends a message to a particular encounter */
        SEND_MSG,
        /* By default, create a topic / enable messaging when an encounter is formed */
        MESSAGING_ON_DEFAULT,
        /* By default, do not create a topic / enable messaging when an encounter is formed */
        MESSAGING_OFF_DEFAULT
     }

    public interface NotificationCallback {
        void onReceiveNotifs(Queue<ESNotifs.Notif> notifs);
    }

    public interface MsgsCallback {
        void onReceiveMessages(List<String> messages);
    }

    public ESTask(Typ typ) {
        this.typ = typ;
    }

    public static void initialize_static_vars() {
        esUser = new ESUser(RETROFIT, ESCLIENT);
        esMsgTopics = new ESMsgTopics(RETROFIT, ESCLIENT);
        esNotifs = new ESNotifs(RETROFIT, ESCLIENT);
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
        // return if we can't possible authenticate this request
        if (task.typ != Typ.LOGIN_GOOGLE && esUser.GOOGLETOKEN == null) {
            return;
        }

        switch(task.typ) {
            case LOGIN_GOOGLE: {
                if (task.googletoken == null)
                    return;
                esUser.loginGoogle(task.googletoken);
                break;
            }
            case REGISTER_USER: {
                if (task.firstname == null || task.lastname == null)
                    return;
                esUser.registerUser(task.firstname, task.lastname, 0);
                break;
            }
            case GET_NOTIFICATIONS: {
                if (task.notificationCallback == null) {
                    return;
                }
                if (esUser.checkLoginStatus() == true) {
                    esNotifs.get_notifications(esUser.AUTH, task.notificationCallback, 0);
                }
                break;
            }
            case GET_MSGS: {
                if (task.encounterID == null || task.msgsCallback == null)
                    return;
                if (esUser.checkLoginStatus() == true) {
                    esMsgTopics.get_encounter_msgs(task.encounterID, task.msgsCallback, esUser.AUTH, 0);
                }
                break;
            }
            case SEND_MSG: {
                if (task.encounterID == null || task.msg == null)
                    return;
                if (esUser.checkLoginStatus() == true) {
                    Log.d(TAG, "Sending message");
                    esMsgTopics.send_msg(esUser.AUTH, task.encounterID, task.msg, 0);
                }
                break;
            }
            case MESSAGING_ON_DEFAULT: {
                esMsgTopics.setAddTopics(true);
                break;
            }
            case MESSAGING_OFF_DEFAULT: {
                esMsgTopics.setAddTopics(false);
                break;
            }
            case CREATE_TOPIC: {
                // don't respond to create topic tasks unless some application wants to create topics
                if (!esMsgTopics.addTopics)
                    return;
                if (task.encounterID == null)
                    return;
                if (esUser.checkLoginStatus() == true) {
                    Log.d(TAG, "Creating topic");
                    esMsgTopics.create_topic(esUser.AUTH, task.encounterID, 0);
                }
                break;
            }
            default:
                Log.v(TAG, "Bad task type");
        }
    }
}
