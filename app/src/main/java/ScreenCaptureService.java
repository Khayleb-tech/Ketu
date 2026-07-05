package scam.detector.ketu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final int NOTIFICATION_ID = 2;
    private static final String CHANNEL_ID = "ScreenCaptureServiceChannel";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback(){
        @Override
        public void onStop(){
            super.onStop();
            stopSelf();
        }
    }; 

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ScreenCaptureService", "onStartCommand, mediaProjection" + (mediaProjection != null ? "live" : "null"));
        if (mediaProjection != null){
            captureFrame();// pipeline is already live - just grap the next frame
            return START_NOT_STICKY;

        }

        if (intent != null) {
            int resultCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent dataIntent = intent.getParcelableExtra("DATA_INTENT");

            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager != null && resultCode != 0 && dataIntent != null) {
                mediaProjection = manager.getMediaProjection(resultCode, dataIntent);
                mediaProjection.registerCallback(projectionCallback, null);
                setUpVirtualDisplay();
            }
        }
        return START_NOT_STICKY;
    }


    private void setUpVirtualDisplay() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null || mediaProjection == null) {
            stopSelf();
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenshotDisplay",
                screenWidth,
                screenHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );

        captureFrame();
    }
    private void captureFrame(){
        Log.d("ScreenCaptureService", "captureFrame called");
        final boolean[] handled = {false};
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (handled[0]) return;
                handled[0] = true;
                imageReader.setOnImageAvailableListener(null, null);
                processAvailableImage();
                Log.d("ScreenCaptureService", "onImageAvailable fired");

                
            }
        }, null);
        new Handler (Looper.getMainLooper()).postDelayed(new Runnable(){
            @Override
            public void run(){
                if (!handled[0]){
                    handled[0] = true;
                    imageReader.setOnImageAvailableListener(null, null);
                    processAvailableImage();
                }
            }
        }, 300);

    }
    private void processAvailableImage(){
        try (Image image = imageReader.acquireLatestImage()) {
                if (image != null) {
                    Bitmap bitmap = processImageBuffer(image, screenWidth, screenHeight);
                    saveOrUseBitmap(bitmap);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
    }


    private Bitmap processImageBuffer(Image image, int screenWidth, int screenHeight) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();

        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture is active")
                .setContentText("Listening for scams...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void saveOrUseBitmap(Bitmap bitmap) {
        File outputDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // context being the Activity pointer
        if (outputDir == null) return;
        File outputFile = new File(outputDir, "capture_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d("ScreenCaptureService", "Saved screenshot to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        } 

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("ScreenCaptureService", "Service destroyed");

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
