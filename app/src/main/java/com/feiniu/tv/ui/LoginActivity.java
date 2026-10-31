package com.feiniu.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.feiniu.tv.R;
import com.feiniu.tv.api.ApiException;
import com.feiniu.tv.api.FnApiClient;
import com.feiniu.tv.util.Prefs;

/** 登录页:输入 NAS 地址 + 飞牛影视账号密码,拿 token 后进首页。 */
public class LoginActivity extends AppCompatActivity {

    private static final String APP_NAME = "trimemedia-web";

    private EditText editHost;
    private EditText editPort;
    private EditText editUsername;
    private EditText editPassword;
    private TextView tvError;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        prefs = new Prefs(this);

        editHost = findViewById(R.id.edit_host);
        editPort = findViewById(R.id.edit_port);
        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        tvError = findViewById(R.id.tv_error);
        Button btnLogin = findViewById(R.id.btn_login);

        // 仅 debug 包:允许用 am start 传参自动登录,方便在电视上调试
        // (遥控器拼音输入法会把 IP 的点变成全角)。
        if (com.feiniu.tv.BuildConfig.DEBUG) {
            String h = getIntent().getStringExtra("dbg_host");
            String u = getIntent().getStringExtra("dbg_user");
            String p = getIntent().getStringExtra("dbg_pass");
            if (!TextUtils.isEmpty(h) && !TextUtils.isEmpty(u) && !TextUtils.isEmpty(p)) {
                editHost.setText(h);
                editPort.setText("5666");
                editUsername.setText(u);
                editPassword.setText(p);
                doLogin();
                return;
            }
        }

        String savedHost = prefs.getHost();
        if (savedHost != null) {
            // 反解出 IP 和端口,便于修改
            String ip = savedHost;
            String port = "5666";
            if (savedHost.startsWith("http://")) {
                ip = savedHost.substring("http://".length());
            } else if (savedHost.startsWith("https://")) {
                ip = savedHost.substring("https://".length());
            }
            int colon = ip.lastIndexOf(':');
            if (colon > 0 && ip.indexOf(':') == colon) {
                port = ip.substring(colon + 1);
                ip = ip.substring(0, colon);
            }
            editHost.setText(ip);
            editPort.setText(port);
        }

        // 已有 token 直接进首页
        if (!TextUtils.isEmpty(prefs.getToken())) {
            startHome();
            return;
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });
    }

    private void doLogin() {
        String host = editHost.getText().toString().trim();
        String port = editPort.getText().toString().trim();
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            showError("请填写 NAS IP、账号和密码");
            return;
        }
        if (TextUtils.isEmpty(port)) {
            port = "5666";
        }
        host = buildHost(host, port);
        tvError.setVisibility(View.GONE);

        final String finalHost = host;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FnApiClient client = new FnApiClient(finalHost);
                    final String token = client.login(username, password, APP_NAME);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prefs.setHost(finalHost);
                            prefs.setToken(token);
                            startHome();
                        }
                    });
                } catch (final Exception e) {
                    Log.e("FeiniuTV", "login failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(friendlyError(e));
                        }
                    });
                }
            }
        }).start();
    }

    private static String buildHost(String host, String port) {
        String ip = host.trim();
        if (ip.startsWith("http://") || ip.startsWith("https://")) {
            return ip; // 已经带端口或协议,原样使用
        }
        // 去掉可能误填的端口(已有 :xxx 则保留)
        if (ip.indexOf(':') > 0) {
            return "http://" + ip;
        }
        return "http://" + ip + ":" + port;
    }

    private static String friendlyError(Exception e) {
        if (e instanceof ApiException) {
            ApiException ae = (ApiException) e;
            String msg = ae.getMessage();
            if (!TextUtils.isEmpty(msg)) {
                return "登录失败: " + msg;
            }
            return "登录失败(错误码 " + ae.code + ")";
        }
        return "连接失败,请检查 NAS IP 和端口(默认 5666)";
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void startHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
