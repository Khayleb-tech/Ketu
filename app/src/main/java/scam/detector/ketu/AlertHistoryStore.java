package scam.detector.ketu;

import org.json.JSONArray;

import org.json.JSONObject;
import org.json.JSONException;
import android.content.SharedPreferences;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import android.content.Intent;

// Import R class so R.layout.alert_row_layout can be resolved
import scam.detector.ketu.R;

public class AlertHistoryStore {
    private final Context context;
    private static final String KEY_ALERT_HISTORY = "alert_history";
    private static final int MAX_ENTRIES = 100;
    
    AlertHistoryStore(Context context) {
        this.context = context;
    }

    void addAlert(String sourcePackage, String shortReason, String reason) {
        SharedPreferences prefs = context.getSharedPreferences("AlertHistoryPrefs", Context.MODE_PRIVATE);
        try {
            JSONArray existing = new JSONArray(prefs.getString(KEY_ALERT_HISTORY, "[]"));

            JSONObject newAlert = new JSONObject();
            newAlert.put("timestamp", System.currentTimeMillis());
            newAlert.put("sourceApp", sourcePackage);
            newAlert.put("shortReason", shortReason);
            newAlert.put("reason", reason);

            JSONArray updated = new JSONArray();
            updated.put(newAlert); // newest first
            for (int i = 0; i < existing.length() && updated.length() < MAX_ENTRIES; i++) {
                updated.put(existing.get(i));
            }

            prefs.edit().putString(KEY_ALERT_HISTORY, updated.toString()).apply();
        } catch (JSONException e) {
            Log.e("AlertHistoryStore", "Failed to save alert", e);
        }
        }

    JSONArray getAlerts() {
        SharedPreferences prefs = context.getSharedPreferences("AlertHistoryPrefs", Context.MODE_PRIVATE);
        try {
            return new JSONArray(prefs.getString(KEY_ALERT_HISTORY, "[]"));
        } catch (JSONException e) {
            Log.e("AlertHistoryStore", "Failed to parse stored alerts", e);
            return new JSONArray();
        }
    }

    public static class AlertEntry {
        long timestamp;
        String sourceApp;
        String shortReason;
        String reason;
    }

    // FIXED: Changed to 'static class' so it can legally host the static ViewHolder.
    // Passed Context into constructor to cleanly manage the Intent launches.
    public static class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.ViewHolder> {
        private final Context context;
        private final List<AlertEntry> alerts;

        public AlertsAdapter(Context context, List<AlertEntry> alerts) { 
            this.context = context;
            this.alerts = alerts; 
        }
    
        @NonNull
        @Override
        public AlertsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.alert_row_layout, parent, false);
            return new AlertsAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AlertsAdapter.ViewHolder holder, int position) {
            if (position < 0 || position >= alerts.size()) {
                Log.e("AlertHistoryStore", "Invalid position in onBindViewHolder: " + position);
                return;
            }
            AlertEntry alert = alerts.get(position);
            holder.shortReasonView.setText(alert.shortReason);
            holder.timestampView.setText(String.valueOf(alert.timestamp));
            holder.sourceAppView.setText(alert.sourceApp);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, AlertAnalysisActivity.class);
                intent.putExtra("timestamp", alert.timestamp);
                intent.putExtra("sourceApp", alert.sourceApp);
                intent.putExtra("shortReason", alert.shortReason);
                intent.putExtra("reason", alert.reason);
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return alerts.size();
        }

        // LEGAL: This static class is now nested inside a static parent class
        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView shortReasonView, timestampView, sourceAppView;
            
            ViewHolder(View itemView) { 
                super(itemView);
                // FIXED: Complete the initialization of your row views here
                shortReasonView = itemView.findViewById(R.id.shortReasonView);
                timestampView = itemView.findViewById(R.id.timestampView);
                sourceAppView = itemView.findViewById(R.id.sourceAppView);
            }
        }
    }
}
