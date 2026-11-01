package com.netbridge.vpn;

import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigSourceManager {

    private static final String TAG = "NetBridgeSources";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final List<String> sources = new ArrayList<>();

    public ConfigSourceManager() {
        sources.add("https://radarhub.top/sing-box.json");
    }

    public interface Callback {
        void onComplete(List<String> results);
        void onError(String message);
    }

    public void scan(Callback callback) {
        executor.execute(() -> {
            List<String> results = new ArrayList<>();

            for (String address : sources) {
                try {
                    String content = download(address);

                    JSONObject root = new JSONObject(content);
                    JSONArray outbounds = root.optJSONArray("outbounds");

                    if (outbounds == null || outbounds.length() == 0) {
                        throw new Exception("outbounds خالی است");
                    }

                    results.add(content);
                    break;

                } catch (Exception e) {
                    Log.e(TAG, "Source failed: " + address, e);
                }
            }

            if (results.isEmpty()) {
                callback.onError("دریافت یا بررسی کانفیگ ناموفق بود");
            } else {
                callback.onComplete(results);
            }
        });
    }

    private String download(String address) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty(
                    "User-Agent", "NetBridgeVPN/1.0"
            );

            int status = connection.getResponseCode();

            if (status != 200) {
                throw new Exception("HTTP " + status);
            }

            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');

                    if (output.length() > 5_000_000) {
                        throw new Exception("حجم کانفیگ بیش از حد مجاز است");
                    }
                }
            }

            return output.toString();

        } finally {
            connection.disconnect();
        }
    }

    public void addSource(String address) {
        if (address != null
                && address.startsWith("https://")
                && !sources.contains(address)) {
            sources.add(address);
        }
    }

    public void close() {
        executor.shutdownNow();
    }
}
