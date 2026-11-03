package com.example.ecowatchadmin1.ui.nav;

import android.content.Context;
import android.content.Intent;

/**
 * Launches the application shell from a feature module.
 *
 * <p>{@code DashboardActivity} lives in the application module, which depends on
 * the feature modules. Referencing its class from a feature would therefore
 * create a cycle, so the component name is used instead.
 */
public final class AppNavigation {

    public static final String DASHBOARD_ACTIVITY =
            "com.example.ecowatchadmin1.ui.home.DashboardActivity";

    private AppNavigation() {
    }

    public static Intent dashboardIntent(Context context) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), DASHBOARD_ACTIVITY);
        return intent;
    }

    public static void startDashboard(Context context) {
        context.startActivity(dashboardIntent(context));
    }
}
