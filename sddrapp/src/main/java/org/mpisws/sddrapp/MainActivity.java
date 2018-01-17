package org.mpisws.sddrapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.mpi_sws.sddrapp.R;
import org.mpisws.sddrapp.googleauth.GoogleNativeAuthenticator;
import org.mpisws.sddrapp.googleauth.GoogleToken;
import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.IEncountersService;
import org.mpisws.sddrservice.embeddedsocial.ESTopics;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Constants;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static EncountersService encountersService = EncountersService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!requestBT() || !request_permissions()) {
            Intent mStartActivity = new Intent(this, MainActivity.class);
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager mgr = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, mPendingIntent);
            System.exit(0);
        }
        findViewById(R.id.testEncountersOnly).setOnClickListener(this);
        findViewById(R.id.testESInactive).setOnClickListener(this);
        findViewById(R.id.testESActive).setOnClickListener(this);
        findViewById(R.id.signIn).setOnClickListener(this);
        findViewById(R.id.deleteAccount).setOnClickListener(this);
        encountersService.startTestEncountersOnly(this);
   }

    @Override
    public void onClick(View v) {
        final ESNotifs.Notif[] notifHolder = {null};

        switch (v.getId()) {
            case R.id.deleteAccount:
                encountersService.deleteAccount();
                break;
            case R.id.signIn:
                if (!encountersService.isSignedIn() && GoogleToken.getToken() == null) {
                    Log.v(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
                }
                break;
            case R.id.testEncountersOnly:
                encountersService.startTestEncountersOnly(this);
                break;
            case R.id.testESInactive:
                encountersService.registerGoogleUser(GoogleToken.getToken());
                encountersService.signIn();
                encountersService.startTestESEnabled(this);
                break;
            case R.id.testESActive:
                encountersService.registerGoogleUser(GoogleToken.getToken());
                encountersService.signIn();
                encountersService.startTestESEnabled(this);

                final ESTopics.GetMessagesCallback msgsCallback = new ESTopics.GetMessagesCallback() {
                    @Override
                    public void onReceiveMessages(final List<ESTopics.Msg> msgs) {
                        Log.d("ESACTIVE_TEST", "End Recv Msgs : " + DateTime.now().getMillis());
                        Log.d("ESACTIVE_TEST", "Start Process Msgs : " + DateTime.now().getMillis());
                        for (ESTopics.Msg msg : msgs) {
                            encountersService.processMessageForBroadcasts(msg);
                        }
                        Log.d("ESACTIVE_TEST", "End Process Msgs : " + DateTime.now().getMillis());
                    }
                };
                final ESNotifs.GetNotificationsCallback notifcallback = new ESNotifs.GetNotificationsCallback() {
                    @Override
                    public void onReceiveNotifications(List<ESNotifs.Notif> notifs) {
                        Log.d("ESACTIVE_TEST", "End Get Notifs: " + DateTime.now().getMillis());
                        Log.v(TAG, "Notif: Calling getNotifsCallback of " + notifs.size() + " notifs");
                        long newestNotifCursorTime = -1;
                        for (ESNotifs.Notif notif : notifs) {
                            if (newestNotifCursorTime < 0 || notif.isNewerThan(newestNotifCursorTime)) {
                                newestNotifCursorTime = notif.getCreatedTime();
                                notifHolder[0] = notif;
                            }
                        }
                        if (notifHolder[0] != null) encountersService.markAllPreviousNotificationsAsRead(notifHolder[0]);
                        Log.d("ESACTIVE_TEST", "Start Recv Msgs : " + DateTime.now().getMillis());
                        encountersService.getMessagesFromNotifications(notifs, msgsCallback);
                    }
                };

                IEncountersService.ForwardingFilter filter = new IEncountersService.ForwardingFilter().setNumHopsLimit(1).setLifetimeTimeMs(Long.MAX_VALUE);
                encountersService.sendBroadcastMsg("", filter.setIsRepeating(true).setNumHopsLimit(1).setLifetimeTimeMs(Long.MAX_VALUE).setFanoutLimit(1000));
                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.d("ESACTIVE_TEST", "Start Get Notifs: " + DateTime.now().getMillis());
                        encountersService.getNotificationsFromNewest(notifcallback, IEncountersService.GetNotificationsRequestFlag.UNREAD_ONLY);
                        encountersService.sendRepeatingBroadcastMessages();
                    }
                };
                for (int i = 0; i < 100; ++i) {
                    handler.postDelayed(runnable, i * 15000);
                }
                break;
            default:
                return;
        }
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            Log.v(TAG, "Bluetooth not enabled");
            Toast.makeText(this, "Exiting encountersService: Bluetooth required", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
        } else {
            Toast.makeText(this, "Bad Response", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean request_permissions() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                Constants.REQUEST_ACCESS_FINE_LOCATION);
                    }
                };
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("encountersService requires location access for Bluetooth protocol; please grant and then restart the application (Note to self: this should be handled better by the actual application using the library)")
                        .setPositiveButton("OK", listener)
                        .setNegativeButton("Cancel", null)
                        .create()
                        .show();
                return false;
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        Constants.REQUEST_ACCESS_FINE_LOCATION);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // permission denied!
                    Log.v(TAG, "No access to fine location");
                    Toast.makeText(this, "Exiting encountersService: Location access required", Toast.LENGTH_SHORT).show();
                    finishAndRemoveTask();
                }
                return;
            }
        }
    }

    private boolean requestBT() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btadapter = bluetoothManager.getAdapter();
        if(btadapter == null||!btadapter.isEnabled())
        {
            Log.v(TAG, "Bluetooth not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
            return false;
        }
        return true;
    }
}
