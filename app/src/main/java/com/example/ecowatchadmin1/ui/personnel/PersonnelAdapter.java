package com.example.ecowatchadmin1.ui.personnel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Vehicle;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** RecyclerView adapter for server-provided personnel accounts. */
public class PersonnelAdapter extends RecyclerView.Adapter<PersonnelAdapter.PersonnelViewHolder> {
    public interface Listener {
        void onLocate(Personnel personnel);

        void onViewDetails(Personnel personnel);
    }

    private final List<Personnel> personnel = new ArrayList<>();

    /**
     * The fleet, held only so a driver's plate can be named.
     *
     * <p>The server sends the plate alongside the vehicle id, but a deployment
     * that stores only the assignment still leaves the id on the card, and the
     * truck it refers to is already in memory. Resolving it here means the card
     * shows a real plate rather than a dash in that case.
     */
    private final List<Vehicle> fleet = new ArrayList<>();
    private Listener listener;

    public void setPersonnel(List<Personnel> people) {
        personnel.clear();
        if (people != null) personnel.addAll(people);
        notifyDataSetChanged();
    }

    /**
     * Hands the adapter the current fleet.
     *
     * <p>Only the personnel list reads it, and it only matters while personnel
     * rows are on screen, so this repaints that list rather than the vehicles one.
     */
    public void setFleet(List<Vehicle> vehicles) {
        fleet.clear();
        if (vehicles != null) fleet.addAll(vehicles);
        if (!personnel.isEmpty()) notifyDataSetChanged();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PersonnelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_personnel, parent, false);
        return new PersonnelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonnelViewHolder holder, int position) {
        Personnel person = personnel.get(position);
        Context context = holder.itemView.getContext();

        holder.name.setText(person.getFullName());

        String formattedId = formatPersonnelId(person.getPersonnelId());
        String status = !person.isEnabled()
                ? context.getString(R.string.personnel_status_disabled)
                : person.isCleaning()
                ? context.getString(R.string.personnel_cleaning)
                : person.isOnline()
                ? context.getString(R.string.personnel_online)
                : context.getString(R.string.personnel_filter_offline);
        holder.subtitle.setText(context.getString(R.string.personnel_subtitle_format, formattedId, status));

        String task = person.getCurrentTask();
        if (task == null || task.trim().isEmpty()) {
            holder.currentTask.setText(context.getString(R.string.no_current_task));
        } else {
            holder.currentTask.setText(task);
        }

        holder.cleaningStatus.setText(person.isCleaning()
                ? context.getString(R.string.personnel_cleaning)
                : person.getStatus());
        boolean available = person.isAvailable() && person.isOnline();
        holder.availability.setText(available
                ? R.string.personnel_availability_available
                : person.isCleaning()
                ? R.string.personnel_availability_busy
                : person.isOnline()
                ? R.string.personnel_availability_offline
                : R.string.personnel_availability_unavailable);
        holder.availability.setTextColor(context.getColor(available
                ? R.color.personnel_online
                : person.isCleaning()
                ? R.color.personnel_cleaning
                : R.color.personnel_offline));
        holder.cleaningStatus.setTextColor(context.getColor(person.isCleaning()
                ? R.color.personnel_cleaning
                : person.isOnline()
                ? R.color.personnel_online
                : R.color.personnel_offline));

        if (person.getProfileImageUrl() == null || person.getProfileImageUrl().trim().isEmpty()) {
            holder.avatar.setImageResource(R.drawable.ic_person_default);
        } else {
            Glide.with(holder.avatar)
                    .load(com.example.ecowatchadmin1.ui.residents.ReporterAccountUi
                            .resolveImageUrl(holder.itemView.getContext(), person.getProfileImageUrl()))
                    .placeholder(R.drawable.ic_person_default)
                    .error(R.drawable.ic_person_default)
                    .centerCrop()
                    .into(holder.avatar);
        }

        holder.lastActive.setText(formatLastActive(person.getLastActive()));

        bindAssignedVehicle(context, person, holder);

        holder.locate.setEnabled(person.isCleaning()
                && person.getAssignmentReportId() != null
                && !person.getAssignmentReportId().trim().isEmpty());
        holder.locate.setAlpha(holder.locate.isEnabled() ? 1f : 0.45f);
        holder.locate.setOnClickListener(v -> {
            if (listener != null) listener.onLocate(person);
        });
        holder.details.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(person);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(person);
        });
    }

    @Override
    public int getItemCount() {
        return personnel.size();
    }

    /**
     * Shows the truck a driver is assigned to.
     *
     * <p>Two conditions, not one: only a driver can have a truck, and only an
     * assigned truck is worth a panel. A regular cleaner's card stays as it was,
     * and a driver who has not been given a truck yet shows nothing rather than a
     * panel with nothing in it.
     */
    private void bindAssignedVehicle(Context context, Personnel person,
                                    PersonnelViewHolder holder) {
        if (!person.isDriver() || !person.hasAssignedVehicle()) {
            holder.vehicleCard.setVisibility(View.GONE);
            return;
        }
        holder.vehicleCard.setVisibility(View.VISIBLE);
        holder.vehiclePlate.setText(resolvePlate(context, person));
        holder.vehicleId.setText(context.getString(R.string.personnel_vehicle_id_format,
                person.getAssignedVehicleId()));
    }

    /**
     * The plate the server sent, or the one the held fleet records for that id.
     *
     * <p>A driver whose truck has since left the fleet keeps the plate the server
     * stored rather than losing it, because the fleet lookup is only a fallback
     * for a response that did not carry one.
     */
    private String resolvePlate(Context context, Personnel person) {
        String plate = person.getAssignedVehiclePlateNumber();
        if (plate != null && !plate.trim().isEmpty()) return plate.trim();
        for (Vehicle vehicle : fleet) {
            if (vehicle != null && person.getAssignedVehicleId().equals(vehicle.getVehicleId())
                    && vehicle.getPlateNumber() != null
                    && !vehicle.getPlateNumber().trim().isEmpty()) {
                return vehicle.getPlateNumber().trim();
            }
        }
        // The id is on the card either way, so saying the plate is missing is
        // better than printing a dash the administrator would have to interpret.
        return context.getString(R.string.personnel_vehicle_plate_unknown);
    }

    private String formatPersonnelId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "PERS-0000";
        }
        String trimmed = raw.trim();
        if (trimmed.toUpperCase(Locale.US).startsWith("PERS-") || trimmed.toUpperCase(Locale.US).startsWith("PERS")) {
            return trimmed;
        }
        return "PERS-" + trimmed;
    }

    private String formatLastActive(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Never";
        String value = raw.trim();
        Date date = parseDate(value);
        if (date == null) return value;

        long now = System.currentTimeMillis();
        long time = date.getTime();
        long diff = now - time;

        if (diff < 60 * 1000) {
            return "Just now";
        } else if (diff < 60 * 60 * 1000) {
            long mins = diff / (60 * 1000);
            return mins + (mins == 1 ? " min ago" : " mins ago");
        } else if (diff < 24 * 60 * 60 * 1000) {
            long hours = diff / (60 * 60 * 1000);
            return hours + (hours == 1 ? " hr ago" : " hrs ago");
        } else if (diff < 7 * 24 * 60 * 60 * 1000) {
            long days = diff / (24 * 60 * 60 * 1000);
            return days + (days == 1 ? " day ago" : " days ago");
        } else {
            // Locale.US and Manila, matching the rest of the app. The relative
            // branches above are computed in the device's own zone, which is a
            // separate issue; this only governs the absolute fallback date.
            SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            formatter.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
            return formatter.format(date);
        }
    }

    private Date parseDate(String raw) {
        try {
            long epoch = Long.parseLong(raw);
            return new Date(epoch);
        } catch (NumberFormatException ignored) {
            // Fall through to parse formatted strings.
        }
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
                // Try next pattern
            }
        }
        return null;
    }

    static final class PersonnelViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final TextView subtitle;
        final TextView lastActive;
        final TextView currentTask;
        final TextView cleaningStatus;
        final TextView availability;
        final View vehicleCard;
        final TextView vehiclePlate;
        final TextView vehicleId;
        final MaterialButton locate;
        final MaterialButton details;

        PersonnelViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.personnelAvatar);
            name = itemView.findViewById(R.id.personnelName);
            subtitle = itemView.findViewById(R.id.personnelSubtitle);
            lastActive = itemView.findViewById(R.id.personnelLastActive);
            currentTask = itemView.findViewById(R.id.personnelCurrentTask);
            cleaningStatus = itemView.findViewById(R.id.personnelCleaningStatus);
            availability = itemView.findViewById(R.id.personnelAvailability);
            vehicleCard = itemView.findViewById(R.id.personnelVehicleCard);
            vehiclePlate = itemView.findViewById(R.id.personnelVehiclePlate);
            vehicleId = itemView.findViewById(R.id.personnelVehicleId);
            locate = itemView.findViewById(R.id.btnLocatePersonnel);
            details = itemView.findViewById(R.id.btnPersonnelDetails);
        }
    }
}
