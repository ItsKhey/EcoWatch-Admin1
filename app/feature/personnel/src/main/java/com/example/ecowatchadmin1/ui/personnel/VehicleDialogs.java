package com.example.ecowatchadmin1.ui.personnel;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.example.ecowatchadmin1.data.repository.VehicleRepository;
import com.example.ecowatchadmin1.utils.AdminAccountDateFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Add, edit and inspect vehicles.
 *
 * <p>One form serves both jobs. The vehicle id is server-owned and shown read-only
 * when editing, so an administrator correcting a typo in the type cannot
 * accidentally re-point the record at a different truck.
 */
final class VehicleDialogs {

    /** The three states the dropdown offers. */
    private static final String[] STATUSES = {
            Vehicle.STATUS_AVAILABLE,
            Vehicle.STATUS_ASSIGNED,
            Vehicle.STATUS_MAINTENANCE,
    };

    private VehicleDialogs() {
    }

    /**
     * Shows the add/edit form.
     *
     * @param vehicle null to register a new vehicle, or the record to edit.
     */
    static void showEditor(@NonNull Context context, @Nullable Vehicle vehicle,
                           @NonNull VehicleViewModel viewModel,
                           @NonNull VehicleRepository.VehicleResultCallback callback) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_vehicle_edit, null, false);

        TextView title = content.findViewById(R.id.vehicleDialogTitle);
        TextView subtitle = content.findViewById(R.id.vehicleDialogSubtitle);
        TextInputLayout plateLayout = content.findViewById(R.id.vehiclePlateLayout);
        TextInputEditText plateInput = content.findViewById(R.id.vehiclePlateInput);
        TextInputLayout typeLayout = content.findViewById(R.id.vehicleTypeLayout);
        TextInputEditText typeInput = content.findViewById(R.id.vehicleTypeInput);
        TextInputEditText ratedLoadInput = content.findViewById(R.id.vehicleRatedLoadInput);
        TextInputLayout reportLayout = content.findViewById(R.id.vehicleReportLayout);
        TextInputEditText reportInput = content.findViewById(R.id.vehicleReportInput);
        TextInputLayout notesLayout = content.findViewById(R.id.vehicleNotesLayout);
        TextInputEditText notesInput = content.findViewById(R.id.vehicleNotesInput);
        TextInputLayout statusLayout = content.findViewById(R.id.vehicleStatusLayout);
        MaterialAutoCompleteTextView statusInput = content.findViewById(R.id.vehicleStatusInput);
        MaterialButton saveButton = content.findViewById(R.id.vehicleSaveBtn);
        MaterialButton cancelButton = content.findViewById(R.id.vehicleCancelBtn);

        boolean editing = vehicle != null;
        title.setText(editing ? R.string.vehicle_edit_title : R.string.vehicle_add_title);
        subtitle.setText(editing
                ? R.string.vehicle_edit_subtitle
                : R.string.vehicle_add_subtitle);

        // Registration only asks what the truck is. Status and notes are
        // lifecycle facts rather than identity, and a vehicle joining the fleet is
        // Available with nothing to report, so offering the fields here could only
        // ever produce the one answer the server accepts. They appear on edit,
        // which is when a truck actually goes out, gets serviced or is written up.
        statusLayout.setVisibility(editing ? View.VISIBLE : View.GONE);
        notesLayout.setVisibility(editing ? View.VISIBLE : View.GONE);

        statusInput.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, STATUSES));

        if (editing) {
            plateInput.setText(vehicle.getPlateNumber());
            typeInput.setText(vehicle.getVehicleType());
            ratedLoadInput.setText(vehicle.getRatedLoadCapacity());
            notesInput.setText(vehicle.getNotes());
            statusInput.setText(vehicle.getStatusLabel(), false);
            reportInput.setText(vehicle.getCurrentReportId());
        }

        // The collection report is required exactly while the status needs it.
        // Hiding it otherwise keeps the form from producing a state the server
        // refuses: Assigned with no report, or Available with one.
        final Runnable syncReportVisibility = () -> {
            boolean needsReport = editing
                    && Vehicle.STATUS_ASSIGNED.equals(selectedStatus(statusInput));
            reportLayout.setVisibility(needsReport ? View.VISIBLE : View.GONE);
            if (!needsReport) reportInput.setText(null);
        };

        statusInput.setOnItemClickListener((parent, item, position, id) -> syncReportVisibility.run());
        syncReportVisibility.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(content)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Clear a field's error as soon as it is corrected, so a stale red
        // underline never survives a fix.
        plateInput.addTextChangedListener(new SimpleWatcher(
                () -> plateLayout.setError(null)));
        typeInput.addTextChangedListener(new SimpleWatcher(
                () -> typeLayout.setError(null)));
        reportInput.addTextChangedListener(new SimpleWatcher(
                () -> reportLayout.setError(null)));

        saveButton.setOnClickListener(v -> {
            // Checked here rather than only by the server, so the administrator
            // is told what to fix without a round trip.
            String plate = textOf(plateInput);
            String type = textOf(typeInput);
            String ratedLoad = textOf(ratedLoadInput);
            // Read through the layout's visibility so a hidden field submits as
            // absent rather than as whatever it happened to be left holding.
            String notes = notesLayout.getVisibility() == View.VISIBLE
                    ? textOf(notesInput) : null;
            String status = statusLayout.getVisibility() == View.VISIBLE
                    ? selectedStatus(statusInput) : null;
            String reportId = textOf(reportInput);

            if (plate.isEmpty()) {
                plateLayout.setError(context.getString(R.string.vehicle_plate_required));
                return;
            }
            if (type.isEmpty()) {
                typeLayout.setError(context.getString(R.string.vehicle_type_required));
                return;
            }
            if (reportLayout.getVisibility() == View.VISIBLE && reportId.isEmpty()) {
                reportLayout.setError(context.getString(R.string.vehicle_collection_report_hint));
                return;
            }

            if (!editing) {
                // Nothing to carry over from a hidden field: the server derives
                // Available itself and refuses anything else on a create.
                viewModel.addVehicle(plate, type, ratedLoad, null,
                        (created, error) -> {
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                                return;
                            }
                            dialog.dismiss();
                            callback.onResult(created, null);
                        });
                return;
            }

            viewModel.updateVehicle(vehicle.getVehicleId(), plate, type, ratedLoad,
                    status, reportLayout.getVisibility() == View.VISIBLE ? reportId : null,
                    notes, (updated, error) -> {
                        if (error != null) {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        dialog.dismiss();
                        callback.onResult(updated, null);
                    });
        });

        dialog.show();
    }

    static void confirmRemoval(Context context, Vehicle vehicle,
                               VehicleViewModel viewModel,
                               VehicleRepository.VehicleResultCallback callback) {
        String plate = vehicle.getPlateNumber() != null
                ? vehicle.getPlateNumber() : vehicle.getVehicleId();

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.vehicle_remove_confirm_title)
                .setMessage(context.getString(R.string.vehicle_remove_confirm_message, plate))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.vehicle_remove, (dialog, which) ->
                        viewModel.deleteVehicle(vehicle.getVehicleId(), (removed, error) -> {
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                                return;
                            }
                            Toast.makeText(context, R.string.vehicle_removed, Toast.LENGTH_SHORT).show();
                            callback.onResult(null, null);
                        }))
                .show();
    }

    /** The status the dropdown currently holds, defaulting to Available. */
    private static String selectedStatus(MaterialAutoCompleteTextView input) {
        CharSequence value = input.getText();
        if (value == null || value.toString().trim().isEmpty()) {
            return Vehicle.STATUS_AVAILABLE;
        }
        return value.toString().trim();
    }

    private static String textOf(TextInputEditText input) {
        CharSequence value = input.getText();
        return value == null ? "" : value.toString().trim();
    }

    /** TextWatcher is three methods for one call site, so only onTextChanged is used. */
    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
