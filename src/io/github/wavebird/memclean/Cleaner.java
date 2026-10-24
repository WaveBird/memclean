package io.github.wavebird.memclean;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 真正干活的地方：枚举目标包并逐个清理。服务和界面共用。 */
public final class Cleaner {

    private static List<String> cachedTargets;
    private static Set<String> cachedSystem;

    private Cleaner() {
    }

    /** 安装/卸载应用后需要调用，重建缓存 */
    public static void invalidate() {
        cachedTargets = null;
        cachedSystem = null;
    }

    private static void build(Context ctx) {
        if (cachedTargets != null) {
            return;
        }
        List<String> targets = new ArrayList<>();
        Set<String> system = new HashSet<>();
        String self = ctx.getPackageName();
        for (PackageInfo pi : ctx.getPackageManager().getInstalledPackages(0)) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null || pi.packageName.equals(self)) {
                continue;
            }
            // 常驻系统进程不能碰，杀了会重启甚至卡开机
            if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
                continue;
            }
            targets.add(pi.packageName);
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                system.add(pi.packageName);
            }
        }
        cachedTargets = targets;
        cachedSystem = system;
    }

    public static boolean isSystem(Context ctx, String pkg) {
        build(ctx);
        return cachedSystem.contains(pkg);
    }

    /** 当前可用内存（MB），用于给历史记录记前后对比 */
    public static long availMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / 1024L / 1024L;
    }

    /**
     * 清理一次并留下历史记录。服务触发和手动清理都走这里，
     * 保证统计口径一致。
     *
     * @param trigger 触发来源，包名或「手动」
     * @return 实际尝试清理的包数量
     */
    public static int cleanAndRecord(Context ctx, String foregroundPkg, String trigger) {
        long before = availMb(ctx);
        int n = clean(ctx, foregroundPkg);
        long after = availMb(ctx);
        Prefs.noteKill(ctx, trigger);
        History.add(ctx, trigger, n, before, after);
        return n;
    }

    /**
     * 清理除 foregroundPkg 和白名单外的后台进程。
     *
     * @return 实际尝试清理的包数量
     */
    public static int clean(Context ctx, String foregroundPkg) {
        build(ctx);
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        Set<String> white = Prefs.whitelist(ctx);
        boolean thirdOnly = Prefs.mode(ctx) == Prefs.MODE_THIRD_PARTY;
        int n = 0;
        for (String pkg : cachedTargets) {
            if (pkg.equals(foregroundPkg) || white.contains(pkg)) {
                continue;
            }
            if (thirdOnly && cachedSystem.contains(pkg)) {
                continue;
            }
            try {
                am.killBackgroundProcesses(pkg);
                n++;
            } catch (Throwable ignored) {
                // 单个包失败不影响整体
            }
        }
        return n;
    }
}
