package scam.detector.ketu;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;

public class LegalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);
        Button backButton = findViewById(R.id.btnLegalBack);
        backButton.setOnClickListener(v -> finish());
    }
}