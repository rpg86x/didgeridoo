package nl.didgeridoo.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Toont de didgeridoo (assets/index.html, dezelfde als op de laptop) schermvullend in een WebView. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Het scherm blijft aan terwijl je speelt of de didgeridoo laat doorspelen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF100C09);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // onthoudt taal, volume en gekozen didgeridoo
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Koffie-link en andere webadressen openen in de gewone browser
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
        });
        setContentView(web);
        if (state == null) web.loadUrl("file:///android_asset/index.html" + songQuery(getIntent()));
        else web.restoreState(state);
    }

    // Song Library opent de didgeridoo met een liedje: extra's "song" en "mode" (practice of listen)
    private static String songQuery(Intent i) {
        String song = i == null ? null : i.getStringExtra("song");
        if (song == null || !song.matches("[a-z0-9]+")) return "";
        String mode = "listen".equals(i.getStringExtra("mode")) ? "listen" : "practice";
        return "?song=" + song + "&mode=" + mode;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String q = songQuery(i);
        if (q.isEmpty() || web == null) return;
        String song = i.getStringExtra("song"), mode = q.endsWith("listen") ? "listen" : "practice";
        web.evaluateJavascript("window.openSong && openSong('" + song + "','" + mode + "')", null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        // App naar de achtergrond: stil, anders speelt 'Laten doorspelen' onzichtbaar door
        web.evaluateJavascript("try { silence(); if (ctx) ctx.suspend(); } catch (e) {}", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.evaluateJavascript("try { if (ctx) ctx.resume(); } catch (e) {}", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
