package scam.detector.ketu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;


public class KetuService extends Service{
    // unique ID for the foreground notification
    private static final int NOTIFICATION_ID = 1;

    // notification channel ID( required for android 8+)
    private static final String CHANNEL_ID = "KetuServiceChannel";

    @Override
    public void onCreate(){
        super.onCreate();
        // start the foreground notification so service stays alive
        startForegroundService();
        Log.d("KetuService", "Service started - waiting for push-to-talk trigger");
    }
    // build and display the persistent foreground notification
    private void startForegroundService(){
        // create notification channel for android 8.0 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ketu Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        // Build the persistent notification in status bar
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ketu is active")
                .setContentText("Listening for scams...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        // attach notification and promote service to foreground
        startForeground(NOTIFICATION_ID, notification);
    }

    // triggered when the wake word "Ketu" is detected
    public void onWakeWordDetected(){
        // TODO:Triggers screenshot capture - coming in the next step
        Log.d("KetuService", "Wake word detected - screenshot capture will go here ");
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d("KetuService", "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent){
        // this service does not support binding to activities
        return null;
    }
    
}