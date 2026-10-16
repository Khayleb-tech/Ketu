package scam.detector.ketu;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;

public class AlertAnalysisActivity extends AppCompatActivity {
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_analysis);

        // Retrieve the alert details directly from the Intent
        Intent intent = getIntent();
        String sourceApp = intent.getStringExtra("sourceApp");
        String shortReason = intent.getStringExtra("shortReason");
        String reason = intent.getStringExtra("reason");
        long timestamp = intent.getLongExtra("timestamp", 0); // Read the numerical timestamp

        Button backButton = findViewById(R.id.btnAlertsAnalysisBack);
        backButton.setOnClickListener(v -> finish());

        // Set the alert details in the TextViews
        TextView sourceAppTextView = findViewById(R.id.sourceAppTextView);
        TextView shortReasonTextView = findViewById(R.id.shortReasonTextView);
        TextView reasonTextView = findViewById(R.id.reasonTextView);    
        TextView timestampTextView = findViewById(R.id.timestampTextView); // Added matching textview

        sourceAppTextView.setText(sourceApp);
        shortReasonTextView.setText(shortReason);
        reasonTextView.setText(reason);
        if (timestampTextView != null) {
            timestampTextView.setText(String.valueOf(timestamp));
        }
    }
}
