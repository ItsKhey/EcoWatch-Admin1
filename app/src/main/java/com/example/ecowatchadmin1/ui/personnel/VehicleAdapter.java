package com.example.ecowatchadmin1.ui.personnel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Vehicle;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Renders the fleet list.
 *
 * <p>Deliberately the same shape as {@link PersonnelAdapter}, because the two sit
 * behind one selector and the list should not change character when the selector
 * flips: image, name, subtitle, task line, activity line, two status labels and
 * the same two actions.
 */
public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {

    public interface Listener {
        /** Centre the map on the vehicle's current collection, when there is one. */
        void onLocate(Vehicle vehicle);

        /** Open the full-screen vehicle details page. */
        void onViewDetails(Vehicle vehicle);
    }

    private final List<Vehicle> vehicles = new ArrayList<>();

    /**
     * The driver holding each truck, keyed by vehicle id.
     *
     * <p>Built here from the roster the screen already holds rather than fetched
     * per vehicle: the relationship lives on the personnel record, so the fleet
     * list would otherwise need one request per truck to answer "who drives
     * this". Both lists are already loaded on entry, so the join costs nothing.
     */
    private final Map<String, Personnel> driversByVehicleId = new HashMap<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setVehicles(List<Vehicle> items) {
        vehicles.clear();
        if (items != null) vehicles.addAll(items);
        notifyDataSetChanged();
    }

    /**
     * Hands the adapter the personnel roster so each truck can name its driver.
     *
     * <p>Only working drivers count. A disabled account cannot sign in and so
     * cannot drive, and the backend treats a paused driver as having released the
     * truck: showing one here would claim a driver the same data model lets
     * somebody else take over.
     */
    public void setDrivers(List<Personnel> personnel) {
        driversByVehicleId.clear();
        if (personnel != null) {
            for (Personnel person : personnel) {
                if (person == null || !person.isEnabled()) continue;
                if (!person.isDriver() || !person.hasAssignedVehicle()) continue;
                String vehicleId = person.getAssignedVehicleId();
                if (vehicleId != null && !vehicleId.trim().isEmpty()) {
                    String key = vehicleId.trim().toUpperCase(Locale.ROOT);
                    // First match wins: the database allows only one working driver per
                    // truck, so a second would be a stale row rather than a real pair.
                    if (!driversByVehicleId.containsKey(key)) {
                        driversByVehicleId.put(key, person);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @Nullable
    private Personnel getDriver(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleId() == null) return null;
        String key = vehicle.getVehicleId().trim().toUpperCase(Locale.ROOT);
        return driversByVehicleId.get(key);
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vehicle, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        Vehicle vehicle = vehicles.get(position);

        // The plate is how a vehicle is known in the field, so it takes the line
        // the cleaner's name takes on the personnel card. The server id follows,
        // where personnel shows "PERS-0001 · Cleaning".
        holder.plate.setText(vehicle.getPlateNumber() != null
                ? vehicle.getPlateNumber()
                : holder.itemView.getContext().getString(R.string.vehicle_plate_unknown));

        String type = vehicle.getVehicleType();
        String ratedLoad = vehicle.getRatedLoadLabel();
        StringBuilder subtitle = new StringBuilder();
        if (type != null && !type.isEmpty()) subtitle.append(type);
        if (ratedLoad != null && !ratedLoad.isEmpty()) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(ratedLoad);
        }
        holder.subtitle.setText(subtitle.length() == 0
                ? holder.itemView.getContext().getString(R.string.vehicle_type_unknown)
                : subtitle.toString());

        // Current collection, the vehicle's stand-in for "current task".
        String collection = vehicle.getCurrentCollection();
        holder.currentCollection.setText(collection != null
                ? holder.itemView.getContext().getString(
                R.string.vehicle_collection_format, collection)
                : holder.itemView.getContext().getString(R.string.vehicle_no_collection));

        holder.lastService.setText(holder.itemView.getContext().getString(
                R.string.vehicle_last_service_format,
                formatActivity(vehicle.getLastServiceAt(), false)));

        // Two labels, like the personnel card: the raw stored status and a plain
        // availability word, both coloured by the same state.
        holder.maintenanceStatus.setText(vehicle.getStatusLabel());
        int statusColor = vehicle.isAssigned()
                ? R.color.personnel_cleaning
                : vehicle.isInMaintenance()
                ? R.color.vehicle_maintenance
                : R.color.personnel_online;
        holder.maintenanceStatus.setTextColor(holder.itemView.getContext().getColor(statusColor));

        Personnel driver = getDriver(vehicle);
        boolean hasDriver = driver != null;

        if (!hasDriver) {
            holder.availability.setText(R.string.vehicle_lacks_driver);
            holder.availability.setTextColor(holder.itemView.getContext().getColor(R.color.vehicle_lacks_driver));
        } else {
            holder.availability.setText(vehicle.isAvailable()
                    ? R.string.vehicle_available
                    : vehicle.isInMaintenance()
                    ? R.string.vehicle_out_of_service
                    : R.string.vehicle_on_collection);
            holder.availability.setTextColor(holder.itemView.getContext().getColor(statusColor));
        }

        bindAssignedDriver(driver, holder);

        // Locate only works while the truck is actually out on a collection.
        boolean canLocate = vehicle.isAssigned() && hasCoordinates(vehicle);
        holder.locate.setEnabled(canLocate);
        holder.locate.setAlpha(canLocate ? 1f : 0.45f);
        holder.locate.setOnClickListener(v -> {
            if (listener != null) listener.onLocate(vehicle);
        });

        holder.details.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(vehicle);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(vehicle);
        });
    }

    @Override
    public int getItemCount() {
        return vehicles.size();
    }

    /**
     * Shows who drives this truck, or leaves the card out.
     *
     * <p>Hidden rather than filled with a dash for a truck nobody drives: half the
     * fleet is normally unassigned, and a permanently present "Assigned
     * Personnel / —" panel on every one of those rows would bury the trucks that
     * actually have a driver.
     */
    private void bindAssignedDriver(@Nullable Personnel driver, VehicleViewHolder holder) {
        if (driver == null) {
            holder.personnelCard.setVisibility(View.GONE);
            return;
        }

        holder.personnelCard.setVisibility(View.VISIBLE);
        holder.personnelName.setText(driver.getFullName());
        holder.personnelId.setText(holder.itemView.getContext().getString(
                R.string.personnel_id_format, driver.getPersonnelId()));
        PersonnelUi.loadAvatar(holder.personnelAvatar, driver.getProfileImageUrl());
    }

    /**
     * Whether the vehicle's collection carries a usable position.
     *
     * <p>0,0 is rejected: it is what an unset coordinate decodes to, and sending
     * the map there would centre on the Gulf of Guinea rather than admitting the
     * vehicle cannot be located.
     */
    private static boolean hasCoordinates(Vehicle vehicle) {
        Double latitude = vehicle.getCurrentReportLatitude();
        Double longitude = vehicle.getCurrentReportLongitude();
        if (latitude == null || longitude == null) return false;
        if (latitude == 0d && longitude == 0d) return false;
        return latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d;
    }

    /** "2 days ago" style, matching the personnel card's activity line. */
    private static String formatActivity(String raw, boolean fallbackToRaw) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallbackToRaw ? "" : "Never serviced";
        }
        String value = raw.trim();
        Date date = parseDate(value);
        if (date == null) return value;

        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 60 * 1000) return "Just now";
        if (diff < 60 * 60 * 1000) {
            long mins = diff / (60 * 1000);
            return mins + (mins == 1 ? " min ago" : " mins ago");
        }
        if (diff < 24 * 60 * 60 * 1000) {
            long hours = diff / (60 * 60 * 1000);
            return hours + (hours == 1 ? " hr ago" : " hrs ago");
        }
        if (diff < 30L * 24 * 60 * 60 * 1000) {
            long days = diff / (24 * 60 * 60 * 1000);
            return days + (days == 1 ? " day ago" : " days ago");
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        return formatter.format(date);
    }

    private static Date parseDate(String raw) {
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                if (pattern.endsWith("'Z'")) parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                return parser.parse(raw);
            } catch (ParseException ignored) {
                // Try the next pattern.
            }
        }
        return null;
    }

    static final class VehicleViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView plate;
        final TextView subtitle;
        final TextView currentCollection;
        final TextView lastService;
        final TextView maintenanceStatus;
        final TextView availability;
        final View personnelCard;
        final ShapeableImageView personnelAvatar;
        final TextView personnelName;
        final TextView personnelId;
        final View locate;
        final View details;

        VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.vehicleIcon);
            plate = itemView.findViewById(R.id.vehiclePlate);
            subtitle = itemView.findViewById(R.id.vehicleSubtitle);
            currentCollection = itemView.findViewById(R.id.vehicleCurrentCollection);
            lastService = itemView.findViewById(R.id.vehicleLastService);
            maintenanceStatus = itemView.findViewById(R.id.vehicleMaintenanceStatus);
            availability = itemView.findViewById(R.id.vehicleAvailability);
            personnelCard = itemView.findViewById(R.id.vehiclePersonnelCard);
            personnelAvatar = itemView.findViewById(R.id.vehiclePersonnelAvatar);
            personnelName = itemView.findViewById(R.id.vehiclePersonnelName);
            personnelId = itemView.findViewById(R.id.vehiclePersonnelId);
            locate = itemView.findViewById(R.id.btnLocateVehicle);
            details = itemView.findViewById(R.id.btnVehicleDetails);
        }
    }
}
