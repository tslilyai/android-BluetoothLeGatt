package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.CommentsOperations;
import com.microsoft.embeddedsocial.autorest.CommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.SearchOperations;
import com.microsoft.embeddedsocial.autorest.SearchOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperations;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicsOperations;
import com.microsoft.embeddedsocial.autorest.TopicsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseTopicView;
import com.microsoft.embeddedsocial.autorest.models.PostCommentRequest;
import com.microsoft.embeddedsocial.autorest.models.PostCommentResponse;
import com.microsoft.embeddedsocial.autorest.models.PostTopicRequest;
import com.microsoft.embeddedsocial.autorest.models.PostTopicResponse;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.mpisws.sddrservice.embeddedsocial.ESTask.RETRIES;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESMsgTopics {
    private static final String TAG = ESMsgTopics.class.getSimpleName();
    private Context context;
    private SearchOperations ES_SEARCH;
    private TopicsOperations ES_TOPICS;
    private TopicCommentsOperations ES_TOPIC_COMMENTS;

    /* Flag that indicates whether topics (message-channels) should be created for each encounter formed */
    protected boolean addTopics;
    public void setAddTopics(boolean bool) {
        addTopics = bool;
    }

   public ESMsgTopics(Context context, Retrofit RETROFIT, EmbeddedSocialClient ESCLIENT) {
        this.context = context;
        ES_TOPICS = new TopicsOperationsImpl(RETROFIT, ESCLIENT);
        ES_SEARCH = new SearchOperationsImpl(RETROFIT, ESCLIENT);
        ES_TOPIC_COMMENTS = new TopicCommentsOperationsImpl(RETROFIT, ESCLIENT);
    }

    protected void create_topic(final String auth, final Identifier title, final int retries) {
        Log.v(TAG, "Creating topic " + title);
        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title.toString());
        ServiceCallback<PostTopicResponse> serviceCallback = new ServiceCallback<PostTopicResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Posting topic failed");
                if (retries < RETRIES) {
                    create_topic(auth, title, retries + 1);
                }
            }
            @Override
            public void success(ServiceResponse<PostTopicResponse> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                Log.v(TAG, "Created topic for EID " + title);
            }
        };
        ES_TOPICS.postTopicAsync(topicReq, auth, serviceCallback);
    }

    protected void send_msg(final String auth, final MEncounter encounter, final String msg, final int retries) {
        // TODO this sends to all of the different encounterIDs associated with this encounter
        Log.v(TAG, "Sending message to " + encounter.getEncounterIDs(context).size() + " encounters");
        final PostCommentRequest req = new PostCommentRequest();
        req.setText(msg);
        for (final Identifier eid : encounter.getEncounterIDs(context)) {
            final ServiceCallback<FeedResponseTopicView> serviceCallback = new ServiceCallback<FeedResponseTopicView>() {
                @Override
                public void failure(Throwable t) {
                    Log.v(TAG, "Topic Error");
                    if (retries < RETRIES) {
                        send_msg(auth, encounter, msg, retries + 1);
                    }
                }
                @Override
                public void success(ServiceResponse<FeedResponseTopicView> result) {
                    if (!result.getResponse().isSuccess()) {
                        failure(new Throwable());
                        return;
                    }
                    // TODO
                    List<TopicView> topics = result.getBody().getData();
                    //postComment(eid.toString(), topicHandle, req, auth, 0);
                }
            };
            ES_SEARCH.getTopicsAsync(eid.toString(), auth, serviceCallback);
        }
    }
    private void postComment(final String eid, final String topicHandle, final PostCommentRequest req, final String ESAuth, final int retries) {
        ServiceCallback<PostCommentResponse> serviceCallback = new ServiceCallback<PostCommentResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "PostComment to topic failed");
                if (retries < RETRIES) {
                    postComment(eid, topicHandle, req, ESAuth, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<PostCommentResponse> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                Log.v(TAG, "Messages sent to EID " + eid + ": " + req.getText());
            }
        };
        ES_TOPIC_COMMENTS.postCommentAsync(topicHandle, req, ESAuth, serviceCallback);
    }
}
