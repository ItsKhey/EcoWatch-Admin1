package com.example.ecowatchadmin1.ui.personnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchadmin1.R;

import org.junit.Test;

/**
 * The Personnel/Vehicles selector's shared chrome.
 *
 * <p>Everything under the selector — the title, the three card labels, the search
 * hint, the four filter chips and the empty text — is driven from one table. If
 * the two sides could describe themselves independently the natural failure is a
 * card still reading "Online" above a list of vehicles, so these cases pin that
 * each side owns a complete, distinct set.
 *
 * <p>Lives in the same package as {@link ManagementMode} so it can read the
 * package-private table directly rather than the class being widened to
 * public just to be tested.
 */
public class ManagementModeTest {

    @Test
    public void personnelKeepsItsOwnWording() {
        ManagementMode mode = ManagementMode.PERSONNEL;

        assertEquals(R.string.personnel_title, mode.title);
        assertEquals(R.string.personnel_total, mode.totalLabel);
        assertEquals(R.string.personnel_online, mode.secondLabel);
        assertEquals(R.string.personnel_cleaning, mode.thirdLabel);
        assertEquals("Online", mode.filterSecondValue);
        assertEquals("Offline", mode.filterThirdValue);
        assertEquals("Cleaning", mode.filterFourthValue);
    }

    /**
     * The mapping the selector promises: Total personnel becomes Total vehicles,
     * Online becomes Available (free to be sent out), Cleaning becomes Assigned
     * (already on a collection).
     */
    @Test
    public void vehiclesReadsAsTheFleetEquivalent() {
        ManagementMode mode = ManagementMode.VEHICLES;

        assertEquals(R.string.vehicle_title, mode.title);
        assertEquals(R.string.vehicle_total, mode.totalLabel);
        assertEquals(R.string.vehicle_available, mode.secondLabel);
        assertEquals(R.string.vehicle_assigned, mode.thirdLabel);
        assertEquals(R.string.vehicle_search_hint, mode.searchHint);
        assertEquals("Available", mode.filterSecondValue);
        assertEquals("Assigned", mode.filterThirdValue);
        assertEquals("Maintenance", mode.filterFourthValue);
    }

    /** Sharing a label would mean the card could not be told from the other side's. */
    @Test
    public void theTwoSidesDoNotShareTheirCardLabels() {
        ManagementMode personnel = ManagementMode.PERSONNEL;
        ManagementMode vehicles = ManagementMode.VEHICLES;

        assertFalse(personnel.totalLabel == vehicles.totalLabel);
        assertFalse(personnel.secondLabel == vehicles.secondLabel);
        assertFalse(personnel.thirdLabel == vehicles.thirdLabel);
        assertFalse(personnel.searchHint == vehicles.searchHint);
        assertFalse(personnel.emptyText == vehicles.emptyText);
        assertFalse(personnel.resultCount == vehicles.resultCount);
    }

    /** Filters are not interchangeable either: they filter on different vocabularies. */
    @Test
    public void theTwoSidesFilterOnDifferentVocabularies() {
        ManagementMode personnel = ManagementMode.PERSONNEL;
        ManagementMode vehicles = ManagementMode.VEHICLES;

        assertFalse(personnel.filterSecondValue.equals(vehicles.filterSecondValue));
        assertFalse(personnel.filterThirdValue.equals(vehicles.filterThirdValue));
        assertTrue("All is the one value both sides share",
                personnel.filterAllValue.equalsIgnoreCase(vehicles.filterAllValue));
    }

    @Test
    public void aFilterValueResolvesToItsOwnChip() {
        ManagementMode vehicles = ManagementMode.VEHICLES;

        assertEquals(0, vehicles.chipIndexFor("All"));
        assertEquals(0, vehicles.chipIndexFor("all"));
        assertEquals(1, vehicles.chipIndexFor("Available"));
        assertEquals(2, vehicles.chipIndexFor("Assigned"));
        assertEquals(3, vehicles.chipIndexFor("Maintenance"));
    }

    /**
     * A personnel filter has no vehicle chip. This is the check that stops the
     * screen keeping "Cleaning" selected after a flip to Vehicles and showing an
     * empty list with no obvious way back.
     */
    @Test
    public void aFilterFromTheOtherSideHasNoChip() {
        ManagementMode vehicles = ManagementMode.VEHICLES;

        assertEquals(-1, vehicles.chipIndexFor("Cleaning"));
        assertEquals(-1, vehicles.chipIndexFor("Offline"));
        assertEquals(-1, ManagementMode.PERSONNEL.chipIndexFor("Maintenance"));
        assertEquals(-1, vehicles.chipIndexFor("Nonsense"));
    }

    @Test
    public void ofResolvesBothKinds() {
        assertEquals(ManagementMode.PERSONNEL,
                ManagementMode.of(ManagementMode.Kind.PERSONNEL));
        assertEquals(ManagementMode.VEHICLES,
                ManagementMode.of(ManagementMode.Kind.VEHICLES));
    }
}
