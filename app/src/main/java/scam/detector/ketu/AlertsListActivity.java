package scam.detector.ketu;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AlertsListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts_list);

        Button backButton = findViewById(R.id.btnAlertsBack);
        backButton.setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.alertsRecyclerView);
        TextView emptyStateView = findViewById(R.id.emptyStateView);

        AlertHistoryStore store = new AlertHistoryStore(this);
        List<AlertHistoryStore.AlertEntry> alerts = parseAlerts(store.getAlerts());

        if (alerts.isEmpty()) {
            emptyStateView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(new AlertHistoryStore.AlertsAdapter(this, alerts));
        }
    }

    private List<AlertHistoryStore.AlertEntry> parseAlerts(JSONArray jsonArray) {
        List<AlertHistoryStore.AlertEntry> result = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.optJSONObject(i);
            if (obj == null) continue;
            AlertHistoryStore.AlertEntry entry = new AlertHistoryStore.AlertEntry();
            entry.timestamp = obj.optLong("timestamp", 0);
            entry.sourceApp = obj.optString("sourceApp", "");
            entry.shortReason = obj.optString("shortReason", "");
            entry.reason = obj.optString("reason", "");
            result.add(entry);
        }
        return result;
    }
}
