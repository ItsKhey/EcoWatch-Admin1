package com.example.ecowatchadmin1.data;

/**
 * Contract shared by the layer that raises system notifications and the
 * activity that receives them.
 *
 * <p>The data layer must not depend on a UI module, so the action and extra
 * names live here where both sides can see them.
 */
public final class NotificationContract {

    /** Action carried by a tap on a system notification. */
    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.ecowatchadmin1.action.OPEN_NOTIFICATION";

    /** Server id of the notification that was tapped. */
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    /** Server id of the report the notification refers to, when there is one. */
    public static final String EXTRA_REPORT_ID = "report_id";

    private NotificationContract() {
    }
}
