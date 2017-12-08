package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.IdentityProvider;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.data.Preferences;
import com.microsoft.embeddedsocial.data.model.AccountData;
import com.microsoft.embeddedsocial.data.model.TopicFeedType;
import com.microsoft.embeddedsocial.fetcher.CommentFeedFetcher;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.ISearchService;
import com.microsoft.embeddedsocial.server.NetworkAvailability;
import com.microsoft.embeddedsocial.server.RequestInfoProvider;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.AddCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentFeedRequest;
import com.microsoft.embeddedsocial.server.model.content.replies.AddReplyRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.AddTopicRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.GetTopicFeedRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.RemoveTopicRequest;
import com.microsoft.embeddedsocial.server.model.content.topics.TopicsListResponse;
import com.microsoft.embeddedsocial.server.model.search.SearchTopicsRequest;
import com.microsoft.embeddedsocial.server.model.view.CommentView;
import com.microsoft.embeddedsocial.server.model.view.TopicView;
import com.microsoft.embeddedsocial.ui.notification.NotificationController;
import com.microsoft.embeddedsocial.ui.util.SocialNetworkAccount;

import org.mpisws.sddrservice.lib.Utils;
import org.w3c.dom.Comment;

import java.util.List;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESCore {
    private Context context;
    private UserAccount userAccount;
    private SocialNetworkAccount socialNetworkAccount;

    public ESCore(Context context) {
        this.context = context;
        this.userAccount = new UserAccount(context);

        EmbeddedSocialServiceProvider serviceProvider = new EmbeddedSocialServiceProvider(context);
        GlobalObjectRegistry.addObject(EmbeddedSocialServiceProvider.class, serviceProvider);
        GlobalObjectRegistry.addObject(new Preferences(context));
        GlobalObjectRegistry.addObject(new RequestInfoProvider(context));
        GlobalObjectRegistry.addObject(new UserAccount(context));
        GlobalObjectRegistry.addObject(new NotificationController(context));
        NetworkAvailability networkAccessibility = new NetworkAvailability();
        networkAccessibility.startMonitoring(context);
        GlobalObjectRegistry.addObject(networkAccessibility);
    }

    public static class TopicAction {
        public enum TATyp {
            GetMsgs,
            SendMsg,
            CreateOnly
        }

        TATyp typ;
        String msg;
        ESTask.MsgsCallback msgsCallback;
        ESTask.NotificationCallback notificationCallback;

        public TopicAction(TATyp typ, String msg) {
            Utils.myAssert(typ == TATyp.SendMsg);
            this.typ = typ;
            this.msg = msg;
        }

        public TopicAction(TATyp typ, ESTask.MsgsCallback msgsCallback) {
            Utils.myAssert(typ == TATyp.GetMsgs);
            this.typ = typ;
            this.msgsCallback = msgsCallback;
        }

        /*TopicAction(TATyp typ, ESTask.NotificationCallback notificationCallback) {
            Utils.myAssert(typ == TATyp.);
            this.typ = typ;
            this.notificationCallback = notificationCallback;
        }*/
    }

    public void register_user_details(String googletoken, String firstname, String lastname) {
        AccountData accountData = new AccountData();
        accountData.setFirstName(firstname);
        accountData.setLastName(lastname);
        accountData.setIdentityProvider(IdentityProvider.GOOGLE);
        accountData.setThirdPartyAccessToken(googletoken);
        userAccount.updateAccountDetails(accountData);
        socialNetworkAccount = new SocialNetworkAccount(IdentityProvider.GOOGLE, googletoken);
    }

    public void sign_in() {
        userAccount.signInUsingThirdParty(socialNetworkAccount);
    }

    public void sign_out() {
        if (userAccount.isSignedIn())
            userAccount.signOut();
    }

    public void find_and_act_on_topic(String eid, final TopicAction ta) {
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        SearchTopicsRequest getreq = new SearchTopicsRequest(eid);
        TopicsListResponse topics = null;

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

    public void do_action(TopicAction ta, TopicView topic, List<Object> comments) {
        final boolean is_my_topic = (userAccount.getUserHandle() == topic.getUser().getHandle()) ? true : false;
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        CommentView reply_comment = null;
        for (Object obj : comments) {
            CommentView comment = (CommentView) obj;
            if (is_my_topic && comment.getUser().getHandle() != userAccount.getUserHandle()
                    || !is_my_topic && comment.getUser().getHandle() == userAccount.getUserHandle())
            {
                Utils.myAssert(comment.getCommentText() == "ReplyComment");
                reply_comment = comment;
                break;
            }
        }
        try {
            switch (ta.typ) {
                case GetMsgs: {
                    for (Object obj : comments) {
                        CommentView comment = (CommentView) obj;
                    }
                    break;
                }
                case SendMsg: {
                    // we add the reply comment if there is none and we're not the topic owner
                    if (!is_my_topic && reply_comment == null) {
                        contentService.addComment(new AddCommentRequest(topic.getHandle(), "ReplyComment"));
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
                        contentService.addComment(new AddCommentRequest(topic.getHandle(), "ReplyComment"));
                    }
                    break;
                }
            }
        } catch (NetworkRequestException e1) {
            e1.printStackTrace();
        }
    }
}
