package de.baumann.browser.unit;

import static de.baumann.browser.unit.HelperUnit.hideSoftKeyboard;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ShortcutManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.baumann.browser.browser.DataURIParser;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.R;
import de.baumann.browser.browser.JavaScriptInterface;
import de.baumann.browser.view.NinjaToast;

public class BrowserUnit {

    public static final int PROGRESS_MAX = 100;
    public static final int LOADING_STOPPED = 101;  //Must be > PROGRESS_MAX !
    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";

    private static final String SEARCH_ENGINE_GOOGLE = "https://www.google.com/search?q=";
    private static final String SEARCH_ENGINE_DUCKDUCKGO = "https://duckduckgo.com/?q=";
    private static final String SEARCH_ENGINE_STARTPAGE = "https://startpage.com/do/search?query=";
    private static final String SEARCH_ENGINE_BING = "https://www.bing.com/search?q=";
    private static final String SEARCH_ENGINE_BAIDU = "https://www.baidu.com/s?wd=";
    private static final String SEARCH_ENGINE_QWANT = "https://www.qwant.com/?q=";
    private static final String SEARCH_ENGINE_ECOSIA = "https://www.ecosia.org/search?q=";
    private static final String SEARCH_ENGINE_Metager = "https://metager.org/meta/meta.ger3?eingabe=";

    private static final String SEARCH_ENGINE_STARTPAGE_DE = "https://startpage.com/do/search?lui=deu&language=deutsch&query=";
    private static final String SEARCH_ENGINE_SEARX = "https://searx.be/?q=";
    private static final String SEARCH_ENGINE_BRAVE = "https://search.brave.com/search?q=";

    public static final String URL_ENCODING = "UTF-8";
    public static final String URL_ABOUT_BLANK = "about:blank";
    public static final String URL_HOME = "file:///android_asset/home.html";
    public static final String URL_HELP = "file:///android_asset/help.html";
    public static final String URL_SCHEME_ABOUT = "about:";
    public static final String URL_SCHEME_MAIL_TO = "mailto:";
    public static final String URL_SCHEME_FILE = "file://";
    public static final String URL_SCHEME_CONTENT = "content://";
    public static final String URL_SCHEME_HTTPS = "https://";
    public static final String URL_SCHEME_HTTP = "http://";
    private static final String URL_SCHEME_FTP = "ftp://";
    public static final String URL_SCHEME_INTENT = "intent://";
    public static final String URL_SCHEME_VIEW_SOURCE = "view-source:";
    public static final String URL_SCHEME_BLOB = "blob:";

    public static final String URL_SCHEME_TEL = "tel:";
    public static final String URL_SCHEME_SMS = "sms:";
    public static final String URL_SCHEME_GEO = "geo:";
    public static final String URL_SCHEME_MARKET = "market:";
    public static final String URL_SCHEME_DATA = "data:";

    public static boolean isURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String raw = url.trim();
        if (raw.contains(" ")) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.startsWith(URL_ABOUT_BLANK)
                || lower.startsWith(URL_SCHEME_ABOUT)
                || lower.startsWith(URL_SCHEME_MAIL_TO)
                || lower.startsWith(URL_SCHEME_FILE)
                || lower.startsWith(URL_SCHEME_CONTENT)
                || lower.startsWith(URL_SCHEME_HTTP)
                || lower.startsWith(URL_SCHEME_HTTPS)
                || lower.startsWith(URL_SCHEME_VIEW_SOURCE)
                || lower.startsWith(URL_SCHEME_FTP)
                || lower.startsWith(URL_SCHEME_INTENT)
                || lower.startsWith(URL_SCHEME_BLOB)
                || lower.startsWith(URL_SCHEME_DATA)
                || lower.startsWith(URL_SCHEME_TEL)
                || lower.startsWith(URL_SCHEME_SMS)
                || lower.startsWith(URL_SCHEME_GEO)
                || lower.startsWith("javascript:")) {
            return true;
        }

        // Support localhost, IPv4, IPv6, and any modern TLD (2-63 chars including IDN punycode xn--)
        if (lower.startsWith("localhost") || lower.matches("^localhost(:[0-9]{1,5})?(/.*)?$")) {
            return true;
        }

        if (android.util.Patterns.WEB_URL.matcher(raw).matches()) {
            return true;
        }

        String regex = "^((ftp|http|https|intent)?://)?"
                + "(([0-9a-z_!~*'().&=+$%-]+: )?[0-9a-z_!~*'().&=+$%-]+@)?"
                + "(([0-9]{1,3}\\.){3}[0-9]{1,3}"
                + "|\\[?[0-9a-f:]+\\]?"
                + "|([0-9a-z_!~*'()-]+\\.)*([0-9a-z][0-9a-z-]{0,61})?[0-9a-z]\\.[a-z0-9-]{2,63})"
                + "(:[0-9]{1,5})?"
                + "((/?)|(/[0-9a-z_!~*'().;?:@&=+$,%#-]*)+/?)$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return pattern.matcher(raw).matches();
    }

    public static String queryWrapper(Context context, String query) {
        if (query == null) {
            return URL_HOME;
        }
        query = query.trim();
        if (query.isEmpty()) {
            return URL_HOME;
        }

        if (isURL(query)) {
            String lower = query.toLowerCase(Locale.ROOT);
            if (lower.startsWith(URL_SCHEME_ABOUT)
                    || lower.startsWith(URL_SCHEME_MAIL_TO)
                    || lower.startsWith(URL_SCHEME_FILE)
                    || lower.startsWith(URL_SCHEME_CONTENT)
                    || lower.startsWith(URL_SCHEME_VIEW_SOURCE)
                    || lower.startsWith(URL_SCHEME_BLOB)
                    || lower.startsWith(URL_SCHEME_DATA)
                    || lower.startsWith(URL_SCHEME_INTENT)
                    || lower.startsWith(URL_SCHEME_TEL)
                    || lower.startsWith(URL_SCHEME_SMS)
                    || lower.startsWith(URL_SCHEME_GEO)
                    || lower.startsWith("javascript:")) {
                return query;
            }

            if (!query.contains("://")) {
                if (lower.startsWith("localhost") || lower.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}.*")) {
                    query = URL_SCHEME_HTTP + query;
                } else {
                    query = URL_SCHEME_HTTPS + query;
                }
            }

            return query;
        }

        String encodedQuery = Uri.encode(query);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String customSearchEngine = sp.getString("sp_search_engine_custom", "");

        if (sp.getBoolean("searchEngineSwitch", false) && !customSearchEngine.isEmpty()) {
            return customSearchEngine + encodedQuery;
        } else {
            final int i = Integer.parseInt(Objects.requireNonNull(sp.getString("sp_search_engine", "4")));
            switch (i) {
                case 1:
                    return SEARCH_ENGINE_STARTPAGE_DE + encodedQuery;
                case 2:
                    return SEARCH_ENGINE_BAIDU + encodedQuery;
                case 3:
                    return SEARCH_ENGINE_BING + encodedQuery;
                case 4:
                    return SEARCH_ENGINE_DUCKDUCKGO + encodedQuery;
                case 5:
                    return SEARCH_ENGINE_GOOGLE + encodedQuery;
                case 6:
                    return SEARCH_ENGINE_SEARX + encodedQuery;
                case 7:
                    return SEARCH_ENGINE_QWANT + encodedQuery;
                case 8:
                    return SEARCH_ENGINE_ECOSIA + encodedQuery;
                case 9:
                    return SEARCH_ENGINE_Metager + encodedQuery;
                case 10:
                    return SEARCH_ENGINE_BRAVE + encodedQuery;
                default:
                    return SEARCH_ENGINE_STARTPAGE + encodedQuery;
            }
        }
    }

    public static void download(final Context context, final WebView webview, final String url, final String contentDisposition, final String mimeType) {
        String filename = HelperUnit.guessFileName(url, contentDisposition, mimeType); // replaces URLUtil.guessFileName(url, contentDisposition, mimeType)
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_edit_extension, null);
        final EditText editTitle = dialogView.findViewById(R.id.dialog_edit_1);
        final EditText editExtension = dialogView.findViewById(R.id.dialog_edit_2);

        if (!filename.contains(".")) filename = filename + ".bin";  //if filename has no extension offer .bin as fall back
        editTitle.setText(filename.substring(0,filename.lastIndexOf(".")));
        String extension = filename.substring(filename.lastIndexOf("."));
        if(extension.length() <= 8) {
            editExtension.setText(extension);
        }
        builder.setView(dialogView);
        builder.setTitle(R.string.dialog_title_download);

        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {

            String title = editTitle.getText().toString().trim();
            String extension1 = editExtension.getText().toString().trim();
            String finalFilename = title + extension1;
            hideSoftKeyboard(editExtension, context);
            if (title.isEmpty() || extension1.isEmpty() || !extension1.startsWith(".")) {
                NinjaToast.show(context, context.getString(R.string.toast_input_empty));
            } else {
                try {
                    Activity activity = (Activity) context;
                    if (url.startsWith(URL_SCHEME_BLOB)) {
                        if (BackupUnit.checkPermissionStorage(context)) {
                            webview.evaluateJavascript(JavaScriptInterface.getBase64StringFromBlobUrl(url, finalFilename, mimeType), null);
                        } else BackupUnit.requestPermission(activity);
                    } else if (url.startsWith("data:")) {
                        DataURIParser dataURIParser = new DataURIParser(url);
                        if (BackupUnit.checkPermissionStorage(context)) {
                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), finalFilename);
                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(dataURIParser.getImagedata());
                            fos.flush();
                            fos.close();
                            HelperUnit.openDialogDownloads(context);
                        } else BackupUnit.requestPermission(activity);
                    } else {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                        CookieManager cookieManager = CookieManager.getInstance();
                        String cookie = cookieManager.getCookie(url);
                        request.addRequestHeader("Cookie", cookie);
                        request.addRequestHeader("Accept", "text/html, application/xhtml+xml, *" + "/" + "*");
                        request.addRequestHeader("Accept-Language", "en-US,en;q=0.7,he;q=0.3");
                        request.addRequestHeader("Referer", url);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setTitle(finalFilename);
                        request.setMimeType(mimeType);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFilename);
                        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        assert manager != null;
                        if (BackupUnit.checkPermissionStorage(context)) {
                            manager.enqueue(request);
                        } else {
                            BackupUnit.requestPermission(activity);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error Downloading File: " + e.toString());
                    Toast.makeText(context, context.getString(R.string.app_error) + e.toString().substring(e.toString().indexOf(":")), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
    }

    public static void clearCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception exception) {
            Log.w("browser", "Error clearing cache");
        }
    }

    public static void clearCookie() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.flush();
        cookieManager.removeAllCookies(value -> {});
    }

    public static void clearBookmark (Context context) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.clearTable(RecordUnit.TABLE_BOOKMARK);
        action.close();
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        Objects.requireNonNull(shortcutManager).removeAllDynamicShortcuts();
    }

    public static void clearIndexedDB (Context context) {
        File data = Environment.getDataDirectory();

        String blob_storage = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//blob_storage";
        String databases = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//databases";
        String indexedDB = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//IndexedDB";
        String localStorage = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//Local Storage";
        String serviceWorker = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//Service Worker";
        String sessionStorage = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//Session Storage";
        String shared_proto_db = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//shared_proto_db";
        String VideoDecodeStats = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//VideoDecodeStats";
        String QuotaManager = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//QuotaManager";
        String QuotaManager_journal = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//QuotaManager-journal";
        String webData = "//data//" + context.getPackageName()  + "//app_webview//" + "//Default//" + "//Web Data";
        String WebDataJournal = "//data//" + context.getPackageName() + "//app_webview//" + "//Default//" + "//Web Data-journal";

        final File blob_storage_file = new File(data, blob_storage);
        final File databases_file = new File(data, databases);
        final File indexedDB_file = new File(data, indexedDB);
        final File localStorage_file = new File(data, localStorage);
        final File serviceWorker_file = new File(data, serviceWorker);
        final File sessionStorage_file = new File(data, sessionStorage);
        final File shared_proto_db_file = new File(data, shared_proto_db);
        final File VideoDecodeStats_file = new File(data, VideoDecodeStats);
        final File QuotaManager_file = new File(data, QuotaManager);
        final File QuotaManager_journal_file = new File(data, QuotaManager_journal);
        final File webData_file = new File(data, webData);
        final File WebDataJournal_file = new File(data, WebDataJournal);

        BrowserUnit.deleteDir(blob_storage_file);
        BrowserUnit.deleteDir(databases_file);
        BrowserUnit.deleteDir(indexedDB_file);
        BrowserUnit.deleteDir(localStorage_file);
        BrowserUnit.deleteDir(serviceWorker_file);
        BrowserUnit.deleteDir(sessionStorage_file);
        BrowserUnit.deleteDir(shared_proto_db_file);
        BrowserUnit.deleteDir(VideoDecodeStats_file);
        BrowserUnit.deleteDir(QuotaManager_file);
        BrowserUnit.deleteDir(QuotaManager_journal_file);
        BrowserUnit.deleteDir(webData_file);
        BrowserUnit.deleteDir(WebDataJournal_file);
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : Objects.requireNonNull(children)) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        }
        return dir != null && dir.delete();
    }

    public static Boolean isUnmeteredConnection(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (sp.getBoolean("sp_metered",false))
            return false;
        else {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network nw = connectivityManager.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
            return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        }
    }

    public static String youtubeRedirect(String url, String invidiousDomain) {
        final Pattern VIDEO_URL_PATTERN = Pattern.compile("https://([^/]+)\\.youtube\\.com/watch\\?(.*)v=([^&]+)");
        final Pattern YOUTU_BE_URL_PATTERN = Pattern.compile("https://youtu\\.be/([^/]+)");
        final Pattern SEARCH_URL_PATTERN = Pattern.compile("https://www\\.youtube\\.com/results\\?(.*)search_query=([^&]+)");
        final Pattern YOUTUBE_DOMAIN_PATTERN = Pattern.compile("https://([^/]+)\\.youtube\\.com/(.*)");
        final Pattern NOCOOKIE_EMBED_URL_PATTERN = Pattern.compile("https://www\\.youtube-nocookie\\.com/embed/([^/]+)");

        String redirectedUrl = url;
        if (!invidiousDomain.startsWith("http")) invidiousDomain="https://"+invidiousDomain;
        invidiousDomain=Uri.parse(invidiousDomain).getHost();
        if (invidiousDomain==null || invidiousDomain.isEmpty()) return url;

        Matcher matcher = VIDEO_URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            redirectedUrl = "https://" + invidiousDomain + "/watch?v=" + matcher.group(3);
        } else {
            matcher = YOUTU_BE_URL_PATTERN.matcher(url);
            if (matcher.matches()) {
                redirectedUrl = "https://" + invidiousDomain + "/watch?v=" + matcher.group(1);
            } else {
                matcher = SEARCH_URL_PATTERN.matcher(url);
                if (matcher.matches()) {
                    redirectedUrl = "https://" + invidiousDomain + "/search?q=" + matcher.group(2);
                } else {
                    matcher = YOUTUBE_DOMAIN_PATTERN.matcher(url);
                    if (matcher.matches()) {
                        redirectedUrl = "https://" + invidiousDomain + "/" + matcher.group(2);
                    } else {
                        matcher = NOCOOKIE_EMBED_URL_PATTERN.matcher(url);
                        if (matcher.matches()) {
                            redirectedUrl = "https://" + invidiousDomain + "/embed/" + matcher.group(1);
                        }
                    }
                }
            }
        }

        return redirectedUrl;
    }
}