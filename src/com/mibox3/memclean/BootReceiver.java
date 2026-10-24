package com.mibox3.memclean;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 开机自启 + 看门狗重启 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "MemClean";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        Log.i(TAG, "receiver: " + action);
        if (!Prefs.isEnabled(ctx)) {
            return;
        }
        try {
            ctx.startService(new Intent(ctx, MemCleanService.class));
        } catch (Throwable t) {
            Log.w(TAG, "startService failed", t);
        }
    }
}
