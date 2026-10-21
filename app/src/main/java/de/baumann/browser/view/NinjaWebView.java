package de.baumann.browser.view;

import static androidx.webkit.WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_DISABLED;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

import android.util.AttributeSet;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

import de.baumann.browser.browser.*;
import de.baumann.browser.R;
import de.baumann.browser.database.FaviconHelper;
import de.baumann.browser.database.Record;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.unit.BrowserUnit;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class NinjaWebView extends WebView implements AlbumController {

    private OnScrollChangeListener onScrollChangeListener;
    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onScrollChanged(int l, int t, int old_l, int old_t) {
        super.onScrollChanged(l, t, old_l, old_t);
        if (onScrollChangeListener != null) {
            onScrollChangeListener.onScrollChange(t, old_t);
        }
    }

    public void setOnScrollChangeListener(OnScrollChangeListener onScrollChangeListener) {
        this.onScrollChangeListener = onScrollChangeListener;
    }

    public boolean isBookmark() {
        return isBookmark;
    }

    public void setIsBookmark(boolean b) {
        isBookmark = b;
    }

    public interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param scrollY    Current vertical scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        void onScrollChange(int scrollY, int oldScrollY);
    }

    private Context context;
    private boolean desktopMode;
    private boolean fingerPrintProtection;
    private boolean javaScriptInherited;
    private boolean domStorageInherited;
    private boolean loadImagesInherited;
    private boolean adBlockEnabled;
    private boolean stopped;
    private String oldDomain;
    private boolean isBookmark;
    private AlbumItem album;
    private AlbumController predecessor=null;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private boolean blockNetworkVideo;

    public Boolean isBackPressed;
    public void setIsBackPressed(Boolean isBackPressed) {
        this.isBackPressed = isBackPressed;
    }

    private Javascript javaHosts;
    private DOM DOMHosts;
    private Cookie cookieHosts;
    private Bitmap favicon;
    private SharedPreferences sp;

    private boolean foreground;
    public boolean isForeground() {
        return foreground;
    }
    private BrowserController browserController = null;
    public BrowserController getBrowserController() {
        return browserController;
    }

    public void setBrowserController(BrowserController browserController) {
        this.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    public NinjaWebView(Context context) {
        super(context);
        this.context = context;
        this.foreground = false;
        this.desktopMode=false;
        this.isBackPressed = false;

        sp = PreferenceManager.getDefaultSharedPreferences(context);
        this.fingerPrintProtection=sp.getBoolean("sp_fingerPrintProtection",true);
        this.javaScriptInherited =sp.getBoolean("sp_javascript", true);
        this.loadImagesInherited = sp.getBoolean("sp_images",true);
        getSettings().setJavaScriptEnabled(javaScriptInherited);
        this.domStorageInherited =sp.getBoolean("sp_dom", true);
        getSettings().setDomStorageEnabled(domStorageInherited);
        this.adBlockEnabled=sp.getBoolean("sp_ad_block", true);

        this.stopped=false;
        this.oldDomain="";
        this.isBookmark=false;
        this.javaHosts = new Javascript(this.context);
        this.DOMHosts = new DOM(this.context);
        this.cookieHosts = new Cookie(this.context);
        this.album = new AlbumItem(this.context, this, this.browserController);
        this.webViewClient = new NinjaWebViewClient(this);
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener(this.context, this);
        initWebView();
        initAlbum();
    }

    private synchronized void initWebView() {
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public synchronized void initPreferences(String url) {

        sp = PreferenceManager.getDefaultSharedPreferences(context);
        WebSettings webSettings = getSettings();

        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, sp.getBoolean("sp_algo_dark",true));
        }

        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            WebSettingsCompat.setWebViewMediaIntegrityApiStatus(webSettings, new WebViewMediaIntegrityApiStatusConfig(new WebViewMediaIntegrityApiStatusConfig.Builder(WEBVIEW_MEDIA_INTEGRITY_API_DISABLED)));
        }

        addJavascriptInterface(new JavaScriptInterface(context, this), "NinjaWebViewJS");
        String userAgent = getUserAgent(desktopMode);
        webSettings.setSafeBrowsingEnabled(true);

        webSettings.setUserAgentString(userAgent);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(domStorageInherited);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(javaScriptInherited);
        webViewClient.enableAdBlock(adBlockEnabled);
        try {
            webSettings.setTextZoom(Integer.parseInt(Objects.requireNonNull(sp.getString("sp_fontSize", "100"))));
        } catch (NumberFormatException ignored) {
            webSettings.setTextZoom(100);
        }

        if (BrowserUnit.isUnmeteredConnection(context)) {
            webSettings.setBlockNetworkImage(false);
        } else {
            webSettings.setBlockNetworkImage(!loadImagesInherited);
        }
        blockNetworkVideo = webSettings.getBlockNetworkImage();

        webSettings.setGeolocationEnabled(sp.getBoolean("sp_location", false));
        webSettings.setMediaPlaybackRequiresUserGesture(false); // Allow modern HTML5 audio/video and streaming sites to play smoothly

        if (sp.getBoolean("sp_autofill", true)) {
            this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        } else {
            this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        if (url != null) {
            // Check if url is a bookmark and apply settings accordingly
            RecordAction action = new RecordAction(context);
            action.open(false);
            List<Record> list = action.listEntries((Activity) context);
            action.close();
            for (Record record : list) {
                if (record.getURL().equalsIgnoreCase(url)) {
                    if (record.getDesktopMode() != isDesktopMode()) toggleDesktopMode(false);
                    setJavaScript(record.getJavascript());
                    setDomStorage(record.getDomStorage());
                    setIsBookmark(true);
                    setOldDomain(url);
                    break;
                }
            }

            CookieManager manager = CookieManager.getInstance();
            if (cookieHosts.isWhite(url) || sp.getBoolean("sp_cookies", true)) {
                manager.setAcceptCookie(true);
                // Allow third-party cookies when cookies are enabled or domain is whitelisted so SSO, OAuth, and embedded players work reliably
                manager.setAcceptThirdPartyCookies(this, !fingerPrintProtection || cookieHosts.isWhite(url) || sp.getBoolean("sp_cookies", true));
                manager.getCookie(url);
            } else {
                manager.setAcceptCookie(false);
                manager.setAcceptThirdPartyCookies(this, false);
            }
            String domain = "";
            try {
                domain = AdBlock.getDomain(url);
            } catch (URISyntaxException e) {
                setJavaScript(javaHosts.isWhite(url) || javaScriptInherited);
                setDomStorage(DOMHosts.isWhite(url) || domStorageInherited);
            }

            // Do not change setting if staying within same domain
            if (domain != null && !oldDomain.equals(domain)) {
                setJavaScript(javaHosts.isWhite(url) || javaScriptInherited);
                setDomStorage(DOMHosts.isWhite(url) || domStorageInherited);
                setIsBookmark(false);
            }

            setOldDomain(url);
        }

        if (sp.getBoolean("userProxy", false)) {
            String proxyRule = sp.getString("sp_proxy", "");
            if (proxyRule != null && !proxyRule.trim().isEmpty()) {
                setProxy(proxyRule.trim());
            }
        }
    }

    private void setProxy(String rule) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyConfig proxyConfig = new ProxyConfig.Builder().addProxyRule(rule).build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, command -> {}, () -> {});
            }
        } catch (IllegalArgumentException e) {
            Toast.makeText(context, R.string.app_error, Toast.LENGTH_SHORT).show();
        }
    }

    public void setOldDomain(String url){
        String domain = null;
        try {
            domain = AdBlock.getDomain(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (domain == null) oldDomain = "";
        else oldDomain = domain;
    }

    public void setJavaScript(boolean value){
        WebSettings webSettings = this.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(value);
        webSettings.setJavaScriptEnabled(value);
    }

    public void setJavaScriptInherited(boolean value){  //this method only sets the default value of the tab!
        if (!javaHosts.isWhite(getUrl()) && !isBookmark) javaScriptInherited = value;
    }

    public void setDomStorage(boolean value){
        WebSettings webSettings = this.getSettings();
        webSettings.setDomStorageEnabled(value);
    }

    public void setDomStorageInherited(boolean value){
        if (!DOMHosts.isWhite(getUrl()) && !isBookmark) domStorageInherited = value;
    }

    public void setLoadImagesInherited(boolean value){
        loadImagesInherited = value;
        if (BrowserUnit.isUnmeteredConnection(context)) {getSettings().setBlockNetworkImage(false);}  //in unmetered Networks (usually WIFI) always load images
        else getSettings().setBlockNetworkImage(!loadImagesInherited); //otherwise check setting
        blockNetworkVideo = getSettings().getBlockNetworkImage(); //if images are blocked, videos of any kind are not allowed either
    }
    private synchronized void initAlbum() {
        album.setAlbumTitle(context.getString(R.string.app_name));
        album.setBrowserController(browserController);
    }

    public synchronized HashMap<String, String> getRequestHeaders(String url) {
        HashMap<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("DNT", "1");
        // Server-side detection for GlobalPrivacyControl
        requestHeaders.put("Sec-GPC", "1");
        if (desktopMode) {
            requestHeaders.put("Sec-CH-UA-Mobile", "?0");
            requestHeaders.put("Sec-CH-UA-Platform", "\"Linux\"");
        } else {
            requestHeaders.put("Sec-CH-UA-Mobile", "?1");
            requestHeaders.put("Sec-CH-UA-Platform", "\"Android\"");
        }
        if (getUrl() != null && url.startsWith(BrowserUnit.URL_SCHEME_HTTPS)) {
            try {
                if (AdBlock.getDomain(getUrl()).endsWith(AdBlock.getDomain(url)) || AdBlock.getDomain(url).endsWith(AdBlock.getDomain(getUrl()))) {
                    requestHeaders.put("Referer", getUrl()); // add Referer when within same domain, parent domain, or subdomain and protocol is https://
                }
            } catch (URISyntaxException ignored) { }
        }
        return requestHeaders;
    }

    @Override
    public synchronized void stopLoading(){
        stopped=true;
        super.stopLoading();
    }

    @Override
    public synchronized void reload(){
        stopped=false;
        super.reload();
    }

    @Override
    public synchronized void loadUrl(String url) {
        if (url == null) return;
        String wrappedUrl = BrowserUnit.queryWrapper(context, url.trim());
        if (sp.getBoolean("sp_invidious_redirect", false)) {
            wrappedUrl = BrowserUnit.youtubeRedirect(wrappedUrl, sp.getString("sp_invidious_domain", "yewtu.be"));
        }
        initPreferences(wrappedUrl);
        InputMethodManager imm = (InputMethodManager) this.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        }
        resetFavicon();
        stopped = false;
        super.loadUrl(wrappedUrl, getRequestHeaders(wrappedUrl));
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title);
        ImageView icon = (ImageView) getAlbumView().findViewById(R.id.faviconView);
        icon.setVisibility(VISIBLE);
        if (getFavicon() != null) icon.setImageBitmap(getFavicon());
        else icon.setImageResource(R.drawable.ic_globe);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate(this);
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate(this);
    }

    public synchronized void updateTitle(int progress) {
        if (foreground && !stopped) {
            browserController.updateProgress(progress);
        } else if (foreground) {
            browserController.updateProgress(BrowserUnit.LOADING_STOPPED);
        }
        if (isLoadFinish() && !stopped) {
            browserController.updateAutoComplete();
        }
    }

    public synchronized void updateTitle(String title) {
        album.setAlbumTitle(title);
    }

    public synchronized void updateFavicon() {
        ImageView icon = (ImageView) getAlbumView().findViewById(R.id.faviconView);
        icon.setVisibility(VISIBLE);
        if (getFavicon() != null) icon.setImageBitmap(getFavicon());
        else icon.setImageResource(R.drawable.ic_globe);
    }

    @Override
    public synchronized void destroy() {
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        super.destroy();
    }

    public boolean isLoadImagesInherited() { return loadImagesInherited; }

    public boolean isLoadFinish() {
        return getProgress() >= BrowserUnit.PROGRESS_MAX;
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    public boolean isFingerPrintProtection() {
        return fingerPrintProtection;
    }

    public boolean isAdBlockEnabled() {
        return adBlockEnabled;
    }

    public String getUserAgent(boolean desktopMode) {
        String ownUserAgent = sp.getString("sp_userAgent", "");
        if (ownUserAgent != null && !ownUserAgent.isEmpty() && sp.getBoolean("userAgentSwitch", false)) {
            return ownUserAgent;
        }

        if (desktopMode) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        }

        String defaultUA = WebSettings.getDefaultUserAgent(context);
        if (defaultUA != null && !defaultUA.isEmpty()) {
            String mobileUA = defaultUA.replace("; wv)", ")").replace("; wv", "").replace("Version/4.0 ", "");
            if (!mobileUA.contains("Mobile")) {
                if (mobileUA.contains("Safari")) {
                    mobileUA = mobileUA.replace("Safari", "Mobile Safari");
                } else {
                    mobileUA = mobileUA + " Mobile Safari/537.36";
                }
            }
            return mobileUA;
        }
        return "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    }

    public void toggleDesktopMode(boolean reload) {
        desktopMode = !desktopMode;
        String newUserAgent = getUserAgent(desktopMode);
        getSettings().setUserAgentString(newUserAgent);
        getSettings().setUseWideViewPort(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setSupportZoom(true);

        if (reload) { reload(); }
    }

    public void toggleAllowFingerprint (boolean reload) {
        fingerPrintProtection = !isFingerPrintProtection();
        if (reload) { reload();}
    }

    public void toggleAdblockEnabled(boolean reload) {
        adBlockEnabled = !isAdBlockEnabled();
        webViewClient.enableAdBlock(adBlockEnabled);
        if (reload) { reload();}
    }

    public void resetFavicon(){this.favicon=null; updateFavicon();}

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
        updateFavicon();
        if (isLoadFinish()) {
            //Save faviconView for existing bookmarks, but only if loading is finished to avoid "wrong" favicons
            FaviconHelper faviconHelper = new FaviconHelper(context);
            RecordAction action = new RecordAction(context);
            action.open(false);
            List<Record> list;
            list = action.listBookmark(context, false, 0);
            action.close();
            for (Record listItem : list) {
                if (listItem.getURL().equals(getUrl())) {
                    if (faviconHelper.getFavicon(listItem.getURL()) == null)
                        faviconHelper.addFavicon(getUrl(), getFavicon());
                }
            }
        }
    }

    @Nullable
    @Override
    public Bitmap getFavicon() {
        return favicon;
    }

    public void setStopped(boolean stopped){this.stopped=stopped;}

    public AlbumController getPredecessor(){ return predecessor;}

    public void setPredecessor(AlbumController predecessor) {
        this.predecessor = predecessor;
    }

    public boolean getBlockNetworkVideo(){ return this.blockNetworkVideo; }

    public String getSettingsBackup(){
        String settings = String.format("%s%s%s%s%s%s%s%s",
                isBookmark ? "1" : "0",
                isAdBlockEnabled() ? "1" : "0",
                isFingerPrintProtection() ? "1" : "0",
                isDesktopMode() ? "1" : "0",
                getSettings().getDomStorageEnabled() ? "1" : "0",
                domStorageInherited ? "1" : "0",
                getSettings().getJavaScriptEnabled() ? "1" : "0",
                javaScriptInherited ? "1" : "0");
        return settings;
    }

    public void restoreSettings(String settings, String url){
        isBookmark = settings.charAt(0) == '1';
        if (isBookmark) setOldDomain(url);
        adBlockEnabled = settings.charAt(1) == '1';
        fingerPrintProtection = settings.charAt(2) == '1';
        desktopMode = settings.charAt(3) == '1';
        getSettings().setDomStorageEnabled(settings.charAt(4) == '1');
        domStorageInherited = settings.charAt(5) == '1';
        getSettings().setJavaScriptEnabled(settings.charAt(6) == '1');
        javaScriptInherited = settings.charAt(7) == '1';
    }
}
