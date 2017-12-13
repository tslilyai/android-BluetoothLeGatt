package org.mpisws.sddrservice.lib;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.mpisws.sddrservice.encounters.SDDR_Core_Service;

import java.util.concurrent.Semaphore;

import static android.app.AlarmManager.RTC_WAKEUP;

public class Sleeper extends BroadcastReceiver {

    private static final String TAG = Sleeper.class.getSimpleName();
    private static final Semaphore semaphore = new Semaphore(0);
    private static PowerManager.WakeLock wakeLock;
    private static AlarmManager alarmMgr;
    private static PowerManager powerMgr;
    private SDDR_Core_Service sddr_service;

    public Sleeper() {}

    public Sleeper(final SDDR_Core_Service sddr_service) {
        this.sddr_service = sddr_service;
        alarmMgr = (AlarmManager) sddr_service.getSystemService(Context.ALARM_SERVICE);
        powerMgr = (PowerManager) sddr_service.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sleeper");
        wakeLock.acquire();
    }

    /** Must be called from a worker thread
     Should not be called more than once concurrently */
    public synchronized void sleep(final long sleepTime_ms) {
        Log.v(TAG, sleepTime_ms + " ms sleep requested, setting wakeup alarm");
        Utils.myAssert(sleepTime_ms > 0);
        Utils.myAssert(sleepTime_ms < 300000);
        final Intent wakeIntent = new Intent(sddr_service, Sleeper.class);
        final PendingIntent pending = PendingIntent.getBroadcast(sddr_service, 0, wakeIntent, 0 /*PendingIntent.FLAG_CANCEL_CURRENT*/);

        alarmMgr.set(RTC_WAKEUP, System.currentTimeMillis() + sleepTime_ms, pending);

        if (wakeLock.isHeld()) {
            Log.v(TAG, "Releasing wakelock");
            wakeLock.release();
        } else {
            Log.v(TAG, "Wakelock not held (another sleep ongoing?)");
        }
        // TODO block onReceive until this point
        semaphore.acquireUninterruptibly(); // Wait for alarm
        Log.v(TAG, "Sleep (" + sleepTime_ms + " ms) DONE");
        if (sddr_service.was_destroyed) {
            sddr_service.restart();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (wakeLock != null) { // TODO HACK
            Log.v(TAG, "[onReceive] Reacquiring wakelock");
            wakeLock.acquire();
            Log.v(TAG, "[onReceive] Releasing semaphore for sleeper");
            semaphore.release();
       } else {
            Log.w(TAG, "[onReceive] Wakelock totally null !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }
}