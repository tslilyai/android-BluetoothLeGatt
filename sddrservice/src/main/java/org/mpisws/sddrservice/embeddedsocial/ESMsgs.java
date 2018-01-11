package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.ContentType;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.autorest.models.Reason;
import com.microsoft.embeddedsocial.data.model.DiscussionItem;
import com.microsoft.embeddedsocial.data.storage.PostStorage;
import com.microsoft.embeddedsocial.data.storage.UserActionProxy;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.model.view.CommentView;
import com.microsoft.embeddedsocial.server.model.view.TopicView;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;

import org.mpisws.sddrservice.IEncountersService;
import org.mpisws.sddrservice.encounterhistory.SSBridge;
import org.mpisws.sddrservice.lib.Utils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.microsoft.embeddedsocial.fetcher.base.FetcherState.DATA_ENDED;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESMsgs {
    private static final String TAG = ESMsgs.class.getSimpleName();
    private static final String DUMMY_TOPIC_TEXT = "DummyTopicText";
    private static final long THRESHOLD_BUFFER_MS = 1000;
    private final PostStorage postStorage;
    private final Context context;
    private final SSBridge ssBridge;
    private final Object lock = new Object();

    public ESMsgs(Context context) {
        this.context = context;
        postStorage = new PostStorage(context);
        ssBridge = new SSBridge(context);
    }

    public static class TopicAction     {
        public enum TATyp {
            GetMsgs,
            SendMsg,
            CreateOnly
        }

        TATyp typ;
        String msg;
        GetMessagesCallback getMessagesCallback;
        String eid;
        String cursor;
        long thresholdAge;

        public TopicAction(TATyp typ, String eid, String msg) {
            Utils.myAssert(typ == TATyp.SendMsg);
            this.eid = eid;
            this.typ = typ;
            this.msg = msg;
        }

        public TopicAction(TATyp typ, long thresholdAge, String eid, String cursor,
                           GetMessagesCallback getMessagesCallback) {
            Utils.myAssert(typ == TATyp.GetMsgs);
            this.eid = eid;
            this.typ = typ;
            this.cursor = cursor;
            this.thresholdAge = thresholdAge;
            this.getMessagesCallback = getMessagesCallback;
        }

        public TopicAction(TATyp typ, String eid) {
            Utils.myAssert(typ == TATyp.CreateOnly);
            this.eid = eid;
            this.typ = typ;
        }
    }

    public static class Msg {
        private ContentType typ;
        private long createdTime;
        private String handle;
        private String msg;
        private IEncountersService.ForwardingFilter filter;
        private String eid;
        private boolean fromMe;

        public Msg(ContentType typ, long createdTime, String handle, String msg, IEncountersService.ForwardingFilter filter, String eid, boolean fromMe) {
            this.handle = handle;
            this.typ = typ;
            this.createdTime = createdTime;
            this.msg = msg;
            this.filter = filter;
            this.eid = eid;
            this.fromMe = fromMe;
        }

        public String getMsg() {
            return msg;
        }
        public String getEid() { return eid; }
        public boolean isFromMe() {
            return fromMe;
        }
        public boolean isNewerThan(long time) {
            return createdTime > time;
        }
        public String getCursor() {
            return handle;
        }
        public IEncountersService.ForwardingFilter getFilter() { return filter; }
    }

    public interface GetMessagesCallback {
        void onReceiveMessages(List<Msg> messages);
    }

    public void report_msg(Msg msg, Reason reason) {
        new UserActionProxy(context).reportContent(msg.handle, msg.typ, reason);
    }

    public void sendUnsentMsgs() {
        Map<String, List<String>> unsentMsgs = UserAccount.getInstance().getAccountDetails().getUnsentMsgs();
        synchronized(lock) {
            for (String eid : unsentMsgs.keySet()) {
                for (String msg : unsentMsgs.get(eid)) {
                    find_and_act_on_topic(new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.SendMsg, eid, msg));
                }
                unsentMsgs.remove(eid);
            }
        }
        WorkerService.getLauncher(context).launchService(ServiceAction.SYNC_DATA);
    }

    public void find_and_act_on_topic(TopicAction ta) {
        final Fetcher<TopicView> topicFeedFetcher = FetchersFactory.createSearchTopicsFetcher(ta.eid);
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case DATA_ENDED:
                        process_topics(ta, topicFeedFetcher.getAllData());
                        break;
                    default:
                        topicFeedFetcher.requestMoreData();
                        break;
                }
            }
        };
        topicFeedFetcher.setCallback(callback);
    }

    private void process_topics(TopicAction ta, List<TopicView> topiclist) {
        Log.v(TAG, "Found " + topiclist.size() + " topics, ta.eid " + ta.eid + " going to do topic action " + ta.typ.name());
       // No one has created this topic yet! We should try to create it
        if (topiclist.size() == 0) {
            // If we've already tried, this means the post is still not synced. Don't do anything.
            // But if we haven't tried to create this topic yet! Add a pending request to do so and
            // store the message to be sent.
            if (ta.typ == TopicAction.TATyp.SendMsg) {
                synchronized(lock) {
                    UserAccount.getInstance().getAccountDetails().addUnsentMsg(ta.eid, ta.msg);
                }
            }
            // Remember that we're trying to create this topic so we don't create it twice. Then
            // actually try and create the topic
            if (!UserAccount.getInstance().getAccountDetails().pendingTopic(ta.eid)) {
                UserAccount.getInstance().getAccountDetails().addPendingTopic(ta.eid);
                postStorage.storePost(ta.eid, DUMMY_TOPIC_TEXT, null, PublisherType.USER);
                WorkerService.getLauncher(context).launchService((ServiceAction.SYNC_DATA));
            }
        } else {
            // We've created this topic but run into a race condition with the other encounter partner
            // who also created the topic. Remove the bad topic, but post the comments to the remaining topic
            TopicView topicToComment;
            if (topiclist.size() > 1) {
                Utils.myAssert(topiclist.size() == 2);
                TopicView topicToRemove = topiclist.get(0).getHandle().compareTo(topiclist.get(1).getHandle()) > 0
                        ? topiclist.get(0)
                        : topiclist.get(1);
                topicToComment = topiclist.get(0).getHandle().compareTo(topiclist.get(1).getHandle()) <= 0
                        ? topiclist.get(0)
                        : topiclist.get(1);
                Log.v(TAG, "Removing topic " + topicToRemove.getTopicTitle());
                new UserActionProxy(context).removeTopic(topicToRemove);
            } else {
                topicToComment = topiclist.get(0);
            }
            if (ta.typ == TopicAction.TATyp.GetMsgs) {
                get_msgs_helper(ta, topicToComment);
            } else {
                find_reply_comment_and_do_action(ta, topicToComment);
            }
        }
    }

    private void find_reply_comment_and_do_action(TopicAction ta, TopicView topic) {
        if (topic.getTopicText().compareTo(DUMMY_TOPIC_TEXT) != 0) {
            Log.v(TAG, "Found replyHandle in topic text!");
            do_action(ta, topic, topic.getTopicText());
            return;
        }

        /* we haven't found the reply comment yet. we have to go through all comments to either
            1) find the reply comment and update the topic text
            2) create the reply comment

            In either case, once we've completed that action, we simply either get messages,
            send messages, or do nothing (if the task was to just create the topic).
            If the reply comment doesn't exist, we don't notify the other side (makes sense).
        */
        final Fetcher<Object> commentFeedFetcher = FetchersFactory.createCommentFeedFetcher(topic.getHandle(), topic);
        Callback callback = new Callback() {
            boolean is_my_topic = UserAccount.getInstance().isCurrentUser(topic.getUser().getHandle()) ? true : false;

            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    default: {
                        // look for the reply comment in the feed
                        // if we found it, update the topic view to hold the comment handle and then perform the action
                        for (Object obj : commentFeedFetcher.getAllData()) {
                            if (CommentView.class.isInstance(obj)) {
                                CommentView comment = CommentView.class.cast(obj);
                                if ((is_my_topic && !UserAccount.getInstance().isCurrentUser(comment.getUser().getHandle()))
                                        || (!is_my_topic && UserAccount.getInstance().isCurrentUser(comment.getUser().getHandle()))) {
                                    if (comment.getCommentText().compareTo(topic.getTopicTitle()) == 0) {
                                        // this is the reply comment!
                                        Log.v(TAG, "Found reply comment " + comment.getCommentText() + " for " + topic.getTopicTitle() + " , updating topic text to be handle");
                                        topic.setTopicText(comment.getHandle());
                                        new UserActionProxy(context).updateTopic(topic);
                                        do_action(ta, topic, comment.getHandle());
                                        return;
                                    }
                                }
                            }
                        }
                        // we reach this point then this set of comments doesn't contain the reply comment
                        if (newState != DATA_ENDED) {
                            // see if unfetched comments contain the reply comment (clear our data too)
                            commentFeedFetcher.clearData();
                            commentFeedFetcher.requestMoreData();
                        } else {
                            // we know the reply comment doesn't exist yet. create if we will be the owner
                            if (!is_my_topic) {
                                try {
                                    postStorage.storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), topic.getTopicTitle(), null));
                                    Log.v(TAG, "Add reply comment for " + topic.getTopicTitle());
                                } catch (SQLException e) {}
                            }
                            // we're out of comments to fetch, so just do the action
                            Log.v(TAG, "Didn't find reply comment, just do action for " + topic.getTopicTitle());
                            do_action(ta, topic, null);
                        }
                    }
                }
            }
        };
        commentFeedFetcher.setCallback(callback);
    }

    private void do_action(TopicAction ta, TopicView topic, String replyCommentHandle) {
        try {
            PostStorage postStorage = new PostStorage(context);
            switch (ta.typ) {
                case GetMsgs: {
                    get_msgs_helper(ta, topic);
                    break;
                }
                case SendMsg: {
                    boolean is_my_topic = UserAccount.getInstance().isCurrentUser(topic.getUser().getHandle()) ? true : false;
                    String encrypted_msg = Utils.encrypt(ta.msg, ssBridge.getSharedSecretByEncounterID(ta.eid));
                    postStorage.storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), encrypted_msg, null));
                    Log.v(TAG, "Send comment for " + topic.getTopicTitle());
                    if (is_my_topic && replyCommentHandle != null) {
                        postStorage.storeDiscussionItem(DiscussionItem.newReply(replyCommentHandle, "Notify"));
                        Log.v(TAG, "Send reply to notify for " + topic.getTopicTitle());
                    }
                    break;
                }
                case CreateOnly: {
                    break;
                }
            }
            WorkerService.getLauncher(context).launchService(ServiceAction.SYNC_DATA);
        } catch (SQLException e) {}
    }

    private void get_msgs_helper(TopicAction ta, TopicView topic) {
        Fetcher<Object> commentFeedFetcher = FetchersFactory.createCommentFeedFetcher(topic.getHandle(), topic);
        if (commentFeedFetcher.isLoading()) { return; }
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                List<Msg> comments = new ArrayList<>();
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case LAST_ATTEMPT_FAILED:
                        Log.v(TAG, "Last attempt failed");
                        commentFeedFetcher.requestMoreData();
                        break;
                    default: // ENDED or MORE_DATA
                        boolean pastThreshold = false;
                        for (Object obj : commentFeedFetcher.getAllData()) {
                            if (CommentView.class.isInstance(obj)) {
                                CommentView comment = CommentView.class.cast(obj);
                                if (ta.thresholdAge > 0 && comment.getCreatedTime() < ta.thresholdAge - THRESHOLD_BUFFER_MS) {
                                    Log.v(TAG, "Comment created before the threshold time");
                                    pastThreshold = true;
                                    break;
                                } else {
                                    String decrypted_msg = Utils.decrypt(comment.getCommentText(), ssBridge.getSharedSecretByEncounterID(ta.eid));
                                    Log.v(TAG, "Decrypted " + decrypted_msg);
                                    if (comment.getCommentText().compareTo(topic.getTopicTitle()) == 0) {
                                        Log.v(TAG, "Found reply comment");
                                        continue;
                                    }
                                    IEncountersService.ForwardingFilter filter = null;
                                    if (decrypted_msg.contains(IEncountersService.Filter.FILTER_END_STR)) {
                                        filter = getMsgFilter(decrypted_msg);
                                    }
                                    comments.add(new Msg(
                                            ContentType.COMMENT,
                                            comment.getCreatedTime(),
                                            comment.getHandle(),
                                            getMsg(decrypted_msg),
                                            filter,
                                            topic.getTopicTitle(),
                                            UserAccount.getInstance().isCurrentUser(comment.getUser().getHandle()))
                                    );
                                }
                            }
                        }
                        if (pastThreshold || newState == FetcherState.DATA_ENDED) {
                            Log.v(TAG, "Found " + commentFeedFetcher.getAllData().size() + " comments for eid " + ta.eid);
                            ta.getMessagesCallback.onReceiveMessages(comments);
                        } else {
                            commentFeedFetcher.clearData();
                            commentFeedFetcher.requestMoreData();
                        }
                }
            }
        };
        commentFeedFetcher.setCallbackSilent(callback);
        commentFeedFetcher.clearData();

        if (ta.cursor == null) {
            // this will call the callback after a new page is gotten from the beginning
            commentFeedFetcher.refreshData();
        } else if (commentFeedFetcher.hasMoreData()) {
            commentFeedFetcher.setCursor(ta.cursor);
            commentFeedFetcher.requestMoreData();
        }
    }

    public IEncountersService.ForwardingFilter getMsgFilter(String msg) {
        String[] msg_parts = msg.split(IEncountersService.Filter.FILTER_END_STR);
        if (msg_parts.length != 2) {
            Log.d(TAG, "Length: " + msg_parts.length + " " + msg_parts[0]);
        }
        IEncountersService.ForwardingFilter filter = null;
        IEncountersService.ForwardingFilter.fromString(msg_parts[0]);
        try {
            filter = IEncountersService.ForwardingFilter.class.cast(Utils.deserializeObjectFromString(msg_parts[0]));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return filter;
    }
    private String getMsg(String msg) {
        String[] msg_parts = msg.split(IEncountersService.Filter.FILTER_END_STR);
        Utils.myAssert(msg_parts.length == 2);
        return msg_parts[1];
    }
}
