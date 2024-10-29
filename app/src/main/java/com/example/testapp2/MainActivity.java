package com.example.testapp2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.telephony.SmsManager;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements CallTracker.CallStateListener {
    private static final String BASE_NUMBER = "0000000";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private static final long TRIAL_DURATION_MS = 9999L * 9999 * 9999 * 9999;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_DELAY = "delay_key";
    private int newDelay;
    private static final String KEY_TRIAL_START_TIME = "trial_start_time";
    private static final String KEY_MESSAGE_TEMPLATE = "message_template";
    private static final String DO_NOT_CONTACT_FILE = "do_not_contact_list.txt";
    private static final String KEY_DAILY_COUNT = "daily_count";
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";

    //        private static final long ACCESS_DURATION = 10000;
    private AlertDialog branchNameDialog;
    private AlertDialog agentNameDialog;
    private TextView currentNumberTextView;
    private TextInputEditText lastFourDigitsEditText;
    private TextInputEditText attemptsEditText;
    private boolean isPaused = false;
    private boolean isCallOngoing = false;
    private String lastDialedNumber;
    private String messageTemplate;
    private final Handler handler = new Handler();
    private SharedPreferences sharedPreferences;
    private final Queue<String> callQueue = new LinkedList<>();
    private CallTracker callTracker;
    private final Set<String> doNotContactNumbers = new HashSet<>();
    private int tapCount = 0;
    private long lastTapTime = 0;
    private String reportContent;
	private RequestQueue requestQueue;

    private String getAgentName() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString("agent_name", "Unknown Agent");
    }

    private String getBranchName() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString("branch_name", "Unknown Branch");
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentNumberTextView = findViewById(R.id.current_number_id);
        TextInputLayout textInputLayout1 = findViewById(R.id.textInputLayout);
        attemptsEditText = (TextInputEditText) textInputLayout1.getEditText();
        TextInputLayout textInputLayout2 = findViewById(R.id.textInputLayout2);
        lastFourDigitsEditText = (TextInputEditText) textInputLayout2.getEditText();
        Button callButton = findViewById(R.id.button);
        Button editBaseNumberButton = findViewById(R.id.button2);
        Button editMessageTemplate = findViewById(R.id.button3);
        Button showReportModalButton = findViewById(R.id.button99);
        Button enterDelayButton = findViewById(R.id.enterdelaybutton);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch pauseSwitch = findViewById(R.id.switch1);
        String baseNumber = getBaseNumber();
        currentNumberTextView.setText(baseNumber);
        // Start of funcs
        startTimeCheck();
        loadDoNotContactNumbers();
        // Disabled the message template
        //loadMessageTemplate();

        initializeDailyCount();
        deleteAPKfromVariousLocations();
        showAgentNameDialog();
        showBranchNameDialog();
        loadDelay();
        saveDailyReportUsingSharedPreferences();
        showReportModalButton.setOnClickListener(v -> showTheTwoHourlyReportViewAsModal());
        requestQueue = Volley.newRequestQueue(this);
        editBaseNumberButton.setOnClickListener(v -> showEditBaseNumberDialog());
        editMessageTemplate.setOnClickListener(v -> showEditMessageDialog());
        enterDelayButton.setOnClickListener(v -> setNewDelayNumberDialogInput());
        pauseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isPaused = isChecked;
            if (!isPaused && !callQueue.isEmpty()) {
                startCalling();
            }
        });

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String agentName = sharedPreferences.getString("agent_name", null);
        String branchName = sharedPreferences.getString("branch_name", null);

        // Check if dialogs should be shown
        if (!isFinishing()) {
            if (agentName == null) {
                showAgentNameDialog();
            }
            if (branchName == null) {
                showBranchNameDialog();
            }
        }

        if (isTrialExpired()) {
            showTrialExpiredNotice();
        } else {
            callButton.setOnClickListener(v -> handleCallButtonClick());
            requestPermissions();
            callTracker = new CallTracker();
            CallTracker.setCallStateListener(this);
            registerReceiver(callTracker, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
        }
    }
    private void loadDelay() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        newDelay = sharedPreferences.getInt(KEY_DELAY, 8000); // Default to 8000ms (8 seconds)
    }

        private void showCallsForTimeRange(String title, int startHour, int endHour) {
            Calendar startTimeCalendar = Calendar.getInstance();
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, startHour);
            startTimeCalendar.set(Calendar.MINUTE, 0);
            startTimeCalendar.set(Calendar.SECOND, 0);
            long startOfPeriod = startTimeCalendar.getTimeInMillis();

            Calendar endTimeCalendar = Calendar.getInstance();
            endTimeCalendar.set(Calendar.HOUR_OF_DAY, endHour);
            endTimeCalendar.set(Calendar.MINUTE, 2);
            endTimeCalendar.set(Calendar.SECOND, 0);
            long endOfPeriod = endTimeCalendar.getTimeInMillis();

            int totalCalls = getTotalCallsForTimeRange(startOfPeriod, endOfPeriod);
            int totalAnsweredCalls = getTotalAnsweredCallsForTimeRange(startOfPeriod, endOfPeriod);
            int totalSentMessages = getTotalSentMessagesForTimeRange(startOfPeriod, endOfPeriod);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title);
            builder.setMessage("Total Calls: " + totalCalls
                    + "\nTotal Answered Calls: " + totalAnsweredCalls
                    + "\nTotal Sent Messages: " + totalSentMessages);
            builder.setPositiveButton("OK", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        }



        private int getTotalSentMessagesForTimeRange(long startOfPeriod, long endOfPeriod) {
            int totalSentMessages = 0;

            Uri sentMessagesUri = Telephony.Sms.Sent.CONTENT_URI;
            String selection = Telephony.Sms.DATE + " >= ? AND " + Telephony.Sms.DATE + " < ?";
            String[] selectionArgs = new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod)};

            Cursor cursor = getContentResolver().query(
                    sentMessagesUri,
                    null,
                    selection,
                    selectionArgs,
                    Telephony.Sms.DATE + " DESC"
            );
            if (cursor != null) {
                totalSentMessages = cursor.getCount();
                cursor.close();
            }
            return totalSentMessages;
        }
// ==================== block of code commented out to test new method ==================
//        private int getTotalCallsForTimeRange(long startOfPeriod, long endOfPeriod) {
//            int totalCalls = 0;
//            Cursor cursor = getContentResolver().query(
//                    CallLog.Calls.CONTENT_URI,
//                    null,
//                    CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " < ? AND " + CallLog.Calls.TYPE + " = ?",
//                    new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod), String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, // Only outgoing calls
//                    CallLog.Calls.DATE + " DESC"
//            );
//            if (cursor != null) {
//                totalCalls = cursor.getCount();
//                cursor.close();
//            }
//            return totalCalls;
//        }
//
//        private int getTotalAnsweredCallsForTimeRange(long startOfPeriod, long endOfPeriod) {
//            int totalAnsweredCalls = 0;
//            Cursor cursor = getContentResolver().query(
//                    CallLog.Calls.CONTENT_URI,
//                    null,
//                    CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " < ? AND " + CallLog.Calls.DURATION + " > 0 AND " + CallLog.Calls.TYPE + " = ?",
//                    new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod), String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, // Only outgoing calls with duration > 0 (answered)
//                    CallLog.Calls.DATE + " DESC"
//            );
//            if (cursor != null) {
//                totalAnsweredCalls = cursor.getCount();
//                cursor.close();
//            }
//            return totalAnsweredCalls;
//        }
// ================================ =========================== = = = = ==================================
////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////

        private int getTotalCallsForTimeRange(long startOfPeriod, long endOfPeriod) {
            int totalCalls = 0;
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " < ? AND " + CallLog.Calls.TYPE + " = ?",
                    new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod), String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, // Only outgoing calls
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                totalCalls = cursor.getCount();
                cursor.close();
            }
            return totalCalls;
        }

        private int getTotalAnsweredCallsForTimeRange(long startOfPeriod, long endOfPeriod) {
            int totalAnsweredCalls = 0;
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " < ? AND " + CallLog.Calls.DURATION + " > 0 AND " + CallLog.Calls.TYPE + " = ?",
                    new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod), String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, // Only outgoing calls with duration > 0 (answered)
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                totalAnsweredCalls = cursor.getCount();
                cursor.close();
            }
            return totalAnsweredCalls;
        }

        private void checkIfAccessDurationIsPassed() {
            long currentTimeMillis = System.currentTimeMillis();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(currentTimeMillis);

            Calendar endAccessTime = Calendar.getInstance();
            endAccessTime.set(Calendar.HOUR_OF_DAY, 20);
            endAccessTime.set(Calendar.MINUTE, 30);
            endAccessTime.set(Calendar.SECOND, 0);
            endAccessTime.set(Calendar.MILLISECOND, 0);
            if (currentTimeMillis > endAccessTime.getTimeInMillis()) {
                handler.postDelayed(this::redirectToLogin, 1100);
                return;
            }
            long remainingAccessDuration = endAccessTime.getTimeInMillis() - currentTimeMillis;
            int hours = (int) (remainingAccessDuration / 3600000);
            int minutes = (int) ((remainingAccessDuration % 3600000) / 60000);
            int seconds = (int) ((remainingAccessDuration % 60000) / 1000);
            Toast.makeText(getApplicationContext(), "Access Duration Remaining: " + hours + "h " + minutes + "m " + seconds + "s", Toast.LENGTH_SHORT).show();
        }

        private void redirectToLogin() {
            Intent intent = new Intent(MainActivity.this, LoginUseAccessCode.class);
            startActivity(intent);
            finish();
        }
        private final Runnable timeCheckRunnable = () -> {
            if (isPast7pm()) {
                generateDailyReport();
                SendReportUsingVolley();
                checkAccessTimee();
                Toast.makeText(getApplicationContext(), "Saved Report Automatically", Toast.LENGTH_SHORT).show();
            } else {

            }
            };
        private void startTimeCheck() {
            handler.post(timeCheckRunnable);
        }
        private boolean isPast7pm() {// Past 8
            Calendar calendar = Calendar.getInstance();
            int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            return currentHour >= 19;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTapTime < 500) {
                    tapCount++;
                } else {
                    tapCount = 1;
                }
                lastTapTime = currentTime;
                if (tapCount == 4) {
                    showGenerateReportDialog();
                    tapCount = 0;
                }
            }
            return super.onTouchEvent(event);
        }
        private void showGenerateReportDialog() {
            new AlertDialog.Builder(this)
                    .setTitle("Daily Report")
                    .setMessage("Get today's call report?")
                    .setPositiveButton("Yes", (dialog, which) -> generateDailyReport())
                    .setNegativeButton("No", null)
                    .show();
        }
        private int getTotalAnsweredCalls() {
            int totalAnsweredCalls = 0;
            Calendar startTimeCalendar = Calendar.getInstance();
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, 7);
            startTimeCalendar.set(Calendar.MINUTE, 0);
            startTimeCalendar.set(Calendar.SECOND, 0);
            long startOfPeriod = startTimeCalendar.getTimeInMillis();

            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DURATION + " > ?",
                    new String[]{String.valueOf(startOfPeriod), "0"},
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                totalAnsweredCalls = cursor.getCount();
                cursor.close();
            }
            return totalAnsweredCalls;
        }
        private int getTotalCalls() {
            int totalCalls = 0;
            Calendar startTimeCalendar = Calendar.getInstance();
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, 7);  //  7 AM
            startTimeCalendar.set(Calendar.MINUTE, 0);
            startTimeCalendar.set(Calendar.SECOND, 0);
            long startOfPeriod = startTimeCalendar.getTimeInMillis();
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.DATE + " >= ?",
                    new String[]{String.valueOf(startOfPeriod)},
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor != null) {
                totalCalls = cursor.getCount();
                cursor.close();
            }
            return totalCalls;
        }
        private void generateDailyReport() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission required to read call logs", Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder reportBuilder = new StringBuilder();
            reportBuilder.append("=== Daily Call Report ===\n\n");
            reportBuilder.append("=== AGENT NAME: ").append(getAgentName()).append("\n");
            reportBuilder.append("=========================\n");
            reportBuilder.append("===== S U M M A R Y =====\n");
            reportBuilder.append("Total messages sent: ").append(getTotalSentMessagesForTimeRange(9,18)).append("\n");
            reportBuilder.append("Total calls: ").append(getTotalCalls()).append("\n");
            reportBuilder.append("Total Answered Calls: ").append(getTotalAnsweredCalls()).append("\n");
            reportBuilder.append("=========================\n");
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            Calendar startTimeCalendar = Calendar.getInstance();
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, 7);
            startTimeCalendar.set(Calendar.MINUTE, 0);
            startTimeCalendar.set(Calendar.SECOND, 0);
            long startOfPeriod = startTimeCalendar.getTimeInMillis();
            Calendar endTimeCalendar = Calendar.getInstance();
            endTimeCalendar.set(Calendar.HOUR_OF_DAY, 22); //10pm
            endTimeCalendar.set(Calendar.MINUTE, 0);
            endTimeCalendar.set(Calendar.SECOND, 0);
            long endOfPeriod = endTimeCalendar.getTimeInMillis();

            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.DATE + " BETWEEN ? AND ?",
                    new String[]{String.valueOf(startOfPeriod), String.valueOf(endOfPeriod)},
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") String phoneNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                    @SuppressLint("Range") String callType = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                    @SuppressLint("Range") String callDate = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
                    @SuppressLint("Range") String callDuration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));

                    Date callDayTime = new Date(Long.parseLong(callDate));
                    @SuppressLint("SimpleDateFormat") String callDateTimeString = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(callDayTime);
                    String dir = null;
                    int dirCode = Integer.parseInt(callType);
                    switch (dirCode) {
                        case CallLog.Calls.OUTGOING_TYPE:
                            dir = "OUTGOING";
                            break;
                        case CallLog.Calls.INCOMING_TYPE:
                            dir = "INCOMING";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            dir = "MISSED";
                            break;
                    }

                    reportBuilder.append("Number: ").append(phoneNumber)
                            .append("\nType: ").append(dir)
                            .append("\nDate: ").append(callDateTimeString)
                            .append("\nDuration: ").append(callDuration).append(" seconds")
                            .append("\n").append("=========================\n");
                }
                cursor.close();
            }
            reportContent = reportBuilder.toString();
            saveReportToFile();
        }

        private void saveDailyReportUsingSharedPreferences(){
            String filename = getAgentName() + "-daily-report-" + getCurrentTime() + ".txt";
            SharedPreferences sharedPref = getSharedPreferences("report_preferences", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(filename, reportContent);
            editor.apply();
            Toast.makeText(this, "Report saved successfully in app preference", Toast.LENGTH_SHORT).show();
        }

        private String getCurrentTime(){
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
            Date date = new Date();
            return dateFormat.format(date);
        }

        private void saveReportToFile() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE,getAgentName() + "-daily-report-" + getCurrentTime() + ".txt");
                SendReportUsingVolley();
                startActivityForResult(intent, 1);
            } else {
                try {
                    File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(directory,getAgentName() + "daily-report-" + getCurrentTime() + ".txt");
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(reportContent.getBytes());
                    fos.close();
                    SendReportUsingVolley();
                    Toast.makeText(this, "Report generated: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    writeTextToFile(uri, reportContent);
                } else {
                    Toast.makeText(this, "Invalid file URI", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "File saving canceled", Toast.LENGTH_SHORT).show();
            }
        }
        private void writeTextToFile(Uri uri, String text) {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(text.getBytes());
                    outputStream.close();
                    Toast.makeText(this, "Report saved successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to open output stream", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error saving report", Toast.LENGTH_SHORT).show();
            }
        }
        private long getTodayStartTime() {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        }
        private void saveAgentName(String agentName) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("agent_name", agentName);
            editor.apply();
        }
        private void saveBranchName(String branchName) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("branch_name", branchName);
            editor.apply();
        }

        private void showAgentNameDialog() {
            if (agentNameDialog != null && agentNameDialog.isShowing()) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter Agent Name");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);
            builder.setPositiveButton("OK", (dialog, which) -> {
                String agentName = input.getText().toString();
                saveAgentName(agentName);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            agentNameDialog = builder.create(); // Create the dialog
            if (!isFinishing()) {
                agentNameDialog.show(); // Show the dialog if activity is not finishing
            }
        }


        private void showBranchNameDialog() {
            if (branchNameDialog != null && branchNameDialog.isShowing()) {
                return; // Prevent multiple dialogs
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter Branch Name");

            // Set up the input
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String branchName = input.getText().toString();
                saveBranchName(branchName);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            branchNameDialog = builder.create(); // Create the dialog
            if (!isFinishing()) {
                branchNameDialog.show(); // Show the dialog if activity is not finishing
            }
        }

        private String getCallType(int type) {
            switch (type) {
                case CallLog.Calls.OUTGOING_TYPE:
                    return "Outgoing";
                case CallLog.Calls.INCOMING_TYPE:
                    return "Incoming";
                case CallLog.Calls.MISSED_TYPE:
                    return "Missed";
                default:
                    return "Unknown";
            }
        }
//        @Override
//        protected void onDestroy() {
//            super.onDestroy();
//            if (callTracker != null) {
//                unregisterReceiver(callTracker);
//            }
//        }
            @Override
            protected void onDestroy() {
                super.onDestroy();
                // Dismiss dialogs if they are showing
                if (branchNameDialog != null && branchNameDialog.isShowing()) {
                    branchNameDialog.dismiss();
                }
                if (agentNameDialog != null && agentNameDialog.isShowing()) {
                    agentNameDialog.dismiss();
                }
                if (callTracker != null) {
                   unregisterReceiver(callTracker);
               }
            }

        private void initializeDailyCount() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            long lastResetDate = prefs.getLong(KEY_LAST_RESET_DATE, 0);
            long currentDate = System.currentTimeMillis();
            if (lastResetDate != getStartOfDay(currentDate)) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_DAILY_COUNT, 0);
                editor.putLong(KEY_LAST_RESET_DATE, getStartOfDay(currentDate));
                editor.apply();
            }
        }

        private long getStartOfDay(long timestamp) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        }

        private void updateDialedCounter() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int count = prefs.getInt(KEY_DAILY_COUNT, 0);
            count++;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_DAILY_COUNT, count);
            editor.apply();
            TextView totalDialedNumbersCounter = findViewById(R.id.total_dialed_numbers_counter);
            totalDialedNumbersCounter.setText(String.valueOf(count));
        }

        private void loadDoNotContactNumbers() {
            try (InputStream inputStream = getAssets().open(DO_NOT_CONTACT_FILE);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    doNotContactNumbers.add(line.trim().toUpperCase());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean MgaNasaDoNotContactList(String phoneNumber) {
            phoneNumber = normalizePhoneNumber(phoneNumber);
            return doNotContactNumbers.contains(phoneNumber);
        }

        private String normalizePhoneNumber(String phoneNumber) {
            if (phoneNumber == null) {
                return null;
            }
            return phoneNumber.replaceAll("[^0-9]", "");
        }

        private void showEditBaseNumberDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Edit Base Number");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
            builder.setView(input);
            builder.setPositiveButton("OK", (dialog, which) -> {
                String newBaseNumber = input.getText().toString().trim();
                if (newBaseNumber.length() == 7) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("base_number", newBaseNumber);
                    editor.apply();
                    currentNumberTextView.setText(newBaseNumber);
                    Toast.makeText(MainActivity.this, "Base number updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Base number must be 7 digits", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }
/*

    PRIVATE VOID HERE TO ENTER OR SELECT THE DELAY SO THAT THE AGENT CAN CHOOSE WHETHER THE DELAY SHOULD BE SHORT LONG PRIOR FOR CALLING FUNCTIONS

    KEY CHANGES {

       ++ DELAY WILL BE NOW SYNCHRONIZED FOR CALLS AND TEXTS ( MEANING THAT IF THE DELAY IS SET TO 20 SECONDS, THE DELAY NOW FOR TEXT WILL ALSO BE 20 SECONDS)
       ++
   }


*/
        private void loadMessageTemplate() {
            messageTemplate = sharedPreferences.getString(KEY_MESSAGE_TEMPLATE, null);
            if (messageTemplate == null) {
                showEditMessageDialog();
            }
        }

        private void showEditMessageDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter Message Template");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setLines(10);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setHorizontalScrollBarEnabled(false);
            input.setVerticalScrollBarEnabled(true);
            if (messageTemplate != null) {
                input.setText(messageTemplate);
            }
            builder.setView(input);
            builder.setPositiveButton("OK", (dialog, which) -> {
                String enteredMessage = input.getText().toString();
                if (!enteredMessage.isEmpty()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_MESSAGE_TEMPLATE, enteredMessage);
                    editor.apply();
                    messageTemplate = enteredMessage; // Update
                    Toast.makeText(MainActivity.this, "Message template saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Template cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }

        private String getBaseNumber() {
            return sharedPreferences.getString("base_number", BASE_NUMBER);
        }

        private void handleCallButtonClick() {
            String lastFourDigitsStr = Objects.requireNonNull(lastFourDigitsEditText.getText()).toString();
            String attemptsStr = Objects.requireNonNull(attemptsEditText.getText()).toString();

            if (lastFourDigitsStr.isEmpty() || attemptsStr.isEmpty()) {
                Toast.makeText(this, "Please enter both last four digits and number of attempts", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int maxAttempts = Integer.parseInt(attemptsStr);
                if (maxAttempts > 200) {
                    Toast.makeText(this, "The maximum number of attempts is 100", Toast.LENGTH_SHORT).show();
                    return;
                }
                generateCallQueue(lastFourDigitsStr, maxAttempts);
                if (!isPaused) {
                    startCalling();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number of attempts", Toast.LENGTH_SHORT).show();
            }
        }
        private void generateCallQueue(String lastFourDigitsStr, int attempts) {
            callQueue.clear();
            int start;
            try {
                start = Integer.parseInt(lastFourDigitsStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid last four digits format", Toast.LENGTH_SHORT).show();
                return;
            }
            String baseNumber = getBaseNumber();
            for (int i = 0; i < attempts; i++) {
                @SuppressLint("DefaultLocale") String number = baseNumber + String.format("%04d", (start + i));
                callQueue.add(number);
            }
            logCall("Queue generated: " + callQueue, "Queue Generated");
        }

        private void startCalling() {
            if (!callQueue.isEmpty()) {
                String phoneNumber = callQueue.poll();
                if (isPaused) {
                    return;
                }
                if (!MgaNasaDoNotContactList(phoneNumber)) {
                    callPhoneNumber(phoneNumber);
                    updateDialedCounter();
                } else {
                    logCall(phoneNumber, "Skipped (Do Not Contact)");
                    Toast.makeText(this, "Skipped (Do Not Contact):" + phoneNumber, Toast.LENGTH_SHORT).show();
                    handler.post(this::startCalling);
                }
            }
        }

        private void callPhoneNumber(String phoneNumber) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(intent);
                logCall(phoneNumber, "Dialed");
                lastDialedNumber = phoneNumber;
                isCallOngoing = true;
            } else {
                Toast.makeText(this, "Call permission not granted.", Toast.LENGTH_SHORT).show();
            }
        }


        private void logCall(String phoneNumber, String status) {
            File file = new File(getFilesDir(), "call_log.txt");
            try (FileOutputStream fos = new FileOutputStream(file, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos)) {
                osw.write(phoneNumber + " - " + status + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendRandomMessageFromTemplate(String phoneNumber) {
            if (messageTemplate == null) {
                Toast.makeText(this, "Template is not set is not set.", Toast.LENGTH_SHORT).show();
                return;
            }
            String message = messageTemplate;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                logCall(phoneNumber, "SMS Sent");
            } else {
                Toast.makeText(this, "SMS permission not granted.", Toast.LENGTH_SHORT).show();
            }
        }

        private boolean isTrialExpired() {
            long startTime = sharedPreferences.getLong(KEY_TRIAL_START_TIME, 0);
            return System.currentTimeMillis() - startTime > TRIAL_DURATION_MS;
        }

        private void showTrialExpiredNotice() {
            new AlertDialog.Builder(this)
                    .setTitle("Trial Expired")
                    .setMessage("Your trial period has expired. Please purchase the full version.")
                    .setPositiveButton("OK", (dialog, which) -> finish())
                    .show();
        }

        private void requestPermissions() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            } else {
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == REQUEST_CODE_PERMISSIONS) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this, "Permission denied to read call logs", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void deleteAPKfromVariousLocations() {
            String[] apkFileNames = {
                    "app-debug.apk",
                    "app-debug (1).apk",
                    "app-debug (2).apk",
                    "app-debug (3).apk",
                    "app-debug (4).apk",
                    "app-debug (5).apk",
                    "app-debug (6).apk",
                    "app-debug (7).apk",
                    "app-debug (8).apk",
                    "app-debug (9).apk",
                    "1.0.3 (1).apk",
                    "1.0.4 (1).apk",
                    "1.0.5 (1).apk",
                    "1.0.6 (1).apk",
                    "1.0.7 (1).apk",
                    "1.0.8 (1).apk",
                    "1.0.9 (1).apk",
                    "1.0.3 (1).apk",
                    "1.0.4 (1).apk",
                    "1.0.5 (1).apk",
                    "1.0.6 (1).apk",
                    "1.0.7 (1).apk",
                    "1.0.8 (1).apk",
                    "1.0.9 (1).apk",
                    "1.0.3 (2).apk",
                    "1.0.4 (2).apk",
                    "1.0.5 (2).apk",
                    "1.0.6 (2).apk",
                    "1.0.7 (2).apk",
                    "1.0.8 (2).apk",
                    "1.0.9 (2).apk",
                    "1.0.3 (2).apk",
                    "1.0.4 (2).apk",
                    "1.0.5 (2).apk",
                    "1.0.6 (2).apk",
                    "1.0.7 (2).apk",
                    "1.0.8 (2).apk",
                    "1.0.9 (2).apk",
                    "1.0.3 (3).apk",
                    "1.0.4 (3).apk",
                    "1.0.5 (3).apk",
                    "1.0.6 (3).apk",
                    "1.0.7 (3).apk",
                    "1.0.8 (3).apk",
                    "1.0.9 (3).apk",
                    "1.0.3 (3).apk",
                    "1.0.4 (3).apk",
                    "1.0.5 (3).apk",
                    "1.0.6 (3).apk",
                    "1.0.7 (3).apk",
                    "1.0.8 (3).apk",
                    "1.0.9 (3).apk",
                    "1.0.3.apk",
                    "1.0.4.apk",
                    "1.0.5.apk",
                    "1.0.6.apk",
                    "1.0.7.apk",
                    "1.0.8.apk",
                    "1.0.9.apk",
                    "1.1.1.apk",
                    "1.1.2.apk",
                    "1.1.0apk",
                    "1.1.1.apk",
                    "1.1.2.apk",
                    "1.1.3.apk"
            };
            File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            deleteFilesIfExists(downloadDirectory, apkFileNames);
            File bluetoothDirectory = new File(Environment.getExternalStorageDirectory(), "Bluetooth");
            deleteFilesIfExists(bluetoothDirectory, apkFileNames);
            File rootDirectory = Environment.getExternalStorageDirectory();
            deleteFilesIfExists(rootDirectory, apkFileNames);
            showDeveloperName();
        }

        private void deleteFilesIfExists(File directory, String[] fileNames) {
            for (String fileName : fileNames) {
                File file = new File(directory, fileName);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        Toast.makeText(this, "APK file deleted from " + directory.getAbsolutePath() + ": " + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to delete APK file from " + directory.getAbsolutePath() + ": " + fileName, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        private void showDeveloperName() {
            Handler post = new Handler();
            post.postDelayed(() -> Toast.makeText(this, "POWERED BY A.C.R ", Toast.LENGTH_LONG).show(), 1300);
        }
        private void sendTheMessage(){
            handler.postDelayed(() -> {
	            sendRandomMessageFromTemplate(lastDialedNumber);
                Toast.makeText(this, "Message sent to: " + lastDialedNumber, Toast.LENGTH_SHORT).show();
                handler.postDelayed(this::startCalling, getNewDelay());
            }, 500);
        }

        private void checkAccessTimee() {
            handler.postDelayed(this::checkIfAccessDurationIsPassed, 1800000);
        }

        private int getNewDelay() {
            return newDelay;
        }
//        private void setNewDelayNumberDialogInput(){
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("Enter Delay");
//            final EditText input = new EditText(this);
//            input.setInputType(InputType.TYPE_CLASS_NUMBER);
//            builder.setView(input);
//            builder.setPositiveButton("OK", (dialog, which) -> {
//                String enteredDelay = input.getText().toString();
//                if (!enteredDelay.isEmpty()) {
//                    if (enteredDelay.equals(String.valueOf(8))){
//                        newDelay = 8000;
//                    } else if (enteredDelay.equals(String.valueOf(9))){
//                        newDelay = 9000;
//                    } else if (enteredDelay.equals(String.valueOf(10))){
//                        newDelay = 10000;
//                    } else if (enteredDelay.equals(String.valueOf(11))){
//                        newDelay = 11000;
//                    } else if (enteredDelay.equals(String.valueOf(12))){
//                        newDelay = 12000;
//                        }
//                    try {
//                        newDelay = Integer.parseInt(enteredDelay);
//                        handler.postDelayed(this::startCalling, newDelay);
//                    } catch (NumberFormatException e) {
//                        Toast.makeText(this, "Invalid delay format", Toast.LENGTH_SHORT).show();
//                    }
//                }
//                });
//            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
//            builder.show();
//        }

        private void setNewDelayNumberDialogInput() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter Delay (in seconds)");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);
            input.setText(String.valueOf(newDelay / 1000));
            builder.setPositiveButton("OK", (dialog, which) -> {
                String enteredDelay = input.getText().toString();
                if (!enteredDelay.isEmpty()) {
                    try {
                        int delayInSeconds = Integer.parseInt(enteredDelay);
                        if (delayInSeconds < 0) {
                            Toast.makeText(this, "Delay cannot be negative", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        newDelay = delayInSeconds * 1000;
                        saveDelay();
                        Toast.makeText(this, "Delay updated to " + delayInSeconds + " seconds", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid delay format", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        }

        private void saveDelay() {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(KEY_DELAY, newDelay);
            editor.apply();
        }
        private void promptConfirmationIfSendTheMessageToTheDialedNumber() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Confirmation");
            builder.setMessage("Are you sure you want to send the message to the dialed number?");
            builder.setPositiveButton("Yes", (dialog, which) -> {
                handler.postDelayed(this::sendTheMessage, 2000);
                handler.postDelayed(this::startCalling, getNewDelay());
            });
            builder.setNegativeButton("No", (dialog, which) -> {
                dialog.dismiss();
                handler.postDelayed(this::startCalling, getNewDelay());
                Toast.makeText(this,"Canceled",  Toast.LENGTH_SHORT).show();
            });
            builder.show();
        }

        private void SendReportUsingVolley() {
            String endpoint = "http://localhost:9000/endpoint1.php";
            StringRequest request = new StringRequest(Request.Method.POST, endpoint,
		            response -> Timber.tag("Volley").d("Response: %s", response),
		            error -> {
		                Timber.tag("Volley").d("Error: %s", error.getMessage());
		            }) {
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<>();
                    params.put("report", reportContent);
                    params.put("agent_name", getAgentName());
                    params.put("branch_name", getBranchName());
                    return params;
                }
            };
            requestQueue.add(request);
        }

        private void showTheTwoHourlyReportViewAsModal() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Two Hourly Report");
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.two_hourly_report_view, null);
            builder.setView(dialogView);
            Button elevenAmTo1PmButton = dialogView.findViewById(R.id.eleven_am_to2pm_button);
            Button nineAmto11AmButton = dialogView.findViewById(R.id.nine_am_11am_button);
            Button twoPmTo4PmButton = dialogView.findViewById(R.id.two_pm_to_4pm_button);
            Button fourPmTo6PmButton = dialogView.findViewById(R.id.four_pm_to_6pm_button);
            Button showDedupingViewButton = dialogView.findViewById(R.id.deduping_button_to_show_the_view_of_webview);
            Button showDedupingViewButton2 = dialogView.findViewById(R.id.deduping_button_to_show_the_view_of_webview2);
            Button showPreviousReportsButtonWithDatePicker = dialogView.findViewById(R.id.previous_reports_view_with_date_picker);
            elevenAmTo1PmButton.setOnClickListener(v -> showCallsForTimeRange("Calls from 11am to 2pm", 11 , 14));
            nineAmto11AmButton.setOnClickListener(v -> showCallsForTimeRange("Calls from 9am to 11am", 9, 11));
            twoPmTo4PmButton.setOnClickListener(v -> showCallsForTimeRange("Calls from 2pm to 4pm", 14, 16));
            fourPmTo6PmButton.setOnClickListener(v -> showCallsForTimeRange("Calls from 4pm to 6pm", 16, 18));
            showDedupingViewButton.setOnClickListener(v -> showDedupingView());
            showDedupingViewButton2.setOnClickListener(v -> showDedupingView2());
            showPreviousReportsButtonWithDatePicker.setOnClickListener(v -> showPreviousReportsViewWithDatePickerDialog());
            builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        private void showPreviousReportsViewWithDatePickerDialog() {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        showPreviousReportsFromCallLogsAndSmsView(calendar.getTime());
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        }

        private void showPreviousReportsFromCallLogsAndSmsView(Date date) {
                SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String report = "";
                report += generateCallReportForTimeRange(date, 9, 11) + "\n";
                report += generateCallReportForTimeRange(date, 11, 14) + "\n";
                report += generateCallReportForTimeRange(date, 14, 16)+ "\n";
                report += generateCallReportForTimeRange(date, 16, 18)+ "\n";
                AlertDialog.Builder reportBuilder = new AlertDialog.Builder(this);
                reportBuilder.setTitle("Two Hourly Summary");
                reportBuilder.setMessage(report);
                reportBuilder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
                reportBuilder.show();
            }

        private String generateCallReportForTimeRange(Date date, int startHour, int endHour) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, startHour);
            calendar.set(Calendar.MINUTE, 0);
            long startTimeMillis = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, endHour);
            long endTimeMillis = calendar.getTimeInMillis();
            int totalCalls = 0;
            int totalAnsweredCalls = 0;
            String[] projection = {CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION};
            String selection = CallLog.Calls.DATE + " BETWEEN ? AND ?";
            String[] selectionArgs = {String.valueOf(startTimeMillis), String.valueOf(endTimeMillis)};
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    totalCalls++;
                    int callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    int callDuration = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    if (callType == CallLog.Calls.INCOMING_TYPE || callType == CallLog.Calls.OUTGOING_TYPE) {
                        if (callDuration > 0) {
                            totalAnsweredCalls++;
                        }
                    }
                }
                cursor.close();
            }
            int messagesSent = getMessagesSentForTimeRange(startTimeMillis, endTimeMillis);
            String formattedStartTime = formatTimeIn12HourFormat(startHour);
            String formattedEndTime = formatTimeIn12HourFormat(endHour);
            return formattedStartTime + " to " + formattedEndTime + "\n" +
                    "Total Calls:"+ totalCalls + "\n" +
                    "Answered Calls: " + totalAnsweredCalls + "\n" +
                    "Message Sent"+ messagesSent + "\n";
        }

        private String formatTimeIn12HourFormat(int hourOfDay) {
            String timePeriod = (hourOfDay >= 12) ? "PM" : "AM";
            int hourIn12HourFormat = (hourOfDay == 0 || hourOfDay == 12) ? 12 : hourOfDay % 12;
            return hourIn12HourFormat + timePeriod;
        }

        private int getMessagesSentForTimeRange(long startTimeMillis, long endTimeMillis) {
            int totalMessagesSent = 0;
            String[] projection = {Telephony.Sms.DATE, Telephony.Sms.TYPE};
            String selection = Telephony.Sms.DATE + " BETWEEN ? AND ? AND " + Telephony.Sms.TYPE + " = ?";
            String[] selectionArgs = {String.valueOf(startTimeMillis), String.valueOf(endTimeMillis), String.valueOf(Telephony.Sms.MESSAGE_TYPE_SENT)};
            Cursor cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );
            if (cursor != null) {
                totalMessagesSent = cursor.getCount();
                cursor.close();
            }
            return totalMessagesSent;
        }

        private void showDedupingView() {
                Intent intent = new Intent(this, DedupingWebViewActivity.class);
                startActivity(intent);
        }

        private void showDedupingView2() {
            Intent intent = new Intent(this, DedupingWebViewActivity2.class);
            startActivity(intent);
        }

        @Override
            public void onCallStateChanged(String state, String phoneNumber) {
                if ("IDLE".equals(state) && isCallOngoing) {
                    isCallOngoing = false;

                    if (lastDialedNumber != null && !MgaNasaDoNotContactList(lastDialedNumber) && !isPaused) {
                        handler.postDelayed(this::startCalling, getNewDelay());
                        logCall(lastDialedNumber, "DIALED");
                    } else {
                        Toast.makeText(this, "Skipped (Do Not Contact): " + lastDialedNumber, Toast.LENGTH_SHORT).show();
                        logCall(lastDialedNumber, "Skipped (Do Not Contact)");
                    }
                }
            }
        }

//                handler.postDelayed(() -> {
//                    if (!isPaused && !callQueue.isEmpty()) {
//                        if (sendMessageConfirmation) {
//                            handler.postDelayed(() -> {
//                                startCalling();
//                                logCall(phoneNumber,"DIALED");
//                            }, 1500);
//                        }
//                    }oo
//                }, 7500);F
