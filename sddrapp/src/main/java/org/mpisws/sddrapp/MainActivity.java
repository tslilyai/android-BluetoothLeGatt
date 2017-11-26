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
import org.mpisws.sddrservice.embedded_social.ESTask;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.lib.Constants;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "SDDR_API: " + MainActivity.class.getSimpleName();
    private static SDDR_API sddrAPI;
    private static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!requestBT() || !request_permissions()) {
            return;
        }

        if (sddrAPI == null) {
            sddrAPI = new SDDR_API(this);
            sddrAPI.start_service();
            sddrAPI.enable_msging();
        }

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.get_messages).setOnClickListener(this);
        findViewById(R.id.SendMsg).setOnClickListener(this);
        findViewById(R.id.AddLink).setOnClickListener(this);

        handler = new Handler();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.AddLink:
                if (sddrAPI != null) {
                    EditText linkID = (EditText) MainActivity.this.findViewById(R.id.linkID);
                    sddrAPI.add_linkid(linkID.getText().toString());
                }
                break;
            case R.id.SendMsg:
                if (sddrAPI != null) {
                    final EditText msg = (EditText) MainActivity.this.findViewById(R.id.Msg);
                    Log.d(TAG, "SENDING MESSAGE " + msg.getText().toString());
                    final List<MEncounter> encounters = sddrAPI.get_encounters(null);
                    if (encounters.size() > 0)
                        sddrAPI.send_msg(encounters.get(0), msg.getText().toString());
                }
                break;
            case R.id.sign_in_button:
                if (GoogleToken.getToken() == null) {
                    Log.d(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
                    break;
                }

                sddrAPI.register_user("Lily", "Tsai");
                break;
            case R.id.sign_out_button:
                Log.d(TAG, "Signing out");
                if (sddrAPI != null) sddrAPI.signout_user();
                break;
            case R.id.get_messages:
                Log.d(TAG, "Getting messages");
                ESTask.NotificationCallback callback = new ESTask.NotificationCallback() {
                    @Override
                    public void onReceiveMessages(final Map<String, List<String>> messages) {
                        handler.post(new Runnable() {
                            public void run() {
                                final TextView text = MainActivity.this.findViewById(R.id.new_messages);
                                for (Map.Entry<String, List<String>> entry : messages.entrySet()) {
                                    text.append(entry.getKey() + ": ");
                                    for (String msg : entry.getValue()) {
                                        text.append(entry.getKey() + ": " + msg + "\n");
                                    }
                                }
                            }
                        });
                    }
                };
                if (sddrAPI != null) sddrAPI.get_msgs(callback);
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
