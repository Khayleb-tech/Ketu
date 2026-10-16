package scam.detector.ketu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

public class OverlayPermissionActivity extends Activity {
    private static final String TAG = "OverlayPermissionAct";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overlay_permission);

        TextView status = findViewById(R.id.overlay_status);
        Button btnRequest = findViewById(R.id.btn_request_overlay);

        boolean allowed = Settings.canDrawOverlays(this);
        status.setText(allowed ? "Overlay permission already granted." : "Overlay permission not granted.");

        btnRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch overlay settings", e);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView status = findViewById(R.id.overlay_status);
        boolean allowed = Settings.canDrawOverlays(this);
        status.setText(allowed ? "Overlay permission already granted." : "Overlay permission not granted.");
        if (allowed) {
            // Close after user granted permission
            finish();
        }
    }
}
