package com.netbridge.vpn;

import android.app.Activity;
import android.net.VpnService;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import java.util.List;

public class MainActivity extends Activity {
    private TextView status;
    private TextView results;
    private Button searchButton;
    private ConfigSourceManager manager;
    private static final int VPN_REQUEST_CODE = 7001;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null && status != null) {
                status.setText(message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = new ConfigSourceManager();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 35, 28, 25);
        layout.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("NetBridge VPN");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 25);

        status = new TextView(this);
        status.setText("وضعیت: آماده جست‌وجو");
        status.setTextSize(17);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 10, 0, 20);

        searchButton = new Button(this);
        searchButton.setText("جست‌وجوی خودکار کانفیگ");

        Button connectButton = new Button(this);
        connectButton.setText("اتصال VPN");

        results = new TextView(this);
        results.setTextColor(Color.WHITE);
        results.setTextSize(13);
        results.setPadding(5, 20, 5, 20);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);

        layout.addView(title);
        layout.addView(status);
        layout.addView(searchButton);
        layout.addView(connectButton);
        layout.addView(scroll);
        setContentView(layout);

        searchButton.setOnClickListener(v -> startSearch());
        connectButton.setOnClickListener(v -> requestVpnPermission());

        IntentFilter filter = new IntentFilter("com.netbridge.vpn.STATUS");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private void requestVpnPermission() {
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST_CODE);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent service = new Intent(this, NetBridgeVpnService.class);
        status.setText("در حال راه‌اندازی موتور و ایجاد تونل...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                status.setText("اجازه VPN داده نشد.");
            }
        }
    }

    private void startSearch() {
        searchButton.setEnabled(false);
        status.setText("در حال دریافت و بررسی کانفیگ‌ها...");
        results.setText("");

        manager.scan(new ConfigSourceManager.Callback() {
            @Override
            public void onComplete(List<String> configs) {
                runOnUiThread(() -> {
                    status.setText("تعداد کانفیگ‌های پیدا شده: " + configs.size());
                    StringBuilder output = new StringBuilder();
                    for (int i = 0; i < configs.size(); i++) {
                        output.append(i + 1)
                              .append(". ")
                              .append(configs.get(i).split("#")[0])
                              .append("\n\n");
                    }
                    results.setText(output.toString());
                    searchButton.setEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    status.setText("کانفیگی پیدا نشد");
                    results.setText(message);
                    searchButton.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {
        }
        if (manager != null) manager.close();
        super.onDestroy();
    }
}
