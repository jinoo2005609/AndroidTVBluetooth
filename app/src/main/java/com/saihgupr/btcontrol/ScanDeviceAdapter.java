package com.saihgupr.btcontrol;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanDeviceAdapter extends RecyclerView.Adapter<ScanDeviceAdapter.ScanViewHolder> {

    public interface OnDeviceSelectedListener {
        void onDeviceSelected(BluetoothDevice device, boolean bonded);
    }

    private static class ScanEntry {
        final BluetoothDevice device;
        boolean bonded;

        ScanEntry(BluetoothDevice device, boolean bonded) {
            this.device = device;
            this.bonded = bonded;
        }
    }

    private final Context context;
    private final OnDeviceSelectedListener listener;
    private final List<ScanEntry> entries = new ArrayList<>();
    private String pairingAddress;

    public ScanDeviceAdapter(Context context, OnDeviceSelectedListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void clear() {
        entries.clear();
        notifyDataSetChanged();
    }

    /**
     * Adds a discovered device. Returns true when a new row was inserted.
     * Rows are left in discovery order while scanning so D-pad focus is not moved by new results.
     */
    public boolean addDevice(BluetoothDevice device) {
        String address = addressOf(device);
        for (int i = 0; i < entries.size(); i++) {
            ScanEntry entry = entries.get(i);
            if (addressOf(entry.device).equalsIgnoreCase(address)) {
                boolean bonded = isBonded(entry.device);
                if (bonded != entry.bonded) {
                    entry.bonded = bonded;
                    notifyItemChanged(i);
                }
                return false;
            }
        }

        entries.add(new ScanEntry(device, isBonded(device)));
        notifyItemInserted(entries.size() - 1);
        return true;
    }

    public void sortResults() {
        Collections.sort(entries, (first, second) -> {
            if (first.bonded != second.bonded) {
                return first.bonded ? 1 : -1;
            }
            int nameCompare = safeName(first.device).compareToIgnoreCase(safeName(second.device));
            if (nameCompare != 0) {
                return nameCompare;
            }
            return addressOf(first.device).compareToIgnoreCase(addressOf(second.device));
        });
        notifyDataSetChanged();
    }

    public void setPairingAddress(String address) {
        if (pairingAddress == null ? address == null : pairingAddress.equalsIgnoreCase(address)) {
            return;
        }
        pairingAddress = address;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_device, parent, false);
        return new ScanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanViewHolder holder, int position) {
        ScanEntry entry = entries.get(position);
        entry.bonded = isBonded(entry.device);

        String name = safeName(entry.device);
        String address = addressOf(entry.device);
        boolean isPairing = pairingAddress != null && pairingAddress.equalsIgnoreCase(address);

        holder.name.setText(name);
        holder.address.setText(address);

        if (isPairing) {
            holder.tag.setVisibility(View.VISIBLE);
            holder.tag.setText(R.string.scan_pairing_tag);
            holder.tag.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.status_warning)));
        } else if (entry.bonded) {
            holder.tag.setVisibility(View.VISIBLE);
            holder.tag.setText(R.string.scan_paired_tag);
            holder.tag.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.status_ready)));
        } else {
            holder.tag.setVisibility(View.GONE);
        }

        String description = name + ", " + address;
        if (isPairing) {
            description += ", " + holder.itemView.getContext().getString(R.string.scan_pairing_tag);
        } else if (entry.bonded) {
            description += ", " + holder.itemView.getContext().getString(R.string.scan_paired_tag);
        }
        holder.itemView.setContentDescription(description);

        holder.itemView.setOnClickListener(v -> {
            if (pairingAddress != null) {
                return;
            }
            if (listener != null) {
                listener.onDeviceSelected(entry.device, entry.bonded);
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private boolean isBonded(BluetoothDevice device) {
        try {
            return device.getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (SecurityException e) {
            return false;
        }
    }

    private String safeName(BluetoothDevice device) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                String name = device.getName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (SecurityException ignored) {
            // Fall through to the localized fallback.
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

    static class ScanViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView address;
        final TextView tag;

        ScanViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.scan_device_name);
            address = itemView.findViewById(R.id.scan_device_address);
            tag = itemView.findViewById(R.id.scan_device_tag);
        }
    }
}
