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
import android.os.Parcel;
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
import org.mpisws.sddrservice.SDDR_API;
import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "SDDR_API: " + MainActivity.class.getSimpleName();
    private static Handler handler;

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
        SDDR_API.start_service(this);
        SDDR_API.enable_msging();

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.get_notifs).setOnClickListener(this);
        findViewById(R.id.get_msgs).setOnClickListener(this);
        findViewById(R.id.SendMsg).setOnClickListener(this);
        findViewById(R.id.AddLink).setOnClickListener(this);

        handler = new Handler();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.AddLink:
                EditText linkID = MainActivity.this.findViewById(R.id.linkID);
                SDDR_API.add_linkid(linkID.getText().toString());
                break;
            case R.id.SendMsg:
                final EditText msg = MainActivity.this.findViewById(R.id.Msg);
                List<Identifier> encounters = SDDR_API.get_encounters(null);
                if (encounters.size() > 0) {
                    Log.d(TAG, "Sending message " + msg.getText().toString() + " for " + encounters.get(0).toString());
                    List<String> list = new LinkedList<>();
                    list.add(msg.getText().toString());
                    for (Identifier e : encounters) {
                        SDDR_API.send_msgs(encounters.get(0), list);
                    }
                }
                break;
            case R.id.sign_in_button:
                if (GoogleToken.getToken() == null) {
                    Log.d(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
                } else {
                    SDDR_API.sign_in();
                }
                break;
             case R.id.sign_out_button:
                SDDR_API.sign_out();
                break;
            case R.id.get_notifs:
                Log.d(TAG, "Getting notifs");
                ESNotifs.NotificationCallback callback = new ESNotifs.NotificationCallback() {
                    @Override
                    public int describeContents() {return 0;}

                    @Override
                    public void writeToParcel(Parcel dest, int flags) {}

                    @Override
                    public void onReceiveNotif(final ESNotifs.Notif notif) {
                        handler.post(new Runnable() {
                            public void run() {
                                final TextView text = MainActivity.this.findViewById(R.id.new_notifs);
                                text.append(notif.getEid().toString() + ": ");
                                text.append(notif.getMsg() + " (" + notif.getTimestamp() + ")\n");
                            }
                        });
                    }
                };
                SDDR_API.get_notifs(callback);
                break;
            case R.id.get_msgs:
                Log.d(TAG, "Getting messages");
                final List<Identifier> encounters2 = SDDR_API.get_encounters(null);
                Log.d(TAG, "Getting messages for " + encounters2.size() + " encounters");
                if (encounters2.size() > 0) {
                   for (final Identifier e : encounters2) {
                        ESMsgs.MsgCallback callback2 = new ESMsgs.MsgCallback() {
                            @Override
                            public void onReceiveMessage(final ESMsgs.Msg msg) {
                                handler.post(new Runnable() {
                                    public void run() {
                                        final TextView text = MainActivity.this.findViewById(R.id.new_messages);
                                        if ((msg.isFromMe())) {
                                            text.append("Me: ");
                                        } else {
                                            text.append(e.toString() + ": ");
                                        }
                                        text.append(msg.getMsg());
                                        text.append("\t" + msg.getTimestamp() + "\n");
                                    }
                                });
                            }
                        };
                        SDDR_API.get_msgs(e, callback2);
                    }
                }
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
            Toast.makeText(this, "Exiting SDDR_API: Bluetooth required", Toast.LENGTH_SHORT).show();
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
                        .setMessage("SDDR_API requires location access for Bluetooth protocol; please grant and then restart the application (Note to self: this should be handled better by the actual application using the library)")
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
                    Toast.makeText(this, "Exiting SDDR_API: Location access required", Toast.LENGTH_SHORT).show();
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
