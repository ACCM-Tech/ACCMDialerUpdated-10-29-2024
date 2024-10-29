package com.example.testapp2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class CallTracker extends BroadcastReceiver {
    private static CallStateListener callStateListener;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            if (callStateListener != null) {
                callStateListener.onCallStateChanged(state, incomingNumber);
            }
        }
    }

    public static void setCallStateListener(CallStateListener listener) {
        callStateListener = listener;
    }



    public interface CallStateListener {
        void onCallStateChanged(String state, String phoneNumber);
    }
}
