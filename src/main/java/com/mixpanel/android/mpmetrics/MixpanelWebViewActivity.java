package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.mixpanel.android.R;

public class MixpanelWebViewActivity extends Activity {

    protected final String LOGTAG = "MixpanelAPI.MixpanelWebViewActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.com_mixpanel_android_activity_webview);
        WebView webView = findViewById(R.id.com_mixpanel_android_activity_webview_view);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(getIntent().getExtras().getString("uri"));
    }
}
