package io.github.wavebird.memclean;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * 监听前台应用切换，切换时清理其他应用的后台进程。
 * 不依赖 root，使用 UsageStats + killBackgroundProcesses。
 */
public class MemCleanService extends Service {

    private static final String TAG = "MemClean";
    private static final long POLL_MS = 2000L;
    private static final long LOOKBACK_MS = 20000L;
    private static final long MIN_INTERVAL_MS = 1500L;
    private static final long WATCHDOG_MS = 15 * 60 * 1000L;

    private Handler handler;
    private UsageStatsManager usm;
    private long lastQueryEnd;
    private String lastFg;
    private long lastCleanAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                poll();
            } catch (Throwable t) {
                Log.w(TAG, "poll failed", t);
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        lastQueryEnd = System.currentTimeMillis();
        Log.i(TAG, "service created");
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleWatchdog();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "service destroyed");
        if (handler != null) {
            handler.removeCallbacks(tick);
        }
        super.onDestroy();
    }

    private void scheduleWatchdog() {
        try {
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(this, BootReceiver.class);
            i.setAction("io.github.wavebird.memclean.WATCHDOG");
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + WATCHDOG_MS, WATCHDOG_MS, pi);
        } catch (Throwable t) {
            Log.w(TAG, "watchdog schedule failed", t);
        }
    }

    private void poll() {
        if (!Prefs.isEnabled(this)) {
            return;
        }
        long now = System.currentTimeMillis();
        long begin = Math.max(lastQueryEnd - 1000L, now - LOOKBACK_MS);
        lastQueryEnd = now;

        UsageEvents events;
        try {
            events = usm.queryEvents(begin, now);
        } catch (Throwable t) {
            Log.w(TAG, "queryEvents failed", t);
            return;
        }
        if (events == null) {
            return;
        }

        String fg = null;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                fg = e.getPackageName();
            }
        }
        if (fg == null || fg.equals(lastFg)) {
            return;
        }
        lastFg = fg;
        Prefs.noteForeground(this, fg);
        Log.i(TAG, "foreground -> " + fg);

        // 不能因为 fg 在白名单里就跳过：桌面也在白名单（避免被当后台杀掉），
        // 但"回到桌面"恰恰是最该清理的时刻。当前前台由 Cleaner 内部保护。
        if (fg.equals(getPackageName())) {
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        if (nowMs - lastCleanAt < MIN_INTERVAL_MS) {
            Log.i(TAG, "throttled");
            return;
        }
        lastCleanAt = nowMs;
        cleanAsync(fg);
    }

    private void cleanAsync(final String fg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = SystemClock.elapsedRealtime();
                int n = Cleaner.cleanAndRecord(MemCleanService.this, fg, fg);
                long cost = SystemClock.elapsedRealtime() - t0;
                Log.i(TAG, "cleaned " + n + " pkgs after switch to " + fg + " in " + cost + "ms");
            }
        }, "memclean-worker").start();
    }
}
