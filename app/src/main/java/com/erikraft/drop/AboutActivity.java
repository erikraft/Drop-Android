package com.erikraft.drop;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/** Stable, self-contained About screen. It does not depend on generated AboutLibraries UI state. */
public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Sobre este app");
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
        TextView version = text("Versão " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", 15);
        version.setPadding(0, dp(6), 0, dp(20));
        content.addView(version);

        TextView description = text(
                "ErikrafT Drop™ é um aplicativo de transferência de arquivos e texto com foco em privacidade, baseado no ecossistema open source do ErikrafT Drop™.",
                16);
        content.addView(description);

        addHeading(content, "Créditos");
        addHtml(content,
                "Este aplicativo tem como base o projeto PairDrop de schlagmichdoch.<br>" +
                "<a href=\"https://github.com/schlagmichdoch/PairDrop\">github.com/schlagmichdoch/PairDrop</a><br><br>" +
                "O código web oficial é mantido em:<br>" +
                "<a href=\"https://github.com/erikraft/Drop\">github.com/erikraft/Drop</a><br><br>" +
                "O cliente Android é mantido em:<br>" +
                "<a href=\"https://github.com/erikraft/Drop-Android\">github.com/erikraft/Drop-Android</a>");

        addHeading(content, "Privacidade e transferência");
        addHtml(content,
                "O ErikrafT Drop™ prioriza transferência direta e recursos locais. A funcionalidade de Transferência via Onion usa Tor para disponibilizar temporariamente o conteúdo do dispositivo através de um serviço Onion.");

        addHeading(content, "Projeto e suporte");
        addButton(content, "GitHub — ErikrafT Drop™", "https://github.com/erikraft/Drop");
        addButton(content, "GitHub — Android", "https://github.com/erikraft/Drop-Android");
        addButton(content, "Site oficial", "https://drop.erikraft.com/");
        addButton(content, "Apoiar o projeto", "https://biodrop.erikraft.com/donation.html");

        addHeading(content, "Referências técnicas");
        addHtml(content,
                "O projeto inclui referências externas para pesquisa de transferência óptica, OnionShare Android, InviZible e download-directory. Esses projetos continuam pertencendo aos seus respectivos autores e licenciadores.");

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addHeading(LinearLayout parent, String value) {
        TextView heading = text(value, 20);
        heading.setPadding(0, dp(24), 0, dp(8));
        parent.addView(heading);
    }

    private void addHtml(LinearLayout parent, String value) {
        TextView view = text("", 15);
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
