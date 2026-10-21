package com.example.ecowatchadmin1.ui.personnel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The available vehicles offered while registering a driver.
 *
 * <p>One row per vehicle, each carrying its own Assign button, rather than a
 * dropdown of plate numbers: assigning a truck is a decision made against the
 * plate and the vehicle id together, and a spinner would show one of the two.
 *
 * <p>Exactly one row reads as assigned at a time. The selection is held here
 * rather than by disabling the other rows, because a driver who changes their mind
 * should be able to pick a different truck without the list going dead.
 */
public class RegistrationVehicleAdapter
        extends RecyclerView.Adapter<RegistrationVehicleAdapter.VehicleViewHolder> {

    public interface Listener {
        void onAssign(Vehicle vehicle);
    }

    private final List<Vehicle> vehicles = new ArrayList<>();
    private String assignedVehicleId;
    private Listener listener;

    /** Replaces the list and repaints the selection, if any, against it. */
    public void setVehicles(List<Vehicle> available) {
        vehicles.clear();
        if (available != null) vehicles.addAll(available);
        notifyDataSetChanged();
    }

    /** Marks a vehicle as the chosen one, or clears the choice with null. */
    public void setAssignedVehicleId(String vehicleId) {
        assignedVehicleId = vehicleId;
        notifyDataSetChanged();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_registration_vehicle, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        Vehicle vehicle = vehicles.get(position);
        boolean assigned = vehicle != null && vehicle.getVehicleId() != null
                && vehicle.getVehicleId().equals(assignedVehicleId);

        holder.plate.setText(vehicle.getPlateNumber() != null
                ? vehicle.getPlateNumber()
                : holder.itemView.getContext().getString(R.string.personnel_vehicle_plate_unknown));
        holder.vehicleId.setText(holder.itemView.getContext()
                .getString(R.string.personnel_vehicle_id_format,
                        vehicle.getVehicleId() == null ? "—" : vehicle.getVehicleId()));

        // The chosen row reads as done, every other row keeps offering itself, so
        // re-assigning is one tap rather than a reset followed by a second tap.
        holder.assign.setText(assigned
                ? R.string.personnel_assigned : R.string.personnel_assign);
        holder.assign.setEnabled(!assigned);
        holder.assign.setAlpha(assigned ? 0.6f : 1f);

        holder.assign.setOnClickListener(v -> {
            if (listener != null && vehicle != null) listener.onAssign(vehicle);
        });
    }

    @Override
    public int getItemCount() {
        return vehicles.size();
    }

    static final class VehicleViewHolder extends RecyclerView.ViewHolder {
        final TextView plate;
        final TextView vehicleId;
        final MaterialButton assign;

        VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            plate = itemView.findViewById(R.id.registrationVehiclePlate);
            vehicleId = itemView.findViewById(R.id.registrationVehicleId);
            assign = itemView.findViewById(R.id.registrationVehicleAssign);
        }
    }
}