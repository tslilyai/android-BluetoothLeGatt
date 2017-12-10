package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

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
import com.microsoft.rest.ServiceResponse;

import org.joda.time.DateTime;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Retrofit;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();

    public class Notif {
        private Identifier eid;
        private String msg;
        private DateTime timestamp;

        Notif(Identifier eid, String msg, DateTime timestamp) {
            this.eid = eid;
            this.msg = msg;
            this.timestamp = timestamp;
        }
        public Identifier getEid() {
            return eid;
        }
        public String getMsg() {
            return msg;
        }
        public DateTime getTimestamp() {
            return timestamp;
        }
    }
    private TopicsOperations ES_TOPICS;
    private CommentsOperations ES_COMMENTS;
    private MyNotificationsOperations ES_NOTIFS;

    private ConcurrentLinkedQueue<Notif> notifQueue;
    private boolean failed = false;
    private AtomicInteger doneCount;


    public ESNotifs(Retrofit RETROFIT, EmbeddedSocialClient ESCLIENT) {
        ES_TOPICS = new TopicsOperationsImpl(RETROFIT, ESCLIENT);;
        ES_COMMENTS = new CommentsOperationsImpl(RETROFIT, ESCLIENT);
        ES_NOTIFS = new MyNotificationsOperationsImpl(RETROFIT, ESCLIENT);
        notifQueue = new ConcurrentLinkedQueue<>();
        doneCount = new AtomicInteger(0);
    }

    protected void get_notifications(final String auth, final ESMsgs.NotificationCallback notificationCallback) {
        Log.v(TAG, "Getting notifications");
        failed = false;
        notifQueue.clear();
        doneCount.set(0);

        ServiceCallback<FeedResponseActivityView> serviceCallback = new ServiceCallback<FeedResponseActivityView>() {
           @Override
           public void failure(Throwable t) {
               Log.v(TAG, "Failed to get notifications");
           }
           @Override
           public void success(ServiceResponse<FeedResponseActivityView> result) {
               if (!result.getResponse().isSuccess()) {
                   failure(new Throwable());
                   return;
               }

               String readActivityHandle = null;
               List<ActivityView> activities = result.getBody().getData();
               Set<String> seenCommentHandles = new HashSet<>();
               int newNotifs = 0;
               boolean sawUnread = false;

               for (ActivityView view : activities) {
                   // we've seen all the unread messages by now
                   if (!view.getUnread()) {
                       sawUnread = true;
                       break;
                   }
                   Log.v(TAG, "New unread notification!");
                   if (view.getActivityType() != ActivityType.COMMENT) {
                       Log.v(TAG, "Not a Comment, instead a " + view.getActivityType().toString());
                       continue; // ignore anything that isn't a comment for now
                   }
                   if (view.getActedOnContent().getContentType() != ContentType.TOPIC
                           || view.getActedOnContent().getContentType() != ContentType.COMMENT) {
                       Log.v(TAG, "Comment not posted on topic");
                       continue; // ignore comments not posted to topics (?)
                   }

                   // get the comment handle. make sure we don't get duplicate comments
                   String commentHandle = view.getActivityHandle();
                   if (seenCommentHandles.contains(commentHandle)) {
                       continue;
                   }
                   seenCommentHandles.add(commentHandle);

                   // set the latest read comment
                   if (readActivityHandle == null) {
                       readActivityHandle = commentHandle;
                   }
                   ES_COMMENTS.getCommentAsync(commentHandle, auth, new CommentCallback(auth));
                   newNotifs++;
               }
               // wait for all callbacks to complete before we check for success
               while (doneCount.get() < newNotifs) {
                   try {
                       Thread.sleep(100);
                   } catch (InterruptedException e) {
                   }
               }
               if (failed) {
                   // try again
                  get_notifications(auth, notificationCallback);
               }
               //notificationCallback.onReceiveNotifs(notifQueue);
               if (sawUnread && readActivityHandle != null) {
                   updateReadNotifs(readActivityHandle, auth);
               } else {

               }
           }
       };
       ES_NOTIFS.getNotificationsAsync(auth, serviceCallback);
    }

    private void updateReadNotifs(final String readActivityHandle, final String auth) {
        PutNotificationsStatusRequest req = new PutNotificationsStatusRequest();
        req.setReadActivityHandle(readActivityHandle);

        ServiceCallback<Object> serviceCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed to update notification status");
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

    private class TopicCallback extends ServiceCallback<TopicView> {
        private String msg;
        private DateTime date;

        TopicCallback(String msg, DateTime date) {
            this.msg = msg;
            this.date = date;
        }

        @Override
        public void failure(Throwable t) {
            failed = true;
        }

        @Override
        public void success(ServiceResponse<TopicView> result) {
            if (!result.getResponse().isSuccess()) {
                failure(new Throwable());
                doneCount.getAndIncrement();
                return;
            }
            Identifier eid = new Identifier(result.getBody().getTitle().getBytes());
            Log.v(TAG, "Adding message " + msg + " of eid " + eid.toString());
            notifQueue.add(new Notif(eid, msg, date));
            doneCount.getAndIncrement();
        }
    }

    private class CommentCallback extends ServiceCallback<CommentView> {
        private String auth;

        CommentCallback(String auth) {
            this.auth = auth;
        }

        @Override
        public void failure(Throwable t) {
            failed = true;
        }

        @Override
        public void success(ServiceResponse<CommentView> result) {
            if (!result.getResponse().isSuccess()) {
                failure(new Throwable());
                return;
            }
            String msg = result.getBody().getText();
            String topicHandle = result.getBody().getTopicHandle();
            DateTime createTime = result.getBody().getCreatedTime();
            ES_TOPICS.getTopicAsync(topicHandle, auth, new TopicCallback(msg, createTime));
        }
    }
}
