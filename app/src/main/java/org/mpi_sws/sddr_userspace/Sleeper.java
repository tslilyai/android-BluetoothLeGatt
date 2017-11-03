package org.mpi_sws.sddr_userspace;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.mpi_sws.sddr_userspace.lib.Utils;

import java.util.concurrent.Semaphore;

import static android.app.AlarmManager.RTC_WAKEUP;

public class Sleeper extends BroadcastReceiver {

    private static final String ACTION_WAKE_UP = "ACTION_WAKE_UP";
    private static final String TAG = "SDDR: " + Sleeper.class.getSimpleName();
    private static final Semaphore semaphore = new Semaphore(0);
    private static PowerManager.WakeLock wakeLock;
    private static AlarmManager alarmMgr;
    private static PowerManager powerMgr;
    private Context context;

    public Sleeper() {}

    public Sleeper(final Context context) {
        this.context = context;
        alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        powerMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sleeper");
        wakeLock.acquire();
    }

    /** Must be called from a worker thread
     Should not be called more than once concurrently */
    public synchronized void sleep(final long sleepTime_ms) {
        Log.d(TAG, sleepTime_ms + " ms sleep requested, setting wakeup alarm");
        Utils.myAssert(sleepTime_ms > 0);
        Utils.myAssert(sleepTime_ms < 300000);
        final Intent wakeIntent = new Intent(context, Sleeper.class);//ACTION_WAKE_UP);
        final PendingIntent pending = PendingIntent.getBroadcast(context, 0, wakeIntent, 0 /*PendingIntent.FLAG_CANCEL_CURRENT*/);

        alarmMgr.set(RTC_WAKEUP, System.currentTimeMillis() + sleepTime_ms, pending);

        if (wakeLock.isHeld()) {
            Log.d(TAG, "Releasing wakelock");
            wakeLock.release();
        } else {
            Log.d(TAG, "Wakelock not held (another sleep ongoing?)");
        }
        // TODO block onReceive until this point
        semaphore.acquireUninterruptibly(); // Wait for alarm
        Log.d(TAG, "Sleep (" + sleepTime_ms + " ms) DONE");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (wakeLock != null) { // TODO HACK
            Log.d(TAG, "[onReceive] Reacquiring wakelock");
            wakeLock.acquire();
            Log.d(TAG, "[onReceive] Releasing semaphore for sleeper");
            semaphore.release();
        } else {
            Log.w(TAG, "[onReceive] Wakelock totally null !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }
}