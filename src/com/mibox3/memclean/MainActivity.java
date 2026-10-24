package com.mibox3.memclean;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面：状态总览 + 手动清理 + 白名单入口 + 模式切换 + 历史入口 */
public class MainActivity extends Activity {

    private TextView memValue;
    private TextView countValue;
    private TextView statusLine;
    private TextView btnToggle;
    private TextView btnMode;
    private TextView btnWhitelist;
    private TextView btnHistory;
    private TextView btnClean;
    private TextView btnGrant;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int ph = Ui.dimen(this, R.dimen.pad_screen_h);
        int pv = Ui.dimen(this, R.dimen.pad_screen_v);
        root.setPadding(ph, pv, ph, pv);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader());

        // 统计卡片
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, Ui.dimen(this, R.dimen.gap_header_stats), 0, 0);

        LinearLayout card1 = Ui.statCard(this, "可用内存", "—");
        memValue = (TextView) card1.getChildAt(1);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.rightMargin = Ui.dimen(this, R.dimen.gap_card);
        stats.addView(card1, p1);

        LinearLayout card2 = Ui.statCard(this, "累计自动清理", "0 次");
        countValue = (TextView) card2.getChildAt(1);
        stats.addView(card2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(stats);

        statusLine = Ui.label(this, "", R.dimen.text_status, Ui.DIM);
        statusLine.setPadding(0, Ui.dimen(this, R.dimen.gap_status_top), 0,
                Ui.dimen(this, R.dimen.gap_status_bottom));
        root.addView(statusLine);

        int gap = Ui.dimen(this, R.dimen.gap_btn);

        btnToggle = Ui.button(this, "关闭自动清理", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isEnabled(MainActivity.this);
                Prefs.setEnabled(MainActivity.this, next);
                if (next) {
                    startService(new Intent(MainActivity.this, MemCleanService.class));
                } else {
                    stopService(new Intent(MainActivity.this, MemCleanService.class));
                }
                refresh();
            }
        });
        root.addView(btnToggle, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gap));

        // 白名单与历史并排，省一行高度（低分辨率电视上很关键）
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);

        btnWhitelist = Ui.button(this, "白名单管理", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WhitelistActivity.class));
            }
        });
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.rightMargin = gap;
        pair.addView(btnWhitelist, wp);

        btnHistory = Ui.button(this, "清理记录", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        pair.addView(btnHistory, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(pair, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gap));

        btnMode = Ui.button(this, "清理范围", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int next = Prefs.mode(MainActivity.this) == Prefs.MODE_ALL
                        ? Prefs.MODE_THIRD_PARTY : Prefs.MODE_ALL;
                Prefs.setMode(MainActivity.this, next);
                refresh();
            }
        });
        root.addView(btnMode, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gap));

        TextView btnClean = Ui.button(this, "立即清理一次", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doManualClean();
            }
        });
        this.btnClean = btnClean;
        root.addView(btnClean, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gap));

        btnGrant = Ui.button(this, "授予「使用情况访问」权限", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "当前系统没有该设置页，请用 adb 授权",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(btnGrant, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gap));

        TextView tip = Ui.label(this, "原理：检测到前台应用切换后，调用系统接口清理其他应用的后台进程。\n"
                + "白名单内的应用不会被清理；当前正在使用的应用永远不受影响。",
                R.dimen.text_tip, Ui.FAINT);
        tip.setPadding(0, Ui.dimen(this, R.dimen.gap_tip_top), 0, 0);
        tip.setLineSpacing(Ui.dimen(this, R.dimen.line_spacing_tip), 1f);
        root.addView(tip);

        wireFocus();
        setContentView(scroll);
        btnToggle.requestFocus();
    }

    /**
     * 显式指定遥控器的上下左右焦点链。
     *
     * 只靠 FocusFinder 自动搜索会漏掉「白名单 / 清理记录」这一行。它的候选打分是
     *     13 * 纵向距离² + 横向偏移²
     * 这一行是左右分栏，两个按钮的中心离屏幕中线都很远（横向偏移 400+px），
     * 而下面的「清理范围」是整宽按钮、横向偏移 0，于是数值上把分栏行压过去了：
     *
     *     白名单：  13*11² + 435²  = 190798
     *     清理范围：13*108² + 0²   = 151632   ← 被选中
     *
     * 纵向差 10 倍，仍然输在横向偏移被平方放大。电视布局里只要出现
     * 「整宽 + 分栏」混排，就必须自己写焦点链，不能指望自动搜索。
     */
    private void wireFocus() {
        btnToggle.setId(View.generateViewId());
        btnWhitelist.setId(View.generateViewId());
        btnHistory.setId(View.generateViewId());
        btnMode.setId(View.generateViewId());
        btnClean.setId(View.generateViewId());
        btnGrant.setId(View.generateViewId());

        btnToggle.setNextFocusDownId(btnWhitelist.getId());

        btnWhitelist.setNextFocusUpId(btnToggle.getId());
        btnWhitelist.setNextFocusDownId(btnMode.getId());
        btnWhitelist.setNextFocusRightId(btnHistory.getId());

        btnHistory.setNextFocusUpId(btnToggle.getId());
        btnHistory.setNextFocusDownId(btnMode.getId());
        btnHistory.setNextFocusLeftId(btnWhitelist.getId());

        btnMode.setNextFocusUpId(btnWhitelist.getId());
        btnMode.setNextFocusDownId(btnClean.getId());

        btnClean.setNextFocusUpId(btnMode.getId());
        // btnGrant 在权限已授予时是 GONE，此时焦点会回退到几何搜索，不会卡住
        btnClean.setNextFocusDownId(btnGrant.getId());

        btnGrant.setNextFocusUpId(btnClean.getId());
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName()));
        } catch (Throwable ignored) {
        }
        int side = Ui.dimen(this, R.dimen.icon_head);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(side, side);
        ip.rightMargin = Ui.dimen(this, R.dimen.gap_icon_text);
        row.addView(icon, ip);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        texts.addView(Ui.title(this, "内存守护", R.dimen.text_title));

        TextView sub = Ui.label(this, "切换应用时自动回收后台内存 · v1.3.0",
                R.dimen.text_sub, Ui.DIM);
        sub.setPadding(0, Ui.dimen(this, R.dimen.gap_sub_top), 0, 0);
        texts.addView(sub);

        row.addView(texts);
        return row;
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager aom = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refresher);
        ui.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresher);
    }

    private void doManualClean() {
        Toast.makeText(this, "正在清理…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = Cleaner.cleanAndRecord(MainActivity.this, getPackageName(), "手动");
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "已清理 " + n + " 个应用的后台", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void refresh() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            memValue.setText(mi.availMem / 1024 / 1024 + " MB");
        }

        countValue.setText(Prefs.killCount(this) + " 次");
        btnToggle.setText(Prefs.isEnabled(this) ? "关闭自动清理" : "开启自动清理");
        btnMode.setText("清理范围：" + (Prefs.mode(this) == Prefs.MODE_ALL
                ? "全部应用" : "仅第三方应用（更保守）"));
        btnWhitelist.setText("白名单（" + Prefs.whitelist(this).size() + "）");
        btnHistory.setText("清理记录（" + History.count(this) + "）");

        boolean granted = hasUsageAccess();
        boolean on = Prefs.isEnabled(this);
        // 权限已授予时不再占用屏幕空间
        btnGrant.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (!granted) {
            statusLine.setText("⚠ 未授予「使用情况访问」权限，无法检测应用切换");
            statusLine.setTextColor(Ui.DANGER);
        } else if (!on) {
            statusLine.setText("自动清理已暂停");
            statusLine.setTextColor(Ui.DIM);
        } else {
            statusLine.setText("运行中 · 上次触发：" + shortPkg(Prefs.lastKill(this)));
            statusLine.setTextColor(Ui.ACCENT);
        }
    }

    private String shortPkg(String pkg) {
        if (pkg == null || pkg.length() == 0 || "—".equals(pkg)) {
            return "尚未触发";
        }
        if ("手动".equals(pkg)) {
            return "手动清理";
        }
        return pkg;
    }
}
