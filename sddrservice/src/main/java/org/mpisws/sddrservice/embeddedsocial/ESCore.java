package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.data.Preferences;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.NetworkAvailability;
import com.microsoft.embeddedsocial.server.RequestInfoProvider;
import com.microsoft.embeddedsocial.ui.notification.NotificationController;

/**
 * Created by tslilyai on 12/8/17.
 */

public class ESCore {
    public ESCore(Context context) {
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
}
