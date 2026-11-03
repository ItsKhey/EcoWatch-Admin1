package com.example.ecowatchadmin1.ui.reportsanalytics;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Container for the hotspot map inside the analytics page.
 *
 * <p>That page scrolls (and sits inside a {@code SwipeRefreshLayout}), so a drag
 * on the map would otherwise be intercepted by the page before Google Maps saw
 * it: panning would scroll, pinch would do nothing, and pull-to-refresh would
 * steal the gesture. Asking the ancestors not to intercept while a touch started
 * on the map keeps the map usable, while a drag that starts anywhere else still
 * scrolls the page normally.</p>
 */
public class MapTouchFrameLayout extends FrameLayout {

    public MapTouchFrameLayout(@NonNull Context context) {
        super(context);
    }

    public MapTouchFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MapTouchFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            requestAncestorIntercept(false);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Let the page scroll again as soon as this gesture is over.
            requestAncestorIntercept(true);
        }
        return handled;
    }

    private void requestAncestorIntercept(boolean allow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(!allow);
        }
    }
}
