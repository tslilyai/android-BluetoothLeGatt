package org.mpi_sws.sddr_service.lib;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mpi_sws.sddr_service.lib.threadpool.ServiceManager;

public abstract class ManagedAlarm extends BroadcastReceiver {

    private final String TAG = Utils.getTAG(getClass());
    protected abstract String getAlarmAction();
    protected abstract String getServiceAction();
    protected abstract long getInterval_ms();
    
    public ManagedAlarm() {
    }
    
    private PendingIntent getPendingIntent(final Context context) {
        final Intent intent = new Intent(getAlarmAction());
        return PendingIntent.getBroadcast(context, 0, intent, 0); // TODO look at the flags
    }

    private AlarmManager getAlarmManager(final Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public void set(final Context context) {
        Log.d(TAG, "Setting up alarm");
        getAlarmManager(context).setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), getInterval_ms(),
                getPendingIntent(context));
    }

    public void cancel(final Context context) {
        Log.d(TAG, "Cancelling alarm");
        final PendingIntent pi = getPendingIntent(context);
        getAlarmManager(context).cancel(pi);
        pi.cancel();
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (getAlarmAction().equals(intent.getAction())) {
            ServiceManager.registerService(context, new Intent(getServiceAction()));
        }
    }
}