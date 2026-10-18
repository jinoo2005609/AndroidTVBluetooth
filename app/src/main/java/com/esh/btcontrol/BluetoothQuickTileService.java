package com.esh.btcontrol;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.N)
@SuppressLint("MissingPermission")
public class BluetoothQuickTileService extends TileService implements BluetoothManagerHelper.OnProfileProxyListener {

    private static final String TAG = "BtQuickTileService";
    private BluetoothManagerHelper btHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        btHelper = new BluetoothManagerHelper(getApplicationContext());
        btHelper.addProfileProxyListener(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            updateTileState();
            return;
        }

        BluetoothDevice target = resolveTargetDevice(adapter);
        if (target == null) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(mainIntent);
            return;
        }

        int state = btHelper.getConnectionState(target);
        if (state == BluetoothProfile.STATE_CONNECTED) {
            btHelper.disconnect(target.getAddress(), this::updateTileState);
        } else {
            btHelper.connect(target.getAddress(), () -> {
                DevicePrefs.applyStartupVolume(getApplicationContext());
                updateTileState();
            });
        }
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.status_bt_off));
            }
            tile.updateTile();
            return;
        }

        BluetoothDevice target = resolveTargetDevice(adapter);
        if (target == null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.quick_connect_no_device));
            }
            tile.updateTile();
            return;
        }

        String displayName = target.getName() != null ? target.getName() : target.getAddress();
        int connectionState = btHelper.getConnectionState(target);

        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(displayName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int battery = btHelper.getBatteryLevel(target);
                String sub = (battery >= 0) ? (getString(R.string.status_connected) + " (" + battery + "%)") : getString(R.string.status_connected);
                tile.setSubtitle(sub);
            }
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(displayName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.connect));
            }
        }

        tile.updateTile();
    }

    private BluetoothDevice resolveTargetDevice(BluetoothAdapter adapter) {
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return null;

            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);
            if (defaultAddress != null) {
                for (BluetoothDevice d : bonded) {
                    if (d.getAddress().equalsIgnoreCase(defaultAddress)) {
                        return d;
                    }
                }
            }

            BluetoothDevice mostRecent = null;
            long maxTime = -1;
            for (BluetoothDevice d : bonded) {
                long lastUsed = DevicePrefs.getLastUsed(this, d.getAddress());
                if (lastUsed > maxTime) {
                    maxTime = lastUsed;
                    mostRecent = d;
                }
            }

            if (mostRecent != null) return mostRecent;
            return bonded.iterator().next();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while reading bonded devices", e);
            return null;
        }
    }

    @Override
    public void onProxyConnected() {
        updateTileState();
    }

    @Override
    public void onProxyDisconnected() {
        updateTileState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (btHelper != null) {
            btHelper.removeProfileProxyListener(this);
        }
    }
}
