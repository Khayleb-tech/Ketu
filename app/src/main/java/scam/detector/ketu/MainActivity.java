package scam.detector.ketu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    // Permission request code for microphone access
    private static final int MIC_PERMISSION_CODE = 100;

    // Tag for filtering logs in Logcat
    private static final String TAG = "MainActivity";

    // Vosk model and speech service
    private Model voskModel;
    private SpeechService speechService;

    // Whether the model has finished loading
    private boolean modelReady = false;

    // UI elements shown on the screen
    private Button btnMic;
    private TextView tvStatus;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Connect the Java variables to the XML views
        btnMic = findViewById(R.id.btnMic);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);

        // Disable mic button until model is loaded
        btnMic.setEnabled(false);
        tvStatus.setText("Loading speech model...");

        // Start the background service for Ketu
        startKetuService();

        // When the mic button is pressed, start listening for speech
        btnMic.setOnClickListener(v -> startListening());

        // Check microphone permission then load the Vosk model
        if (hasAudioPermission()) {
            loadVoskModel();
        } else {
            requestAudioPermission();
        }
    }

    @Override
    protected void onDestroy() {
        // Clean up Vosk resources when the activity closes
        stopListening();
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Handle microphone permission result
        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVoskModel();
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show();
                tvStatus.setText("Permission denied");
            }
        }
    }

    // Check if the app already has microphone permission
    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Request microphone permission from the user
    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
    }

    // Start the background service for the app
    private void startKetuService() {
        Intent serviceIntent = new Intent(this, KetuService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // Copy the Vosk model from assets to internal storage and load it
    private void loadVoskModel() {
        tvStatus.setText("Loading model...");

        // Run model loading on a background thread to avoid freezing the UI
        new Thread(() -> {
            try {
                // Copy model from assets to internal storage
                File modelDir = new File(getFilesDir(), "model");
                if (!modelDir.exists()) {
                    copyAssetFolder("model", modelDir.getAbsolutePath());
                }

                // Load the Vosk model
                Model model = new Model(modelDir.getAbsolutePath());

                // Update UI on main thread once model is ready
                runOnUiThread(() -> {
                    voskModel = model;
                    modelReady = true;
                    btnMic.setEnabled(true);
                    tvStatus.setText("Ready — tap mic to speak");
                    Log.d(TAG, "Vosk model loaded successfully");
                });

            } catch (IOException e) {
                Log.e(TAG, "Failed to load Vosk model: " + e.getMessage());
                runOnUiThread(() -> {
                    tvStatus.setText("Model failed to load");
                    Toast.makeText(this, "Speech model failed to load", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // Copy a folder from assets to a destination path on internal storage
    private void copyAssetFolder(String assetPath, String destPath) throws IOException {
        String[] files = getAssets().list(assetPath);
        File destDir = new File(destPath);
        destDir.mkdirs();

        if (files != null) {
            for (String file : files) {
                String subAssetPath = assetPath + "/" + file;
                String subDestPath = destPath + "/" + file;

                // Check if this is a folder or a file
                String[] subFiles = getAssets().list(subAssetPath);
                if (subFiles != null && subFiles.length > 0) {
                    // It's a folder — recurse into it
                    copyAssetFolder(subAssetPath, subDestPath);
                } else {
                    // It's a file — copy it
                    copyAssetFile(subAssetPath, subDestPath);
                }
            }
        }
    }

    // Copy a single file from assets to internal storage
    private void copyAssetFile(String assetPath, String destPath) throws IOException {
        File destFile = new File(destPath);
        if (destFile.exists()) return; // Skip if already copied

        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // Start listening using Vosk
    private void startListening() {
        if (!modelReady || voskModel == null) {
            Toast.makeText(this, "Model not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop any existing session first
        stopListening();

        try {
            // Create a recognizer with the model, sample rate 16000 Hz
            Recognizer recognizer = new Recognizer(voskModel, 16000.0f);

            // Start the speech service
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(recognitionListener);

            btnMic.setEnabled(false);
            tvStatus.setText("Listening...");
            tvResult.setText("");
            Log.d(TAG, "Vosk listening started");

        } catch (IOException e) {
            Log.e(TAG, "Failed to start listening: " + e.getMessage());
            tvStatus.setText("Error starting mic");
        }
    }

    // Stop the current Vosk listening session
    private void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
    }

    // Vosk recognition listener — handles all speech callbacks
    private final RecognitionListener recognitionListener = new RecognitionListener() {

        @Override
        public void onPartialResult(String hypothesis) {
            // Called continuously as speech is being recognized
            Log.d(TAG, "Partial: " + hypothesis);
        }

        @Override
        public void onResult(String hypothesis) {
            // Called when a full phrase is recognized
            Log.d(TAG, "Result: " + hypothesis);

            // Vosk returns JSON like: {"text": "ketu activate"}
            // Extract just the text value
            String spokenText = extractTextFromJson(hypothesis).toLowerCase();
            tvResult.setText(spokenText);

            if (!spokenText.isEmpty()) {
                // Check if the wake word was spoken
                if (isWakeWord(spokenText)) {
                    tvStatus.setText("Wake word detected!");
                    Toast.makeText(MainActivity.this, "Ketu activated!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Wake word detected in: " + spokenText);
                    // TODO: trigger screenshot + scam analysis here
                } else {
                    tvStatus.setText("Say 'Ketu' to activate");
                }
            }

            // Re-enable mic button after result
            btnMic.setEnabled(true);
            stopListening();
        }

        @Override
        public void onFinalResult(String hypothesis) {
            // Called when speech service finishes completely
            Log.d(TAG, "Final: " + hypothesis);
            btnMic.setEnabled(true);
        }

        @Override
        public void onError(Exception e) {
            // Handle any recognition errors
            Log.e(TAG, "Recognition error: " + e.getMessage());
            tvStatus.setText("Error - try again");
            btnMic.setEnabled(true);
            stopListening();
        }

        @Override
        public void onTimeout() {
            // Called if no speech is detected within the timeout period
            Log.d(TAG, "Recognition timed out");
            tvStatus.setText("Timed out - tap mic to try again");
            btnMic.setEnabled(true);
            stopListening();
        }
    };
    //words that sound similar to "ketu" on vosk
    private boolean isWakeWord(String text){
        if (text == null || text.isEmpty()) return false;
        String t = text.toLowerCase().trim();
        return t.contains("care to")
            || t.contains("key to")
            || t.contains("ketu")
            || t.contains("get through")
            || t.contains("cats who")
            || t.contains("kid to")
            || t.contains("k two")
            || t.contains("k to")
            || t.contains("kettle")
            || t.contains("kids")
            || t.contains("cats")
            || t.contains("cat");
            


    }

    // Extract the "text" field from Vosk's JSON response
    // Example input:  {"text": "ketu activate"}
    // Example output: "ketu activate"
    private String extractTextFromJson(String json) {
        try {
            int start = json.indexOf("\"text\"");
            if (start == -1) return "";
            start = json.indexOf("\"", start + 6);
            if (start == -1) return "";
            start++; // move past the opening quote
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
