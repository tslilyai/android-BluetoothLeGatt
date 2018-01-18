package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

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
import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.encounterhistory.MyAdvertsBridge;
import org.mpisws.sddrservice.encounters.SDDR_Native;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static com.microsoft.embeddedsocial.fetcher.base.FetcherState.DATA_ENDED;
import static org.mpisws.sddrservice.embeddedsocial.ESTopics.TopicAction.TATyp.CreateEIDTopic;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESAdvertTopics {
    private static final String TAG = ESAdvertTopics.class.getSimpleName();
    private static final String DUMMY_TOPIC_TEXT = "DummyTopicText";

    public static void tryPostAdvertAndDHPubKey(Context context, Identifier myAdvert, Identifier myDHPubKey) {
        Pair<Boolean, Boolean> posted = new MyAdvertsBridge(context).isAdvertAndDHPubKeyPosted(myAdvert);
        if (!posted.first) {
            new PostStorage(context).storePost(myAdvert.toString(), DUMMY_TOPIC_TEXT, null, PublisherType.USER);
            WorkerService.getLauncher(context).launchService((ServiceAction.SYNC_DATA));
            new MyAdvertsBridge(context).updatePostedAdvert(myAdvert);
        }
        if (!posted.second) {
            getTopicAndAct(context, myAdvert, myDHPubKey, null, null);
        }
    }

    public static void tryGetSecretKeys(Context context, Identifier myDHKey, long pkid, List<Identifier> adverts) {
        for (Identifier advert : adverts) {
            getTopicAndAct(context, advert, null, myDHKey, pkid);
        }
    }

    private static void getTopicAndAct(Context context, Identifier advert, Identifier myDHPubKey, Identifier myDHKey, Long pkid) {
        // look up topic
        final Fetcher<TopicView> topicFeedFetcher = FetchersFactory.createSearchTopicsFetcher(advert.toString());
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case DATA_ENDED:
                        List<TopicView> topics = (topicFeedFetcher.getAllData());
                        TopicView topic_to_save = topics.get(0);

                        // somehow we created more than one topic. remove all but the
                        // one with the oldest timestamp
                        if (topics.size() > 1) {
                            UserActionProxy proxy = new UserActionProxy(context);
                            for (int i = 1; i < topics.size(); ++i) {
                                if (topics.get(i).getElapsedSeconds() > topic_to_save.getElapsedSeconds()) {
                                    topic_to_save = topics.get(i);
                                    proxy.removeTopic(topic_to_save);
                                } else {
                                    proxy.removeTopic(topics.get(i));
                                }
                            }
                        }
                        if (myDHPubKey != null) {
                            postToAdvertTopic(context, topic_to_save, advert, myDHPubKey);
                        } else {
                            getKeyFromTopic(context, myDHKey, advert, pkid, topic_to_save);
                        }
                        break;
                    default:
                        topicFeedFetcher.requestMoreData();
                        break;
                }
            }
        };
        topicFeedFetcher.setCallback(callback);
    }

    private static void postToAdvertTopic(Context context, TopicView topic, Identifier myAdvert, Identifier myDHPubKey) {
        try {
            new PostStorage(context).storeDiscussionItem(DiscussionItem.newComment(topic.getHandle(), myDHPubKey.toString(), null));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        WorkerService.getLauncher(context).launchService(ServiceAction.SYNC_DATA);
        new MyAdvertsBridge(context).updatePostedKey(myAdvert);
    }

    private static void getKeyFromTopic(Context context, Identifier myDHKey, Identifier advert, long pkid, TopicView topic) {
        // see if the DH key is posted on the topic
        // if so, update with the secret key formed from the DH keys
        Fetcher<Object> commentFeedFetcher = FetchersFactory.createCommentFeedFetcher(topic.getHandle(), topic);
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                List<String> comments = new ArrayList<>();
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case LAST_ATTEMPT_FAILED:
                    case HAS_MORE_DATA: // ENDED or MORE_DATA
                        commentFeedFetcher.requestMoreData();
                        break;
                    case DATA_ENDED:
                        for (Object obj : commentFeedFetcher.getAllData()) {
                            if (CommentView.class.isInstance(obj)) {
                                String comment = CommentView.class.cast(obj).getCommentText();
                                Identifier secretKey = new Identifier(SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), advert.getBytes(), comment.getBytes()));
                                new ConfirmEncounterEvent(pkid, Arrays.asList((secretKey)), System.currentTimeMillis()).broadcast(context);
                            }
                        }
                        break;
                    default:
                        return;
                }
            }
        };
        commentFeedFetcher.setCallback(callback);
    }
}
