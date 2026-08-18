package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements BluetoothManagerHelper.OnProfileProxyListener {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_AUTO_CONNECT = "autoconnect";
    public static final String ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";

    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private MaterialCardView autoconnectCard;
    private TextView autoconnectSubtitle;
    private SwitchCompat autoconnectSwitch;
    private MaterialCardView volumeCard;
    private TextView volumeSubtitle;
    private TextView volumeBadge;
    private RecyclerView devicesRecycler;
    private DeviceAdapter deviceAdapter;
    private BluetoothManagerHelper btHelper;
    private boolean hasAutoConnectedThisSession = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                mainHandler.postDelayed(() -> DevicePrefs.applyStartupVolume(context), 1200);
            }
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) ||
                BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) ||
                ACTION_BATTERY_LEVEL_CHANGED.equals(action)) {
                updateDeviceList();
                updateStatus();
                updateAutoConnectUI();
                updateVolumeUI();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSubtitle = findViewById(R.id.status_subtitle);
        autoconnectCard = findViewById(R.id.autoconnect_card);
        autoconnectSubtitle = findViewById(R.id.autoconnect_subtitle);
        autoconnectSwitch = findViewById(R.id.autoconnect_switch);
        volumeCard = findViewById(R.id.volume_card);
        volumeSubtitle = findViewById(R.id.volume_subtitle);
        volumeBadge = findViewById(R.id.volume_badge);
        devicesRecycler = findViewById(R.id.devices_recycler);

        btHelper = new BluetoothManagerHelper(this);
        deviceAdapter = new DeviceAdapter(btHelper, new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onConnect(BluetoothDevice device) {
                btHelper.connect(device.getAddress());
            }

            @Override
            public void onDisconnect(BluetoothDevice device) {
                btHelper.disconnect(device.getAddress());
            }

            @Override
            public void onToggleDefault(BluetoothDevice device) {
                toggleDefaultDevice(device);
            }
        });

        btHelper.addProfileProxyListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ACTION_BATTERY_LEVEL_CHANGED);
        registerReceiver(mReceiver, filter);

        devicesRecycler.setLayoutManager(new LinearLayoutManager(this));
        devicesRecycler.setAdapter(deviceAdapter);

        setupAutoConnectCard();
        setupVolumeCard();
    }

    private void setupAutoConnectCard() {
        if (autoconnectCard == null) return;

        autoconnectCard.setOnClickListener(v -> {
            boolean newState = !DevicePrefs.isAutoConnectEnabled(this);
            DevicePrefs.setAutoConnectEnabled(this, newState);
            updateAutoConnectUI();
        });

        updateAutoConnectUI();
    }

    private void setupVolumeCard() {
        if (volumeCard == null) return;

        volumeCard.setOnClickListener(v -> cycleVolumeSetting());
        updateVolumeUI();
    }

    private void cycleVolumeSetting() {
        boolean isEnabled = DevicePrefs.isStartupVolumeEnabled(this);
        int currentPercent = DevicePrefs.getStartupVolumePercent(this);

        if (!isEnabled) {
            DevicePrefs.setStartupVolumeEnabled(this, true);
            DevicePrefs.setStartupVolumePercent(this, 30);
            Toast.makeText(this, getString(R.string.startup_volume_set, 30), Toast.LENGTH_SHORT).show();
        } else {
            int nextPercent;
            if (currentPercent < 40) nextPercent = 40;
            else if (currentPercent < 50) nextPercent = 50;
            else if (currentPercent < 60) nextPercent = 60;
            else if (currentPercent < 70) nextPercent = 70;
            else if (currentPercent < 80) nextPercent = 80;
            else {
                DevicePrefs.setStartupVolumeEnabled(this, false);
                Toast.makeText(this, R.string.startup_volume_disabled_toast, Toast.LENGTH_SHORT).show();
                updateVolumeUI();
                return;
            }
            DevicePrefs.setStartupVolumePercent(this, nextPercent);
            Toast.makeText(this, getString(R.string.startup_volume_set, nextPercent), Toast.LENGTH_SHORT).show();
        }

        updateVolumeUI();
    }

    private void updateVolumeUI() {
        if (volumeSubtitle == null || volumeBadge == null) return;

        boolean isEnabled = DevicePrefs.isStartupVolumeEnabled(this);
        if (isEnabled) {
            int percent = DevicePrefs.getStartupVolumePercent(this);
            volumeSubtitle.setText(getString(R.string.startup_volume_desc_enabled, percent));
            volumeSubtitle.setTextColor(ContextCompat.getColor(this, R.color.colorSecondary));
            volumeBadge.setText(percent + "%");
            volumeBadge.setTextColor(ContextCompat.getColor(this, R.color.colorSecondary));
        } else {
            volumeSubtitle.setText(R.string.startup_volume_desc_disabled);
            volumeSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            volumeBadge.setText("OFF");
            volumeBadge.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    private void updateAutoConnectUI() {
        if (autoconnectSwitch == null || autoconnectSubtitle == null) return;

        boolean isEnabled = DevicePrefs.isAutoConnectEnabled(this);
        autoconnectSwitch.setChecked(isEnabled);

        if (isEnabled) {
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);
            String defaultName = DevicePrefs.getDefaultDeviceName(this);
            if (defaultAddress != null) {
                String name = defaultName != null ? defaultName : defaultAddress;
                autoconnectSubtitle.setText(getString(R.string.autoconnect_desc_target, name));
            } else {
                autoconnectSubtitle.setText(R.string.autoconnect_desc_recent);
            }
            autoconnectSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_ready));
        } else {
            autoconnectSubtitle.setText(R.string.autoconnect_desc_disabled);
            autoconnectSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }


    private void toggleDefaultDevice(BluetoothDevice device) {
        String currentDefault = DevicePrefs.getDefaultDeviceAddress(this);
        String deviceName = device.getName() != null ? device.getName() : device.getAddress();

        if (currentDefault != null && currentDefault.equalsIgnoreCase(device.getAddress())) {
            DevicePrefs.clearDefaultDevice(this);
            Toast.makeText(this, R.string.default_device_cleared, Toast.LENGTH_SHORT).show();
        } else {
            DevicePrefs.setDefaultDevice(this, device.getAddress(), deviceName);
            DevicePrefs.setAutoConnectEnabled(this, true);
            Toast.makeText(this, getString(R.string.default_device_set, deviceName), Toast.LENGTH_SHORT).show();
        }

        updateAutoConnectUI();
        deviceAdapter.notifyDataSetChanged();
    }

    private void checkAndTriggerAutoConnect() {
        if (hasAutoConnectedThisSession) return;

        boolean explicitIntent = getIntent() != null && getIntent().getBooleanExtra(EXTRA_AUTO_CONNECT, false);
        boolean autoConnectPref = DevicePrefs.isAutoConnectEnabled(this);

        if (!explicitIntent && !autoConnectPref) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;

        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return;

            BluetoothDevice target = null;
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);

            if (defaultAddress != null) {
                for (BluetoothDevice d : bonded) {
                    if (d.getAddress().equalsIgnoreCase(defaultAddress)) {
                        target = d;
                        break;
                    }
                }
            }

            if (target == null) {
                long maxTime = -1;
                for (BluetoothDevice d : bonded) {
                    long lastUsed = DevicePrefs.getLastUsed(this, d.getAddress());
                    if (lastUsed > maxTime) {
                        maxTime = lastUsed;
                        target = d;
                    }
                }
            }

            if (target != null) {
                int state = btHelper.getConnectionState(target);
                if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_CONNECTING) {
                    hasAutoConnectedThisSession = true;
                    Log.i(TAG, "Auto-connecting on launch to: " + target.getName() + " [" + target.getAddress() + "]");
                    btHelper.connect(target.getAddress());
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during auto-connect", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateDeviceList();
        updateAutoConnectUI();
        updateVolumeUI();
    }


    private void updateDeviceList() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            try {
                Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
                List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
                
                String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);

                // Sort: Default device first, then recent, then connected, then alphabetical
                Collections.sort(deviceList, (d1, d2) -> {
                    boolean isDef1 = defaultAddress != null && defaultAddress.equalsIgnoreCase(d1.getAddress());
                    boolean isDef2 = defaultAddress != null && defaultAddress.equalsIgnoreCase(d2.getAddress());
                    if (isDef1 && !isDef2) return -1;
                    if (!isDef1 && isDef2) return 1;

                    // 1. Sort by last used timestamp (most recent first)
                    long t1 = DevicePrefs.getLastUsed(getApplicationContext(), d1.getAddress());
                    long t2 = DevicePrefs.getLastUsed(getApplicationContext(), d2.getAddress());
                    if (t1 != t2) return Long.compare(t2, t1);

                    // 2. Sort by connection state: Connected/Connecting first
                    int s1 = btHelper.getConnectionState(d1);
                    int s2 = btHelper.getConnectionState(d2);
                    
                    boolean c1 = (s1 == BluetoothProfile.STATE_CONNECTED || s1 == BluetoothProfile.STATE_CONNECTING);
                    boolean c2 = (s2 == BluetoothProfile.STATE_CONNECTED || s2 == BluetoothProfile.STATE_CONNECTING);
                    
                    if (c1 && !c2) return -1;
                    if (!c1 && c2) return 1;
                    
                    // 3. Alphabetical fallback
                    String n1 = d1.getName() != null ? d1.getName() : "";
                    String n2 = d2.getName() != null ? d2.getName() : "";
                    return n1.compareToIgnoreCase(n2);
                });
                
                deviceAdapter.setDevices(deviceList);
            } catch (SecurityException e) {
                deviceAdapter.setDevices(new ArrayList<>());
            }
        } else {
            deviceAdapter.setDevices(new ArrayList<>());
        }
    }

    private void updateStatus() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                setReadyState();
            } else {
                setDisabledState();
            }
        } catch (SecurityException e) {
            setPermissionRequiredState();
        }
    }

    private void setReadyState() {
        int color = ContextCompat.getColor(this, R.color.status_ready);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_ready);
        statusSubtitle.setText(R.string.status_bt_on);
        statusSubtitle.setTextColor(color);
    }

    private void setDisabledState() {
        int color = ContextCompat.getColor(this, R.color.status_error);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_bt_off);
        statusSubtitle.setText(R.string.status_enable_hint);
        statusSubtitle.setTextColor(color);
    }

    private void setPermissionRequiredState() {
        int color = ContextCompat.getColor(this, R.color.status_error);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_permission_required);
        statusSubtitle.setText(R.string.status_permission_hint);
        statusSubtitle.setTextColor(color);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btHelper != null) {
            btHelper.removeProfileProxyListener(this);
        }
        unregisterReceiver(mReceiver);
    }
    
    @Override
    public void onProxyConnected() {
        runOnUiThread(() -> {
            updateDeviceList();
            checkAndTriggerAutoConnect();
        });
    }

    @Override
    public void onProxyDisconnected() {
        runOnUiThread(this::updateDeviceList);
    }
}

