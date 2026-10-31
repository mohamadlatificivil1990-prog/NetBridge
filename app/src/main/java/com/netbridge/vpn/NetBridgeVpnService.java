package com.netbridge.vpn;

import android.net.VpnService;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class NetBridgeVpnService extends VpnService {

    private static final String TAG = "NetBridgeVPN";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VPN Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "VPN Service started");

        // Engine initialization will be connected here
        // after libbox.aar and its API are integrated.

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "VPN Service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
