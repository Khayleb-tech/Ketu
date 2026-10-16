package scam.detector.ketu;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;   

import android.util.Log;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static final String PREFS_NAME = "ScamDetectorPrefs";
    private static final String KEY_IS_ENABLED = "is_enabled";
    private static final String ACTION_REQUEST_SCREEN_CAPTURE = "scam.detector.ketu.REQUEST_SCREEN_CAPTURE";
    private static final long FALLBACK_COOLDOWN_MS = 5000L;
    private long lastFallbackRequestTime = 0L;
    private OverlayManager overlayManager;
    private long lastOverlayPromptTime = 0L;
    private static final long OVERLAY_PROMPT_COOLDOWN_MS = 60 * 1000; // 1 minute

    private static final int MAX_QUEUED_EVENTS = 5;
    private static final long ANALYSIS_RETENTION_MS = 30000L;
    // Background thread pool to offload text matching away from the UI thread
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ScamProxyClient scamProxyClient = new ScamProxyClient();
    private final AlertHistoryStore alertHistoryStore = new AlertHistoryStore(MyAccessibilityService.this);
    private String lastAnalysedText = "";
    private long lastAnalyzedTime = 0L;
    // Pre-compiled regex patterns to maintain blazing fast local lookups
    private static final Pattern urgencyPattern = Pattern.compile(
        "\\b(verify|suspicious|blocked|unauthorized|frozen|login required|" +
        "act now|urgent|immediately|expi(re|red|ring)s?|final notice|limited time|" +
        "hurry|closing soon|disable|delete|suspend(ed|ing)?)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern serviceBaitPattern = Pattern.compile(
        "\\b(cloud|storage|photos?|subscription|billing|mailbox|quota|drive|backup)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern financialBaitPattern = Pattern.compile(
        "\\b(winner|claim your|cash prize|guaranteed|risk-free|wire transfer|western union|" +
        "bitcoin|itunes|steam card|gift card|investment opportunity|final allocation|disbursement|payment method|billing issue|invoice|renewal|card declined|custom fee|clearance charge|)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern credentialRequestPattern = Pattern.compile(
        "(confirm your (password|identity|account)|provide your (card|pin|otp|ssn)|reset your password)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern shortLinkPattern = Pattern.compile(
        "https?://(bit\\.ly|tinyurl\\.com|t\\.co|cutt\\.ly|rb\\.gy|t\\.ly)/\\w+",
        Pattern.CASE_INSENSITIVE);

    private final Map<String, Long> recentlyEscalatedItems = new ConcurrentHashMap<>();
    private static final long ESCALATION_COOLDOWN_MS = 2 * 60 * 1000; // 2 min, tune freely

    private static final int SCAM_CACHE_MAX_SIZE = 200;
    private static final long SCAM_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24);

    private static final long SAFE_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15);

    private final LinkedHashMap<String, Long> knownScamMessages =
        new LinkedHashMap<String, Long>(SCAM_CACHE_MAX_SIZE, 0.75f, true){
            @Override 
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest){
                return size() > SCAM_CACHE_MAX_SIZE;
            }
        };
    
    private boolean isKnownScam(String messageText){
        Long flaggedAt = knownScamMessages.get(messageText);
        if (flaggedAt == null) return false;
        if  (System.currentTimeMillis() - flaggedAt > SCAM_CACHE_TTL_MS){
            knownScamMessages.remove(messageText);
            return false;
        }
        return true;
    }

    private final LinkedHashMap<String, Long> knownSafeMessages =
        new LinkedHashMap<String, Long>(SCAM_CACHE_MAX_SIZE, 0.75f, true){
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest){
                return size() > SCAM_CACHE_MAX_SIZE;
            }
        };

    private boolean isKnownSafe(String messageText){
        Long checkedAt = knownSafeMessages.get(messageText);
        if (checkedAt == null) return false;
        if (System.currentTimeMillis() - checkedAt > SAFE_CACHE_TTL_MS){
            knownSafeMessages.remove(messageText);
            return false;
        }
        return true;
    }

            
    private final boolean isOpenThread(AccessibilityNodeInfo root) {
        if (root == null) return false;

    // Signal 1 (primary): an editable message-input field exists.
    // Chat lists and notification previews never have one; an open
    // thread does.
        boolean hasInput = hasEditableInputField(root);

    // Signal 2 : window/activity class.
        String className = root.getClassName() != null
            ? root.getClassName().toString() : "unknown";
        boolean classMatch = KNOWN_THREAD_CLASSES.contains(className);

        return hasInput || classMatch;
    }

    private boolean hasEditableInputField(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isEditable()) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (hasEditableInputField(child)) return true;
            if (child != null) child.recycle();
        }
        return false;
    }
    private static final Set<String> KNOWN_THREAD_CLASSES = new HashSet<>(); // fill in later

    // stop the redundant escalation
    private boolean hasNewContentToEscalate(List<String> items) {
        String fingerprint = buildComparisonFingerprint(items);
        if (fingerprint.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastSeen = recentlyEscalatedItems.get(fingerprint);
        boolean anyNew = lastSeen == null || (now - lastSeen) > ESCALATION_COOLDOWN_MS;

        Log.d(TAG, "Fingerprint check: hash=" + fingerprint.hashCode() + " len=" + fingerprint.length()
                + " lastSeen=" + (lastSeen == null ? "none" : (now - lastSeen) + "ms ago")
                + " decision=" + (anyNew ? "NEW" : "DUPLICATE")
                + " mapSize=" + recentlyEscalatedItems.size());

                        if (anyNew) {
            recentlyEscalatedItems.put(fingerprint, now);
        }
        if (recentlyEscalatedItems.size() > 500) {
            recentlyEscalatedItems.entrySet().removeIf(e -> now - e.getValue() > ESCALATION_COOLDOWN_MS);
        }
        return anyNew;
    }

    private static String normalizeForComparison(String item) {
        if (item == null) {
            return "";
        }

        String normalized = item.replace('\u00A0', ' ').trim();
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.toLowerCase(Locale.US);

        String[] boilerplatePrefixes = {
                "compose",
                "search in mail",
                "search",
                "mail",
                "reply",
                "forward",
                "new message",
                "select",
                "edit",
                "open",
                "close",
                "done"
        };

        for (String prefix : boilerplatePrefixes) {
            normalized = normalized.replaceAll("^" + Pattern.quote(prefix) + "\\s+", "");
            normalized = normalized.replaceAll("\\s+" + Pattern.quote(prefix) + "\\s*$", " ");
        }

        normalized = normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normalized;
    }
    static String buildComparisonFingerprint(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (!looksLikeScamContent(item)) {
                continue;
            }
            String normalized = normalizeForComparison(item);
            if (normalized.isEmpty()) {
                continue;
            }
            builder.append(normalized).append('\n');
        }
        return builder.toString().trim();
    }

    private static boolean looksLikeScamContent(String rawText) {
        if (rawText == null || rawText.trim().length() < 4) {
            return false;
        }

        return urgencyPattern.matcher(rawText).find()
                || financialBaitPattern.matcher(rawText).find()
                || credentialRequestPattern.matcher(rawText).find()
                || shortLinkPattern.matcher(rawText).find();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        try {
            SharedPreferences sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnabledInApp = sharedPrefs.getBoolean(KEY_IS_ENABLED, true);
            if (!isEnabledInApp) {
                Log.d(TAG, "Accessibility detection paused by app switch.");
                return;
            }

            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (overlayManager != null) {
                    overlayManager.removeOverlay();
                    Log.d(TAG, "Scroll detected — overlay dismissed.");
                }
            }

            long capturedAt = System.currentTimeMillis();
            CharSequence pkg = event.getPackageName();
            String sourcePackage = (pkg != null) ? pkg.toString() : "unknown";
            backgroundExecutor.execute(() -> {
                try{
                    if (System.currentTimeMillis() - capturedAt > 3000) {
                        Log.d(TAG, "Discarding stale event, screen has likely moved on");
                        return;
                    }
                    AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                    if (rootNode == null) {
                        Log.d(TAG, "No root node available from accessibility event.");
                        return;
                    }

                    if (!isOpenThread(rootNode)) {
                        Log.d(TAG, "Not inside an open thread; skipping analysis.");
                        rootNode.recycle();
                        return;
                    }

                    // Collect visible text nodes and their screen bounds
                    List<TextNode> collected = new ArrayList<>();
                    traverseCollectTextNodes(rootNode, collected);

                    if (collected.isEmpty()) {
                        Log.d(TAG, "Accessibility extracted no useful text nodes; requesting screenshot fallback.");
                        return;
                    }

                    List<String> extractedTexts = new ArrayList<>();
                    for (TextNode tn : collected) extractedTexts.add(tn.text);

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

                    final boolean overlaysAllowed = Settings.canDrawOverlays(this);
                    long now = System.currentTimeMillis();
                        if (!overlaysAllowed && now - lastOverlayPromptTime > OVERLAY_PROMPT_COOLDOWN_MS) {
                        lastOverlayPromptTime = now;
                        try {
                            // Launch the in-app OverlayPermissionActivity which explains and routes user to settings.
                            Intent intent = new Intent(this, OverlayPermissionActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Log.d(TAG, "Launched OverlayPermissionActivity to request overlay permission.");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to launch OverlayPermissionActivity, fallback to system settings", e);
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                Log.d(TAG, "Prompted user to grant overlay permission via system settings.");
                            } catch (Exception ex) {
                                Log.e(TAG, "Failed to launch overlay permission intent", ex);
                            }
                        }
                    }
                    findAndEscalateScamNodeBounds(collected, sourcePackage);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in background accessibility processing", e);
                }
            
            });
               
        } catch (Exception e) {
            Log.e(TAG, "Error in accessibility event loop", e);
        }
    }

    public void scrollNode(AccessibilityNodeInfo nodeInfo){
        if (nodeInfo == null) return;
        if (nodeInfo.isScrollable()) {
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            Log.d(TAG, "Performed scroll forward on node: " + nodeInfo.toString());
        } else {
            for (int i = 0; i < nodeInfo.getChildCount(); i++) {
                scrollNode(nodeInfo.getChild(i));
            }
            Log.d(TAG, "child nodes found");
        } 
    }

    private void traverseAndExtractText(AccessibilityNodeInfo node, List<String> result) {
        if (node == null) return;

        // PRIVACY SHIELD 1: Never scan password containers
        if (node.isPassword()) return;
        Log.d(TAG, "Extracted Password Skipped");

        // PRIVACY SHIELD 2: Explicitly bypass fields labeled for pins/cards/credentials
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            if (lowerId.contains("password") || lowerId.contains("pin") || lowerId.contains("card")) {
                return;
            }
            Log.d(TAG, "Extracted Credentials Skipped");
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

    
    // Lightweight holder for text + screen bounds for a node
    private static class TextNode {
        final String text;
        final Rect bounds;

        TextNode(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
    }

    private void traverseCollectTextNodes(AccessibilityNodeInfo node, List<TextNode> result) {
        if (node == null) return;

        if (node.isPassword()) return;
        Log.d(TAG, "Collected Password Skipped");

        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            if (lowerId.contains("password") || lowerId.contains("pin") || lowerId.contains("card")) {
                return;
            }
            Log.d(TAG, "Collected Credentials Skipped");
        }

        if (node.isVisibleToUser()) {
            CharSequence nodeText = node.getText();
            if (nodeText != null) {
                String cleanText = nodeText.toString().trim();
                if (!cleanText.isEmpty()) {
                    Rect r = new Rect();
                    node.getBoundsInScreen(r);
                    result.add(new TextNode(cleanText, r));
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseCollectTextNodes(child, result);
                child.recycle();
            }
        }
    }

    /**
     * Finds the most suspicious node among the collected nodes, performs escalation & notifications,
     * and returns the bounds of the chosen node (or null if none matched or were escalated).
     */
    private void findAndEscalateScamNodeBounds(List<TextNode> nodes, String sourcePackage) {
        if (nodes == null || nodes.isEmpty()) return;

        StringBuilder combinedBuilder = new StringBuilder();
        for (TextNode tn : nodes) {
            combinedBuilder.append(tn.text).append(' ');
        }
        String combinedString = combinedBuilder.toString();

        if (combinedString.trim().length() < 8) return;

        int blobScore = 0;
        if (urgencyPattern.matcher(combinedString).find()) blobScore++;
        if (financialBaitPattern.matcher(combinedString).find()) blobScore++;
        if (credentialRequestPattern.matcher(combinedString).find()) blobScore++;
        if (shortLinkPattern.matcher(combinedString).find()) blobScore++;
        if (serviceBaitPattern.matcher(combinedString).find()) blobScore++;

        if (blobScore < 2) {
            Log.d(TAG, "No strong scam pattern detected from accessibility text.");
            return;
        }

        int bestScore = 0;
        TextNode bestNode = null;
        for (TextNode tn : nodes) {
            int score = 0;
            if (urgencyPattern.matcher(tn.text).find()) score++;
            if (financialBaitPattern.matcher(tn.text).find()) score++;
            if (credentialRequestPattern.matcher(tn.text).find()) score++;
            if (shortLinkPattern.matcher(tn.text).find()) score++;
            if (serviceBaitPattern.matcher(tn.text).find()) score++;
            
            if (score > bestScore) {
                bestScore = score;
                bestNode = tn;
            }
        }
        if (bestNode == null) bestNode = nodes.get(0);
        final TextNode chosenNode = bestNode;

	    String messageKey = normalizeForComparison(chosenNode.text);

        if (isKnownScam(messageKey) || isKnownSafe(messageKey)) {
    	    Log.d(TAG, "Already checked this exact message — skipping.");
    	    return;
	    }

        if (ScamProxyClient.isInCooldown()) {
            Log.d(TAG, "In rate-limit cooldown — skipping escalation.");
            return;
        }

        List<String> texts = new ArrayList<>();
        for (TextNode tn : nodes) texts.add(tn.text);

        if (!hasNewContentToEscalate(texts)) {
            Log.d(TAG, "Same scam content already escalated recently — skipping.");
            return;
        }
        

        scamProxyClient.sendText(combinedString, new ScamProxyClient.ScamAnalysisCallback() {
            @Override
            public void onResult(boolean isScam, String reason, String shortReason) {
                if (!isScam) {
                    Log.d(TAG, "Gemini says not a scam — suppressing overlay. Reason: " + reason);
		            knownSafeMessages.put(messageKey, System.currentTimeMillis());
                    return;
                }
                Log.d(TAG, "Gemini confirmed scam: " + reason);
		        knownScamMessages.put(messageKey, System.currentTimeMillis());
                alertHistoryStore.addAlert(sourcePackage, shortReason, reason);
                showScamWarningNotification();
                if (Settings.canDrawOverlays(MyAccessibilityService.this)) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (overlayManager == null) {
                            overlayManager = new OverlayManager(MyAccessibilityService.this);
                        }
                        overlayManager.showOverlayAt(chosenNode.bounds, shortReason);
                    });
                }
            }
            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Scam analysis unavailable, suppressing overlay: " + e.getMessage());
            }
        });
    }
    public void showScamWarningNotification() {
        // Placeholder for notification logic; implement as needed
        Log.d(TAG, "Triggering scam warning notification to user.");
    }
    @Override
    protected void onServiceConnected(){
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected and live:" + this.hashCode());
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
        if (overlayManager != null) {
            overlayManager.removeOverlay();
        }
    }
}