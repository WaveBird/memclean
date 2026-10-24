package io.github.wavebird.memclean;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    private static final String SYSTEM_UI = "com.android.systemui";

    /**
     * 所有 Android 上都存在的系统组件，清掉会直接导致异常
     * （蓝牙、电话、电话数据库被清可能让系统弹 ANR 或丢来电）。
     */
    private static final String[] ALWAYS_PROTECT = {
            "com.android.bluetooth",
            "com.android.phone",
            "com.android.providers.telephony",
    };

    /** 缓存探测结果，避免每次判断都查一遍 PackageManager */
    private static Set<String> lockedCache;

    private Prefs() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * 运行时探测需要默认保护的组件。
     *
     * 这里刻意不写死任何具体机型或桌面的包名——不同电视盒子用的桌面和输入法千差万别，
     * 写死一个就等于把程序绑死在某一台上：
     *   - 桌面：可能装了多个，全部保护，否则从应用返回时要重新加载
     *   - 输入法：可能装了多个，全部保护，否则清掉后无法输入
     *   - 系统界面：清掉会导致状态栏/导航栏重启
     */
    public static Set<String> detectProtected(Context ctx) {
        Set<String> out = new HashSet<>();
        out.add(SYSTEM_UI);
        out.addAll(Arrays.asList(ALWAYS_PROTECT));

        // 当前注册为 HOME 的应用
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homes = ctx.getPackageManager()
                    .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : homes) {
                if (ri.activityInfo != null && !TextUtils.isEmpty(ri.activityInfo.packageName)) {
                    out.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
        }

        out.addAll(installedInputMethods(ctx));
        return out;
    }

    /** 已安装的输入法包名 */
    private static Set<String> installedInputMethods(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo imi : imm.getInputMethodList()) {
                    String pkg = imi.getPackageName();
                    if (!TextUtils.isEmpty(pkg)) {
                        out.add(pkg);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 兜底：系统设置里记录的默认输入法，形如 "包名/服务名"
        try {
            String def = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (!TextUtils.isEmpty(def) && def.indexOf('/') > 0) {
                out.add(def.substring(0, def.indexOf('/')));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Set<String> lockedSet(Context ctx) {
        if (lockedCache == null) {
            Set<String> s = new HashSet<>();
            s.add(SYSTEM_UI);
            s.addAll(installedInputMethods(ctx));
            lockedCache = s;
        }
        return lockedCache;
    }

    /** 系统关键组件：清掉会导致界面重启或输入法失灵，不允许取消保护 */
    public static boolean isLocked(Context ctx, String pkg) {
        return lockedSet(ctx).contains(pkg);
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

    /** 读取白名单；首次调用时按本机实际情况初始化，之后完全由用户控制 */
    public static Set<String> whitelist(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.getBoolean(K_SEEDED, false)) {
            Set<String> s = detectProtected(ctx);
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
                .putStringSet(K_WHITELIST, detectProtected(ctx))
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
