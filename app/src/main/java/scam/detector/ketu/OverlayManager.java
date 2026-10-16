package scam.detector.ketu;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class OverlayManager {
    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private static final String TAG = "OverlayManager";
    private static final int MIN_OVERLAY_HEIGHT_DP = 56; // starting point, tune it

    // handler and runnable for auto-dismiss functionality
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::removeOverlay;
    private static final long OVERLAY_AUTO_DISMISS_MS = 6000; // starting point, tune it

    public OverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Shows a warning overlay exactly on top of the problematic message node.
     */
    public void showOverlayAt(Rect bounds, String shortReason) {
        removeOverlay();

        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.overlay_manager, null);

        int width = Math.max(1, bounds.width());
        int height = Math.max(1, bounds.height());

        TextView reasonView = overlayView.findViewById(R.id.warning_reason);
        if (shortReason != null && !shortReason.trim().isEmpty()) {
            reasonView.setText(shortReason);
            reasonView.setVisibility(View.VISIBLE);
            float density = context.getResources().getDisplayMetrics().density;
            int minHeightPx = (int) (MIN_OVERLAY_HEIGHT_DP * density);
            height = Math.max(height, minHeightPx);
        } else {
            reasonView.setVisibility(View.GONE);
        }
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        );

        // 4. Position it directly over the bounding box
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bounds.left;
        params.y = bounds.top;

        // 5. Handle close action inside the layout
        ImageView closeButton = overlayView.findViewById(R.id.close_warning);
        closeButton.setOnClickListener(v -> removeOverlay());

        // 6. Draw on screen — ensure this runs on main thread and handle failures
        Runnable add = () -> {
            try {
                windowManager.addView(overlayView, params);
            } catch (SecurityException se) {
                Log.e(TAG, "SecurityException adding overlay — missing SYSTEM_ALERT_WINDOW or overlay permission?", se);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "IllegalArgumentException when adding overlay (possibly already added or bad token)", iae);
            } catch (Exception e) {
                Log.e(TAG, "Failed to add overlay view", e);
            }
        };
        hideHandler.postDelayed(hideRunnable, OVERLAY_AUTO_DISMISS_MS);
        // Ensure we run on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            add.run();
        } else {
            new Handler(Looper.getMainLooper()).post(add);
        }
    }

    /**
     * Clears the overlay from the screen.
     */
    public void removeOverlay() {
        hideHandler.removeCallbacks(hideRunnable);
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException e) {
                // View might already be removed
            }
            overlayView = null;
        }
    }


}
