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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.mpi_sws.sddrapp.R;
import org.mpisws.sddrapp.googleauth.GoogleNativeAuthenticator;
import org.mpisws.sddrapp.googleauth.GoogleToken;
import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.IEncountersService;
import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Constants;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static Handler handler;
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
        encountersService.startEncounterService(this);
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.get_all_notifs_old).setOnClickListener(this);
        findViewById(R.id.get_all_notifs_new).setOnClickListener(this);
        findViewById(R.id.get_unread_notifs_old).setOnClickListener(this);
        findViewById(R.id.get_unread_notifs_new).setOnClickListener(this);
        findViewById(R.id.GetMsgs).setOnClickListener(this);
        findViewById(R.id.SendMsg).setOnClickListener(this);
        findViewById(R.id.SendBroadcastMsg).setOnClickListener(this);
        findViewById(R.id.set_notifs).setOnClickListener(this);

        handler = new Handler();
   }

    @Override
    public void onClick(View v) {
        String testEid = "TopicTest6";
        final TextView msgtext = MainActivity.this.findViewById(R.id.new_messages);
        final ESNotifs.Notif[] notifHolder = {null};
        final ESMsgs.GetMessagesCallback msgsCallback = new ESMsgs.GetMessagesCallback() {
            @Override
            public void onReceiveMessages(final List<ESMsgs.Msg> msgs) {
                handler.post(new Runnable() {
                    public void run() {
                        msgtext.setText("");
                        for (ESMsgs.Msg msg : msgs) {
                            encountersService.processMessageForBroadcasts(msg);
                            if ((msg.isFromMe())) {
                                msgtext.append("Me: ");
                            } else {
                                msgtext.append(msg.getEid() + ": ");
                            }
                            msgtext.append(msg.getMsg() + "\n");
                        }
                    }
                });
            }
        };

        final ESNotifs.GetNotificationsCallback notifcallback = new ESNotifs.GetNotificationsCallback() {
            @Override
            public void onReceiveNotifications(List<ESNotifs.Notif> notifs) {
                Log.d(TAG, "Notif: Calling getNotifsCallback of " + notifs.size() + " notifs");
                long newestNotifCursorTime = -1;
                for (ESNotifs.Notif notif : notifs) {
                    if (newestNotifCursorTime < 0 || notif.isNewerThan(newestNotifCursorTime)) {
                        newestNotifCursorTime = notif.getCreatedTime();
                        notifHolder[0] = notif;
                    }
                }
                encountersService.getMessagesFromNotifications(notifs, msgsCallback);
            }
        };

        final EditText msg = MainActivity.this.findViewById(R.id.Msg);
        switch (v.getId()) {
           case R.id.sign_in_button:
                if (!encountersService.isSignedIn() && GoogleToken.getToken() == null) {
                    Log.d(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
                } else {
                    encountersService.signIn();
                    encountersService.createEncounterMsgingChannel(testEid);
                }
                break;
           case R.id.sign_out_button:
                encountersService.signOut();
                break;
           case R.id.GetMsgs:
               msgtext.setText("");
               encountersService.getMsgsFromNewest(testEid, -1, msgsCallback);
               break;
           case R.id.SendMsg:
                encountersService.sendMsg(testEid, msg.getText().toString());
                break;
           case R.id.SendBroadcastMsg:
                encountersService.sendBroadcastMsg(msg.getText().toString(), null, 5);
                break;
           case R.id.get_all_notifs_old:
               if (notifHolder[0] == null)
                   encountersService.getNotificationsWithCursor(notifcallback, IEncountersService.GetNotificationsRequestFlag.ALL, null);
               else
                   encountersService.getNotificationsWithCursor(notifcallback, IEncountersService.GetNotificationsRequestFlag.ALL, notifHolder[0].getCursor());
               break;
           case R.id.get_all_notifs_new:
               encountersService.getNotificationsFromNewest(notifcallback, IEncountersService.GetNotificationsRequestFlag.ALL);
               break;
           case R.id.get_unread_notifs_old:
               if (notifHolder[0] == null)
                   encountersService.getNotificationsWithCursor(notifcallback, IEncountersService.GetNotificationsRequestFlag.UNREAD_ONLY, null);
               else
                   encountersService.getNotificationsWithCursor(notifcallback, IEncountersService.GetNotificationsRequestFlag.UNREAD_ONLY, notifHolder[0].getCursor());
               break;
            case R.id.set_notifs:
                encountersService.markAllPreviousNotificationsAsRead(notifHolder[0]);
                break;
            default:
                // Unknown id.
                Log.d(TAG, "Unknown button press");
        }
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "Bluetooth not enabled");
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
                    Log.d(TAG, "No access to fine location");
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
            Log.d(TAG, "Bluetooth not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
            return false;
        }
        return true;
    }
}
