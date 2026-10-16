package scam.detector.ketu;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ScamProxyClient {

    private static final String TAG = "ScamProxyClient";
    private static final String URL = "https://scam-proxy.scamketu.workers.dev/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private static volatile long cooldownUntil = 0;

    public interface ScamAnalysisCallback {
        void onResult(boolean isScam, String reason, String shortReason);
        void onError(Exception e);
    }
    
    public static boolean isInCooldown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    public ScamProxyClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }

    public void sendText(String textToScan, ScamAnalysisCallback callback) {
        if (textToScan == null || textToScan.trim().isEmpty()) {
            return;
        }

        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("screenText", textToScan);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build request JSON", e);
            if (callback != null) callback.onError(e);
            return;
        }
RequestBody body = RequestBody.create(bodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network failed to call", e);
                if (callback != null) callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    int responseCode = response.code();
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (responseCode == 429 || responseBody.contains("RESOURCE_EXHAUSTED")) {
                        Log.e(TAG, "Rate limited — backing off 60s.");
                        cooldownUntil = System.currentTimeMillis() + 60_000;
                        if (callback != null) callback.onError(new IOException("Rate limited"));
                        return;
                    }

                    if (!response.isSuccessful()) {
                        Log.d(TAG, "Proxy request failed with code: " + responseCode + " body: " + responseBody);
                        if (callback != null) callback.onError(new IOException("HTTP " + responseCode));
                        return;
                    }

                    Log.d(TAG, "Response successfully received: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    if (json.has("error")) {
                        JSONObject errObj = json.optJSONObject("error");
                        String errMsg = errObj != null ? errObj.optString("message", "unknown proxy error") : "unknown proxy error";
                        Log.d(TAG, "Proxy relayed an error: " + errMsg);
                        if (callback != null) callback.onError(new IOException(errMsg));
                        return;
                    }
                    String verdictText = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                    
                    JSONObject verdict = new JSONObject(verdictText);
                    boolean isScam = verdict.optBoolean("isScam", false);
                    String reason = verdict.optString("reason", "");
                    String shortReason = verdict.optString("shortReason", "");
                    Log.d(TAG, "Parsed Verdict : isScam=" + isScam + ", reason=" + reason + ", shortReason=" + shortReason);
                    if (callback != null) callback.onResult(isScam, reason, shortReason);
                } catch (JSONException je) {
                    Log.e(TAG, "Failed to parse proxy response JSON", je);
                    if (callback != null) callback.onError(je);
                } finally {
                    response.close();
                }
            }
        });
    }
}