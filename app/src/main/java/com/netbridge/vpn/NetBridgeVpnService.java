package com.netbridge.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.VpnService;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class NetBridgeVpnService extends VpnService {
    private static final String TAG = "NetBridgeVPN";
    private static final String CHANNEL = "netbridge_vpn";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(41, buildNotification("سرویس VPN آماده‌سازی شد"));
        Log.i(TAG, "VPN foreground service started");
        // Engine start is deliberately not faked here. The next integration step
        // must provide a validated sing-box JSON config and libbox PlatformInterface.
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "NetBridge VPN", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                .setContentTitle("NetBridge VPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true).build();
        }
        return new Notification.Builder(this)
            .setContentTitle("NetBridge VPN").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true).build();
    }

    @Override public void onDestroy() {
        Log.i(TAG, "VPN service stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return super.onBind(intent); }
}
