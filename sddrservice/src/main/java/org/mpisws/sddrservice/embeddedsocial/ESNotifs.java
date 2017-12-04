package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.microsoft.embeddedsocial.autorest.CommentsOperations;
import com.microsoft.embeddedsocial.autorest.CommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.MyNotificationsOperations;
import com.microsoft.embeddedsocial.autorest.MyNotificationsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicsOperations;
import com.microsoft.embeddedsocial.autorest.TopicsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.autorest.models.ActivityView;
import com.microsoft.embeddedsocial.autorest.models.CommentView;
import com.microsoft.embeddedsocial.autorest.models.ContentType;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseActivityView;
import com.microsoft.embeddedsocial.autorest.models.PutNotificationsStatusRequest;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceException;
import com.microsoft.rest.ServiceResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import retrofit2.Retrofit;

import static org.mpisws.sddrservice.embeddedsocial.ESTask.RETRIES;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();

    private Context context;
    private TopicsOperations ES_TOPICS;
    private CommentsOperations ES_COMMENTS;
    private MyNotificationsOperations ES_NOTIFS;

    /* Flag that indicates whether topics (message-channels) should be created for each encounter formed */
    private static boolean addTopics;
    public static void setAddTopics(boolean bool) {
        addTopics = bool;
    }

    public interface NotificationCallback {
        // unread messages are returned with the most recent message first in the list
        public void onReceiveMessages(Map<String, List<String>> messages);
    }

    public ESNotifs(Context context, Retrofit RETROFIT, EmbeddedSocialClient ESCLIENT) {
        this.context = context;
        ES_TOPICS = new TopicsOperationsImpl(RETROFIT, ESCLIENT);;
        ES_COMMENTS = new CommentsOperationsImpl(RETROFIT, ESCLIENT);
        ES_NOTIFS = new MyNotificationsOperationsImpl(RETROFIT, ESCLIENT);
        this.context = context;
    }

    private class getMsgRunnable implements Runnable {
        int me;
        String commentHandle;
        String auth;
        List<Pair<String, String>> msgs;

        public getMsgRunnable(int me, String commentHandle, String auth, List<Pair<String, String>> msgs) {
            this.me = me;
            this.commentHandle = commentHandle;
            this.auth = auth;
            this.msgs = msgs;
        }

        @Override
        public void run() {
            ServiceResponse<CommentView> sResp2;
            try {
                sResp2 = ES_COMMENTS.getComment(commentHandle, auth);
                if (!sResp2.getResponse().isSuccess()) {
                    Log.v(TAG, "Topic Error " + sResp2.getResponse().code());
                }
                String msg = sResp2.getBody().getText();
                Log.v(TAG, "Got message " + msg);

                // get the encounterID
                String topicHandle = sResp2.getBody().getTopicHandle();
                ServiceResponse<TopicView> sResp3 = ES_TOPICS.getTopic(topicHandle, auth);
                if (!sResp3.getResponse().isSuccess()) {
                    Log.v(TAG, "Topic Error " + sResp3.getResponse().code());
                }
                String encounterID = sResp3.getBody().getTitle();
                msgs.set(me, new Pair<>(encounterID, msg));
            } catch (ServiceException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void get_notifications(final String auth, final NotificationCallback notificationCallback, final int retries) {
        Log.v(TAG, "Getting notifications");
        ServiceCallback<FeedResponseActivityView> serviceCallback = new ServiceCallback<FeedResponseActivityView>() {
           @Override
           public void failure(Throwable t) {
               Log.v(TAG, "Failed to get notifications");
               if (retries < RETRIES) {
                   get_notifications(auth, notificationCallback, retries + 1);
               }
           }
           @Override
           public void success(ServiceResponse<FeedResponseActivityView> result) {
               if (!result.getResponse().isSuccess()) {
                   failure(new Throwable());
                   return;
               }
               Map<String, List<String>> messages = new HashMap<>();
               String readActivityHandle = null;
               String commentHandle, topicHandle, msg, encounterID;
               List<ActivityView> activities = result.getBody().getData();
               List<Thread> msgThreads = new ArrayList<>();
               List<Pair<String, String>> msgs = new ArrayList<>();

               for (int i = 0; i < activities.size(); i++) {
                   ActivityView view = activities.get(i);
                   // we've seen all the unread messages by now
                   if (!view.getUnread()) {
                       break;
                   }
                   Log.v(TAG, "New unread notification!");
                   if (view.getActivityType() != ActivityType.COMMENT) {
                       Log.v(TAG, "Not a Comment");
                       continue; // ignore anything that isn't a comment for now
                   }
                   if (view.getActedOnContent().getContentType() != ContentType.TOPIC) {
                       Log.v(TAG, "Comment not posted on topic");
                       continue; // ignore comments not posted to topics (?)
                   }

                   // get the unread message
                   commentHandle = view.getActivityHandle();
                   // set the latest read comment
                   if (readActivityHandle == null) {
                       readActivityHandle = commentHandle;
                   }
                   msgs.add(null);
                   msgThreads.add(new Thread(new getMsgRunnable(i, commentHandle, auth, msgs)));
               }
               // Spawn and wait for all threads to get all comments
               for (Thread t : msgThreads) {
                    t.start();
               }
               for (Thread t : msgThreads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
               }
               for (Pair<String, String> p : msgs) {
                   if (p == null) {
                       Log.v(TAG, "Failed to get a comment! Retry " + retries);
                       get_notifications(auth, notificationCallback, retries + 1);
                       return;
                   }
                   if (messages.get(p.first) == null) {
                        List<String> msgList = new LinkedList<String>();
                        msgList.add(p.second);
                        messages.put(p.first, msgList);
                   } else {
                        messages.get(p.first).add(p.second);
                   }
                   Log.v(TAG, "Messages for encounterID " + p.first + " is size " + messages.get(p.first).size());
               }
               notificationCallback.onReceiveMessages(messages);

               if (readActivityHandle != null) {
                   updateReadNotifs(readActivityHandle, auth, 0);
              }
           }
       };
       ES_NOTIFS.getNotificationsAsync(auth, serviceCallback);
    }
    private void updateReadNotifs(final String readActivityHandle, final String auth, final int retries) {
        PutNotificationsStatusRequest req = new PutNotificationsStatusRequest();
        req.setReadActivityHandle(readActivityHandle);

        ServiceCallback<Object> serviceCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed to update notification status");
                if (retries < RETRIES) {
                    updateReadNotifs(readActivityHandle, auth, retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<Object> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                Log.v(TAG, "Set read activity to latest notification");
            }
        };
        ES_NOTIFS.putNotificationsStatusAsync(req, auth, serviceCallback);
    }
}
