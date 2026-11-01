package com.netbridge.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.IpPrefix;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import io.nekohasekai.libbox.*;

public class NetBridgeVpnService extends VpnService
        implements CommandServerHandler {

    private static final String TAG = "NetBridgeVPN";
    private static final String CHANNEL = "netbridge_vpn";
    private static final int NOTIFICATION_ID = 41;

    private static boolean libboxReady = false;

    private CommandServer commandServer;
    private NetBridgePlatform platform;
    private ParcelFileDescriptor tunDescriptor;
    private volatile String currentConfig = "";
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        platform = new NetBridgePlatform(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(
                NOTIFICATION_ID,
                buildNotification("در حال آماده‌سازی موتور sing-box")
        );

        if (!running) {
            new Thread(() -> startEngine(), "NetBridge-Engine").start();
        }

        return START_NOT_STICKY;
    }

    private synchronized void startEngine() {
        if (running) return;

        try {
            currentConfig = readConfig();

            if (currentConfig.trim().isEmpty()) {
                throw new Exception("source.json خالی است");
            }

            setupLibbox();

            Libbox.checkConfig(currentConfig);

            commandServer = new CommandServer(this, platform);
            commandServer.start();

            commandServer.startOrReloadService(
                    currentConfig,
                    new OverrideOptions()
            );

            running = true;
            updateNotification("موتور sing-box راه‌اندازی شد");
            sendStatus("ENGINE_STARTED", "موتور sing-box راه‌اندازی شد");

            Log.i(TAG, "sing-box service started");

        } catch (Exception e) {
            running = false;
            Log.e(TAG, "Engine startup failed", e);
            updateNotification("خطا در اتصال: " + e.getMessage());
            sendStatus("ENGINE_ERROR", e.getMessage());

            stopEngine();
        }
    }

    private String readConfig() throws Exception {
        String selected = getSharedPreferences("NetBridge", MODE_PRIVATE)
                .getString("selected_config", null);

        if (selected != null && !selected.trim().isEmpty()) {
            return selected;
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getAssets().open("source.json"),
                        "UTF-8"
                ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private synchronized void setupLibbox() throws Exception {
        if (libboxReady) return;

        SetupOptions options = new SetupOptions();

        options.setBasePath(getFilesDir().getAbsolutePath());
        options.setWorkingPath(getFilesDir().getAbsolutePath());
        options.setTempPath(getCacheDir().getAbsolutePath());
        options.setFixAndroidStack(true);
        options.setLogMaxLines(3000L);
        options.setDebug(false);
        options.setAppVersion("1.0");
        options.setAppMarketingVersion("1.0");

        Libbox.setup(options);
        libboxReady = true;
    }

    public synchronized int openTunInterface(TunOptions options)
            throws Exception {

        if (VpnService.prepare(this) != null) {
            throw new Exception("اجازه VPN صادر نشده است");
        }

        Builder builder = new Builder()
                .setSession("NetBridge")
                .setMtu(options.getMTU());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        boolean hasIPv4 = false;
        boolean hasIPv6 = false;

        RoutePrefixIterator ipv4 = options.getInet4Address();

        while (ipv4.hasNext()) {
            RoutePrefix address = ipv4.next();
            builder.addAddress(address.address(), address.prefix());
            hasIPv4 = true;
        }

        RoutePrefixIterator ipv6 = options.getInet6Address();

        while (ipv6.hasNext()) {
            RoutePrefix address = ipv6.next();
            builder.addAddress(address.address(), address.prefix());
            hasIPv6 = true;
        }

        if (options.getAutoRoute()) {

            StringBox dnsMode = options.getDNSMode();

            if (dnsMode != null
                    && !Libbox.DNSModeDisabled.equals(dnsMode.getValue())) {

                StringIterator dns = options.getDNSServerAddress();

                while (dns.hasNext()) {
                    builder.addDnsServer(dns.next());
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {

                RoutePrefixIterator routes4 =
                        options.getInet4RouteAddress();

                boolean hasRoutes4 = false;

                while (routes4.hasNext()) {
                    RoutePrefix route = routes4.next();

                    builder.addRoute(new IpPrefix(
                            InetAddress.getByName(route.address()),
                            route.prefix()
                    ));

                    hasRoutes4 = true;
                }

                if (!hasRoutes4 && hasIPv4) {
                    builder.addRoute("0.0.0.0", 0);
                }

                RoutePrefixIterator routes6 =
                        options.getInet6RouteAddress();

                boolean hasRoutes6 = false;

                while (routes6.hasNext()) {
                    RoutePrefix route = routes6.next();

                    builder.addRoute(new IpPrefix(
                            InetAddress.getByName(route.address()),
                            route.prefix()
                    ));

                    hasRoutes6 = true;
                }

                if (!hasRoutes6 && hasIPv6) {
                    builder.addRoute("::", 0);
                }

                RoutePrefixIterator exclude4 =
                        options.getInet4RouteExcludeAddress();

                while (exclude4.hasNext()) {
                    RoutePrefix route = exclude4.next();

                    builder.excludeRoute(new IpPrefix(
                            InetAddress.getByName(route.address()),
                            route.prefix()
                    ));
                }

                RoutePrefixIterator exclude6 =
                        options.getInet6RouteExcludeAddress();

                while (exclude6.hasNext()) {
                    RoutePrefix route = exclude6.next();

                    builder.excludeRoute(new IpPrefix(
                            InetAddress.getByName(route.address()),
                            route.prefix()
                    ));
                }

            } else {

                RoutePrefixIterator routes4 =
                        options.getInet4RouteRange();

                while (routes4.hasNext()) {
                    RoutePrefix route = routes4.next();
                    builder.addRoute(
                            route.address(),
                            route.prefix()
                    );
                }

                RoutePrefixIterator routes6 =
                        options.getInet6RouteRange();

                while (routes6.hasNext()) {
                    RoutePrefix route = routes6.next();
                    builder.addRoute(
                            route.address(),
                            route.prefix()
                    );
                }
            }

            StringIterator include = options.getIncludePackage();

            while (include.hasNext()) {
                String packageName = include.next();

                try {
                    builder.addAllowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Package not found: " + packageName);
                }
            }

            StringIterator exclude = options.getExcludePackage();

            while (exclude.hasNext()) {
                String packageName = exclude.next();

                try {
                    builder.addDisallowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Package not found: " + packageName);
                }
            }
        }

        if (options.isHTTPProxyEnabled()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            List<String> bypass = new ArrayList<>();
            StringIterator iterator =
                    options.getHTTPProxyBypassDomain();

            while (iterator.hasNext()) {
                bypass.add(iterator.next());
            }

            builder.setHttpProxy(ProxyInfo.buildDirectProxy(
                    options.getHTTPProxyServer(),
                    options.getHTTPProxyServerPort(),
                    bypass
            ));
        }

        ParcelFileDescriptor descriptor = builder.establish();

        if (descriptor == null) {
            throw new Exception("ساخت تونل VPN ناموفق بود");
        }

        if (tunDescriptor != null) {
            try {
                tunDescriptor.close();
            } catch (Exception ignored) {
            }
        }

        tunDescriptor = descriptor;

        Log.i(TAG, "Android TUN established");

        return descriptor.getFd();
    }

    private void stopEngine() {
        running = false;

        if (commandServer != null) {
            try {
                commandServer.closeService();
            } catch (Exception e) {
                Log.w(TAG, "closeService failed", e);
            }

            try {
                commandServer.close();
            } catch (Exception e) {
                Log.w(TAG, "CommandServer close failed", e);
            }

            commandServer = null;
        }

        closeTun();
    }

    private synchronized void closeTun() {
        if (tunDescriptor != null) {
            try {
                tunDescriptor.close();
            } catch (Exception e) {
                Log.w(TAG, "TUN close failed", e);
            }

            tunDescriptor = null;
        }
    }

    @Override
    public int connectSSHAgent() {
        return -1;
    }

    @Override
    public SystemProxyStatus getSystemProxyStatus() {
        SystemProxyStatus status = new SystemProxyStatus();
        status.setAvailable(false);
        status.setEnabled(false);
        return status;
    }

    @Override
    public void serviceReload() throws Exception {
        if (commandServer != null && !currentConfig.isEmpty()) {
            commandServer.startOrReloadService(
                    currentConfig,
                    new OverrideOptions()
            );
        }
    }

    @Override
    public void serviceStop() {
        stopSelf();
    }

    @Override
    public void setSystemProxyEnabled(boolean enabled) {
        Log.d(TAG, "System proxy request: " + enabled);
    }

    @Override
    public void triggerNativeCrash() {
        Log.w(TAG, "Native crash request ignored");
    }

    @Override
    public void writeDebugMessage(String message) {
        Log.d(TAG, message);
    }


    private void sendStatus(String action, String message) {
        Intent intent = new Intent("com.netbridge.vpn.STATUS");
        intent.setPackage(getPackageName());
        intent.putExtra("state", action);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "NetBridge VPN",
                    NotificationManager.IMPORTANCE_LOW
            );

            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("NetBridge VPN")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(this)
                .setContentTitle("NetBridge VPN")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        manager.notify(
                NOTIFICATION_ID,
                buildNotification(message)
        );
    }

    @Override
    public void onRevoke() {
        stopEngine();
        stopForeground(true);
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopEngine();
        Log.i(TAG, "VPN service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
