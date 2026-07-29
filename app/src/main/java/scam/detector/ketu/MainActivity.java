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

        // Initialize UI elements
        Button btnPermission = findViewById(R.id.btnPermission);
        Switch switchDetector = findViewById(R.id.switchDetector);

        // 1. System Accessibility Permission Button
        btnPermission.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
                // Drop the user directly into Android's Accessibility Settings page
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Permission already granted!", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Internal Kill-Switch (Saves state locally in SharedPreferences)
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

    /// Helper method to verify if the OS has physically enabled your service
    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String expectedComponentName = context.getPackageName() + "/" + serviceClass.getName();
        String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return settingValue != null && settingValue.contains(expectedComponentName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
