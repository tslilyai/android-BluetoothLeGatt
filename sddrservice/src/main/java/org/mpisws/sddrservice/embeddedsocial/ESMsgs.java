package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.data.model.DiscussionItem;
import com.microsoft.embeddedsocial.data.storage.PostStorage;
import com.microsoft.embeddedsocial.data.storage.UserActionProxy;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.model.view.CommentView;
import com.microsoft.embeddedsocial.server.model.view.ReplyView;
import com.microsoft.embeddedsocial.server.model.view.TopicView;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;

import org.mpisws.sddrservice.lib.Utils;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESMsgs {
    private static final String TAG = ESMsgs.class.getSimpleName();
    private final UserAccount userAccount;
    private final PostStorage postStorage;
    private final Context context;
    private final Object lock = new Object();

    public ESMsgs(Context context) {
        this.context = context;
        userAccount = GlobalObjectRegistry.getObject(UserAccount.class);
        postStorage = new PostStorage(context);
    }

    public static class TopicAction {
    public enum TATyp {
        GetMsgs,
        SendMsg,
        CreateOnly
    }

    TATyp typ;
    List<String> msgs;
    MsgCallback msgCallback;

    public TopicAction(TATyp typ, List<String> msgs) {
        Utils.myAssert(typ == TATyp.SendMsg);
        this.typ = typ;
        this.msgs = msgs;
    }

    public TopicAction(TATyp typ, MsgCallback msgCallback) {
        Utils.myAssert(typ == TATyp.GetMsgs);
        this.typ = typ;
        this.msgCallback = msgCallback;
    }

    public TopicAction(TATyp typ) {
        Utils.myAssert(typ == TATyp.CreateOnly);
        this.typ = typ;
    }
}

    public static class Msg {
        public String msg;
        private boolean fromMe;
        public long timestamp;

        Msg(String msg, boolean fromMe, long timestamp) {
            this.msg = msg;
            this.fromMe = fromMe;
            this.timestamp = timestamp;
        }
    }


    public interface MsgCallback {
        void onReceiveMessage(Msg messages);
    }

    public void sendUnsentMsgs() {
        Map<String, List<String>> unsentMsgs = UserAccount.getInstance().getAccountDetails().getUnsentMsgs();
        synchronized(lock) {
            for (String eid : unsentMsgs.keySet()) {
                find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.SendMsg, unsentMsgs.get(eid)));
                unsentMsgs.remove(eid);
            }
        }
        WorkerService.getLauncher(context).launchService(ServiceAction.SYNC_DATA);
    }

    public void find_and_act_on_topic(String eid, final TopicAction ta) {
        find_and_process_topics(eid, ta);
    }

    private void find_and_process_topics(String eid, TopicAction ta) {
        final Fetcher<TopicView> topicFeedFetcher = FetchersFactory.createSearchTopicsFetcher(eid);
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case DATA_ENDED:
                        process_topics(eid, ta, topicFeedFetcher.getAllData());
                        break;
                    default:
                        topicFeedFetcher.requestMoreData();
                        break;
                }
            }
        };
        topicFeedFetcher.setCallback(callback);
    }


    private void process_topics(String eid, TopicAction ta, List<TopicView> topiclist) {
       // No one has created this topic yet! We should try to create it
        if (topiclist.size() == 0) {
            // If we've already tried, this means the post is still not synced. Don't do anything.
            // But if we haven't tried to create this topic yet! Add a pending request to do so and
            // store the message to be sent.
            // Store the message to be sent
            if (ta.typ == TopicAction.TATyp.SendMsg) {
                synchronized(lock) {
                    userAccount.getAccountDetails().addUnsentMsgs(eid, ta.msgs);
                }
            }
            if (!userAccount.getAccountDetails().pendingTopic(eid)) {
                userAccount.getAccountDetails().addPendingTopic(eid);
                postStorage.storePost(eid, eid, null, PublisherType.USER);
                WorkerService.getLauncher(context).launchService((ServiceAction.SYNC_DATA));
            }
        } else {
            // We've created this topic but run into a race condition with the other encounter partner
            // who also created the topic. Remove the topic, but post the comments to the remaining topic
            // anyway
            TopicView topicToComment;
            if (topiclist.size() > 1) {
                Utils.myAssert(topiclist.size() == 2);
                TopicView topicToRemove = topiclist.get(0).getHandle().compareTo(topiclist.get(1).getHandle()) > 0
                        ? topiclist.get(0)
                        : topiclist.get(1);
                topicToComment = topiclist.get(0).getHandle().compareTo(topiclist.get(1).getHandle()) <= 0
                        ? topiclist.get(0)
                        : topiclist.get(1);
                new UserActionProxy(context).removeTopic(topicToRemove);
            } else {
                topicToComment = topiclist.get(0);
            }
            // Post the comments (or replies)
            final Fetcher<Object> commentFeedFetcher = FetchersFactory.createCommentFeedFetcher(topicToComment.getHandle(), topicToComment);
            Callback callback = new Callback() {
                @Override
                public void onStateChanged(FetcherState newState) {
                    super.onStateChanged(newState);
                    switch (newState) {
                        case LOADING:
                            break;
                        case DATA_ENDED:
                            do_action(ta, topicToComment, commentFeedFetcher.getAllData());
                        default:
                            commentFeedFetcher.requestMoreData();
                            break;
                    }
                }
            };
            commentFeedFetcher.setCallback(callback);
        }
    }

    private void do_action(TopicAction ta, TopicView topic, List<Object> comments) {
        final boolean is_my_topic = userAccount.isCurrentUser(topic.getUser().getHandle()) ? true : false;

        /* Find the comment that acts as the "response" thread for the user that did not create the topic */
        CommentView reply_comment = null;
        for (Object obj : comments) {
            if (CommentView.class.isInstance(obj)) {
                Log.d(TAG, "Is an instance of commentview!");
                CommentView comment = CommentView.class.cast(obj);
                if (is_my_topic && !userAccount.isCurrentUser(comment.getUser().getHandle())
                        || !is_my_topic && userAccount.isCurrentUser(comment.getUser().getHandle())) {
                    reply_comment = comment;
                    Utils.myAssert(reply_comment.getCommentText() == topic.getTopicTitle());
                    break;
                }
            }
        }

        try {
            PostStorage postStorage = new PostStorage(context);
            switch (ta.typ) {
                case GetMsgs: {
                    get_msgs_helper(reply_comment, comments, ta.msgCallback);
                    break;
                }
                case SendMsg: {
                    // we add the reply comment if there is none and we're not the topic owner
                    if (!is_my_topic && reply_comment == null) {
                        postStorage.storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), topic.getTopicText(), null));
                    }
                    // if either (1) there is no reply comment and we're the topic owner
                    // or (2) we're not the topic owner, then post a comment
                    if ((is_my_topic && reply_comment == null) || !is_my_topic) {
                        for (String msg : ta.msgs) {
                            postStorage.storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), msg, null));
                        }
                    }
                    // post a reply to the reply comment if it exists and we're the topic owner
                    else if (is_my_topic && reply_comment != null) {
                        for (String msg : ta.msgs) {
                            postStorage.storeDiscussionItem(DiscussionItem.newReply(reply_comment.getHandle(), msg));
                        }
                    }
                    break;
                }
                case CreateOnly: {
                    // create the reply comment if none exists
                    if (!is_my_topic || reply_comment == null) {
                        postStorage.storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), topic.getTopicText(), null));
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void get_msgs_helper(CommentView reply_comment, List<Object> comments, final MsgCallback msgCallback) {
        final List<Msg> msgmap = new LinkedList<>();
        // get all the messages associated with the topic thread
        for (Object obj : comments) {
            if (CommentView.class.isInstance(obj)) {
                Log.d(TAG, "Fetcher returned something that is not a comment!");
            }
            CommentView comment = (CommentView) obj;
            if (comment != reply_comment) {
                msgCallback.onReceiveMessage(new Msg(
                        comment.getCommentText(),
                        userAccount.isCurrentUser(comment.getUser().getHandle()),
                        comment.getElapsedSeconds()));
            }
        }
        // get all the messages associated with reply_comment
        final Fetcher<Object> replyFetcher = FetchersFactory.createReplyFeedFetcher(reply_comment.getHandle(), reply_comment);
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case DATA_ENDED:
                        for (Object obj : replyFetcher.getAllData()) {
                            if (!ReplyView.class.isInstance(obj)) {
                                Log.d(TAG, "Fetcher returned something that is not a reply!");
                            }
                            ReplyView reply = (ReplyView) obj;
                            msgCallback.onReceiveMessage(new Msg(
                                    reply.getReplyText(),
                                    userAccount.isCurrentUser(reply.getUser().getHandle()),
                                    reply.getElapsedSeconds()));
                        }
                        break;
                    default:
                        replyFetcher.requestMoreData();
                        break;
                }
            }
        };
        replyFetcher.setCallback(callback);
    }
}
