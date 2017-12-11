package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.IdentityProvider;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.data.Preferences;
import com.microsoft.embeddedsocial.data.model.AccountData;
import com.microsoft.embeddedsocial.data.storage.DatabaseHelper;
import com.microsoft.embeddedsocial.sdk.Options;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.NetworkAvailability;
import com.microsoft.embeddedsocial.server.RequestInfoProvider;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;
import com.microsoft.embeddedsocial.ui.notification.NotificationController;
import com.microsoft.embeddedsocial.ui.util.SocialNetworkAccount;

import org.mpisws.sddrservice.R;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESCore {
    private final Context context;
    private final ESMsgs esMsgs;
    private final ESNotifs esNotifs;

    private SocialNetworkAccount socialNetworkAccount;
    private int defaultCreateMsgChannels;

    public ESCore(Context context) {
        this.context = context;
        Options options = new Options(context.getString(R.string.es_api_key), context.getString(R.string.es_server_url));
        options.verify();

        GlobalObjectRegistry.addObject(options);
        GlobalObjectRegistry.addObject(OpenHelperManager.getHelper(context, DatabaseHelper.class));
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
        GlobalObjectRegistry.addObject(gson);
        EmbeddedSocialServiceProvider serviceProvider = new EmbeddedSocialServiceProvider(context);
        GlobalObjectRegistry.addObject(EmbeddedSocialServiceProvider.class, serviceProvider);
        GlobalObjectRegistry.addObject(new Preferences(context));
        GlobalObjectRegistry.addObject(new RequestInfoProvider(context));
        GlobalObjectRegistry.addObject(new UserAccount(context));
        GlobalObjectRegistry.addObject(new NotificationController(context));
        NetworkAvailability networkAccessibility = new NetworkAvailability();
        networkAccessibility.startMonitoring(context);
        GlobalObjectRegistry.addObject(networkAccessibility);
        // TODO gcm?
        //WorkerService.getLauncher(context).launchService(ServiceAction.BACKGROUND_INIT);

        esMsgs = new ESMsgs();
        esNotifs = new ESNotifs();
        this.defaultCreateMsgChannels = 0;
    }

    public void setDefaultCreateMsgChannels() {
        defaultCreateMsgChannels++;
    }

    public void unsetDefaultCreateMsgChannels() {
        defaultCreateMsgChannels--;
    }

    public void register_user_details(String googletoken, String firstname, String lastname) {
        AccountData accountData = new AccountData();
        accountData.setFirstName(firstname);
        accountData.setLastName(lastname);
        accountData.setIdentityProvider(IdentityProvider.GOOGLE);
        accountData.setThirdPartyAccessToken(googletoken);
        GlobalObjectRegistry.getObject(UserAccount.class).updateAccountDetails(accountData);
        socialNetworkAccount = new SocialNetworkAccount(IdentityProvider.GOOGLE, googletoken);
    }

    public void sign_in() {
        // TODO how to deal with session tokens?
        GlobalObjectRegistry.getObject(UserAccount.class).signInUsingThirdParty(socialNetworkAccount);
    }

    public void sign_out() {
        GlobalObjectRegistry.getObject(UserAccount.class).signOut();
    }

    public void create_topic(String eid) {
        if (defaultCreateMsgChannels > 0)
            esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.CreateOnly));
    }

    public void send_msg(String eid, String msg) {
        esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.SendMsg, msg));
    }

    public void get_msgs(String eid, ESMsgs.MsgsCallback msgsCallback) {
        esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.GetMsgs, msgsCallback));
    }

    public void get_notifs(ESNotifs.NotificationCallback notificationCallback) {
        esNotifs.get_notifications(notificationCallback);
    }
}
