package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;

import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.data.storage.PostStorage;
import com.microsoft.embeddedsocial.data.storage.UserActionProxy;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.model.view.TopicView;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.encounterhistory.MyAdvertsBridge;
import org.mpisws.sddrservice.encounters.SDDR_Native;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.Arrays;
import java.util.List;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESAdvertTopics {
    private static final String TAG = ESAdvertTopics.class.getSimpleName();

    public static void postAdvertAndDHPubKey(Context context, Identifier myAdvert, Identifier myDHPubKey) {
        new PostStorage(context).storePost(myAdvert.toString(), myDHPubKey.toString(), null, PublisherType.USER);
        WorkerService.getLauncher(context).launchService((ServiceAction.SYNC_DATA));
        new MyAdvertsBridge(context).deleteAdvert(myAdvert);
    }

    public static void tryGetSecretKeys(Context context, Identifier myDHKey, long pkid, List<Identifier> adverts) {
        for (Identifier advert : adverts) {
            getTopicAndAct(context, advert, myDHKey, pkid);
        }
    }

    private static void getTopicAndAct(Context context, Identifier advert, Identifier myDHKey, Long pkid) {
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
                        getKeyFromTopic(context, myDHKey, advert, pkid, topic_to_save.getTopicText());
                        break;
                    default:
                        topicFeedFetcher.requestMoreData();
                        break;
                }
            }
        };
        topicFeedFetcher.setCallback(callback);
    }

    private static void getKeyFromTopic(Context context, Identifier myDHKey, Identifier advert, long pkid, String dhPubKey) {
        Identifier secretKey = new Identifier(SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), advert.getBytes(), dhPubKey.getBytes()));
        new ConfirmEncounterEvent(pkid, Arrays.asList((secretKey)), System.currentTimeMillis()).broadcast(context);
    }
}
