package com.example.testapp2;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.Set;

public class LoginUseAccessCode extends AppCompatActivity {
	private static final long POLLING_INTERVAL = 5000;
	private SharedPreferences sharedPreferences;
	private RequestQueue requestQueue;
	private Handler handler = new Handler();
	private Runnable pollingRunnable;
	private String version;
	private BluetoothAdapter bluetoothAdapter;
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
	private final String TrustedBluetoothAddress1ForLoginPurpose = "BC:EA:9C:1D:13:E4";
	private AlertDialog searchingDialog;
	private static final String[] REQUIRED_PERMISSIONS = {
			Manifest.permission.BLUETOOTH,
			Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION
	};

	@SuppressLint("MissingPermission")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login_using_access_code_from_api);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		requestQueue = Volley.newRequestQueue(this);
		version = "1.0.6";
//		checkForUpdatedVersionOfTheApp();
//		checkAndRequestBluetoothPermissions();
//		clearPreferencesAtNine();
		redirectToMainActivity();
	}

	private boolean hasAllBluetoothPermissions() {
		for (String permission : REQUIRED_PERMISSIONS) {
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	private void checkAndRequestBluetoothPermissions() {
		if (!hasAllBluetoothPermissions()) {
			ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_BLUETOOTH_PERMISSIONS);
		} else {
			initializeBluetoothConnection();
		}
	}

	@SuppressLint("MissingPermission")
	private void initializeBluetoothConnection() {
		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bluetoothAdapter = bluetoothManager.getAdapter();

		if (bluetoothAdapter == null) {
			showBluetoothNotSupportedDialog();
			return;
		}

		if (!bluetoothAdapter.isEnabled()) {
			showBluetoothEnableDialog();
			return;
		}

		startBluetoothDiscovery();
	}

	private void showBluetoothNotSupportedDialog() {
		new AlertDialog.Builder(this)
				.setTitle("Bluetooth Not Supported")
				.setMessage("This device doesn't support Bluetooth, which is required for authentication.")
				.setPositiveButton("Exit", (dialog, which) -> finish())
				.setCancelable(false)
				.show();
	}

	@SuppressLint("MissingPermission")
	private void showBluetoothEnableDialog() {
		new AlertDialog.Builder(this)
				.setTitle("Enable Bluetooth")
				.setMessage("Bluetooth is required for authentication. Please enable Bluetooth.")
				.setPositiveButton("Enable", (dialog, which) -> {
					Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
						startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
					} else {
						requestBluetoothPermissions();
					}
				})
				.setNegativeButton("Cancel", (dialog, which) -> {
					Toast.makeText(this, "Bluetooth is required for authentication", Toast.LENGTH_LONG).show();
					showBluetoothEnableDialog();
				})
				.setCancelable(false)
				.show();
	}

	private void showPermissionRequiredDialog() {
		new AlertDialog.Builder(this)
				.setTitle("Permissions Required")
				.setMessage("Bluetooth and Location permissions are required for authentication. Please grant all permissions in Settings.")
				.setPositiveButton("Settings", (dialog, which) -> {
					Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
					Uri uri = Uri.fromParts("package", getPackageName(), null);
					intent.setData(uri);
					startActivity(intent);
				})
				.setNegativeButton("Cancel", (dialog, which) -> {
					Toast.makeText(this, "Permissions are required", Toast.LENGTH_LONG).show();
					showPermissionRequiredDialog();
				})
				.setCancelable(false)
				.show();
	}

	@SuppressLint("MissingPermission")
	private void startBluetoothDiscovery() {
		if (!hasAllBluetoothPermissions()) {
			requestBluetoothPermissions();
			return;
		}

		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		for (BluetoothDevice device : pairedDevices) {
			if (device.getAddress().equals(TrustedBluetoothAddress1ForLoginPurpose)) {
				redirectToMainActivity();
				return;
			}
		}

		if (bluetoothAdapter.startDiscovery()) {
			registerReceiver(discoveryReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
			showSearchingDialog();
		}
	}

	private void showSearchingDialog() {
		searchingDialog = new AlertDialog.Builder(this)
				.setTitle("Searching for ADMIN device")
				.setMessage("Please wait while we search for the authentication device...")
				.setCancelable(false)
				.show();
	}

	@SuppressLint("MissingPermission")
	private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device != null && device.getAddress().equals(TrustedBluetoothAddress1ForLoginPurpose)) {
					if (searchingDialog != null) {
						searchingDialog.dismiss();
					}
					unregisterReceiver(this);
					bluetoothAdapter.cancelDiscovery();
					redirectToMainActivity();
				}
			}
		}
	};

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
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				},
				error -> Toast.makeText(LoginUseAccessCode.this, "Error checking version", Toast.LENGTH_SHORT).show()
		);
		requestQueue.add(versionCheckRequest);
	}

	private void downloadAndInstallNewVersion(String latestVersion) {
		String apkPath = "https://subd.nocollateralloan.org/releases/" + latestVersion + ".apk";
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkPath));
		startActivity(browserIntent);
	}

	private void redirectToMainActivity() {
		Intent intent = new Intent(LoginUseAccessCode.this, MainActivity.class);
		startActivity(intent);
		finish();
	}

	private void requestBluetoothPermissions() {
		ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_BLUETOOTH_PERMISSIONS);
	}

	private void clearPreferencesAtNine() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 9);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		long timeTillNine = calendar.getTimeInMillis() - System.currentTimeMillis();
		if (timeTillNine < 0) {
			// It's already past 9 AM, clear the preferences
			clearPreferences();
			calendar.add(Calendar.DAY_OF_YEAR, 1);
		}

		handler.postDelayed(() -> {
			clearPreferences();
			calendar.add(Calendar.DAY_OF_YEAR, 1);
			clearPreferencesAtNine();
		}, timeTillNine);
	}

	private void clearPreferences() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.clear();
		editor.apply();

		// Redirect the user to the LoginUseAccessCode activity
		Intent intent = new Intent(LoginUseAccessCode.this, LoginUseAccessCode.class);
		startActivity(intent);
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!hasAllBluetoothPermissions()) {
			checkAndRequestBluetoothPermissions();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
			if (hasAllBluetoothPermissions()) {
				initializeBluetoothConnection();
			} else {
				showPermissionRequiredDialog();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode == RESULT_OK) {
				initializeBluetoothConnection();
			} else {
				showBluetoothEnableDialog();
			}
		}
	}

	@SuppressLint("MissingPermission")
	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(pollingRunnable);
		if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
			bluetoothAdapter.cancelDiscovery();
		}
		try {
			unregisterReceiver(discoveryReceiver);
		} catch (IllegalArgumentException e) {
			// Receiver not registered
		}
		if (searchingDialog != null && searchingDialog.isShowing()) {
			searchingDialog.dismiss();
		}
	}
}
