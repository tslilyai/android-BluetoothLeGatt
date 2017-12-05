package org.mpisws.sddrapp;

import android.Manifest;
import android.app.AlertDialog;
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
import org.mpisws.sddrservice.SDDR_API;
import org.mpisws.sddrservice.embeddedsocial.ESMsgTopics;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.embeddedsocial.ESTask;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "SDDR_API: " + MainActivity.class.getSimpleName();
    private static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!requestBT() || !request_permissions()) {
            return;
        }

        SDDR_API.start_service(this);
        SDDR_API.enable_msging();

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.get_notifs).setOnClickListener(this);
        findViewById(R.id.SendMsg).setOnClickListener(this);
        findViewById(R.id.AddLink).setOnClickListener(this);

        handler = new Handler();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.AddLink:
                EditText linkID = (EditText) MainActivity.this.findViewById(R.id.linkID);
                SDDR_API.add_linkid(linkID.getText().toString());
                break;
            case R.id.SendMsg:
                final EditText msg = (EditText) MainActivity.this.findViewById(R.id.Msg);
                Log.d(TAG, "SENDING MESSAGE " + msg.getText().toString());
                List<Identifier> encounters = SDDR_API.get_encounters(null);
                if (encounters.size() > 0)
                    SDDR_API.send_msg(encounters.get(0), msg.getText().toString());
                break;
            case R.id.sign_in_button:
                if (GoogleToken.getToken() == null) {
                    Log.d(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
                }
                break;
            case R.id.get_notifs:
                Log.d(TAG, "Getting notifs");
                ESTask.NotificationCallback callback = new ESTask.NotificationCallback() {
                    @Override
                    public void onReceiveNotifs(final Queue<ESNotifs.Notif> notifs) {
                        handler.post(new Runnable() {
                            public void run() {
                                final TextView text = MainActivity.this.findViewById(R.id.new_notifs);
                                for (ESNotifs.Notif notif : notifs) {
                                    text.append(notif.getEid().toString() + ": ");
                                    text.append(notif.getMsg() + " (" + notif.getTimestamp().toString() + ")\n");
                                }
                            }
                        });
                    }
                };
                SDDR_API.get_notifs(callback);
                break;
            case R.id.get_msgs:
                Log.d(TAG, "Getting messages");
                final List<Identifier> encounters2 = SDDR_API.get_encounters(null);
                if (encounters2.size() > 0) {
                    ESTask.MsgsCallback callback2 = new ESTask.MsgsCallback() {
                        @Override
                        public void onReceiveMessages(final List<String> messages) {
                            handler.post(new Runnable() {
                                public void run() {
                                    final TextView text = MainActivity.this.findViewById(R.id.new_messages);
                                    for (String msg : messages) {
                                        text.append(encounters2.get(0).toString() + ": ");
                                        text.append(msg);
                                        text.append("\n");
                                    }
                                }
                            });
                        }
                    };
                    SDDR_API.get_msgs(encounters2.get(0), callback2);
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
                        .setMessage("SDDR_API requires location access for Bluetooth protocol")
                        .setPositiveButton("OK", listener)
                        .setNegativeButton("Cancel", null)
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        Constants.REQUEST_ACCESS_FINE_LOCATION);
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
                    finishAndRemoveTask ();
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
