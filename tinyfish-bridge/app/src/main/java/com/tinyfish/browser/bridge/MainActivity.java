package com.tinyfish.browser.bridge;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.util.Base64;
import java.security.SecureRandom;
import java.util.List;

public final class MainActivity extends Activity {
    static final String PREFS = "tinyfish_agent_bridge";
    static final String TOKEN_KEY = "api_token";
    private TextView status;
    private TextView tokenView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ensureToken();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(26), dp(22), dp(24));
        page.setBackgroundColor(0xFFF7F8FA);
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("TinyFish Control");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF102033);
        page.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Secure local browser automation bridge for TinyFish Browser. Enable its Accessibility service, then connect an agent over an authenticated local API.");
        intro.setTextSize(15);
        intro.setTextColor(0xFF465568);
        intro.setPadding(0, dp(10), 0, dp(18));
        page.addView(intro);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF102033);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(0xFFE7ECF3);
        page.addView(status, matchWrap());

        addButton(page, "1. Enable Accessibility service", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        addButton(page, "2. Open a page in TinyFish Browser", v -> openBrowser());

        TextView tokenTitle = new TextView(this);
        tokenTitle.setText("API bearer token");
        tokenTitle.setTextSize(17);
        tokenTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tokenTitle.setTextColor(0xFF102033);
        tokenTitle.setPadding(0, dp(20), 0, dp(8));
        page.addView(tokenTitle);

        tokenView = new TextView(this);
        tokenView.setTextIsSelectable(true);
        tokenView.setTextSize(14);
        tokenView.setTextColor(0xFF102033);
        tokenView.setPadding(dp(12), dp(12), dp(12), dp(12));
        tokenView.setBackgroundColor(0xFFFFFFFF);
        page.addView(tokenView, matchWrap());

        addButton(page, "Copy token", v -> copy("TinyFish API token", tokenView.getText().toString()));
        addButton(page, "Regenerate token", v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TOKEN_KEY, newToken()).apply();
            refresh();
        });

        TextView endpoint = new TextView(this);
        endpoint.setText("API: http://127.0.0.1:8765/v1/\n\nFrom a computer with Android platform-tools, forward the local port:\nadb forward tcp:8765 tcp:8765\n\nAll API calls require: Authorization: Bearer <token>\n\nEndpoints:\nGET /health\nGET /snapshot\nPOST /navigate   { \"url\": \"https://example.com\" }\nPOST /action     { \"action\": \"click\", \"ref\": \"e1\", \"snapshot_id\": \"...\" }\n\nActions: click, fill, type, check, uncheck, scroll_down, scroll_up, back, tap, press, wait, read. Take a new snapshot before acting; stale references are rejected.");
        endpoint.setTextSize(14);
        endpoint.setTextColor(0xFF465568);
        endpoint.setPadding(0, dp(18), 0, dp(12));
        page.addView(endpoint);

        addButton(page, "Copy ADB port-forward command", v ->
                copy("ADB command", "adb forward tcp:8765 tcp:8765"));

        TextView privacy = new TextView(this);
        privacy.setText("Security: the control server listens only on device loopback (127.0.0.1), not the Wi-Fi/LAN interface. Keep the token private. Page text returned by snapshots can contain untrusted website content.");
        privacy.setTextSize(13);
        privacy.setTextColor(0xFF65758B);
        privacy.setPadding(0, dp(12), 0, dp(4));
        page.addView(privacy);

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (status != null) {
            boolean enabled = isServiceEnabled();
            status.setText(enabled
                    ? "Accessibility: ENABLED\nBridge API should be available at 127.0.0.1:8765."
                    : "Accessibility: NOT ENABLED\nTap the first button, select TinyFish Agent Control, and enable it.");
        }
        if (tokenView != null) {
            tokenView.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(TOKEN_KEY, ""));
        }
    }

    private boolean isServiceEnabled() {
        String wanted = new ComponentName(this, TinyFishAccessibilityService.class).flattenToString();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String item = splitter.next();
            if (wanted.equalsIgnoreCase(item) || item.toLowerCase().contains(TinyFishAccessibilityService.class.getName().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void ensureToken() {
        String old = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TOKEN_KEY, "");
        if (old.isEmpty()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TOKEN_KEY, newToken()).apply();
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private void openBrowser() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        intent.setPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) startActivity(launch);
        }
    }

    private void copy(String label, String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        status.setText("Copied to clipboard. Keep the token private.");
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(10);
        parent.addView(button, lp);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
