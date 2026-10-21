package de.baumann.browser.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.webkit.*;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import de.baumann.browser.R;
import de.baumann.browser.Utils;
import de.baumann.browser.activity.BrowserActivity;
import de.baumann.browser.unit.HelperUnit;
import de.baumann.browser.view.NinjaToast;
import de.baumann.browser.view.NinjaWebView;

public class NinjaWebChromeClient extends WebChromeClient {

    private final NinjaWebView ninjaWebView;

    public NinjaWebChromeClient(NinjaWebView ninjaWebView) {
        super();
        this.ninjaWebView = ninjaWebView;
    }

    @Override
    public void onProgressChanged(WebView view, int progress) {
        super.onProgressChanged(view, progress);
        ninjaWebView.updateTitle(progress);
        String title = view.getTitle();
        if (title == null || title.isEmpty()) {
            ninjaWebView.updateTitle(view.getUrl() != null ? view.getUrl() : "");
        } else {
            ninjaWebView.updateTitle(title);
        }
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
        Context context = view.getContext();
        WebView newWebView = new WebView(context);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();

        newWebView.setWebViewClient(new WebViewClient() {
            private boolean handled = false;
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                if (!handled && request.getUrl() != null) {
                    handled = true;
                    Intent browserIntent = new Intent(context, BrowserActivity.class);
                    browserIntent.setAction(Intent.ACTION_VIEW);
                    browserIntent.setData(request.getUrl());
                    context.startActivity(browserIntent);
                    v.destroy();
                }
                return true;
            }
        });
        return true;
    }

    @Override
    public void onCloseWindow(WebView window) {
        super.onCloseWindow(window);
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        Context context = view.getContext();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(HelperUnit.domain(url));
        builder.setMessage(message);
        builder.setPositiveButton(R.string.app_ok, (dialog, which) -> result.confirm());
        builder.setOnCancelListener(dialog -> result.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        Context context = view.getContext();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(HelperUnit.domain(url));
        builder.setMessage(message);
        builder.setPositiveButton(R.string.app_ok, (dialog, which) -> result.confirm());
        builder.setNegativeButton(R.string.app_cancel, (dialog, which) -> result.cancel());
        builder.setOnCancelListener(dialog -> result.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
        Context context = view.getContext();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(message);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText input = new EditText(context);
        input.setSingleLine(true);
        if (defaultValue != null) {
            input.setText(defaultValue);
        }
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton(R.string.app_ok, (dialog, which) -> result.confirm(input.getText().toString()));
        builder.setNegativeButton(R.string.app_cancel, (dialog, which) -> result.cancel());
        builder.setOnCancelListener(dialog -> result.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
        return true;
    }

    @Override
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        ninjaWebView.getBrowserController().onShowCustomView(view, callback);
        super.onShowCustomView(view, callback);
    }

    @Override
    public void onHideCustomView() {
        ninjaWebView.getBrowserController().onHideCustomView();
        super.onHideCustomView();
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        ninjaWebView.getBrowserController().showFileChooser(filePathCallback);
        return true;
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        Activity activity = (Activity) ninjaWebView.getContext();
        HelperUnit.grantPermissionsLoc(activity);
        callback.invoke(origin, true, false);
        super.onGeolocationPermissionsShowPrompt(origin, callback);
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        Activity activity = (Activity) ninjaWebView.getContext();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ninjaWebView.getContext());
        String[] resources = request.getResources();
        List<String> grantedResources = new ArrayList<>();
        boolean needsDrmPrompt = false;

        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                if (sp.getBoolean("sp_camera", false)) {
                    grantedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
                } else {
                    Snackbar.make(ninjaWebView, activity.getString(R.string.error_allow_camera), 3000).setAction(activity.getString(R.string.app_ok), view -> {
                        sp.edit().putBoolean("sp_camera", true).apply();
                        HelperUnit.grantPermissionsCam(activity);
                        if (!HelperUnit.checkPermissionsCam(activity))
                            NinjaToast.show(activity, activity.getResources().getString(R.string.error_missing_permission) + "\n" + activity.getResources().getString(R.string.error_allow_camera));
                        ninjaWebView.reload();
                    }).setBackgroundTint(Utils.getThemeColor(activity, R.attr.colorHint)).setTextColor(activity.getColor(android.R.color.holo_red_dark)).setActionTextColor(activity.getColor(android.R.color.holo_red_dark)).show();
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                if (sp.getBoolean("sp_microphone", false)) {
                    grantedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
                } else {
                    Snackbar.make(ninjaWebView, activity.getString(R.string.error_allow_microphone), 3000).setAction(activity.getString(R.string.app_ok), view -> {
                        sp.edit().putBoolean("sp_microphone", true).apply();
                        HelperUnit.grantPermissionsMic(activity);
                        if (!HelperUnit.checkPermissionsMic(activity))
                            NinjaToast.show(activity, activity.getResources().getString(R.string.error_missing_permission) + "\n" + activity.getString(R.string.error_allow_microphone));
                        ninjaWebView.reload();
                    }).setBackgroundTint(Utils.getThemeColor(activity, R.attr.colorHint)).setTextColor(activity.getColor(android.R.color.holo_red_dark)).setActionTextColor(activity.getColor(android.R.color.holo_red_dark)).show();
                }
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                if (!ninjaWebView.getBlockNetworkVideo()) {
                    if (sp.getBoolean("sp_drm", true)) {
                        grantedResources.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
                    } else {
                        needsDrmPrompt = true;
                    }
                }
            } else if (PermissionRequest.RESOURCE_MIDI_SYSEX.equals(resource)) {
                grantedResources.add(PermissionRequest.RESOURCE_MIDI_SYSEX);
            }
        }

        if (needsDrmPrompt && grantedResources.isEmpty()) {
            final boolean[] handled = {false};
            Snackbar.make(ninjaWebView, activity.getString(R.string.hint_DRM_Media), 3000).setAction(activity.getString(R.string.app_ok), view -> {
                handled[0] = true;
                try {
                    request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                } catch (IllegalStateException ignored) {}
            }).addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    super.onDismissed(transientBottomBar, event);
                    if (!handled[0]) {
                        try { request.deny(); } catch (IllegalStateException ignored) {}
                    }
                }
            }).setBackgroundTint(Utils.getThemeColor(activity, R.attr.colorHint)).setTextColor(activity.getColor(android.R.color.holo_red_dark)).setActionTextColor(activity.getColor(android.R.color.holo_red_dark)).show();
            return;
        }

        try {
            if (!grantedResources.isEmpty()) {
                request.grant(grantedResources.toArray(new String[0]));
            } else {
                request.deny();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
        ninjaWebView.setFavicon(icon);
    }

    @Override
    public void onReceivedTitle(WebView view, String sTitle) {
        super.onReceivedTitle(view, sTitle);
    }
}
