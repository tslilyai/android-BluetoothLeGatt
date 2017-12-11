package org.mpisws.sddrservice.encounters;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

/**
 * Created by tslilyai on 10/27/17.
 */

/**
 * SDDR_Core_Service runs in the background and uses an SDDR_Core object
 * to form and store encounters. Our API provides a wrapper around intent calls
 * to this service.
 */
public class SDDR_Core_Service extends Service {
    private static final String TAG = SDDR_Core_Service.class.getSimpleName();
    private SDDR_Core core;
    private Thread thread;
    public boolean was_destroyed = false;

    public void restart() {
        was_destroyed = false;
        check_and_start_core();
    }

    private void check_and_start_core() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bta = bluetoothManager.getAdapter();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permissions required", Toast.LENGTH_SHORT);
            Log.d(TAG, "Location permissions not enabled");
            this.stopSelf();
        }

        if (bta == null || !bta.isEnabled()) {
            Toast.makeText(this, "Bluetooth required", Toast.LENGTH_SHORT);
            Log.d(TAG, "Bluetooth not enabled");
            this.stopSelf();
        }
        Log.d(TAG, "Bluetooth and location permissions enabled");
        if (core == null) {
            Log.d(TAG, "Starting SDDR_API Core");
            core = new SDDR_Core(this);
            Log.d(TAG, "Starting SDDR_API Core thread");
            thread = new Thread(core);
            thread.start();
        } else {
            Log.d(TAG, "SDDR_API already running");
        };
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "creating service");
        check_and_start_core();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        if (intent == null) {
            return START_STICKY;
        }

        if (intent.getExtras().containsKey("@string.start_sddr_service"))
        {
            check_and_start_core();
            return START_STICKY;
        }

        if (intent.getExtras().containsKey("@string.add_linkid"))
        {
            String ID = intent.getExtras().getString("@string.add_linkid");
            core.addNewLink(new Identifier(ID.getBytes()), LinkabilityEntryMode.AdvertiseAndListen);
            return START_STICKY;
        }
        else {
            Log.d(TAG, "Unknown intent");
            return START_STICKY;
        }
    }

    public void onDestroy() {
        Log.d(TAG, "Destroying service");
        if (core != null) {
            core.should_run = false;
            core = null;
        }
        was_destroyed = true;
    }

    /**
     * Required for extending service
     */
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
