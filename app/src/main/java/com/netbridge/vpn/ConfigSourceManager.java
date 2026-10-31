package com.netbridge.vpn;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigSourceManager {

    private static final String TAG = "NetBridgeSources";
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Set<String> configs = new LinkedHashSet<>();
    private final Set<String> sources = new LinkedHashSet<>();

    public ConfigSourceManager() {
        sources.add("https://radarhub.top/sing-box.json");
        sources.add("https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt");
        sources.add("https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_base64.txt");
        sources.add("https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt");
        sources.add("https://raw.githubusercontent.com/Leon406/SubCrawler/master/sub/share/all3");
    }

    public interface Callback {
        void onComplete(List<String> results);
        void onError(String message);
    }

    public void scan(Callback callback) {
        executor.execute(() -> {
            synchronized (configs) {
                configs.clear();
            }

            List<String> sourceCopy;
            synchronized (sources) {
                sourceCopy = new ArrayList<>(sources);
            }

            List<Thread> workers = new ArrayList<>();

            for (String source : sourceCopy) {
                Thread worker = new Thread(() -> download(source));
                workers.add(worker);
                worker.start();
            }

            for (Thread worker : workers) {
                try {
                    worker.join(25000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            List<String> result;
            synchronized (configs) {
                result = new ArrayList<>(configs);
            }

            if (result.isEmpty()) {
                callback.onError("از منابع فعلی کانفیگ معتبری دریافت نشد.");
            } else {
                callback.onComplete(result);
            }
        });
    }

    private void download(String address) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NetBridgeVPN/1.1");
            connection.setRequestProperty("Accept", "*/*");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return;
            }

            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int count;
                int total = 0;

                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) {
                        Log.w(TAG, "Source too large: " + address);
                        return;
                    }
                    output.write(buffer, 0, count);
                }

                String body = output.toString("UTF-8");
                extractConfigs(body);

                String compact = body.replaceAll("\\s+", "");
                if (!compact.isEmpty()
                        && !compact.contains("://")
                        && compact.matches("[A-Za-z0-9+/=]+")) {
                    try {
                        String decoded = new String(
                                Base64.decode(compact, Base64.DEFAULT),
                                StandardCharsets.UTF_8
                        );
                        extractConfigs(decoded);
                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Source failed: " + address, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void extractConfigs(String text) {
        String[] schemes = {
                "vless://", "vmess://", "trojan://",
                "wireguard://", "ss://", "hysteria2://",
                "hysteria://", "tuic://", "socks://"
        };

        for (String scheme : schemes) {
            int start = 0;

            while ((start = text.indexOf(scheme, start)) >= 0) {
                int end = start;

                while (end < text.length()) {
                    char c = text.charAt(end);
                    if (Character.isWhitespace(c)
                            || c == '"'
                            || c == '\''
                            || c == '<'
                            || c == '>'
                            || c == ',') {
                        break;
                    }
                    end++;
                }

                String config = text.substring(start, end)
                        .replaceAll("[\\]\\}\\)]+$", "")
                        .trim();

                if (config.length() > scheme.length()
                        && config.length() < 8192) {
                    synchronized (configs) {
                        configs.add(config);
                    }
                }

                start = Math.max(end, start + scheme.length());
            }
        }
    }

    public void addSource(String address) {
        if (address != null && address.startsWith("https://")) {
            synchronized (sources) {
                sources.add(address.trim());
            }
        }
    }

    public void close() {
        executor.shutdownNow();
    }
}
