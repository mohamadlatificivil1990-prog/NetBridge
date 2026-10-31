package com.netbridge.vpn;

import android.util.Base64;
import org.json.JSONObject;
import java.net.URI;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConfigTester {

    public static class Result {
        public final String config;
        public final String host;
        public final int port;
        public final long latency;
        public final boolean reachable;

        Result(String config, String host, int port,
               long latency, boolean reachable) {
            this.config = config;
            this.host = host;
            this.port = port;
            this.latency = latency;
            this.reachable = reachable;
        }
    }

    public static List<Result> testAll(List<String> configs) {
        List<Result> results = new ArrayList<>();

        for (String config : configs) {
            String host = null;
            int port = -1;

            try {
                if (config.startsWith("vmess://")) {
                    String encoded = config.substring(8).split("#")[0];
                    String json = new String(
                        Base64.decode(encoded, Base64.DEFAULT),
                        "UTF-8"
                    );
                    JSONObject obj = new JSONObject(json);
                    host = obj.optString("add");
                    port = obj.optInt("port");
                } else if (config.startsWith("vless://")
                        || config.startsWith("trojan://")
                        || config.startsWith("ss://")) {
                    URI uri = URI.create(config);
                    host = uri.getHost();
                    port = uri.getPort();
                }

                if (host == null || host.isEmpty()
                        || port < 1 || port > 65535) {
                    results.add(new Result(
                        config, "", -1, -1, false
                    ));
                    continue;
                }

                long start = System.currentTimeMillis();
                boolean reachable = false;

                try (Socket socket = new Socket()) {
                    socket.connect(
                        new java.net.InetSocketAddress(host, port),
                        3000
                    );
                    reachable = true;
                } catch (Exception ignored) {
                }

                long latency = reachable
                    ? System.currentTimeMillis() - start
                    : -1;

                results.add(new Result(
                    config, host, port, latency, reachable
                ));

            } catch (Exception e) {
                results.add(new Result(
                    config, "", -1, -1, false
                ));
            }
        }

        results.sort(Comparator.comparingLong(
            r -> r.reachable ? r.latency : Long.MAX_VALUE
        ));

        return results;
    }
}
