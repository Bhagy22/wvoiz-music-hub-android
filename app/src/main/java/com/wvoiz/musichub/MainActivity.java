package com.wvoiz.musichub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.security.MessageDigest;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout customContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static final String HOME_URL = "https://wvoiz-music-hub.edgeone.dev/";
    private static final String HOME_HOST = "wvoiz-music-hub.edgeone.dev";

    // SHA-256 digest of the official Wvoiz release signing certificate.
    // If the app was re-signed by anyone else (a tampered clone), the digest
    // will not match and the app refuses to load the site.
    private static final String OFFICIAL_CERT_SHA256 =
            "1dceeea2b706b28ce4a28101446d6abab5fd8bca2c833bd1240a0eba1b7b3589";

    /** True when the app carries the official signature (or it cannot be read). */
    private boolean hasOfficialSignature() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = getPackageManager().getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signingInfo = info.signingInfo;
                if (signingInfo == null) return true;
                Signature[] sigs = signingInfo.hasMultipleSigners()
                        ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
                if (sigs == null || sigs.length == 0) return true;
                return OFFICIAL_CERT_SHA256.equals(sha256Hex(sigs[0].toByteArray()));
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNATURES);
                if (info.signatures == null || info.signatures.length == 0) return true;
                return OFFICIAL_CERT_SHA256.equals(sha256Hex(info.signatures[0].toByteArray()));
            }
        } catch (Exception e) {
            return true; // never lock the real app out over a read failure
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private boolean isHomeHost(Uri url) {
        String host = url.getHost();
        return host != null && (host.equals(HOME_HOST) || host.endsWith("." + HOME_HOST));
    }

    private void openExternally(Uri url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Anti-clone guard: a re-signed copy is not the official app.
        if (!hasOfficialSignature()) {
            new AlertDialog.Builder(this)
                    .setTitle("Not the official app")
                    .setMessage("This copy of Wvoiz Music Hub was not signed by Wvoiz "
                            + "and may have been tampered with. Please get the official "
                            + "app from Wvoiz directly.")
                    .setCancelable(false)
                    .setPositiveButton("Close", (dialog, which) -> finish())
                    .show();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        customContainer = new FrameLayout(this);
        customContainer.setVisibility(View.GONE);
        root.addView(customContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Safety hardening: the site needs JS + DOM storage only. Everything
        // a web page could use to reach the phone stays off.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSavePassword(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setSafeBrowsingEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setBackgroundColor(0xFF0A0B0F);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isHomeHost(url)) {
                    return false; // stay inside the app
                }
                openExternally(url); // Telegram, YouTube watch links, etc.
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // Links with target=_blank (e.g. "Full song on YouTube") arrive here:
            // route our own host back into the main view, everything else out to its app/browser.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        Uri url = request.getUrl();
                        if (isHomeHost(url)) {
                            webView.loadUrl(url.toString());
                        } else {
                            openExternally(url);
                        }
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            // HTML5 fullscreen (YouTube player fullscreen button).
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                customView = view;
                customViewCallback = callback;
                customContainer.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                customContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    customContainer.removeView(customView);
                    customView = null;
                }
                customContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.getWebChromeClient().onHideCustomView();
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // No webView.onPause() on purpose: music and radio keep playing when the
    // app is backgrounded, like a normal music app.

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
