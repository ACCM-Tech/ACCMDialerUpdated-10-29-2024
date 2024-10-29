package com.example.testapp2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class LoginUseAccessCode extends AppCompatActivity {
	private static final long POLLING_INTERVAL = 5000;
	private SharedPreferences sharedPreferences;
	private RequestQueue requestQueue;
	private Handler handler = new Handler();
	private Runnable pollingRunnable;
	private String agentName;
	private String version;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login_using_access_code_from_api);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		requestQueue = Volley.newRequestQueue(this);
		long accessStartTime = sharedPreferences.getLong("access_start_time", -1);
		clearPreferencesIfPast7AM();
		version = "1.0.6";
		checkForUpdatedVersionOfTheApp();
		if (isAccessGranted(accessStartTime)) {
			redirectToMainActivity();
		} else {
			clearPreferencesIfPast7AM();
			showPasswordDialog();
		}
	}

	private String TrustedBluetoothAddress1ForLoginPurpose = "BC:EA:9C:1D:13:E4";

	private void redirectToMainActivityIfConnectedSuccessfullyToTrustedBluetoothAddress() {

	}

	private void clearPreferencesIfPast7AM() {
		Calendar calendar = Calendar.getInstance();
		int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
		long lastClearedTime = sharedPreferences.getLong("last_cleared_time", -1);
		Calendar lastClearedCalendar = Calendar.getInstance();
		lastClearedCalendar.setTimeInMillis(lastClearedTime);
		if (currentHour >= 7 && isDifferentDay(lastClearedCalendar, calendar)) {
			sharedPreferences.edit().clear().apply();
			sharedPreferences.edit().putLong("last_cleared_time", System.currentTimeMillis()).apply();
			Toast.makeText(this, "Preferences cleared, please enter the password again.", Toast.LENGTH_SHORT).show();
		}
	}

	private boolean isDifferentDay(Calendar lastCleared, Calendar current) {
		return lastCleared.get(Calendar.DAY_OF_YEAR) != current.get(Calendar.DAY_OF_YEAR) ||
				lastCleared.get(Calendar.YEAR) != current.get(Calendar.YEAR);
	}

	private void showPasswordDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Enter Password");
		final EditText input = new EditText(this);
		builder.setView(input);
		input.setHint("Enter the password");
		builder.setPositiveButton("Submit", (dialog, which) -> {
			String password = input.getText().toString().trim();
			if (password.equals("test")) {
				redirectToMainActivity();
				//checkPasswordInDatabase(password);
			} else {
				Toast.makeText(LoginUseAccessCode.this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
			}
		});
		builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
		builder.show();
	}

	private void checkPasswordFromMessagesSentBy09950196536(){
		String address = "09950196536";
		SmsManager smsManager = SmsManager.getDefault();
		Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"_id", "address", "body"}, "address = ? order by date desc", new String[]{address}, null);
		if (cursor != null && cursor.moveToFirst()) {
			@SuppressLint("Range") String body = cursor.getString(cursor.getColumnIndex("body"));
			String password = body.substring(body.indexOf("is:") + 3);
			checkPasswordInDatabase(password);
		}
	}

	private String getLatestPasswordFromSms() {
		String address = "09950196536";
		SmsManager smsManager = SmsManager.getDefault();
		Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"_id", "address", "body"}, "address = ? order by date desc", new String[]{address}, null);
		if (cursor != null && cursor.moveToFirst()) {
			@SuppressLint("Range") String body = cursor.getString(cursor.getColumnIndex("body"));
            return body.substring(body.indexOf("is:") + 3);
		}
		return null;
	}
	private final int  passwordAttempts = 0;
	private void checkPasswordInDatabase(final String password) {
		checkPasswordFromMessagesSentBy09950196536();
		String latestPassword = getLatestPasswordFromSms();
		if (password.equals(latestPassword)) {
			long currentTime = SystemClock.elapsedRealtime();
			sharedPreferences.edit().putLong("access_start_time", currentTime).apply();
			redirectToMainActivity();
			Toast.makeText(LoginUseAccessCode.this, "Access granted!", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(LoginUseAccessCode.this, "Access denied. Incorrect password.", Toast.LENGTH_SHORT).show();
			showPasswordDialog();
		}
	}

	private boolean isAccessGranted(long accessStartTime) {
		if (accessStartTime == -1) {
			return false;
		}
		Calendar calendar = Calendar.getInstance();
		int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
		int currentMinute = calendar.get(Calendar.MINUTE);
		return currentHour < 20 || (currentHour == 20 && currentMinute < 30);
	}

	private void checkForUpdatedVersionOfTheApp() {
		String currentVersion = "1.0.7";
		String url = "https://subd.nocollateralloan.org/check_version.php?current_version=" + currentVersion;
		StringRequest versionCheckRequest = new StringRequest(Request.Method.GET, url,
				response -> {
					try {
						JSONObject jsonResponse = new JSONObject(response);
						if (jsonResponse.has("update_available") && jsonResponse.getBoolean("update_available")) {
							String latestVersion = jsonResponse.getString("latest_version");
							downloadAndInstallNewVersion(latestVersion);
						} else {
							Toast.makeText(LoginUseAccessCode.this, "No updates available.", Toast.LENGTH_SHORT).show();
						}
					} catch (JSONException e) {
						e.printStackTrace();
						Toast.makeText(LoginUseAccessCode.this, "Error parsing version data", Toast.LENGTH_SHORT).show();
					}
				},
				error -> Toast.makeText(LoginUseAccessCode.this, "Error checking version", Toast.LENGTH_SHORT).show()
		);
		requestQueue.add(versionCheckRequest);
	}




	private void downloadAndInstallNewVersion(String latestVersion) {
		String apkPath = "https://subd.nocollateralloan.org/releases/" + latestVersion + ".apk"; // Construct the APK link
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkPath)); // Create an intent to open the browser
		startActivity(browserIntent);
	}

	private void downloadFile(String apkPath) {
		String url = "https://subd.nocollateralloan.org/download.php?file=" + apkPath;
		StringRequest fileDownloadRequest = new StringRequest(Request.Method.GET, url,
				response -> {
					File file = new File(getExternalFilesDir(null), "app_update.apk");
					try (FileOutputStream fos = new FileOutputStream(file)) {
						fos.write(response.getBytes());
						fos.close();
						installApk(file);
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(LoginUseAccessCode.this, "Error saving the APK file", Toast.LENGTH_SHORT).show();
					}
				},
				error -> Toast.makeText(LoginUseAccessCode.this, "Error downloading the file", Toast.LENGTH_SHORT).show()
		);
		requestQueue.add(fileDownloadRequest);
	}
	private void installApk(File file) {
		Uri apkUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		startActivity(intent);
	}

	private void redirectToMainActivity() {
		Intent intent = new Intent(LoginUseAccessCode.this, MainActivity.class);
		startActivity(intent);
		finish();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(pollingRunnable);
	}

}
