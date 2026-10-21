package com.example.ecowatchadmin1.utils;

import android.content.Context;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;

/**
 * Turns a waste scale into the label shown on screen.
 *
 * <p>The server always speaks the canonical vocabulary — "Small-Scale Waste",
 * "Medium-Scale Waste", "Large-Scale Waste" — because that is what it stores
 * and what it dispatches against. The labels shown in the app may be
 * translated, so the two are kept apart: the canonical value travels on the
 * wire and this class only decides what the administrator reads.
 */
public final class WasteScaleDisplay {

    private WasteScaleDisplay() {
    }

    /**
     * The translated label for a canonical scale.
     *
     * <p>Anything unrecognised falls back to the small scale, which is the only
     * size a report could have carried before the field existed.
     */
    public static String labelFor(Context context, String canonicalScale) {
        if (context == null) {
            return canonicalScale;
        }
        if (Report.SCALE_MEDIUM.equals(canonicalScale)) {
            return context.getString(R.string.waste_scale_medium);
        }
        if (Report.SCALE_LARGE.equals(canonicalScale)) {
            return context.getString(R.string.waste_scale_large);
        }
        return context.getString(R.string.waste_scale_small);
    }
}
