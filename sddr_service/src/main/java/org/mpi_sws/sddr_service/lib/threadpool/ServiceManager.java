package org.mpi_sws.sddr_service.lib.threadpool;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.mpi_sws.sddr_service.lib.Utils;

public class ServiceManager {

    private final static String TAG = Utils.getTAG(ServiceManager.class);
    private static int activeServices = 0;
    private static PowerManager.WakeLock wakeLock;

    public synchronized static void registerService(final Context context, final Intent startServiceIntent) {
        if (activeServices == 0) {
            getWakeLock(context).acquire();
            Log.d(TAG, "Registering service: no active service yet, wakelock acquired");
        }
        activeServices++;
        Log.d(TAG, "Registered service, now active: " + activeServices);
        context.startService(startServiceIntent);
    }

    public synchronized static void unregisterService(final Context context) {
        activeServices--;
        Log.d(TAG, "[ServiceManagement] Unregistered service, now active: " + activeServices);
        if (activeServices == 0) {
            getWakeLock(context).release();
            Log.d(TAG, "[ServiceManagement] Last active service finished, wakelock released");
        }
    }

    private static PowerManager.WakeLock getWakeLock(final Context context) {
        if (wakeLock == null) {
            final PowerManager powerMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationLogger-Alarm");
        }
        return wakeLock;
    }
}
