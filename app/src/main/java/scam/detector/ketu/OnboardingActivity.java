package scam.detector.ketu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AnimationSet;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "ScamDetectorPrefs";
    private static final String KEY_ONBOARDED = "has_seen_onboarding";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean seen = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDED, false);
        if (seen) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_onboarding);
        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        View ketuMark = findViewById(R.id.ketuMark);
        View onboardingContent = findViewById(R.id.onboardingContent);
        btnGetStarted.setVisibility(View.INVISIBLE);

        AnimationSet entrance = new AnimationSet(true);
        entrance.addAnimation(new AlphaAnimation(0f, 1f));
        entrance.addAnimation(new ScaleAnimation(0.82f, 1f, 0.82f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f));
        entrance.setDuration(700);
        ketuMark.startAnimation(entrance);
        onboardingContent.setAlpha(0f);
        onboardingContent.animate().alpha(1f).setStartDelay(350).setDuration(500).start();
        btnGetStarted.postDelayed(() -> {
            btnGetStarted.setVisibility(View.VISIBLE);
            btnGetStarted.setAlpha(0f);
            btnGetStarted.animate().alpha(1f).setDuration(450).start();
        }, 850);
        btnGetStarted.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ONBOARDED, true).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}