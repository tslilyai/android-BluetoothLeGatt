package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.IdentityProvider;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.data.Preferences;
import com.microsoft.embeddedsocial.data.model.AccountData;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.NetworkAvailability;
import com.microsoft.embeddedsocial.server.RequestInfoProvider;
import com.microsoft.embeddedsocial.ui.notification.NotificationController;
import com.microsoft.embeddedsocial.ui.util.SocialNetworkAccount;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESCore {
    private Context context;
    private UserAccount userAccount;
    private SocialNetworkAccount socialNetworkAccount;
    private ESMsgs esMsgs;
    private int defaultCreateMsgChannels;

    public ESCore(Context context) {
        this.context = context;
        this.userAccount = new UserAccount(context);
        this.esMsgs = new ESMsgs(userAccount);
        this.defaultCreateMsgChannels = 0;

        // TODO session tokens?
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

    public void create_topic(String eid) {
        if (!userAccount.isSignedIn()) return;
        if (defaultCreateMsgChannels > 0)
            esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.CreateOnly));
    }

    public void send_msg(String eid, String msg) {
        if (!userAccount.isSignedIn()) return;
        esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.SendMsg, msg));
    }

    public void get_msgs(String eid, ESMsgs.MsgsCallback msgsCallback) {
        if (!userAccount.isSignedIn()) return;
        esMsgs.find_and_act_on_topic(eid, new ESMsgs.TopicAction(ESMsgs.TopicAction.TATyp.GetMsgs, msgsCallback));
    }
}
