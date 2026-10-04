package com.erikraft.drop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;

public class SftpSettingsActivity extends AppCompatActivity {
    private TextInputEditText port,user,password;
    private TextView folder,status,address;
    private ActivityResultLauncher<Intent> picker;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        picker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{
            if(r.getResultCode()!=Activity.RESULT_OK||r.getData()==null)return;
            android.net.Uri uri=r.getData().getData(); if(uri==null)return;
            try{getContentResolver().takePersistableUriPermission(uri,r.getData().getFlags()&
                (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(SecurityException ignored){}
            DocumentFile df=DocumentFile.fromTreeUri(this,uri);
            if(df==null)return;
            String p=com.anggrayudi.storage.file.DocumentFileUtils.getAbsolutePath(df,this);
            File f=p==null?null:new File(p);
            if(f==null||!f.isDirectory()||!f.canRead()||!f.canWrite()){
                Toast.makeText(this,"Esta pasta não pode ser usada diretamente pelo SFTP.",Toast.LENGTH_LONG).show();return;
            }
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("save_location",f.getAbsolutePath()).apply();
            folder.setText(f.getAbsolutePath());
        });
        buildUi();
    }

    private void buildUi(){
        android.content.SharedPreferences p=PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        Toolbar tb=new Toolbar(this);tb.setTitle("Transferência via SFTP");tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);tb.setNavigationOnClickListener(v->finish());
        root.addView(tb,new LinearLayout.LayoutParams(-1,dp(56)));setSupportActionBar(tb);
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(12),dp(20),dp(28));
        c.addView(text("Execute um servidor SFTP local via SSH. A pasta selecionada será a raiz do SFTP.",15));
        MaterialButton choose=new MaterialButton(this);choose.setText("Escolher pasta");c.addView(choose,top(12));
        folder=text(p.getString("save_location",Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()),13);folder.setTextIsSelectable(true);c.addView(folder,top(4));
        choose.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);picker.launch(i);});
        port=field("Porta",p.getString("ftp_port","2222"));user=field("Nome de usuário",p.getString("ftp_username","admin"));password=field("Senha",p.getString("ftp_username_secret","admin"));
        c.addView((TextInputLayout)port.getTag(),top(12));c.addView((TextInputLayout)user.getTag(),top(8));c.addView((TextInputLayout)password.getTag(),top(8));
        status=text("Servidor parado",15);c.addView(status,top(18));address=text("",14);address.setTextIsSelectable(true);c.addView(address,top(4));
        LinearLayout row=new LinearLayout(this);MaterialButton start=button("Iniciar servidor SFTP"),stop=button("Parar servidor SFTP");row.addView(start,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.setMargins(dp(8),0,0,0);row.addView(stop,sp);c.addView(row,top(14));
        MaterialButton copy=button("Copiar endereço SFTP");c.addView(copy,top(8));
        start.setOnClickListener(v->startServer());stop.setOnClickListener(v->stopServer());copy.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("SFTP",address.getText()));});
        root.addView(c,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void startServer(){
        android.content.SharedPreferences p=PreferenceManager.getDefaultSharedPreferences(this);String home=p.getString("save_location","");File f=new File(home);
        if(!f.isDirectory()||!f.canRead()||!f.canWrite()){Toast.makeText(this,"Escolha uma pasta acessível diretamente pelo SFTP.",Toast.LENGTH_LONG).show();return;}
        int po;try{po=Integer.parseInt(port.getText().toString());}catch(Exception e){Toast.makeText(this,"Informe uma porta válida.",Toast.LENGTH_SHORT).show();return;}
        if(po<1025||po>65535){Toast.makeText(this,"A porta deve estar entre 1025 e 65535.",Toast.LENGTH_SHORT).show();return;}
        String u=user.getText().toString().trim(),pw=password.getText().toString();if(u.isEmpty()||pw.isEmpty()){Toast.makeText(this,"Usuário e senha são obrigatórios.",Toast.LENGTH_SHORT).show();return;}
        p.edit().putString("save_location",f.getAbsolutePath()).putString("ftp_port",String.valueOf(po)).putString("ftp_username",u).putString("ftp_username_secret",pw).apply();
        startService(SftpServerService.startIntent(this));status.setText("Iniciando servidor SFTP…");address.setText("sftp://"+com.erikraft.drop.utils.NetworkUtils.getIpAddress(this)+":"+po);
    }
    private void stopServer(){startService(SftpServerService.stopIntent(this));status.setText("Servidor SFTP parado.");address.setText("");}
    private TextInputEditText field(String h,String v){TextInputLayout l=new TextInputLayout(this);l.setHint(h);if("Senha".equals(h))l.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);TextInputEditText e=new TextInputEditText(this);e.setSingleLine(true);e.setText(v);l.addView(e,new LinearLayout.LayoutParams(-1,-2));e.setTag(l);return e;}
    private MaterialButton button(String label){MaterialButton b=new MaterialButton(this);b.setText(label);return b;}
    private TextView text(String s,float z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);return v;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(m);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
