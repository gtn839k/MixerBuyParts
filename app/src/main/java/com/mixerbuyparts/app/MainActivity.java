package com.mixerbuyparts.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MixerBuyParts";
    private static final int REQUEST_FILE_PICKER   = 1001;
    private static final int REQUEST_PERMISSIONS   = 1002;

    private WebView webView;
    private ProgressBar progressBar;

    // Holds the callback for <input type="file"> from WebChromeClient
    private ValueCallback<Uri[]> filePathCallback;

    // ─────────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen immersive: hides status bar and nav bar
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        webView     = findViewById(R.id.webView);

        requestStoragePermissions();
        setupWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebView configuration
    // ─────────────────────────────────────────────────────────────────────────
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Core settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);          // localStorage support
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Allow file:// pages to access other file:// resources
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Viewport — critical for responsive layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Cache — useful for CDN libs after first load
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Hardware acceleration (already set in manifest, belt-and-suspenders)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // JavaScript bridge: exposes AndroidBridge to JS as window.AndroidBridge
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // ── WebViewClient: navigation + print intercept ───────────────────────
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Keep asset pages inside the WebView
                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }
                // Open external links in the system browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "No browser found for: " + url);
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                // Inject JS to intercept window.print() → Android print dialog
                view.evaluateJavascript(
                    "(function() {" +
                    "  var _origPrint = window.print;" +
                    "  window.print = function() {" +
                    "    if (window.AndroidBridge) {" +
                    "      AndroidBridge.triggerPrint();" +
                    "    } else if (_origPrint) {" +
                    "      _origPrint();" +
                    "    }" +
                    "  };" +
                    "})();",
                    null
                );
            }
        });

        // ── WebChromeClient: progress + file picker ───────────────────────────
        webView.setWebChromeClient(new WebChromeClient() {

            // Progress bar
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // File chooser for <input type="file"> — Android 5.0+
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                // Cancel any pending callback
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                // Accept CSV and TXT explicitly
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"text/csv", "text/plain", "application/octet-stream"});

                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Seleziona file CSV"),
                        REQUEST_FILE_PICKER
                    );
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Nessuna app per selezionare file", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            // Console messages from JS — useful for debugging
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS [" + msg.messageLevel() + "] " +
                      msg.message() + " — " + msg.sourceId() + ":" + msg.lineNumber());
                return true;
            }
        });

        // ── DownloadListener: handles blob:// and data: URLs from jsPDF / CSV ──
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                    String contentDisposition, String mimeType, long contentLength) {

                if (url.startsWith("data:") || url.startsWith("blob:")) {
                    // For data: URLs (jsPDF exports), handle via JS bridge
                    // The JS side will call AndroidBridge.saveBase64File()
                    Log.d(TAG, "Download intercepted (data/blob URL): " + mimeType);
                } else {
                    // Regular http(s) downloads → DownloadManager
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    request.setDescription("Download MixerBuyParts");
                    request.setTitle(filename);
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, filename);

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(MainActivity.this,
                                "Download avviato: " + filename, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JavaScript Bridge
    // ─────────────────────────────────────────────────────────────────────────
    private class AndroidBridge {

        /**
         * Called by the injected window.print() override.
         * Opens the native Android Print dialog for the current WebView.
         */
        @JavascriptInterface
        public void triggerPrint() {
            runOnUiThread(() -> {
                PrintManager printManager =
                        (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (printManager == null) return;

                PrintDocumentAdapter printAdapter =
                        webView.createPrintDocumentAdapter("MixerBuyParts");

                PrintAttributes.Builder builder = new PrintAttributes.Builder();
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4);

                printManager.print("MixerBuyParts", printAdapter,
                        builder.build());
            });
        }

        /**
         * Saves a Base64-encoded file (PDF or CSV) to the Downloads folder.
         * Called from JavaScript via:
         *   AndroidBridge.saveBase64File(base64Data, filename, mimeType)
         *
         * Returns the file path as a string, or empty string on error.
         */
        @JavascriptInterface
        public String saveBase64File(String base64Data, String filename, String mimeType) {
            try {
                // Strip data URI prefix if present: "data:application/pdf;base64,..."
                String pureBase64 = base64Data;
                int commaIdx = base64Data.indexOf(',');
                if (commaIdx >= 0) {
                    pureBase64 = base64Data.substring(commaIdx + 1);
                }

                byte[] bytes = Base64.decode(pureBase64, Base64.DEFAULT);

                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();

                File outFile = new File(downloadsDir, filename);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(bytes);
                }

                // Notify MediaScanner
                Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scanIntent.setData(Uri.fromFile(outFile));
                sendBroadcast(scanIntent);

                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "✅ Salvato in Download: " + filename, Toast.LENGTH_LONG).show());

                return outFile.getAbsolutePath();

            } catch (Exception e) {
                Log.e(TAG, "saveBase64File error: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Errore salvataggio: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return "";
            }
        }

        /**
         * Opens a saved file with an external viewer (e.g. PDF reader).
         */
        @JavascriptInterface
        public void openFile(String filePath) {
            runOnUiThread(() -> {
                File file = new File(filePath);
                if (!file.exists()) return;

                Uri fileUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        "com.mixerbuyparts.app.fileprovider",
                        file);

                String ext = MimeTypeMap.getFileExtensionFromUrl(filePath);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime == null) mime = "application/octet-stream";

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mime);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,
                            "Nessuna app per aprire " + ext, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * Shows a native Android toast message from JavaScript.
         */
        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        /**
         * Returns the Android OS version string — useful for JS-side feature detection.
         */
        @JavascriptInterface
        public String getAndroidVersion() {
            return Build.VERSION.RELEASE;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File picker result
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE_PICKER) {
            if (filePathCallback == null) return;

            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{ Uri.parse(dataString) };
                } else if (data.getClipData() != null) {
                    // Multiple file selection
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────
    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Android 12 and below: request legacy storage permissions
            String[] perms = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            boolean needRequest = false;
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p)
                        != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
            }
        }
        // Android 13+: uses scoped storage — no runtime permission needed for Downloads
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (!allGranted) {
                Toast.makeText(this,
                    "Permessi storage necessari per salvare file",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Back button: navigate WebView history before closing app
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save / restore WebView state across rotations
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }
}
