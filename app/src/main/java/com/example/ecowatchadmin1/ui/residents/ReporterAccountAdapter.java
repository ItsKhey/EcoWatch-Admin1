package com.example.ecowatchadmin1.ui.residents;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter for the modern reporter-account cards. */
public class ReporterAccountAdapter extends RecyclerView.Adapter<ReporterAccountAdapter.ResidentViewHolder> {
    public interface Listener {
        void onResidentClick(ResidentAccount resident);
    }

    private final List<ResidentAccount> residents = new ArrayList<>();
    private Listener listener;

    public void setResidents(List<ResidentAccount> value) {
        residents.clear();
        if (value != null) residents.addAll(value);
        notifyDataSetChanged();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResidentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reporter_account, parent, false);
        return new ResidentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResidentViewHolder holder, int position) {
        ResidentAccount resident = residents.get(position);
        holder.name.setText(resident.getFullName());
        holder.residentId.setText(ReporterAccountUi.valueOrDash(resident.getResidentId()));
        holder.username.setText(ReporterAccountUi.valueOrDash(resident.getUsername()));
        holder.email.setText(ReporterAccountUi.valueOrDash(resident.getEmail()));
        holder.lastActive.setText(ReporterAccountUi.formatLastActive(resident.getLastActive()));
        holder.reportCount.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.reporter_total_reports_value, resident.getTotalReports(),
                resident.getTotalReports()));
        ReporterAccountUi.bindStatus(holder.status, resident);
        ReporterAccountUi.loadAvatar(holder.avatar, resident.getProfileImageUrl());

        View.OnClickListener clickListener = v -> {
            if (listener != null) listener.onResidentClick(resident);
        };
        holder.itemView.setOnClickListener(clickListener);
        holder.itemView.setContentDescription(resident.getFullName());
    }

    @Override
    public int getItemCount() {
        return residents.size();
    }

    static final class ResidentViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final TextView residentId;
        final TextView username;
        final TextView email;
        final TextView lastActive;
        final TextView status;
        final TextView reportCount;

        ResidentViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.reporterAvatar);
            name = itemView.findViewById(R.id.reporterName);
            residentId = itemView.findViewById(R.id.reporterResidentId);
            username = itemView.findViewById(R.id.reporterUsername);
            email = itemView.findViewById(R.id.reporterEmail);
            lastActive = itemView.findViewById(R.id.reporterLastActive);
            status = itemView.findViewById(R.id.reporterStatus);
            reportCount = itemView.findViewById(R.id.reporterReportCount);
        }
    }
}
