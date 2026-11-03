package com.example.ecowatchadmin1.ui.nav;

import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.Report;

/**
 * Navigation operations a feature screen can ask of the dashboard host.
 *
 * <p>Features are library modules while the dashboard is part of the
 * application shell, so screens cannot reference the concrete activity
 * directly. They check {@code getActivity() instanceof DashboardNavigator}
 * instead and call through this interface.
 */
public interface DashboardNavigator {

    void navigateToHome();

    void navigateToPendingReports();

    void navigateToInProgressReports();

    void navigateToAnnouncements();

    void navigateToReportsWithFilter(String status);

    void openReportsAnalytics();

    void navigateToPersonnel();

    void openMap();

    void openReporterAccounts();

    void registerNewPersonnel();

    void openReportById(String reportId);

    void openMapForReport(Report report);

    void openMapForPersonnel(Personnel personnel);

    void openMapForLocation(Double latitude, Double longitude, String label);
}
