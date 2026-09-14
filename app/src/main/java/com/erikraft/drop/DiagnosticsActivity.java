package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import java.nio.charset.StandardCharsets;

public class DiagnosticsActivity extends AppCompatActivity {
    private TextView logs; private String currentLogs=""; private ActivityResultLauncher<String> saver;
    @Override protected void onCreate(Bundle state) { super.onCreate(state); SnapdropApplication.setAppTheme(this);
        saver=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri->{ if(uri==null)return; try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){ if(out==null)throw new java.io.IOException("No output stream"); out.write(currentLogs.getBytes(StandardCharsets.UTF_8)); Toast.makeText(this,R.string.diagnostics_saved,Toast.LENGTH_SHORT).show(); }catch(Exception e){Toast.makeText(this,"Falha ao salvar: "+e.getMessage(),Toast.LENGTH_LONG).show();} });
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); Toolbar bar=new Toolbar(this); bar.setTitle(R.string.diagnostics_title); bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); bar.setNavigationOnClickListener(v->finish()); root.addView(bar,new LinearLayout.LayoutParams(-1,getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height))); setSupportActionBar(bar);
        LinearLayout actions=new LinearLayout(this); MaterialButton refresh=button(R.string.diagnostics_refresh), copy=button(R.string.diagnostics_copy), save=button(R.string.diagnostics_save); actions.addView(refresh); actions.addView(copy); actions.addView(save); root.addView(actions); ScrollView scroll=new ScrollView(this); logs=new TextView(this); logs.setTextIsSelectable(true); logs.setTypeface(android.graphics.Typeface.MONOSPACE); logs.setTextSize(12); scroll.addView(logs); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        refresh.setOnClickListener(v->refreshLogs()); copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop diagnostics",currentLogs)); Toast.makeText(this,R.string.diagnostics_copied,Toast.LENGTH_SHORT).show();}); save.setOnClickListener(v->saver.launch("erikraft-drop-diagnostics.txt")); refreshLogs();
    }
    private void refreshLogs(){boolean enabled=PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_diagnostics_enabled),false); currentLogs=enabled?com.erikraft.drop.utils.LogUtils.getLogs(PreferenceManager.getDefaultSharedPreferences(this),true):getString(R.string.diagnostics_disabled); logs.setText(currentLogs);}
    private MaterialButton button(int id){MaterialButton b=new MaterialButton(this);b.setText(id);return b;}
}
