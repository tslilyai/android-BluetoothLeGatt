package org.mpisws.sddrservice.embeddedsocial;

import android.util.Log;

import com.microsoft.embeddedsocial.autorest.EmbeddedSocialClient;
import com.microsoft.embeddedsocial.autorest.SearchOperations;
import com.microsoft.embeddedsocial.autorest.SearchOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperations;
import com.microsoft.embeddedsocial.autorest.TopicCommentsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.TopicsOperations;
import com.microsoft.embeddedsocial.autorest.TopicsOperationsImpl;
import com.microsoft.embeddedsocial.autorest.models.CommentView;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseCommentView;
import com.microsoft.embeddedsocial.autorest.models.FeedResponseTopicView;
import com.microsoft.embeddedsocial.autorest.models.PostCommentRequest;
import com.microsoft.embeddedsocial.autorest.models.PostCommentResponse;
import com.microsoft.embeddedsocial.autorest.models.PostTopicRequest;
import com.microsoft.embeddedsocial.autorest.models.PostTopicResponse;
import com.microsoft.embeddedsocial.autorest.models.PublisherType;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.LinkedList;
import java.util.List;

import retrofit2.Retrofit;

/**
 * Created by tslilyai on 11/14/17.
 */

public class ESMsgTopics {
    private static final String TAG = ESMsgTopics.class.getSimpleName();
    private SearchOperations ES_SEARCH;
    private TopicsOperations ES_TOPICS;
    private TopicCommentsOperations ES_TOPIC_COMMENTS;

    private enum TopicAction {
        SendMsg,
        CreateOnly,
        GetMsgs
    }

    /* Flag that indicates whether topics (message-channels) should be created for each encounter formed */
    protected boolean addTopics;

    public void setAddTopics(boolean bool) {
        addTopics = bool;
    }

    public ESMsgTopics(Retrofit RETROFIT, EmbeddedSocialClient ESCLIENT) {
        ES_TOPICS = new TopicsOperationsImpl(RETROFIT, ESCLIENT);
        ES_SEARCH = new SearchOperationsImpl(RETROFIT, ESCLIENT);
        ES_TOPIC_COMMENTS = new TopicCommentsOperationsImpl(RETROFIT, ESCLIENT);
    }
    protected void create_topic(final String auth, final Identifier title) {
        Log.v(TAG, "Creating topic " + title);
        find_and_do_action_on_topic(auth, title, TopicAction.CreateOnly, null, null);
    }

    protected void get_encounter_msgs(Identifier eid, ESTask.MsgsCallback msgsCallback, String auth) {
        Log.d(TAG, "Getting encounter msgs for " + eid);
        find_and_do_action_on_topic(auth, eid, TopicAction.GetMsgs, msgsCallback, null);
    }

    protected void send_msg(final String auth, final Identifier eid, final String msg) {
        final PostCommentRequest req = new PostCommentRequest();
        req.setText(msg);
        Log.v(TAG, "Sending message " + msg + " to " + eid.toString() + " with auth " + auth);
        find_and_do_action_on_topic(auth, eid, TopicAction.SendMsg, null, msg);
    }

    /* Private helper functions */
    private void find_and_do_action_on_topic(final String auth, final Identifier title, final TopicAction topicAction, final ESTask.MsgsCallback msgsCallback, final String msg) {
        // callback if we have to create a new topic. this simply re-invokes find_and_do_action on success
        final ServiceCallback<PostTopicResponse> createTopicCallback = new ServiceCallback<PostTopicResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed create topic");
            }

            @Override
            public void success(ServiceResponse<PostTopicResponse> result) {
                find_and_do_action_on_topic(auth, title, topicAction, msgsCallback, msg);
            }
        };
        // callback if we have to create a new topic. this simply re-invokes find_and_do_action
        final ServiceCallback<Object> removeTopicCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed remove topic");
            }

            @Override
            public void success(ServiceResponse<Object> result) {
                find_and_do_action_on_topic(auth, title, topicAction, msgsCallback, msg);
            }
        };
        ServiceCallback<FeedResponseTopicView> serviceCallback = new ServiceCallback<FeedResponseTopicView>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Failed search topics");
            }

            @Override
            public void success(ServiceResponse<FeedResponseTopicView> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                List<TopicView> topics = result.getBody().getData();
                if (topics.size() > 1) {
                    Utils.myAssert(topics.size() == 2);
                    String topichandle = (topics.get(0).getCreatedTime().compareTo(topics.get(1).getCreatedTime()) > 0)
                            ? topics.get(0).getTopicHandle() : topics.get(1).getTopicHandle();
                    Log.d(TAG, ">1 topic for this encounter " + title.toString() + ", removing topichandle " + topichandle);
                    delete_topic(topichandle, auth, removeTopicCallback);
                    return;
                }
                // we need to try and create the topic, which calls send_msg again
                if (topics.size() == 0) {
                    create_topic(auth, title, createTopicCallback);
                    return;
                }
                if (topics.size() == 1) {
                    String topichandle = topics.get(0).getTopicHandle();
                    switch (topicAction) {
                        case SendMsg: {
                            PostCommentRequest req = new PostCommentRequest();
                            req.setText(msg);
                            ServiceCallback<PostCommentResponse> serviceCallback = new ServiceCallback<PostCommentResponse>() {
                                @Override
                                public void failure(Throwable t) {
                                    Log.v(TAG, "PostComment to topic failed");
                                }

                                @Override
                                public void success(ServiceResponse<PostCommentResponse> result) {
                                    if (!result.getResponse().isSuccess()) {
                                        failure(new Throwable());
                                        return;
                                    }
                                    Log.v(TAG, "Messages sent to EID " + title.toString() + ": " + msg);
                                }
                            };
                            ES_TOPIC_COMMENTS.postCommentAsync(topichandle, req, auth, serviceCallback);
                            break;
                        }
                        case GetMsgs: {
                            final ServiceCallback<FeedResponseCommentView> serviceCallbackComments = new ServiceCallback<FeedResponseCommentView>() {
                                @Override
                                public void failure(Throwable t) {
                                }

                                @Override
                                public void success(ServiceResponse<FeedResponseCommentView> result) {
                                    if (!result.getResponse().isSuccess()) {
                                        failure(new Throwable());
                                        return;
                                    }
                                    List<String> comments = new LinkedList<>();
                                    for (CommentView comment : result.getBody().getData()) {
                                        comments.add(comment.getText());
                                    }
                                    msgsCallback.onReceiveMessages(comments);
                                }
                            };
                            ES_TOPIC_COMMENTS.getTopicCommentsAsync(topichandle, auth, serviceCallbackComments);
                            break;
                        }
                        case CreateOnly:
                            return;
                    }
                }
            }
        };
        ES_SEARCH.getTopicsAsync(title.toString(), auth, serviceCallback);
    }
    private void create_topic(final String auth, final Identifier title, ServiceCallback serviceCallback) {
        try {
            Thread.sleep((long) Math.random() * 100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.v(TAG, "Creating topic " + title);
        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title.toString());
        topicReq.setText(title.toString());
        // sleep for some random time < 0.1s before sending
        ES_TOPICS.postTopicAsync(topicReq, auth, serviceCallback);
    }
    private void delete_topic(String topichandle, String auth, ServiceCallback<Object> serviceCallback) {
        ES_TOPICS.deleteTopicAsync(topichandle, auth, serviceCallback);
    }
}
