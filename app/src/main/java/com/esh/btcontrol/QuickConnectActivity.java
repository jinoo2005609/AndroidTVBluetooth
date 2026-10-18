package com.esh.btcontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Set;

@SuppressLint("MissingPermission")
public class QuickConnectActivity extends AppCompatActivity {

    private static final String TAG = "QuickConnectActivity";
    public static final String ACTION_QUICK_CONNECT = "com.esh.btcontrol.ACTION_QUICK_CONNECT";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NAME = "name";

    private BluetoothManagerHelper btHelper;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, R.string.status_bt_off, Toast.LENGTH_SHORT).show();
            finishAndCleanUp();
            return;
        }

        // Safety timeout to ensure activity doesn't stay alive indefinitely
        timeoutHandler.postDelayed(this::finishAndCleanUp, 3500);

        btHelper = new BluetoothManagerHelper(getApplicationContext());
        
        Intent intent = getIntent();
        String targetAddress = (intent != null) ? intent.getStringExtra(EXTRA_ADDRESS) : null;
        String targetName = (intent != null) ? intent.getStringExtra(EXTRA_NAME) : null;

        BluetoothDevice targetDevice = resolveTargetDevice(adapter, targetAddress, targetName);

        if (targetDevice == null) {
            Toast.makeText(this, R.string.quick_connect_no_device, Toast.LENGTH_LONG).show();
            Intent openMain = new Intent(this, MainActivity.class);
            openMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(openMain);
            finishAndCleanUp();
            return;
        }

        String displayName = targetDevice.getName() != null ? targetDevice.getName() : targetDevice.getAddress();

        // Check if already connected
        int state = btHelper.getConnectionState(targetDevice);
        if (state == BluetoothProfile.STATE_CONNECTED) {
            Toast.makeText(this, getString(R.string.quick_connect_already_connected, displayName), Toast.LENGTH_SHORT).show();
            finishAndCleanUp();
            return;
        }

        Toast.makeText(this, getString(R.string.quick_connect_connecting, displayName), Toast.LENGTH_SHORT).show();
        btHelper.connect(targetDevice.getAddress(), () -> {
            DevicePrefs.applyStartupVolume(getApplicationContext());
            runOnUiThread(this::finishAndCleanUp);
        });
    }


    private BluetoothDevice resolveTargetDevice(BluetoothAdapter adapter, String explicitAddress, String explicitName) {
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                return null;
            }

            // 1. Explicit Address from Intent
            if (explicitAddress != null && BluetoothAdapter.checkBluetoothAddress(explicitAddress)) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getAddress().equalsIgnoreCase(explicitAddress)) {
                        return device;
                    }
                }
            }

            // 2. Explicit Name from Intent
            if (explicitName != null) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getName() != null && device.getName().equalsIgnoreCase(explicitName)) {
                        return device;
                    }
                }
            }

            // 3. User's configured Default Device
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);
            if (defaultAddress != null) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getAddress().equalsIgnoreCase(defaultAddress)) {
                        return device;
                    }
                }
            }

            // 4. Fallback: Most recently connected / used device
            BluetoothDevice mostRecent = null;
            long latestTime = -1;
            for (BluetoothDevice device : bondedDevices) {
                long lastUsed = DevicePrefs.getLastUsed(this, device.getAddress());
                if (lastUsed > latestTime) {
                    latestTime = lastUsed;
                    mostRecent = device;
                }
            }

            if (mostRecent != null) {
                return mostRecent;
            }

            // 5. Ultimate fallback: First bonded device in list
            return bondedDevices.iterator().next();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while accessing bonded devices", e);
            return null;
        }
    }

    private synchronized void finishAndCleanUp() {
        if (isFinished) return;
        isFinished = true;
        timeoutHandler.removeCallbacksAndMessages(null);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
    }
}
