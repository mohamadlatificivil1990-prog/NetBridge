package com.netbridge.vpn;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigSourceManager {

    private static final String TAG = "NetBridgeSources";

    private final ExecutorService executor =
            Executors.newFixedThreadPool(6);

    private final Set<String> configs =
            new LinkedHashSet<>();

    private final List<String> sources = new ArrayList<>();

    public ConfigSourceManager() {
        // Public subscription source
        sources.add("https://radarhub.top/sing-box.json");

        // Add more public subscription URLs here.
        // Each source is downloaded independently.
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

            List<Thread> workers = new ArrayList<>();

            for (String source : sources) {
                Thread worker = new Thread(() -> download(source));
                workers.add(worker);
                worker.start();
            }

            for (Thread worker : workers) {
                try {
                    worker.join(20000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            List<String> result;
            synchronized (configs) {
                result = new ArrayList<>(configs);
            }

            if (result.isEmpty()) {
                callback.onError("No supported configurations found");
            } else {
                callback.onComplete(result);
            }
        });
    }

    private void download(String address) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty(
                    "User-Agent", "NetBridgeVPN/1.0"
            );

            int status = connection.getResponseCode();

            if (status != 200) return;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    extractConfigs(line);
                }
            }

        } catch (Exception e) {
            android.util.Log.w(TAG, "Source failed: " + address, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void extractConfigs(String text) {
        String[] schemes = {
                "vless://",
                "vmess://",
                "trojan://",
                "wireguard://",
                "ss://"
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
                            || c == '>') {
                        break;
                    }

                    end++;
                }

                String config = text.substring(start, end)
                        .replace(",", "")
                        .replace("]", "")
                        .replace("}", "");

                if (config.length() > scheme.length()) {
                    synchronized (configs) {
                        configs.add(config);
                    }
                }

                start = Math.max(end, start + scheme.length());
            }
        }
    }

    public void addSource(String address) {
        if (address != null && address.startsWith("https://")
                && !sources.contains(address)) {
            sources.add(address);
        }
    }

    public void close() {
        executor.shutdownNow();
    }
}
