package com.mixpanel.android.mpmetrics;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
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
        webView.setWebChromeClient(new WebChromeClient());

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);

        webView.loadUrl(getIntent().getExtras().getString("uri"));
    }
}
