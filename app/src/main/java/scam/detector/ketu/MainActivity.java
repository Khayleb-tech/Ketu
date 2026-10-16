package scam.detector.ketu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.widget.Switch;
import android.content.SharedPreferences;
import android.provider.Settings;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.os.PowerManager;
import android.provider.Settings;
import android.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    // Constants for SharedPreferences
    private static final String PREFS_NAME = "ScamDetectorPrefs";
    private static final String KEY_IS_ENABLED = "is_enabled";

    // Tag for filtering logs in Logcat
    private static final String TAG = "MainActivity";

    // UI elements shown on the screen
    private static final String ACTION_REQUEST_SCREEN_CAPTURE = "scam.detector.ketu.REQUEST_SCREEN_CAPTURE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndDisableBatteryOptimization();

        // Initialize UI elements
        Button btnPermission = findViewById(R.id.btnPermission);
        Switch switchDetector = findViewById(R.id.switchDetector);
        Button btnViewAlerts = findViewById(R.id.btnViewAlerts);
        Button btnSettings = findViewById(R.id.btnSettings);
        btnViewAlerts.setOnClickListener(v -> {
            startActivity(new Intent(this, AlertsListActivity.class));
        });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // System Accessibility Permission Button
        btnPermission.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
                // Drop the user directly into Android's Accessibility Settings page
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
            } else {
                Toast.makeText(this, "Permission already granted!", Toast.LENGTH_SHORT).show();
            }
        });

        // Internal Kill-Switch (Saves state locally in SharedPreferences)
        SharedPreferences sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load previous state (defaults to true if app was just installed)
        boolean isEnabled = sharedPrefs.getBoolean(KEY_IS_ENABLED, true);
        switchDetector.setChecked(isEnabled);

        // Listen for changes to the switch and update SharedPreferences accordingly
        switchDetector.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPrefs.edit().putBoolean(KEY_IS_ENABLED, isChecked).apply();
            String status = isChecked ? "Scam protection active." : "Scam protection paused.";
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });

    }

   private void checkAndDisableBatteryOptimization() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        
        if (powerManager != null) {
            String packageName = getPackageName();
            
            // Only trigger if the app is still being optimized
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                
                // Inflate the custom XML view layout
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_battery_warning, null);

                // Build the AlertDialog using the custom view
                AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false) // Forces user interaction
                    .create();

                // Find the YES button within the custom layout and attach the click listener
                Button btnYes = dialogView.findViewById(R.id.btn_dialog_yes);
                btnYes.setOnClickListener(v -> {
                    dialog.dismiss(); // Close the dialog box first
                    
                    // Redirect user directly to the battery optimization selection list
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        // Fallback configuration intent for older SDK variants
                        Intent fallbackIntent = new Intent();
                        fallbackIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        fallbackIntent.setData(Uri.parse("package:" + packageName));
                        startActivity(fallbackIntent);
                    }
                });

                // Present the dialog window on screen
                dialog.show();
            }
        }
    }

    /// Helper method to verify if the OS has physically enabled your service
    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String expectedComponentName = context.getPackageName() + "/" + serviceClass.getName();
        String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return settingValue != null && settingValue.contains(expectedComponentName);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
