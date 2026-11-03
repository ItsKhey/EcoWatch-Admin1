package com.example.ecowatchadmin1.ui.personnel;

import androidx.annotation.StringRes;

import com.example.ecowatchadmin1.R;

/**
 * What changes when the Personnel/Vehicles/Teams selector flips.
 *
 * <p>Collected here rather than scattered through the fragment because every
 * piece of chrome under the selector has to agree: the card labels, the search
 * hint, the four filter chips and the empty text. Reading them from one table is
 * what stops a card from still saying "Online" while the list below it is showing
 * vehicles.
 *
 * <p>The filter values are the strings the three domains actually filter on, which
 * are not interchangeable: personnel filters on Online/Offline/Cleaning, the
 * fleet on Available/Assigned/Maintenance, and teams on whether the crew is
 * dispatch-ready.
 */
public final class ManagementMode {

    /** The selected side of the selector. */
    public enum Kind {
        PERSONNEL, VEHICLES, TEAMS
    }

    @StringRes final int title;
    @StringRes final int subtitle;
    @StringRes final int totalLabel;
    @StringRes final int secondLabel;
    @StringRes final int thirdLabel;
    @StringRes final int searchHint;
    @StringRes final int emptyText;
    @StringRes final int resultCount;

    /** Labels for the four filter chips, in order: All, second, third, fourth. */
    @StringRes final int filterAllLabel;
    @StringRes final int filterSecondLabel;
    @StringRes final int filterThirdLabel;
    @StringRes final int filterFourthLabel;

    /** Values the chips filter on, matching the three domains' own vocabularies. */
    public final String filterAllValue;
    public final String filterSecondValue;
    public final String filterThirdValue;
    public final String filterFourthValue;

    private ManagementMode(@StringRes int title, @StringRes int subtitle,
                           @StringRes int totalLabel, @StringRes int secondLabel,
                           @StringRes int thirdLabel, @StringRes int searchHint,
                           @StringRes int emptyText, @StringRes int resultCount,
                           @StringRes int filterAllLabel, @StringRes int filterSecondLabel,
                           @StringRes int filterThirdLabel, @StringRes int filterFourthLabel,
                           String filterAllValue, String filterSecondValue,
                           String filterThirdValue, String filterFourthValue) {
        this.title = title;
        this.subtitle = subtitle;
        this.totalLabel = totalLabel;
        this.secondLabel = secondLabel;
        this.thirdLabel = thirdLabel;
        this.searchHint = searchHint;
        this.emptyText = emptyText;
        this.resultCount = resultCount;
        this.filterAllLabel = filterAllLabel;
        this.filterSecondLabel = filterSecondLabel;
        this.filterThirdLabel = filterThirdLabel;
        this.filterFourthLabel = filterFourthLabel;
        this.filterAllValue = filterAllValue;
        this.filterSecondValue = filterSecondValue;
        this.filterThirdValue = filterThirdValue;
        this.filterFourthValue = filterFourthValue;
    }

    public static final ManagementMode PERSONNEL = new ManagementMode(
            R.string.personnel_title,
            R.string.personnel_subtitle,
            R.string.personnel_total,
            R.string.personnel_online,
            R.string.personnel_cleaning,
            R.string.personnel_search_hint,
            R.string.personnel_empty,
            R.string.personnel_result_count,
            R.string.personnel_filter_all,
            R.string.personnel_filter_online,
            R.string.personnel_filter_offline,
            R.string.personnel_filter_cleaning,
            "All", "Online", "Offline", "Cleaning");

    /**
     * The vehicle side.
     *
     * <p>The card mapping is the one the selector promises: Total personnel
     * becomes Total vehicles, Online becomes Available (a truck free to be sent
     * out), and Cleaning becomes Assigned (a truck already on a collection).
     */
    public static final ManagementMode VEHICLES = new ManagementMode(
            R.string.vehicle_title,
            R.string.vehicle_subtitle,
            R.string.vehicle_total,
            R.string.vehicle_available,
            R.string.vehicle_assigned,
            R.string.vehicle_search_hint,
            R.string.vehicle_empty,
            R.string.vehicle_result_count,
            R.string.vehicle_filter_all,
            R.string.vehicle_filter_available,
            R.string.vehicle_filter_assigned,
            R.string.vehicle_filter_maintenance,
            "All", "Available", "Assigned", "Maintenance");

    /**
     * The team side.
     *
     * <p>A crew is the unit the office actually dispatches, so the cards answer
     * the questions an administrator has before sending one out: how many teams
     * exist, how many are ready to go, and how many are currently working.
     * "Ready" means a leader plus at least two people on the crew, which is the
     * same rule the server enforces.
     */
    public static final ManagementMode TEAMS = new ManagementMode(
            R.string.team_title,
            R.string.team_subtitle,
            R.string.team_total,
            R.string.team_ready,
            R.string.team_cleaning,
            R.string.team_search_hint,
            R.string.team_empty,
            R.string.team_result_count,
            R.string.team_filter_all,
            R.string.team_filter_ready,
            R.string.team_filter_no_leader,
            R.string.team_filter_understaffed,
            "All", "Ready", "No leader", "Understaffed");

    public static ManagementMode of(Kind kind) {
        if (kind == Kind.VEHICLES) return VEHICLES;
        if (kind == Kind.TEAMS) return TEAMS;
        return PERSONNEL;
    }

    /**
     * Which chip a stored filter value belongs on, or 0 for the All chip.
     *
     * <p>Used when the selector flips: a filter that means nothing on the other
     * side resets to All rather than silently showing an empty list.
     *
     * @return 0, 1, 2 or 3 for the four chips, or -1 when the value has no chip.
     */
    public int chipIndexFor(String filterValue) {
        if (filterAllValue.equalsIgnoreCase(filterValue)) return 0;
        if (filterSecondValue.equalsIgnoreCase(filterValue)) return 1;
        if (filterThirdValue.equalsIgnoreCase(filterValue)) return 2;
        if (filterFourthValue.equalsIgnoreCase(filterValue)) return 3;
        return -1;
    }
}
