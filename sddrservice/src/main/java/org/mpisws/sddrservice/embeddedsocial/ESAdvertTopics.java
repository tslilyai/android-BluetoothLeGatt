package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.data.storage.UserActionProxy;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.model.view.TopicView;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.encounterhistory.MyAdvertsBridge;
import org.mpisws.sddrservice.encounterhistory.NewAdvertsBridge;
import org.mpisws.sddrservice.encounters.SDDR_Native;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.Arrays;
import java.util.List;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESAdvertTopics {
    private static final String TAG = ESAdvertTopics.class.getSimpleName();

    public static void postAdvertAndDHPubKey(Context context, Identifier myAdvert, Identifier myDHPubKey) {
        UserAccount.getInstance().postTopicUnique(myAdvert.toString(), myDHPubKey.toString());
        new MyAdvertsBridge(context).deleteMyAdvert(myAdvert);
    }

    public static void tryToComputeSecret(Context context, Identifier myDHKey, long pkid, Identifier advert) {
        Log.d(TAG, "Looking for " + advert.toString());
        getTopicAndAct(context, advert, myDHKey, pkid);
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
                        if (topics.isEmpty()) {
                            Log.d(TAG, "Topic for advert doesn't exist: " + advert.toString());
                            return;
                        }
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
                        Log.d(TAG, "Got topic for advert " + advert.toString());
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
        Log.d(TAG, "Computing secret key with myDHKey " + myDHKey.toString() + " " + advert.toString() + " and " + dhPubKey);
        byte[] secretKey = SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), advert.getBytes(), Utils.hexStringToByteArray(dhPubKey));
        if (secretKey!= null) {
            Identifier secretKeyID = new Identifier(secretKey);
            Log.d(TAG, "Got secret key for advert " + advert.toString() + " " + secretKeyID.toString());
            new ConfirmEncounterEvent(pkid, secretKeyID, System.currentTimeMillis()).broadcast(context);
            new NewAdvertsBridge(context).deleteNewAdvert(advert);
        }
    }
}
