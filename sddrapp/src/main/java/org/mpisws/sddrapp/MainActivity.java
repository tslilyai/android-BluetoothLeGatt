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
import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
        findViewById(R.id.get_notifs_old).setOnClickListener(this);
        findViewById(R.id.get_notifs_new).setOnClickListener(this);
        findViewById(R.id.get_msgs).setOnClickListener(this);
        findViewById(R.id.SendMsg).setOnClickListener(this);
        findViewById(R.id.AddLink).setOnClickListener(this);

        handler = new Handler();
   }

    @Override
    public void onClick(View v) {
        String testEid = new String("TopicTest3".getBytes());
        final TextView notiftext = MainActivity.this.findViewById(R.id.new_notifs);
        final TextView msgtext = MainActivity.this.findViewById(R.id.new_messages);
        final String[] notifCursor = {null};
        final ESMsgs.GetMessagesCallback msgsCallback = new ESMsgs.GetMessagesCallback() {
            @Override
            public void onReceiveMessages(final List<ESMsgs.Msg> msgs) {
                handler.post(new Runnable() {
                    public void run() {
                        for (ESMsgs.Msg msg : msgs) {
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
        final ESNotifs.GetEncountersOfNotifsCallback getEncountersOfNotifsCallback = new ESNotifs.GetEncountersOfNotifsCallback() {
            @Override
            public void onReceiveEncounters(Set<String> encounterIds) {
                Log.d(TAG, "Notif: Calling onReceiveEncounter of " + encounterIds.size() + " encounters");
                for (String eid : encounterIds) {
                    encountersService.getNewMsgs(eid, msgsCallback);
                }
            }
        };
        final ESNotifs.GetNotificationsCallback notifcallback = new ESNotifs.GetNotificationsCallback() {
            @Override
            public void onReceiveNotifications(List<ESNotifs.Notif> notifs) {
                for (ESNotifs.Notif notif : notifs) {
                    notifCursor[0] = notif.getNotifCursor();
                }
                Log.d(TAG, "Notif: Calling getNotifsCallback of " + notifs.size() + " notifs");
                encountersService.getEncountersOfNotifs(notifs, getEncountersOfNotifsCallback);
            }
        };

        switch (v.getId()) {
            case R.id.AddLink:
                break;
            case R.id.SendMsg:
                final EditText msg = MainActivity.this.findViewById(R.id.Msg);
                List<String> encounters = encountersService.getEncounters(null);
                Log.d(TAG, "Sending message " + msg.getText().toString() + " for " + encounters.size() + " encounters");
                List<String> list = new LinkedList<>();
                for (int i = 0; i < 100; i++)
                    list.add(msg.getText().toString() + i);
                encountersService.sendMsgs(testEid, list);
                break;
            case R.id.sign_in_button:
                if (GoogleToken.getToken() == null) {
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
            case R.id.get_notifs_old:
               Log.d(TAG, "Getting notifs old");
                notiftext.setText("");
                encountersService.getNotifsWithCursor(notifcallback, notifCursor[0]);
                break;
            case R.id.get_notifs_new:
                notiftext.setText("");
                Log.d(TAG, "Getting notifs new");
                encountersService.getNewNotifs(notifcallback);
                break;

            case R.id.get_msgs:
                msgtext.setText("");
               encountersService.getNewMsgs(testEid, msgsCallback);
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
