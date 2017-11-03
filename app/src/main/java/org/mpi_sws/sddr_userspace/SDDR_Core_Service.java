package org.mpi_sws.sddr_userspace;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.mpi_sws.sddr_userspace.lib.Identifier;
import org.mpi_sws.sddr_userspace.lib.Utils;
import org.mpi_sws.sddr_userspace.linkability.LinkabilityEntryMode;

/**
 * Created by tslilyai on 10/27/17.
 */

public class SDDR_Core_Service extends Service {
    private static final String TAG = "SDDR: " + SDDR_Core_Service.class.getSimpleName();

    private SDDR_Core core;
    private Thread thread;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        if (intent == null) {
            return START_STICKY;
        }
        if (intent.getExtras().containsKey("Start")) {
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bta = bluetoothManager.getAdapter();

            if (bta == null || !bta.isEnabled()) {
                Toast.makeText(this, "Bluetooth required", Toast.LENGTH_SHORT);
                Log.d(TAG, "Bluetooth not enabled");
                this.stopSelf();
                return START_NOT_STICKY;
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
                }
                return START_STICKY;
            }
        } else if (intent.getExtras().containsKey("AddLink")){
            Utils.myAssert(core != null);
            String ID = intent.getExtras().getString("AddLink");
            Utils.myAssert(intent.getExtras().containsKey("Mode"));
            int md = intent.getExtras().getInt("Mode");
            core.addNewLink(new Identifier(ID.getBytes()), LinkabilityEntryMode.fromInt(md));
            return START_STICKY;
        } else if (intent.getExtras().containsKey("RetroactiveMatching")) {
            Log.d(TAG, "Got RetroactiveMatching Intent");
            Utils.myAssert(core != null);
            core.updateEncounterMatchings();
            return START_STICKY;
        } else {
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
