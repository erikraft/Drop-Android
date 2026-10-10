package com.erikraft.drop;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.content.pm.PackageManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;

public class SftpSettingsActivity extends DropPipActivity {
    private TextInputEditText port,user,password;
    private String initialStatus = "Servidor parado";
    private TextView folder,status,address;
    private MaterialButton serverToggleButton;
    private ActivityResultLauncher<Intent> picker;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private SwitchCompat stopNotificationSwitch;
    private BroadcastReceiver statusReceiver;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (stopNotificationSwitch != null) stopNotificationSwitch.setChecked(granted);
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean(getString(R.string.pref_sftp_stop_notification), granted).apply();
                    if (SftpServerService.isRunning()) {
                        startService(new Intent(this, SftpServerService.class)
                                .setAction(SftpServerService.ACTION_REFRESH_NOTIFICATION));
                    }
                });
        picker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{
            if(r.getResultCode()!=Activity.RESULT_OK||r.getData()==null)return;
            android.net.Uri uri=r.getData().getData(); if(uri==null)return;
            try{int flags=r.getData().getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)
                    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                else if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)
                    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                else if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)
                    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException ignored){}
            DocumentFile df=DocumentFile.fromTreeUri(this,uri);
            if(df==null)return;
            String p=com.anggrayudi.storage.file.DocumentFileUtils.getAbsolutePath(df,this);
            File f=p==null?null:new File(p);
            if(f==null||!f.isDirectory()||!f.canRead()||!f.canWrite()){
                Toast.makeText(this,"Esta pasta não pode ser usada diretamente pelo SFTP.",Toast.LENGTH_LONG).show();return;
            }
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(getString(R.string.pref_sftp_save_location),f.getAbsolutePath()).apply();
            folder.setText(f.getAbsolutePath());
        });
        buildUi();
        statusReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) {
            if (!SftpServerService.EXTRA_STATUS.equals(intent.getAction())) return;
            boolean running = intent.getBooleanExtra("running", false);
            String error = intent.getStringExtra(SftpServerService.EXTRA_ERROR);
            status.setText(running ? "Servidor SFTP ativo" : (error == null ? "Servidor parado" : "Falha: " + error));
            updateServerToggle(running);
            if (running) address.setText("sftp://" + com.erikraft.drop.utils.NetworkUtils.getIpAddress(SftpSettingsActivity.this) + ":" + SftpServerService.getRunningPort());
            else address.setText("");
        }};
        ContextCompat.registerReceiver(this, statusReceiver, new IntentFilter(SftpServerService.EXTRA_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (SftpServerService.isRunning()) {
            status.setText("Servidor SFTP ativo");
            updateServerToggle(true);
            address.setText("sftp://" + com.erikraft.drop.utils.NetworkUtils.getIpAddress(this) + ":" + SftpServerService.getRunningPort());
        } else {
            updateServerToggle(false);
        }
    }

    private void buildUi(){
        android.content.SharedPreferences p=PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        Toolbar tb=new Toolbar(this);tb.setTitle("Transferência via SFTP");tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);tb.setNavigationOnClickListener(v->finish());
        root.addView(tb,new LinearLayout.LayoutParams(-1,dp(56)));setSupportActionBar(tb);
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(12),dp(20),dp(28));
        c.addView(text("Execute um servidor SFTP local via SSH. A pasta selecionada será a raiz do SFTP.",15));
        MaterialButton choose=new MaterialButton(this);choose.setText("Escolher pasta");c.addView(choose,top(12));
        folder=text(p.getString(getString(R.string.pref_sftp_save_location),Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()),13);folder.setTextIsSelectable(true);c.addView(folder,top(4));
        choose.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);picker.launch(i);});
        port=field("Porta",p.getString(getString(R.string.pref_sftp_port),"2222"));user=field("Nome de usuário",p.getString(getString(R.string.pref_sftp_username),"admin"));password=field("Senha",p.getString(getString(R.string.pref_sftp_password),"admin"));
        c.addView((TextInputLayout)port.getTag(),top(12));c.addView((TextInputLayout)user.getTag(),top(8));c.addView((TextInputLayout)password.getTag(),top(8));
        stopNotificationSwitch = new SwitchCompat(this);
        stopNotificationSwitch.setText(R.string.ftp_stop_notification_title);
        stopNotificationSwitch.setChecked(p.getBoolean(getString(R.string.pref_sftp_stop_notification), false));
        c.addView(stopNotificationSwitch,top(10));
        status=text(initialStatus,15);c.addView(status,top(18));address=text("",14);address.setTextIsSelectable(true);c.addView(address,top(4));
        serverToggleButton=button(getString(R.string.sftp_start));c.addView(serverToggleButton,top(14));
        MaterialButton copy=button("Copiar endereço SFTP");c.addView(copy,top(8));
        serverToggleButton.setOnClickListener(v->{if(SftpServerService.isRunning())stopServer();else startServer();});
        stopNotificationSwitch.setOnClickListener(v->handleStopNotificationPreference());
        copy.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("SFTP",address.getText()));});
        root.addView(c,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void handleStopNotificationPreference() {
        if (!stopNotificationSwitch.isChecked()) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(getString(R.string.pref_sftp_stop_notification), false).apply();
            if (SftpServerService.isRunning()) {
                startService(new Intent(this, SftpServerService.class)
                        .setAction(SftpServerService.ACTION_REFRESH_NOTIFICATION));
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(getString(R.string.pref_sftp_stop_notification), true).apply();
        if (SftpServerService.isRunning()) {
            startService(new Intent(this, SftpServerService.class)
                    .setAction(SftpServerService.ACTION_REFRESH_NOTIFICATION));
        }
    }

    private void startServer(){
        android.content.SharedPreferences p=PreferenceManager.getDefaultSharedPreferences(this);String home=p.getString(getString(R.string.pref_sftp_save_location),"");File f=new File(home);
        if(!f.isDirectory()||!f.canRead()||!f.canWrite()){Toast.makeText(this,"Escolha uma pasta acessível diretamente pelo SFTP.",Toast.LENGTH_LONG).show();return;}
        int po;try{po=Integer.parseInt(port.getText().toString());}catch(Exception e){Toast.makeText(this,"Informe uma porta válida.",Toast.LENGTH_SHORT).show();return;}
        if(po<1025||po>65535){Toast.makeText(this,"A porta deve estar entre 1025 e 65535.",Toast.LENGTH_SHORT).show();return;}
        String u=user.getText().toString().trim(),pw=password.getText().toString();if(u.isEmpty()||pw.isEmpty()){Toast.makeText(this,"Usuário e senha são obrigatórios.",Toast.LENGTH_SHORT).show();return;}
        p.edit().putString(getString(R.string.pref_sftp_save_location),f.getAbsolutePath()).putString(getString(R.string.pref_sftp_port),String.valueOf(po)).putString(getString(R.string.pref_sftp_username),u).putString(getString(R.string.pref_sftp_password),pw).putBoolean(getString(R.string.pref_sftp_stop_notification), stopNotificationSwitch.isChecked()).apply();
        androidx.core.content.ContextCompat.startForegroundService(this, SftpServerService.startIntent(this));status.setText("Iniciando servidor SFTP…");address.setText("sftp://"+com.erikraft.drop.utils.NetworkUtils.getIpAddress(this)+":"+po);
    }
    private void stopServer(){startService(SftpServerService.stopIntent(this));status.setText("Parando servidor SFTP…");}
    private void updateServerToggle(boolean running){if(serverToggleButton!=null)serverToggleButton.setText(running?R.string.sftp_stop:R.string.sftp_start);}
    @Override protected void onDestroy(){ if(statusReceiver!=null) unregisterReceiver(statusReceiver); super.onDestroy(); }
    private TextInputEditText field(String h,String v){TextInputLayout l=new TextInputLayout(this);l.setHint(h);if("Senha".equals(h))l.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);TextInputEditText e=new TextInputEditText(this);e.setSingleLine(true);e.setText(v);l.addView(e,new LinearLayout.LayoutParams(-1,-2));e.setTag(l);return e;}
    private MaterialButton button(String label){MaterialButton b=new MaterialButton(this);b.setText(label);return b;}
    private TextView text(String s,float z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);return v;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(m);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
