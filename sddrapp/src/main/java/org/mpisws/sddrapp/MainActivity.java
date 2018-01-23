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
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.mpi_sws.sddrapp.R;
import org.mpisws.sddrapp.googleauth.GoogleNativeAuthenticator;
import org.mpisws.sddrapp.googleauth.GoogleToken;
import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.embeddedsocial.ESTopics;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Utils;

import java.util.Random;

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
            AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, mPendingIntent);
            System.exit(0);
        }
        findViewById(R.id.testEncountersOnly).setOnClickListener(this);
        findViewById(R.id.testESTopics).setOnClickListener(this);
        findViewById(R.id.testConfirmES).setOnClickListener(this);
        findViewById(R.id.testConfirmActive).setOnClickListener(this);
        findViewById(R.id.signIn).setOnClickListener(this);
        findViewById(R.id.deleteAccount).setOnClickListener(this);
        encountersService.startTestEncountersES(this);
   }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.deleteAccount:
               if (!encountersService.isSignedIn() && GoogleToken.getToken() != null) {
                    encountersService.registerGoogleUser(GoogleToken.getToken());
                    encountersService.signIn();
               }
               encountersService.deleteAccount();
               break;
            case R.id.signIn:
                if (!encountersService.isSignedIn() && GoogleToken.getToken() == null) {
                    Log.v(TAG, "Not registered with Google yet");
                    GoogleNativeAuthenticator GNA = new GoogleNativeAuthenticator(GoogleNativeAuthenticator.AuthenticationMode.SIGN_IN_ONLY, this);
                    GNA.makeAuthRequest();
               }
               break;
            case R.id.testESTopics:
                encountersService.startTestTopics(this);
                PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ESTopics");
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire();
                    Log.d("TOPICS_TEST", "Acquired wakelock");
                }
               if (!encountersService.isSignedIn() && GoogleToken.getToken() != null) {
                    encountersService.registerGoogleUser(GoogleToken.getToken());
                    encountersService.signIn();
               }
               final Runnable runnableinactive = new Runnable() {
                    Random ran = new Random(System.currentTimeMillis());
                    @Override
                    public void run() {
                        /*Log.d("TOPICS_TEST", System.currentTimeMillis() + ": SPINNING!");
                        String encrypted = "hello world";
                        for (int i = 0; i < 10000; ++i) {
                            encrypted = Utils.encrypt(String.valueOf(System.currentTimeMillis()), encrypted);
                        }
                        Log.d("TOPICS_TEST", System.currentTimeMillis() + ": SLEEPING!");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }*/
                        Log.d("TOPICS_TEST", System.currentTimeMillis() + ": CREATING " + ESTopics.NUM_TOPICS_TO_CREATE + " topics");
                        for (int i = 0; i < ESTopics.NUM_TOPICS_TO_CREATE; ++i) {
                            Log.d("TOPICS_TEST", "\tCreate channel named " + String.valueOf(Math.abs(ran.nextInt())));
                            encountersService.createEncounterMsgingChannel(String.valueOf(Math.abs(ran.nextInt())));
                        }
                    }
                };
                for (int i = 1; i < 1000; ++i)
                    new Handler().postDelayed(runnableinactive, 20000*i);
                break;
            case R.id.testConfirmActive:
                encountersService.setConfirmEncountersOverBT(true);
                break;
            case R.id.testConfirmES:
                if (!encountersService.isSignedIn() && GoogleToken.getToken() != null) {
                    encountersService.registerGoogleUser(GoogleToken.getToken());
                    encountersService.signIn();
                }
                 final Runnable runnableactive = new Runnable() {
                    @Override
                    public void run() {
                        encountersService.confirmEncountersOverES();
                    }
                };
                for (int i = 0; i < 10; ++i) {
                    Log.d(TAG, "Running confirm encounters");
                    new Handler().postDelayed(runnableactive, i * 15*60*1000);
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
