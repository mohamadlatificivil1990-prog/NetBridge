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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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

    private final int BG = Color.rgb(5, 10, 30);
    private final int CARD = Color.rgb(15, 27, 57);
    private final int CYAN = Color.rgb(0, 220, 255);
    private final int PURPLE = Color.rgb(119, 45, 255);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(166, 184, 220);

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
        buildInterface();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(24));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView logo = new TextView(this);
        logo.setText("N");
        logo.setTextSize(29);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(roundGradient(CYAN, PURPLE, 18));
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);

        TextView title = text("NetBridge", 25, WHITE, true);
        TextView subtitle = text("SECURE  •  FAST  •  GLOBAL", 10, MUTED, false);
        brand.addView(title);
        brand.addView(subtitle);

        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView settings = text("⚙", 27, CYAN, true);
        settings.setGravity(Gravity.CENTER);
        header.addView(settings, new LinearLayout.LayoutParams(dp(42), dp(48)));

        page.addView(header);
        addSpace(page, 25);

        // Status card
        LinearLayout statusCard = card();
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(17), dp(15), dp(17), dp(15));

        TextView statusTitle = text("وضعیت اتصال", 13, MUTED, true);
        status = text("آماده برای اتصال", 17, CYAN, true);
        status.setGravity(Gravity.CENTER);

        statusCard.addView(statusTitle);
        addSpace(statusCard, 8);
        statusCard.addView(status);
        page.addView(statusCard);

        addSpace(page, 25);

        // Main connection button
        TextView connectButton = new TextView(this);
        connectButton.setText("⏻");
        connectButton.setTextSize(60);
        connectButton.setTextColor(WHITE);
        connectButton.setGravity(Gravity.CENTER);
        connectButton.setBackground(roundGradient(Color.rgb(0, 190, 220),
                Color.rgb(0, 125, 170), 100));
        LinearLayout.LayoutParams powerParams =
                new LinearLayout.LayoutParams(dp(155), dp(155));
        powerParams.gravity = Gravity.CENTER;
        connectButton.setElevation(dp(12));
        page.addView(connectButton, powerParams);

        TextView connectCaption = text("اتصال VPN", 19, WHITE, true);
        connectCaption.setGravity(Gravity.CENTER);
        page.addView(connectCaption);
        addSpace(page, 25);

        connectButton.setOnClickListener(v -> requestVpnPermission());

        // Server selection card
        LinearLayout serverCard = card();
        serverCard.setOrientation(LinearLayout.HORIZONTAL);
        serverCard.setGravity(Gravity.CENTER_VERTICAL);
        serverCard.setPadding(dp(17), dp(17), dp(17), dp(17));

        TextView globe = text("◎", 35, CYAN, true);
        serverCard.addView(globe);

        LinearLayout serverInfo = new LinearLayout(this);
        serverInfo.setOrientation(LinearLayout.VERTICAL);
        serverInfo.setPadding(dp(13), 0, 0, 0);

        serverInfo.addView(text("انتخاب سرور", 16, WHITE, true));
        serverInfo.addView(text("بهترین سرور در دسترس", 12, MUTED, false));

        serverCard.addView(serverInfo,
                new LinearLayout.LayoutParams(0, -2, 1));
        serverCard.addView(text("›", 30, CYAN, true));
        page.addView(serverCard);

        addSpace(page, 17);

        // Information tiles
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);

        stats.addView(statCard("سرعت دانلود", "--", "Mbps"),
                new LinearLayout.LayoutParams(0, dp(95), 1));
        stats.addView(statCard("سرعت آپلود", "--", "Mbps"),
                new LinearLayout.LayoutParams(0, dp(95), 1));
        stats.addView(statCard("زمان اتصال", "--:--", ""),
                new LinearLayout.LayoutParams(0, dp(95), 1));

        page.addView(stats);
        addSpace(page, 20);

        // Config search button
        searchButton = actionButton("جست‌وجوی خودکار کانفیگ", CYAN);
        page.addView(searchButton);
        searchButton.setOnClickListener(v -> startSearch());

        addSpace(page, 10);

        // Config validation button
        Button validateButton = actionButton("آزمایش اعتبار کانفیگ جدید", PURPLE);
        page.addView(validateButton);
        validateButton.setOnClickListener(v -> {
            status.setText("در حال اعتبارسنجی کانفیگ آزمایشی...");
            Intent test = new Intent(this, NetBridgeVpnService.class);
            test.setAction("com.netbridge.vpn.VALIDATE_CONFIG");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(test);
            } else {
                startService(test);
            }
        });

        addSpace(page, 18);

        // Results panel
        LinearLayout resultCard = card();
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setPadding(dp(15), dp(13), dp(15), dp(13));
        resultCard.addView(text("گزارش و جزئیات", 15, CYAN, true));

        results = text("هنوز گزارشی ثبت نشده است.", 12, MUTED, false);
        results.setPadding(0, dp(10), 0, dp(5));
        resultCard.addView(results);
        page.addView(resultCard);

        addSpace(page, 20);

        TextView footer = text("NetBridge  •  اتصال امن و هوشمند", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        IntentFilter filter = new IntentFilter("com.netbridge.vpn.STATUS");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(roundBorder(CARD, CYAN, 18));
        return box;
    }

    private View statCard(String heading, String value, String unit) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(8), dp(4), dp(8));

        box.addView(text(heading, 10, MUTED, false));
        box.addView(text(value, 17, WHITE, true));
        if (!unit.isEmpty()) {
            box.addView(text(unit, 10, CYAN, false));
        }
        return box;
    }

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundGradient(color, PURPLE, 15));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundGradient(int first, int second, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{first, second});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundBorder(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addSpace(LinearLayout layout, int height) {
        View space = new View(this);
        layout.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
                    if (configs == null || configs.isEmpty()) {
                        status.setText("کانفیگی پیدا نشد");
                        results.setText("منبع، کانفیگ قابل استفاده‌ای برنگرداند.");
                        searchButton.setEnabled(true);
                        return;
                    }

                    status.setText("تعداد کانفیگ‌های پیدا شده: " + configs.size());
                    String selectedConfig = configs.get(0);

                    getSharedPreferences("NetBridge", MODE_PRIVATE)
                            .edit()
                            .putString("selected_config", selectedConfig)
                            .apply();

                    results.setText(
                            "کانفیگ sing-box دریافت و ذخیره شد.\n"
                            + "تعداد کانفیگ‌های دریافتی: " + configs.size()
                            + "\n\nبرای اجرای آن، دکمه اتصال VPN را بزنید."
                    );
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
