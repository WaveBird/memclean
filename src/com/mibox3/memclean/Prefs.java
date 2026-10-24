package com.mibox3.memclean;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 开关状态、清理模式、白名单、统计 */
public final class Prefs {

    private static final String FILE = "memclean";
    private static final String K_ENABLED = "enabled";
    private static final String K_WHITELIST = "whitelist";
    private static final String K_SEEDED = "whitelist_seeded";
    private static final String K_MODE = "mode";
    private static final String K_LAST_FG = "last_fg";
    private static final String K_LAST_KILL = "last_kill";
    private static final String K_KILL_COUNT = "kill_count";

    /** 清理模式：0 = 全部应用，1 = 仅第三方应用 */
    public static final int MODE_ALL = 0;
    public static final int MODE_THIRD_PARTY = 1;

    /** 首次安装时预置的白名单：桌面、输入法、蓝牙、电话等，误杀会导致系统异常 */
    private static final String[] SEED = {
            "com.oversea.aslauncher",
            "com.android.systemui",
            "com.android.inputmethod.pinyin",
            "com.android.bluetooth",
            "com.android.providers.telephony",
            "com.android.phone",
    };

    /** 系统关键组件：清掉会导致界面重启或输入法失灵，不允许取消保护 */
    private static final Set<String> LOCKED = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.inputmethod.pinyin"));

    private Prefs() {
    }

    public static boolean isLocked(String pkg) {
        return LOCKED.contains(pkg);
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context ctx) {
        return sp(ctx).getBoolean(K_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean(K_ENABLED, v).apply();
    }

    public static int mode(Context ctx) {
        return sp(ctx).getInt(K_MODE, MODE_ALL);
    }

    public static void setMode(Context ctx, int m) {
        sp(ctx).edit().putInt(K_MODE, m).apply();
    }

    /** 读取白名单；首次调用时用 SEED 初始化，之后完全由用户控制 */
    public static Set<String> whitelist(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.getBoolean(K_SEEDED, false)) {
            Set<String> s = new HashSet<>(Arrays.asList(SEED));
            p.edit().putStringSet(K_WHITELIST, s).putBoolean(K_SEEDED, true).apply();
            return s;
        }
        Set<String> s = p.getStringSet(K_WHITELIST, null);
        return s == null ? new HashSet<String>() : new HashSet<>(s);
    }

    public static boolean isWhitelisted(Context ctx, String pkg) {
        return whitelist(ctx).contains(pkg);
    }

    public static void setWhitelisted(Context ctx, String pkg, boolean on) {
        Set<String> s = whitelist(ctx);
        if (on) {
            s.add(pkg);
        } else {
            s.remove(pkg);
        }
        sp(ctx).edit().putStringSet(K_WHITELIST, s).apply();
    }

    public static void resetWhitelist(Context ctx) {
        sp(ctx).edit()
                .putStringSet(K_WHITELIST, new HashSet<>(Arrays.asList(SEED)))
                .putBoolean(K_SEEDED, true)
                .apply();
    }

    public static void noteForeground(Context ctx, String pkg) {
        sp(ctx).edit().putString(K_LAST_FG, pkg).apply();
    }

    public static void noteKill(Context ctx, String fg) {
        SharedPreferences p = sp(ctx);
        p.edit()
                .putString(K_LAST_KILL, fg)
                .putLong(K_KILL_COUNT, p.getLong(K_KILL_COUNT, 0) + 1)
                .apply();
    }

    public static String lastForeground(Context ctx) {
        return sp(ctx).getString(K_LAST_FG, "—");
    }

    public static String lastKill(Context ctx) {
        return sp(ctx).getString(K_LAST_KILL, "—");
    }

    public static long killCount(Context ctx) {
        return sp(ctx).getLong(K_KILL_COUNT, 0);
    }
}
