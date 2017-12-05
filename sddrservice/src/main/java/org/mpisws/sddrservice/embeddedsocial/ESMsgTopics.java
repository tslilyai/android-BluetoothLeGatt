package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
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
import org.w3c.dom.Comment;

import java.util.LinkedList;
import java.util.List;

import retrofit2.Retrofit;

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

    private void create_topic(final String auth, final Identifier title, ServiceCallback<PostTopicResponse> serviceCallback) {
        Log.v(TAG, "Creating topic " + title);
        PostTopicRequest topicReq = new PostTopicRequest();
        topicReq.setPublisherType(PublisherType.USER);
        topicReq.setTitle(title.toString());
        ES_TOPICS.postTopicAsync(topicReq, auth, serviceCallback);
    }
    private void delete_topic(String topichandle, String auth, ServiceCallback<Object> serviceCallback) {
        ES_TOPICS.deleteTopicAsync(topichandle,auth,serviceCallback);
    }

    protected void create_topic(final String auth, final Identifier title, final int retries) {
        Log.v(TAG, "Creating topic " + title);
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
        create_topic(auth, title, serviceCallback);
    }

    protected void get_encounter_msgs(final Identifier eid, final ESTask.MsgsCallback msgsCallback, final String auth, final int retries) {
        final ServiceCallback<FeedResponseCommentView> serviceCallbackComments = new ServiceCallback<FeedResponseCommentView>() {
            @Override
            public void failure(Throwable t) {
                if (retries < RETRIES) {
                    get_encounter_msgs(eid, msgsCallback, auth, retries + 1);
                }
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

        ServiceCallback<FeedResponseTopicView> serviceCallback = new ServiceCallback<FeedResponseTopicView>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Topic Error");
                if (retries < RETRIES) {
                    get_encounter_msgs(eid, msgsCallback, auth, retries + 1);
                }
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
                    Log.d(TAG, ">1 topic for this encounter " + eid.toString() + ", removing topichandle " + topichandle);
                    removeTopicAndGetMsgs(auth, eid, topichandle, msgsCallback, 0);
                    return;
                }
                // we need to try and create the topic, which calls send_msg again
                if (topics.size() == 0) {
                    createTopicAndGetMsgs(auth, eid, msgsCallback, 0);
                    return;
                }
                if (topics.size() == 1) {
                    ES_TOPIC_COMMENTS.getTopicCommentsAsync(topics.get(0).getTopicHandle(), auth, serviceCallbackComments);
                }
            }
        };
        // sleep for some random time < 0.1s before sending
        try {
            Thread.sleep((long) Math.random() * 100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ES_SEARCH.getTopicsAsync(eid.toString(), auth, serviceCallback);
    }

    private void createTopicAndGetMsgs(final String auth, final Identifier eid, final ESTask.MsgsCallback callback, final int retries) {
        ServiceCallback<PostTopicResponse> serviceCallback = new ServiceCallback<PostTopicResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "createTopicAndGet Failure");
                if (retries < RETRIES) {
                    createTopicAndGetMsgs(auth, eid, callback, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<PostTopicResponse> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                get_encounter_msgs(eid, callback, auth, 0);
            }
        };
        create_topic(auth, eid, serviceCallback);
    }

    private void removeTopicAndGetMsgs(final String auth, final Identifier eid, final String topichandle, final ESTask.MsgsCallback callback, final int retries) {
        ServiceCallback<Object> serviceCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "removeTopicAndSend Failure");
                if (retries < RETRIES) {
                    removeTopicAndGetMsgs(auth, eid, topichandle, callback, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<Object> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                get_encounter_msgs(eid, callback, auth, 0);
            }
        };
        delete_topic(topichandle, auth, serviceCallback);
    }

    protected void send_msg(final String auth, final Identifier eid, final String msg, final int retries) {
        final PostCommentRequest req = new PostCommentRequest();
        req.setText(msg);
        Log.v(TAG, "Sending message to " + eid.toString());
        final ServiceCallback<FeedResponseTopicView> serviceCallback = new ServiceCallback<FeedResponseTopicView>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "Topic Error");
                if (retries < RETRIES) {
                    send_msg(auth, eid, msg, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<FeedResponseTopicView> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                List<TopicView> topics = result.getBody().getData();
                // if there are duplicate topics with this name, delete the one created later and
                // try to send the message again
                if (topics.size() > 1) {
                    Utils.myAssert(topics.size() == 2);
                    String topichandle = (topics.get(0).getCreatedTime().compareTo(topics.get(1).getCreatedTime()) > 0)
                            ? topics.get(0).getTopicHandle() : topics.get(1).getTopicHandle();
                    Log.d(TAG, ">1 topic for this encounter " + eid.toString() + ", removing topichandle " + topichandle);
                    removeTopicAndSendMsg(auth, msg, eid, topichandle, 0);
                    return;
                }
                // we need to try and create the topic, which calls send_msg again
                if (topics.size() == 0) {
                    createTopicAndSendMsg(auth, msg, eid, 0);
                    return;
                }
                if (topics.size() == 1) {
                    postComment(eid.toString(), topics.get(0).getTopicHandle(), req, auth, 0);
                }
            }
        };
        // sleep for some random time < 0.1s before sending
        try {
            Thread.sleep((long) Math.random() * 100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ES_SEARCH.getTopicsAsync(eid.toString(), auth, serviceCallback);
    }

    private void createTopicAndSendMsg(final String auth, final String msg, final Identifier eid, final int retries) {
        ServiceCallback<PostTopicResponse> serviceCallback = new ServiceCallback<PostTopicResponse>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "createTopicAndSend Failure");
                if (retries < RETRIES) {
                    createTopicAndSendMsg(auth, msg, eid, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<PostTopicResponse> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                send_msg(auth, eid, msg, 0);
            }
        };
        create_topic(auth, eid, serviceCallback);
    }

    private void removeTopicAndSendMsg(final String auth, final String msg, final Identifier eid, final String topichandle, final int retries) {
        ServiceCallback<Object> serviceCallback = new ServiceCallback<Object>() {
            @Override
            public void failure(Throwable t) {
                Log.v(TAG, "removeTopicAndSend Failure");
                if (retries < RETRIES) {
                    removeTopicAndSendMsg(auth, msg, eid, topichandle, retries + 1);
                }
            }

            @Override
            public void success(ServiceResponse<Object> result) {
                if (!result.getResponse().isSuccess()) {
                    failure(new Throwable());
                    return;
                }
                send_msg(auth, eid, msg, 0);
            }
        };
        delete_topic(topichandle, auth, serviceCallback);
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
