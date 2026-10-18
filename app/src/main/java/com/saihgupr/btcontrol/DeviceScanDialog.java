package com.saihgupr.btcontrol;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

public class DeviceScanDialog extends Dialog {

    public interface OnPairingCompleteListener {
        void onPairingComplete(BluetoothDevice device);
    }

    private static final String TAG = "DeviceScanDialog";

    private final Context context;
    private final OnPairingCompleteListener pairingListener;
    private final BluetoothAdapter adapter;

    private ScanDeviceAdapter scanAdapter;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView emptyText;
    private MaterialButton scanAgainButton;
    private RecyclerView recyclerView;

    private BluetoothDevice pairingDevice;
    private boolean receiverRegistered;
    private boolean scanning;

    public DeviceScanDialog(@NonNull Context context, @Nullable OnPairingCompleteListener listener) {
        super(context);
        this.context = context;
        this.pairingListener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        setCanceledOnTouchOutside(true);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (!scanning) {
                    return;
                }
                BluetoothDevice device = getParcelableDevice(intent);
                if (device == null) {
                    return;
                }
                if (scanAdapter.addDevice(device)) {
                    statusText.setText(context.getString(
                            R.string.scan_status_searching_count, scanAdapter.getItemCount()));
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                onDiscoveryFinished();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                onBondStateChanged(intent);
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state != BluetoothAdapter.STATE_ON) {
                    onBluetoothOff();
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_scan_devices);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.7f;
            window.setAttributes(attributes);
        }

        statusText = findViewById(R.id.scan_status);
        progressBar = findViewById(R.id.scan_progress);
        emptyText = findViewById(R.id.scan_empty);
        scanAgainButton = findViewById(R.id.scan_again);
        recyclerView = findViewById(R.id.scan_recycler);

        scanAdapter = new ScanDeviceAdapter(context, this::onDeviceSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(scanAdapter);

        scanAgainButton.setOnClickListener(v -> startScan());
        MaterialButton cancelButton = findViewById(R.id.scan_cancel);
        cancelButton.setOnClickListener(v -> dismiss());

        setOnDismissListener(dialog -> cleanup());
        startScan();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) {
            return;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int maxWidth = (int) (720 * metrics.density);
        int width = Math.min((int) (metrics.widthPixels * 0.9f), maxWidth);
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
    }

    private void startScan() {
        if (statusText == null) {
            return;
        }
        if (adapter == null) {
            showUnavailable(R.string.scan_not_supported);
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                showUnavailable(R.string.scan_status_bt_off);
                return;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission to read Bluetooth state", e);
            showUnavailable(R.string.scan_permission_denied);
            return;
        }

        scanning = false;
        cancelDiscovery();
        pairingDevice = null;
        scanAdapter.setPairingAddress(null);
        scanAdapter.clear();

        statusText.setText(R.string.scan_status_searching);
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        scanAgainButton.setVisibility(View.GONE);

        if (!ensureReceiverRegistered()) {
            showUnavailable(R.string.scan_start_failed);
            return;
        }

        try {
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            if (!adapter.startDiscovery()) {
                showUnavailable(R.string.scan_start_failed);
                return;
            }
            scanning = true;
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to start Bluetooth discovery", e);
            showUnavailable(R.string.scan_permission_denied);
        }
    }

    private boolean ensureReceiverRegistered() {
        if (receiverRegistered) {
            return true;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try {
            ContextCompat.registerReceiver(
                    context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to register Bluetooth receiver", e);
            return false;
        }
    }

    private void onDiscoveryFinished() {
        if (!scanning) {
            return;
        }
        // A cancellation from the previous scan can deliver a stale finish broadcast after a
        // new discovery has already started. Ignore it while the adapter is discovering again.
        if (isDiscovering()) {
            return;
        }

        scanning = false;
        scanAdapter.sortResults();
        progressBar.setVisibility(View.GONE);
        scanAgainButton.setVisibility(View.VISIBLE);

        if (scanAdapter.getItemCount() == 0) {
            statusText.setText(R.string.scan_status_none);
            emptyText.setText(R.string.scan_empty_hint);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            statusText.setText(R.string.scan_status_select);
            emptyText.setVisibility(View.GONE);
            recyclerView.post(() -> {
                if (recyclerView.isShown() && scanAdapter.getItemCount() > 0) {
                    recyclerView.requestFocus();
                }
            });
        }
    }

    private void onBluetoothOff() {
        scanning = false;
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.scan_status_bt_off);
        scanAgainButton.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.scan_status_bt_off);
        emptyText.setVisibility(scanAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
    }

    private void onDeviceSelected(BluetoothDevice device, boolean bonded) {
        if (pairingDevice != null) {
            return;
        }

        String address = addressOf(device);
        if (address.isEmpty()) {
            onPairingFailed();
            return;
        }

        if (bonded) {
            showAlreadyPaired(device);
            return;
        }

        int bondState;
        try {
            bondState = device.getBondState();
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission to read bond state", e);
            onPairingFailed();
            return;
        }

        if (bondState == BluetoothDevice.BOND_BONDED) {
            showAlreadyPaired(device);
            return;
        }

        scanning = false;
        cancelDiscovery();
        progressBar.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        scanAgainButton.setVisibility(View.GONE);

        pairingDevice = device;
        scanAdapter.setPairingAddress(address);
        statusText.setText(context.getString(R.string.scan_pairing_with, safeName(device)));

        if (bondState == BluetoothDevice.BOND_BONDING) {
            return;
        }

        try {
            if (!device.createBond()) {
                onPairingFailed();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission to create bond", e);
            onPairingFailed();
        }
    }

    private void showAlreadyPaired(BluetoothDevice device) {
        Toast.makeText(context,
                context.getString(R.string.scan_already_paired, safeName(device)),
                Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void onBondStateChanged(Intent intent) {
        BluetoothDevice device = getParcelableDevice(intent);
        if (device == null || pairingDevice == null) {
            return;
        }

        String pendingAddress = addressOf(pairingDevice);
        String deviceAddress = addressOf(device);
        if (pendingAddress.isEmpty() || !pendingAddress.equalsIgnoreCase(deviceAddress)) {
            return;
        }

        int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
        int previous = intent.getIntExtra(
                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
        Log.i(TAG, "Bond state " + previous + " -> " + state + " for " + deviceAddress);

        if (state == BluetoothDevice.BOND_BONDED) {
            Toast.makeText(context,
                    context.getString(R.string.scan_pairing_success, safeName(device)),
                    Toast.LENGTH_SHORT).show();
            if (pairingListener != null) {
                pairingListener.onPairingComplete(device);
            }
            dismiss();
        } else if (state == BluetoothDevice.BOND_BONDING) {
            statusText.setText(context.getString(R.string.scan_pairing_with, safeName(device)));
            scanAdapter.setPairingAddress(deviceAddress);
        } else if (state == BluetoothDevice.BOND_NONE
                && previous == BluetoothDevice.BOND_BONDING) {
            onPairingFailed();
        }
    }

    private void onPairingFailed() {
        scanning = false;
        cancelDiscovery();
        progressBar.setVisibility(View.GONE);
        pairingDevice = null;
        scanAdapter.setPairingAddress(null);
        statusText.setText(R.string.scan_pairing_failed);
        scanAgainButton.setVisibility(View.VISIBLE);
        Toast.makeText(context, R.string.scan_pairing_failed, Toast.LENGTH_SHORT).show();
    }

    private void showUnavailable(int messageRes) {
        scanning = false;
        progressBar.setVisibility(View.GONE);
        statusText.setText(messageRes);
        scanAgainButton.setVisibility(View.VISIBLE);
        emptyText.setText(messageRes);
        emptyText.setVisibility(scanAdapter != null && scanAdapter.getItemCount() > 0
                ? View.GONE : View.VISIBLE);
    }

    private boolean isDiscovering() {
        try {
            return adapter != null && adapter.isDiscovering();
        } catch (SecurityException e) {
            return false;
        }
    }

    private void cancelDiscovery() {
        try {
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to cancel Bluetooth discovery", e);
        }
    }

    private void cleanup() {
        scanning = false;
        cancelDiscovery();
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Bluetooth receiver was already unregistered", e);
            }
            receiverRegistered = false;
        }
    }

    @SuppressWarnings("deprecation")
    private BluetoothDevice getParcelableDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to read Bluetooth device name", e);
        }
        return context.getString(R.string.unknown_device);
    }

    private String addressOf(BluetoothDevice device) {
        try {
            String address = device.getAddress();
            return address != null ? address : "";
        } catch (SecurityException e) {
            return "";
        }
    }
}
