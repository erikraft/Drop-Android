package com.erikraft.drop;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/** Stable, self-contained About screen with transparent dependency and attribution information. */
public class AboutActivity extends DropPipActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.title_activity_about));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(18), pad, dp(32));

        TextView appName = text(getString(R.string.app_name_long), 28);
        content.addView(appName);

        TextView version = text(
                getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                15);
        version.setPadding(0, dp(6), 0, dp(20));
        content.addView(version);

        addHtml(content, getString(R.string.about_description), 16);

        addHeading(content, getString(R.string.about_dependencies_title));
        addHtml(content, getString(R.string.about_dependencies_summary), 15);
        addHtml(content, buildAndroidDependenciesHtml(), 14);

        addHeading(content, getString(R.string.about_references_title));
        addHtml(content, buildReferencesHtml(), 15);

        addHeading(content, getString(R.string.about_licenses_title));
        addHtml(content, getString(R.string.about_licenses_summary), 15);
        addButton(content, getString(R.string.about_licenses_title), "https://github.com/erikraft/Drop-Android/blob/master/LICENSE");

        addHeading(content, getString(R.string.about_credits_title));
        addHtml(content, buildReferencesHtml(), 15);

        addHeading(content, getString(R.string.support_us));
        addButton(content, "GitHub — ErikrafT Drop™", "https://github.com/erikraft/Drop");
        addButton(content, "GitHub — Android", "https://github.com/erikraft/Drop-Android");
        addButton(content, "Site oficial", "https://drop.erikraft.com/");
        addButton(content, getString(R.string.support_us), "https://biodrop.erikraft.com/donation.html");

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private String buildAndroidDependenciesHtml() {
        return "<b>AndroidX / UI</b><br>"
                + "AndroidX AppCompat 1.7.1<br>"
                + "AndroidX CoordinatorLayout 1.3.0<br>"
                + "AndroidX Core SplashScreen 1.2.0<br>"
                + "AndroidX Lifecycle LiveData KTX 2.9.4<br>"
                + "AndroidX Lifecycle ViewModel KTX 2.8.7<br>"
                + "AndroidX Preference 1.2.1<br>"
                + "AndroidX SwipeRefreshLayout 1.1.0<br>"
                + "AndroidX WebKit 1.14.0<br>"
                + "Google Material Components 1.13.0<br><br>"
                + "<b>Transferência, rede e arquivos</b><br>"
                + "Apache FtpServer 1.2.1 (FTP/FTPS)<br>"
                + "Apache MINA SSHD 2.20.0 (SSH/SFTP)<br>"
                + "NanoHTTPD 2.3.1<br>"
                + "jsoup 1.18.1<br>"
                + "Gson 2.13.2<br>"
                + "ZXing Core 3.5.3<br>"
                + "Anggrayudi Storage 1.5.6<br>"
                + "SLF4J Android 1.7.36<br><br>"
                + "<b>Tor / Onion</b><br>"
                + "Tor Android 0.4.9.6<br>"
                + "jtorctl 0.4.5.7<br>"
                + "Onionwrapper Android 0.1.4<br>"
                + "Lyrebird Android 0.6.2<br>"
                + "dont-kill-me-lib 0.2.8<br><br>"
                + "<b>Criptografia</b><br>"
                + "Bouncy Castle bcprov-jdk18on 1.86<br>"
                + "Bouncy Castle bcpkix-jdk18on 1.86<br><br>"
                + "<b>Infraestrutura</b><br>"
                + "AboutLibraries 11.2.3<br>"
                + "desugar_jdk_libs 2.1.5<br>"
                + "fileTree de bibliotecas locais do projeto.";
    }

    private String buildReferencesHtml() {
        return "ErikrafT Drop™ é um fork/ecossistema baseado no PairDrop, que por sua vez deriva do Snapdrop.<br><br>"
                + "<b>Web / Desktop</b><br>"
                + "WebRTC, WebSockets, Node.js, Progressive Web App (PWA), IndexedDB, zip.js, cyrb53, NoSleep, heic2any, Crowdin e BrowserStack.<br><br>"
                + "<b>Android / integração</b><br>"
                + "WebView/AndroidX, Tor/Onion, FTP/FTPS, SSH/SFTP, ZXing e as bibliotecas listadas acima.<br><br>"
                + "<b>Projetos de referência</b><br>"
                + link("PairDrop", "https://github.com/schlagmichdoch/PairDrop") + "<br>"
                + link("Snapdrop", "https://github.com/SnapDrop/snapdrop") + "<br>"
                + link("PairDrop Android", "https://github.com/fm-sys/pairdrop-android") + "<br>"
                + link("ErikrafT Drop Web", "https://github.com/erikraft/Drop") + "<br>"
                + link("ErikrafT Drop Android", "https://github.com/erikraft/Drop-Android") + "<br><br>"
                + getString(R.string.about_references_disclaimer);
    }

    private String link(String label, String url) {
        return "<a href="" + url + "">" + label + "</a>";
    }

    private void addHeading(LinearLayout parent, String value) {
        TextView heading = text(value, 20);
        heading.setPadding(0, dp(24), 0, dp(8));
        parent.addView(heading);
    }

    private void addHtml(LinearLayout parent, String value, float size) {
        TextView view = text("", size);
        view.setText(Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY));
        view.setAutoLinkMask(0);
        view.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        parent.addView(view);
    }

    private void addButton(LinearLayout parent, String label, String url) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
