package com.example.ecowatchadmin1.utils;

import android.content.Context;

import com.example.ecowatchadmin1.R;

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
     * <p>The backend normally returns the canonical label, but history payloads
     * can also contain the short or snake-case spelling. Treat those as the same
     * scale instead of letting them fall through to the legacy small default.
     * Unknown and missing values still use small for reports from before the
     * scale field existed.
     */
    public static String labelFor(Context context, String canonicalScale) {
        if (context == null) {
            return canonicalScale;
        }
        String normalized = canonicalScale == null
                ? "" : canonicalScale.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        if (normalized.startsWith("large")) {
            return context.getString(R.string.waste_scale_large);
        }
        if (normalized.startsWith("medium")) {
            return context.getString(R.string.waste_scale_medium);
        }
        return context.getString(R.string.waste_scale_small);
    }
}
