package com.example.ecowatchadmin1.ui.clusters;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.ecowatchadmin1.R;
import com.google.android.material.card.MaterialCardView;

public class ClusterDetailActivity extends AppCompatActivity {

    private CardView clusterCard;
    private TextView clusterId;
    private TextView clusterLastActivity;
    private TextView clusterReportCount;
    private TextView clusterWasteSpots;
    private TextView clusterAssignedTo;
    private TextView clusterAssignedAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cluster_detail);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // CardView casting safely succeeds because activity_cluster_detail.xml uses MaterialCardView
        clusterCard = findViewById(R.id.clusterCard);
        clusterId = findViewById(R.id.clusterId);
        clusterLastActivity = findViewById(R.id.clusterLastActivity);
        clusterReportCount = findViewById(R.id.clusterReportCount);
        clusterWasteSpots = findViewById(R.id.clusterWasteSpots);
        clusterAssignedTo = findViewById(R.id.clusterAssignedTo);
        clusterAssignedAt = findViewById(R.id.clusterAssignedAt);

        bindClusterData();
    }

    private void bindClusterData() {
        String idExtra = getIntent() != null ? getIntent().getStringExtra("cluster_id") : null;
        if (idExtra == null) idExtra = "N/A";

        if (clusterId != null) clusterId.setText("Cluster: " + idExtra);
        if (clusterLastActivity != null) clusterLastActivity.setText("Last activity: N/A");
        if (clusterReportCount != null) clusterReportCount.setText("Reports: 0");
        if (clusterWasteSpots != null) clusterWasteSpots.setText("Waste spots: 0");
        if (clusterAssignedTo != null) clusterAssignedTo.setText("Assigned to: Unassigned");
        if (clusterAssignedAt != null) clusterAssignedAt.setText("Assigned: N/A");
    }
}
