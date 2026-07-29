package scam.detector.ketu;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.util.Log;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static final String PREFS_NAME = "ScamDetectorPrefs";
    private static final String KEY_IS_ENABLED = "is_enabled";
    private static final String ACTION_REQUEST_SCREEN_CAPTURE = "scam.detector.ketu.REQUEST_SCREEN_CAPTURE";
    private static final long FALLBACK_COOLDOWN_MS = 5000L;
    private long lastFallbackRequestTime = 0L;

    private static final int MAX_QUEUED_EVENTS = 5;
    private static final long ANALYSIS_RETENTION_MS = 30000L;
    // Background thread pool to offload text matching away from the UI thread
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private String lastAnalysedText = "";
    private long lastAnalyzedTime = 0L;

    // Pre-compiled regex patterns to maintain blazing fast local lookups
    private final Pattern urgencyPattern = Pattern.compile(
    "(verify|suspicious|blocked|unauthorized|frozen|login required|" +
    "act now|urgent|immediately|expires?|final notice|limited time|hurry|closing soon)",
    Pattern.CASE_INSENSITIVE);

    private final Pattern financialBaitPattern = Pattern.compile(
        "(winner|claim your|cash prize|guaranteed|risk-free|wire transfer|" +
        "gift card|investment opportunity|final allocation|disbursement)",
        Pattern.CASE_INSENSITIVE);

    private final Pattern credentialRequestPattern = Pattern.compile(
        "(confirm your (password|identity|account)|provide your (card|pin|otp|ssn)|reset your password)",
        Pattern.CASE_INSENSITIVE);

    private final Pattern shortLinkPattern = Pattern.compile(
        "https?://(bit\\.ly|tinyurl\\.com|t\\.co|cutt\\.ly)/\\w+",
        Pattern.CASE_INSENSITIVE);

        @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            SharedPreferences sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnabledInApp = sharedPrefs.getBoolean(KEY_IS_ENABLED, true);
            if (!isEnabledInApp) {
                Log.d(TAG, "Accessibility detection paused by app switch.");
                return;
            }

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "No root node available from accessibility event.");
                return;
            }

            List<String> extractedTexts = new ArrayList<>();
            traverseAndExtractText(rootNode, extractedTexts);
            rootNode.recycle();

            if (extractedTexts.isEmpty()) {
                Log.d(TAG, "Accessibility extracted no useful text; requesting screenshot fallback.");
                return;
            }

            Log.d(TAG, "Accessibility extracted " + extractedTexts.size() + " text items.");
            String combinedText = String.join(" ", extractedTexts);

            if (!lastAnalysedText.isEmpty()
                    && System.currentTimeMillis() - lastAnalyzedTime > ANALYSIS_RETENTION_MS) {
                lastAnalysedText = "";
                lastAnalyzedTime = 0L;
            }

            if (combinedText.equals(lastAnalysedText)) {
                Log.d(TAG, "Accessibility extracted no new text; skipping analysis.");
                return;
            }
            lastAnalysedText = combinedText;
            lastAnalyzedTime = System.currentTimeMillis();

            long capturedAt = System.currentTimeMillis();
            backgroundExecutor.execute(() -> {
                if (System.currentTimeMillis() - capturedAt > 3000) {
                    Log.d(TAG, "Discarding stale event, screen has likely moved on");
                    return;
                }
                runScamAnalysis(extractedTexts);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in accessibility event loop", e);
        }
    }

    private void traverseAndExtractText(AccessibilityNodeInfo node, List<String> result) {
        if (node == null) return;

        // PRIVACY SHIELD 1: Never scan password containers
        if (node.isPassword()) return;

        // PRIVACY SHIELD 2: Explicitly bypass fields labeled for pins/cards/credentials
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            if (lowerId.contains("password") || lowerId.contains("pin") || lowerId.contains("card")) {
                return;
            }
        }

        // PRIVACY SHIELD 3: Only collect text actively visible to the user interface
        if (node.isVisibleToUser()) {
            CharSequence nodeText = node.getText();
            if (nodeText != null) {
                String cleanText = nodeText.toString().trim();
                if (!cleanText.isEmpty()) {
                    result.add(cleanText);
                }
            }
        }

        // Iteratively dig into structural child components
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseAndExtractText(child, result);
                child.recycle(); // Prevent extreme memory leaks on budget hardware
            }
        }
    }

    private void runScamAnalysis(List<String> texts) {
        StringBuilder blockTextBuilder = new StringBuilder();
        for (String s : texts) {
            blockTextBuilder.append(s).append(" ");
        }
        String combinedString = blockTextBuilder.toString();

        android.util.Log.d(TAG, "Analyzing combined text: " + combinedString);

        if (combinedString.trim().length() < 8) {
            android.util.Log.d(TAG, "Text content too short for reliable detection; triggering fallback.");
            return;
        }

        int matchCount = 0;
        if (urgencyPattern.matcher(combinedString).find()) matchCount++;
        if (financialBaitPattern.matcher(combinedString).find()) matchCount++;
        if (credentialRequestPattern.matcher(combinedString).find()) matchCount++;
        if (shortLinkPattern.matcher(combinedString).find()) matchCount++;

        if (matchCount >= 2) {
            android.util.Log.d(TAG, "Suspicious pattern detected: " + matchCount + " signal categories matched.");
            showScamWarningNotification();
        } else {
            android.util.Log.d(TAG, "No strong scam pattern detected from accessibility text.");
        }
    }
    
    public void showScamWarningNotification() {
        // Placeholder for notification logic; implement as needed
        android.util.Log.d(TAG, "Triggering scam warning notification to user.");
    }

    @Override
    public void onInterrupt() {
        // Safe placeholder context hook if OS interrupts service lifecycle
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
        lastAnalysedText = "";
        lastAnalyzedTime = 0L;
    }
}