package com.example.testapp2;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class DedupingWebViewActivity2 extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.deduping_view_using_webview_2);
		WebView dedupingWebView = findViewById(R.id.deduping_web_view);
		WebSettings webSettings = dedupingWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		dedupingWebView.setWebViewClient(new WebViewClient());
		dedupingWebView.loadUrl("https://nocollateralloan.org/subd/auto_mailer.php");
		Button closeButton = findViewById(R.id.close_deduping_button);
		closeButton.setOnClickListener(v -> {
			finish();
		});
	}
}
