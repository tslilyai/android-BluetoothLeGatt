package org.mpi_sws.sddr_userspace;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.mpi_sws.sddr_userspace.lib.Constants;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SDDR: " + MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!requestBT() || !request_permissions()) {
            return;
        }
        startSDDR();
    }

    private void startSDDR() {
        Intent serviceIntent = new Intent(MainActivity.this, SDDR_Core_Service.class);
        serviceIntent.putExtra("Start", true);
        Log.d(TAG, "Starting SDDR_Core_Service");
        MainActivity.this.startService(serviceIntent);
        Log.d(TAG, "Started SDDR");

        FloatingActionButton myFab = (FloatingActionButton) this.findViewById(R.id.AddLink);
        myFab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                EditText linkID = (EditText) MainActivity.this.findViewById(R.id.linkID);
                Intent serviceIntent = new Intent(MainActivity.this, SDDR_Core_Service.class);
                serviceIntent.putExtra("AddLink", linkID.getText().toString());
                serviceIntent.putExtra("Mode", 1); // just for now
                Log.d(TAG, "Adding linkID " + linkID.getText());
                linkID.setText("");
                MainActivity.this.startService(serviceIntent);
            }
        });

        Button buttonRM = (Button) this.findViewById(R.id.buttonRM);
        buttonRM.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, SDDR_Core_Service.class);
                serviceIntent.putExtra("RetroactiveMatching", "");
                Log.d(TAG, "Requesting Retroactive Matching");
                MainActivity.this.startService(serviceIntent);
            }
        });
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
                        .setMessage("SDDR requires location access for Bluetooth protocol")
                        .setPositiveButton("OK", listener)
                        .setNegativeButton("Cancel", null)
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        Constants.REQUEST_ACCESS_FINE_LOCATION);
            }
            return false;
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
                    Toast.makeText(this, "Exiting SDDR: Location access required", Toast.LENGTH_SHORT).show();
                    finishAndRemoveTask ();
                } else {
                    Toast.makeText(this, "Please restart SDDR with Location access enabled", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "Bluetooth not enabled");
            Toast.makeText(this, "Exiting SDDR: Bluetooth required", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
        } else {
            Toast.makeText(this, "Please restart SDDR with Bluetooth now enabled", Toast.LENGTH_SHORT).show();
        }
    }

}

