package scam.detector.ketu;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button homeButton = findViewById(R.id.btnSettingsHome);
        Button aboutPrivacyButton = findViewById(R.id.btnAboutPrivacy);

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        aboutPrivacyButton.setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)));
    }
}
