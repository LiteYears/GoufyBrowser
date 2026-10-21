package de.baumann.browser.browser;

import static de.baumann.browser.database.UserScript.DOC_END;
import static de.baumann.browser.database.UserScript.DOC_START;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.activity.BrowserActivity;
import de.baumann.browser.database.UserScript;
import de.baumann.browser.unit.BrowserUnit;
import de.baumann.browser.unit.ScriptUnit;
import de.baumann.browser.view.NinjaToast;
import de.baumann.browser.view.NinjaWebView;

public class NinjaWebViewClient extends WebViewClient {

    private final NinjaWebView ninjaWebView;
    private final Context context;
    private final SharedPreferences sp;
    private final AdBlock adBlock;

    private final boolean white;
    private boolean enable;
    public void enableAdBlock(boolean enable) {
        this.enable = enable;
    }

    public NinjaWebViewClient(NinjaWebView ninjaWebView) {
        super();
        this.ninjaWebView = ninjaWebView;
        this.context = ninjaWebView.getContext();
        this.sp = PreferenceManager.getDefaultSharedPreferences(context);
        this.adBlock = new AdBlock(this.context);
        this.white = false;
        this.enable = true;
    }

    @Override
    public boolean onRenderProcessGone (WebView view, RenderProcessGoneDetail detail){
        String text = context.getString(R.string.app_error) + ": onRenderProcessGone: " + "crash: " + detail.didCrash();
        Log.d(context.getString(R.string.app_name),"onRenderProcessGone:" + text);
        if (BrowserActivity.isVisible) NinjaToast.show(context, text);
        view.removeAllViews();
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        ninjaWebView.isBackPressed = false;

        if (ninjaWebView.isForeground()) {
            ninjaWebView.invalidate();
        } else {
            ninjaWebView.postInvalidate();
        }

        List<UserScript> list = ScriptUnit.findScriptsToExecute(context, url, DOC_END);
        for (UserScript script : list){
            if (sp.getBoolean("sp_debug",false)) NinjaToast.show(context, script.getName());
            view.evaluateJavascript(script.getScript(),null);
        }

        if (sp.getBoolean("sp_deny_cookie_banners",false)){ //click opt-out if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageFinished();
            if (bannerBlockScript != null) {new Handler().postDelayed(() -> view.evaluateJavascript(bannerBlockScript,null), 50);}
        }

        //inject printing support via JavaScriptInterface
        view.evaluateJavascript(JavaScriptInterface.injectPrintSupport(), null);

        // Save visited HTTP/HTTPS pages to browsing history and favicon database
        if (url != null && (url.startsWith(BrowserUnit.URL_SCHEME_HTTP) || url.startsWith(BrowserUnit.URL_SCHEME_HTTPS))) {
            try {
                String pageTitle = view.getTitle();
                if (pageTitle == null || pageTitle.trim().isEmpty()) {
                    pageTitle = url;
                }
                de.baumann.browser.database.RecordAction action = new de.baumann.browser.database.RecordAction(context);
                action.open(true);
                action.addHistory(new de.baumann.browser.database.Record(pageTitle, url, System.currentTimeMillis(), false, true, true, 1));
                action.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        ninjaWebView.setStopped(false);
        ninjaWebView.resetFavicon();
        super.onPageStarted(view,url,favicon);

        List<UserScript> list = ScriptUnit.findScriptsToExecute(context, url, DOC_START);
        for (UserScript script : list){
            if (sp.getBoolean("sp_debug",false)) NinjaToast.show(context, script.getName());
            view.evaluateJavascript(script.getScript(),null);
        }

        if (sp.getBoolean("sp_deny_cookie_banners",false)){  //inject cookies if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageStarted();
            if (bannerBlockScript != null) view.evaluateJavascript(bannerBlockScript,null);
        }

        if (ninjaWebView.isFingerPrintProtection()) {
            // Block WebRTC local IP leak safely without returning null on promises
            view.evaluateJavascript(
                    "(function() {" +
                    "  if (window.webkitRTCPeerConnection) {" +
                    "    ['createOffer', 'createAnswer', 'setLocalDescription', 'setRemoteDescription'].forEach(function(method) {" +
                    "      if (webkitRTCPeerConnection.prototype && webkitRTCPeerConnection.prototype[method]) {" +
                    "        webkitRTCPeerConnection.prototype[method] = function() {" +
                    "          return Promise.reject(new DOMException('WebRTC blocked by privacy settings', 'NotAllowedError'));" +
                    "        };" +
                    "      }" +
                    "    });" +
                    "  }" +
                    "  try {" +
                    "    Object.defineProperty(window.screen, 'colorDepth', { value: 24, configurable: true });" +
                    "    Object.defineProperty(navigator, 'getBattery', { value: undefined, configurable: true });" +
                    "    Object.defineProperty(navigator, 'deviceMemory', { value: 8, configurable: true });" +
                    "    Object.defineProperty(navigator, 'hardwareConcurrency', { value: 8, configurable: true });" +
                    "    Object.defineProperty(navigator, 'sendBeacon', { value: function() { return true; }, configurable: true });" +
                    "  } catch(e) {}" +
                    "})();", null);

            // Prevent Canvas 2D fingerprinting without corrupting WebGL geometry or DOM offsetHeight/offsetWidth
            view.evaluateJavascript(
                    "(function() {\n" +
                    "  try {\n" +
                    "    const toDataURL = HTMLCanvasElement.prototype.toDataURL;\n" +
                    "    const getImageData = CanvasRenderingContext2D.prototype.getImageData;\n" +
                    "    var noisify = function (canvas, context) {\n" +
                    "      if (context && canvas.width > 0 && canvas.height > 0 && canvas.width <= 500 && canvas.height <= 500) {\n" +
                    "        const imageData = getImageData.apply(context, [0, 0, canvas.width, canvas.height]);\n" +
                    "        if (imageData && imageData.data && imageData.data.length > 4) {\n" +
                    "          imageData.data[0] = (imageData.data[0] + 1) % 256;\n" +
                    "          context.putImageData(imageData, 0, 0);\n" +
                    "        }\n" +
                    "      }\n" +
                    "    };\n" +
                    "    Object.defineProperty(HTMLCanvasElement.prototype, 'toDataURL', {\n" +
                    "      value: function () {\n" +
                    "        try { noisify(this, this.getContext('2d')); } catch(e) {}\n" +
                    "        return toDataURL.apply(this, arguments);\n" +
                    "      }, configurable: true\n" +
                    "    });\n" +
                    "  } catch(e) {}\n" +
                    "})();", null);
        }
    }

    @Override
    public void onLoadResource(WebView view, String url) {
        if (ninjaWebView.isFingerPrintProtection()) {
            view.evaluateJavascript("var pingtest=document.querySelector(\"a[ping]\"); if(pingtest!==null){pingtest.removeAttribute('ping')};", null);
        }

        if (ninjaWebView.isDesktopMode() && (view.getWidth() < 1300)) {
            view.evaluateJavascript("var vp = document.querySelector('meta[name=\"viewport\"]'); if (vp) { vp.setAttribute('content', 'width=1200'); }", null);
        }

        view.evaluateJavascript(
                "(function() {" +
                "  try {" +
                "    if (navigator.globalPrivacyControl === undefined) {" +
                "      Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false, configurable: true });" +
                "    }" +
                "    if (navigator.doNotTrack === null || navigator.doNotTrack === undefined) {" +
                "      Object.defineProperty(navigator, 'doNotTrack', { value: '1', writable: false, configurable: true });" +
                "    }" +
                "  } catch (e) {}" +
                "})();", null);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final Uri uri = request.getUrl();
        if (uri == null) return false;
        String url = uri.toString();

        // If this is a sub-frame (iframe) and standard http/https/about/blob/data, let WebView load it inside the iframe!
        if (!request.isForMainFrame()) {
            if (url.startsWith(BrowserUnit.URL_SCHEME_HTTP)
                    || url.startsWith(BrowserUnit.URL_SCHEME_HTTPS)
                    || url.startsWith(BrowserUnit.URL_SCHEME_ABOUT)
                    || url.startsWith(BrowserUnit.URL_SCHEME_BLOB)
                    || url.startsWith(BrowserUnit.URL_SCHEME_DATA)
                    || url.startsWith("javascript:")) {
                return false;
            }
        }

        // Preserve POST requests and non-GET requests natively so logins and forms never break!
        if (request.getMethod() != null && !request.getMethod().equalsIgnoreCase("GET")) {
            return false;
        }

        return handleUri(uri);
    }

    private boolean handleUri(final Uri uri) {
        if (ninjaWebView.isBackPressed != null && ninjaWebView.isBackPressed) {
            return false;
        }
        String url = uri.toString();

        if (url.startsWith(BrowserUnit.URL_SCHEME_HTTP) || url.startsWith(BrowserUnit.URL_SCHEME_HTTPS)) {
            if (sp.getBoolean("sp_invidious_redirect", false)) {
                String redirected = BrowserUnit.youtubeRedirect(url, sp.getString("sp_invidious_domain", "yewtu.be"));
                if (!redirected.equals(url)) {
                    ninjaWebView.loadUrl(redirected);
                    return true;
                }
            }
            // Update preferences for the target domain without aborting native WebView navigation (preserves cookies, referer, and session state)
            ninjaWebView.initPreferences(url);
            return false;
        } else if (url.startsWith(BrowserUnit.URL_SCHEME_FILE)
                || url.startsWith(BrowserUnit.URL_SCHEME_ABOUT)
                || url.startsWith(BrowserUnit.URL_SCHEME_CONTENT)
                || url.startsWith(BrowserUnit.URL_SCHEME_VIEW_SOURCE)
                || url.startsWith(BrowserUnit.URL_SCHEME_BLOB)
                || url.startsWith(BrowserUnit.URL_SCHEME_DATA)
                || url.startsWith("javascript:")) {
            return false;
        } else if (url.startsWith(BrowserUnit.URL_SCHEME_INTENT)) {
            Intent intent = null;
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent != null) {
                    intent.addCategory("android.intent.category.BROWSABLE");
                    intent.setComponent(null);
                    intent.setSelector(null);
                    // Check if fallback URL exists before throwing or launching
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (intent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(intent);
                        return true;
                    } else if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        this.ninjaWebView.loadUrl(fallbackUrl);
                        return true;
                    }
                }
                return true;
            } catch (URISyntaxException | ActivityNotFoundException | SecurityException e) {
                if (intent != null) {
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        this.ninjaWebView.loadUrl(fallbackUrl);
                        return true;
                    }
                }
                return true;
            }
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                return true; // Prevent WebView from showing ERR_UNKNOWN_URL_SCHEME page
            }
        }
    }

    /*
    @Override
    @SuppressWarnings("deprecation")
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        if (enable && !white && adBlock.isAd(url)) {
            return new WebResourceResponse(
                    BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                    BrowserUnit.URL_ENCODING,
                    new ByteArrayInputStream("".getBytes())
            );
        }
        return super.shouldInterceptRequest(view, url);
    }*/

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

        if (ninjaWebView.isFingerPrintProtection()){
            String url = request.getUrl().toString();
            if (url.contains("api.fpjs.pro") ||
                    url.contains("api.fpjs.io") ||
                    url.contains("openfpcdn.io") ||
                    url.contains("fpjscdn.net") ||
                    url.contains("fingerprint.com")){
                Log.d("WebViewBlock", "BLOCKED FingerprintJS request: " + url);
                return new WebResourceResponse("text/javascript", "UTF-8", null);
            }
        }
/*
        ((Activity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (request.getUrl().toString().contains("utm_")) NinjaToast.show(context,"Tracking URL utm_");
            }
        });*/

        if (ninjaWebView.getBlockNetworkVideo()){
            String extension = MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString());
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType!= null && mimeType.startsWith("video")) {
                return new WebResourceResponse(
                        BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                        BrowserUnit.URL_ENCODING,
                        new ByteArrayInputStream("".getBytes())
                );
            }

        }

        if (enable && !white && adBlock.isAd(request.getUrl().toString())) {
            return new WebResourceResponse(
                    BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                    BrowserUnit.URL_ENCODING,
                    new ByteArrayInputStream("".getBytes())
            );
        }

        if (sp.getBoolean("sp_invidious_redirect", false)) {
            String redirect = BrowserUnit.youtubeRedirect(String.valueOf(request.getUrl()), sp.getString("sp_invidious_domain", "yewtu.be"));
            if (!redirect.equals(String.valueOf(request.getUrl()))) {
                CustomWebResourceRequest newRequest = new CustomWebResourceRequest(Uri.parse(redirect), request.getMethod(), request.getRequestHeaders());
                return super.shouldInterceptRequest(view, newRequest);
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onFormResubmission(WebView view, @NonNull final Message doNotResend, final Message resend) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(R.string.dialog_content_resubmission);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> resend.sendToTarget());
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setOnCancelListener(dialog1 -> doNotResend.sendToTarget());
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
    }

    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        String message = "\"SSL Certificate error.\"";
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                message = "\"Certificate authority is not trusted.\"";
                break;
            case SslError.SSL_EXPIRED:
                message = "\"Certificate has expired.\"";
                break;
            case SslError.SSL_IDMISMATCH:
                message = "\"Certificate Hostname mismatch.\"";
                break;
            case SslError.SSL_NOTYETVALID:
                message = "\"Certificate is not yet valid.\"";
                break;
            case SslError.SSL_DATE_INVALID:
                message = "\"Certificate date is invalid.\"";
                break;
            case SslError.SSL_INVALID:
                message = "\"Certificate is invalid.\"";
                break;
        }
        String text = message + " - " + context.getString(R.string.dialog_content_ssl_error);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(text);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> handler.proceed());
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setOnCancelListener(dialog1 -> handler.cancel());
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, @NonNull final HttpAuthHandler handler, String host, String realm) {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_http_auth, null);

        EditText pass_userNameET = dialogView.findViewById(R.id.edit_userName);
        EditText pass_userPWET = dialogView.findViewById(R.id.edit_PW);
        TextView title = dialogView.findViewById(R.id.title);
        title.setText(host);

        builder.setView(dialogView);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
            String user = pass_userNameET.getText().toString().trim();
            String pass = pass_userPWET.getText().toString().trim();
            handler.proceed(user, pass);
            dialog.cancel();
        });
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
        dialog.setOnCancelListener(dialog1 -> {
            handler.cancel();
            dialog1.cancel();
        });
    }
}
