package com.example.ecowatchadmin1.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * SwipeRefreshLayout that leaves horizontal drags to the child rows.
 *
 * <p>The dashboard stacks {@link android.widget.HorizontalScrollView}s inside the
 * vertical content. The stock layout begins a pull-to-refresh as soon as the
 * finger has moved down by the touch slop, even when the gesture is mostly
 * sideways. That intercepts the row mid-swipe, cancels its scroll and flashes the
 * refresh indicator, which reads as the two gestures fighting over one finger.
 *
 * <p>This variant measures the gesture first and gives it to whichever axis is
 * dominant: a clearly horizontal drag is never intercepted, while a vertical
 * pull still refreshes as before.
 */
public class AxisAwareSwipeRefreshLayout extends SwipeRefreshLayout {

    private final int touchSlop;
    private float initialX;
    private float initialY;
    private boolean horizontalDrag;

    public AxisAwareSwipeRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public AxisAwareSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = event.getX();
                initialY = event.getY();
                horizontalDrag = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!horizontalDrag) {
                    float dx = Math.abs(event.getX() - initialX);
                    float dy = Math.abs(event.getY() - initialY);
                    // Wait until the drag is past the slop on both axes before
                    // deciding, so a tap or a jittery press never flips the mode.
                    if (dx > touchSlop && dx > dy) {
                        horizontalDrag = true;
                    }
                }
                if (horizontalDrag) {
                    // The row owns this gesture; do not start a refresh.
                    return false;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                horizontalDrag = false;
                break;

            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If a horizontal gesture reached us anyway (for example because the row
        // did not consume it), do not turn it into a refresh.
        if (horizontalDrag) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                horizontalDrag = false;
            }
            return false;
        }
        return super.onTouchEvent(event);
    }
}
