package org.mpi_sws.sddr_service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.mpi_sws.sddr_service.lib.Identifier;
import org.mpi_sws.sddr_service.lib.Utils;
import org.mpi_sws.sddr_service.linkability.LinkabilityEntryMode;

/**
 * Created by tslilyai on 10/27/17.
 */

/**
 * SDDR_Core_Service runs in the background and uses an SDDR_Core object
 * to form and store encounters. Our API provides a wrapper around intent calls
 * to this service.
 */
public class SDDR_Core_Service extends Service {
    private static final String TAG = "SDDR: " + SDDR_Core_Service.class.getSimpleName();

    private SDDR_Core core;
    private Thread thread;

    private void check_and_start_core() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bta = bluetoothManager.getAdapter();

        if (bta == null || !bta.isEnabled()) {
            Toast.makeText(this, "Bluetooth required", Toast.LENGTH_SHORT);
            Log.d(TAG, "Bluetooth not enabled");
            this.stopSelf();
        } else {
            Log.d(TAG, "Bluetooth enabled");
            if (core == null) {
                Log.d(TAG, "Starting SDDR Core");
                core = new SDDR_Core(this);
                Log.d(TAG, "Starting SDDR Core thread");
                thread = new Thread(core);
                thread.start();
            } else {
                Log.d(TAG, "SDDR already running");
            };
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
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
            check_and_start_core();
            String ID = intent.getExtras().getString("@string.add_linkid");
            core.addNewLink(new Identifier(ID.getBytes()), LinkabilityEntryMode.AdvertiseAndListen);
            return START_STICKY;
        }
        else if (intent.getExtras().containsKey("@string.send_msg"))
        {
            Log.d(TAG, "Got Send_Msg Intent");
            check_and_start_core();
            // TODO
            return START_STICKY;
        }
        else if (intent.getExtras().containsKey("@string.send_group_msg"))
        {
            Log.d(TAG, "Got Send_Group_Msg Intent");
            check_and_start_core();
            // TODO
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
    }

    public SDDR_Core getCore() {
        return core;
    }

    /**
     * Required for extending service
     */
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
