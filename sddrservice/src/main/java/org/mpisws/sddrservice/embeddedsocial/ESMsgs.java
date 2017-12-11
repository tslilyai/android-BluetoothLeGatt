package org.mpisws.sddrservice.embeddedsocial;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.ISearchService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.AddCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.replies.AddReplyRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.AddTopicRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.RemoveTopicRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.TopicsListResponse;
import com.microsoft.embeddedsocial.server.model.search.SearchTopicsRequest;
import com.microsoft.embeddedsocial.server.model.view.CommentView;
import com.microsoft.embeddedsocial.server.model.view.ReplyView;
import com.microsoft.embeddedsocial.server.model.view.TopicView;

import org.mpisws.sddrservice.lib.Utils;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESMsgs {
    private final UserAccount userAccount;
    public ESMsgs() {
        userAccount = GlobalObjectRegistry.getObject(UserAccount.class);
    }

    public static class TopicAction {
    public enum TATyp {
        GetMsgs,
        SendMsg,
        CreateOnly
    }

    TATyp typ;
    String msg;
    MsgsCallback msgsCallback;

    public TopicAction(TATyp typ, String msg) {
        Utils.myAssert(typ == TATyp.SendMsg);
        this.typ = typ;
        this.msg = msg;
    }

    public TopicAction(TATyp typ, MsgsCallback msgsCallback) {
        Utils.myAssert(typ == TATyp.GetMsgs);
        this.typ = typ;
        this.msgsCallback = msgsCallback;
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


    public interface MsgsCallback {
        void onReceiveMessages(List<Msg> messages);
    }

    public void find_and_act_on_topic(String eid, final TopicAction ta) {
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        SearchTopicsRequest getreq = new SearchTopicsRequest(eid);
        TopicsListResponse topics;

        ISearchService searchService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getSearchService();
        try {
            topics = searchService.searchTopics(getreq);
            List<TopicView> topiclist = topics.getData();
            if (topiclist.size() == 0) {
                AddTopicRequest addreq = new AddTopicRequest.Builder()
                        .setTopicTitle(eid)
                        .setTopicText(eid)
                        .setPublisherType(PublisherType.USER)
                        .build();
                contentService.addTopic(addreq);
                find_and_act_on_topic(eid, ta);
            } else if (topiclist.size() > 1) {
                Utils.myAssert(topiclist.size() == 2);
                String topicHandle = topiclist.get(0).getHandle().compareTo(topiclist.get(1).getHandle()) > 0
                        ? topiclist.get(0).getHandle()
                        : topiclist.get(1).getHandle();
                RemoveTopicRequest remreq = new RemoveTopicRequest(topicHandle);
                contentService.removeTopic(remreq);
                find_and_act_on_topic(eid, ta);
            } else {
                final TopicView topic = topiclist.get(0);
                final Fetcher<Object> commentFeedFetcher = FetchersFactory.createCommentFeedFetcher(topic.getHandle(), topic);
                Callback callback = new Callback() {
                    @Override
                    public void onStateChanged(FetcherState newState) {
                    super.onStateChanged(newState);
                    switch (newState) {
                        case LOADING:
                            break;
                        case DATA_ENDED:
                            do_action(ta, topic, commentFeedFetcher.getAllData());
                        default:
                            commentFeedFetcher.requestMoreData();
                            break;
                    }
                    }
                };
                commentFeedFetcher.setCallback(callback);
                commentFeedFetcher.requestMoreData();
            }
        } catch (NetworkRequestException e) {
            e.printStackTrace();
        }
    }

    private void do_action(TopicAction ta, TopicView topic, List<Object> comments) {
        final boolean is_my_topic = userAccount.isCurrentUser(topic.getUser().getHandle()) ? true : false;
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();

        /* Find the comment that acts as the "response" thread for the user that did not create the topic */
        CommentView reply_comment = null;
        for (Object obj : comments) {
            CommentView comment = (CommentView) obj;
            if (is_my_topic && !userAccount.isCurrentUser(comment.getUser().getHandle())
                    || !is_my_topic && userAccount.isCurrentUser(comment.getUser().getHandle()))
            {
                Utils.myAssert(comment.getCommentText() == "ReplyComment");
                reply_comment = comment;
                break;
            }
        }
        try {
            switch (ta.typ) {
                case GetMsgs: {
                    get_msgs_helper(reply_comment, comments, ta.msgsCallback);
                    break;
                }
                case SendMsg: {
                    // we add the reply comment if there is none and we're not the topic owner
                    if (!is_my_topic && reply_comment == null) {
                        contentService.addComment(new AddCommentRequest(topic.getHandle(), topic.getTopicText()));
                    }
                    // if either (1) there is no reply comment and we're the topic owner
                    // or (2) we're not the topic owner, then post a comment
                    if ((is_my_topic && reply_comment == null) || !is_my_topic) {
                        contentService.addComment(new AddCommentRequest(topic.getHandle(), ta.msg));
                    }
                    // post a reply to the reply comment if it exists and we're the topic owner
                    else if (is_my_topic && reply_comment != null) {
                        contentService.addReply(new AddReplyRequest(reply_comment.getHandle(), ta.msg));
                    }
                    break;
                }
                case CreateOnly: {
                    if (!is_my_topic || reply_comment == null) {
                        contentService.addComment(new AddCommentRequest(topic.getHandle(), topic.getTopicText()));
                    }
                    break;
                }
            }
        } catch (NetworkRequestException e1) {
            e1.printStackTrace();
        }
    }

    private void get_msgs_helper(CommentView reply_comment, List<Object> comments, final MsgsCallback msgsCallback) {
        final List<Msg> msgmap = new LinkedList<>();
        // get all the messages associated with the topic thread
        for (Object obj : comments) {
            CommentView comment = (CommentView) obj;
            if (comment != reply_comment) {
                msgmap.add(new Msg(
                        comment.getCommentText(),
                        userAccount.isCurrentUser(comment.getUser().getHandle()),
                        comment.getElapsedSeconds()
                ));
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
                            ReplyView reply = (ReplyView) obj;
                            msgmap.add(new Msg(
                                    reply.getReplyText(),
                                    userAccount.isCurrentUser(reply.getUser().getHandle()),
                                    reply.getElapsedSeconds()
                            ));
                        }
                        msgsCallback.onReceiveMessages(msgmap);
                        break;
                    default:
                        replyFetcher.requestMoreData();
                        break;
                }
            }
        };
        replyFetcher.setCallback(callback);
        replyFetcher.requestMoreData();
    }
}
