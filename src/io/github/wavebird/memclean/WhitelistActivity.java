package io.github.wavebird.memclean;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 白名单管理：列出所有应用，确定键切换是否保护 */
public class WhitelistActivity extends Activity {

    private static final class Row {
        String pkg;
        String label;
        boolean system;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private AppAdapter adapter;
    private TextView headerCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // 顶栏
        int bh = Ui.dimen(this, R.dimen.bar_pad_h);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(bh, Ui.dimen(this, R.dimen.bar_pad_v), bh,
                Ui.dimen(this, R.dimen.bar_pad_bottom));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.title(this, "白名单", R.dimen.text_title));

        headerCount = Ui.label(this, "", R.dimen.text_sub, Ui.DIM);
        headerCount.setPadding(0, Ui.dimen(this, R.dimen.gap_sub_top), 0, 0);
        titles.addView(headerCount);

        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = Ui.button(this, "恢复默认", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.resetWhitelist(WhitelistActivity.this);
                adapter.notifyDataSetChanged();
                updateHeader();
                Toast.makeText(WhitelistActivity.this, "已恢复默认白名单", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(reset, new LinearLayout.LayoutParams(
                Ui.dimen(this, R.dimen.btn_small_w), ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bar);

        int lh = Ui.dimen(this, R.dimen.list_pad_h);
        TextView hint = Ui.label(this,
                "选中后按「确定」加入或移出白名单。白名单内的应用不会被自动清理。",
                R.dimen.text_hint, Ui.FAINT);
        hint.setPadding(lh, 0, lh, Ui.dimen(this, R.dimen.hint_pad_bottom));
        root.addView(hint);

        ListView list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(Ui.roundRect(this, Ui.alpha(Ui.ACCENT, 0x22), Ui.ACCENT,
                R.dimen.radius_row_sel));
        list.setPadding(lh, 0, lh, Ui.dimen(this, R.dimen.list_pad_bottom));
        list.setClipToPadding(false);
        list.setCacheColorHint(0);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Row r = rows.get(position);
                if (Prefs.isLocked(WhitelistActivity.this, r.pkg)) {
                    Toast.makeText(WhitelistActivity.this,
                            "系统关键组件，不能取消保护", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean now = !Prefs.isWhitelisted(WhitelistActivity.this, r.pkg);
                Prefs.setWhitelisted(WhitelistActivity.this, r.pkg, now);
                adapter.notifyDataSetChanged();
                updateHeader();
            }
        });

        setContentView(root);
        // 列表在下面、按钮在右上角，自动搜索可能找不到，显式连一条
        reset.setId(View.generateViewId());
        list.setId(View.generateViewId());
        reset.setNextFocusDownId(list.getId());
        list.setNextFocusUpId(reset.getId());
        list.requestFocus();
        loadAsync();
    }

    private void updateHeader() {
        int n = Prefs.whitelist(this).size();
        headerCount.setText("共 " + rows.size() + " 个应用 · 已保护 " + n + " 个");
    }

    private void loadAsync() {
        headerCount.setText("正在读取应用列表…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Row> loaded = new ArrayList<>();
                for (PackageInfo pi : getPackageManager().getInstalledPackages(0)) {
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai == null) {
                        continue;
                    }
                    Row r = new Row();
                    r.pkg = pi.packageName;
                    r.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    CharSequence lb = ai.loadLabel(getPackageManager());
                    r.label = lb == null ? pi.packageName : lb.toString();
                    loaded.add(r);
                }
                final Collator coll = Collator.getInstance(Locale.CHINA);
                Collections.sort(loaded, new Comparator<Row>() {
                    @Override
                    public int compare(Row a, Row b) {
                        boolean wa = Prefs.isWhitelisted(WhitelistActivity.this, a.pkg);
                        boolean wb = Prefs.isWhitelisted(WhitelistActivity.this, b.pkg);
                        if (wa != wb) {
                            return wa ? -1 : 1;          // 已保护的排前面
                        }
                        if (a.system != b.system) {
                            return a.system ? 1 : -1;    // 第三方排在系统应用前
                        }
                        return coll.compare(a.label, b.label);
                    }
                });
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        rows.clear();
                        rows.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        updateHeader();
                    }
                });
            }
        }).start();
    }

    private Drawable icon(String pkg) {
        Drawable d = iconCache.get(pkg);
        if (d != null) {
            return d;
        }
        try {
            d = getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable ignored) {
        }
        iconCache.put(pkg, d);
        return d;
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            Holder h;
            if (convertView == null) {
                row = new LinearLayout(WhitelistActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rh = Ui.dimen(WhitelistActivity.this, R.dimen.list_row_pad_h);
                int rv = Ui.dimen(WhitelistActivity.this, R.dimen.list_row_pad_v);
                row.setPadding(rh, rv, rh, rv);

                ImageView ic = new ImageView(WhitelistActivity.this);
                int side = Ui.dimen(WhitelistActivity.this, R.dimen.icon_row);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(side, side);
                ip.rightMargin = Ui.dimen(WhitelistActivity.this, R.dimen.gap_icon_row);
                row.addView(ic, ip);

                LinearLayout texts = new LinearLayout(WhitelistActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView name = Ui.label(WhitelistActivity.this, "", R.dimen.text_row_name, Ui.TEXT);
                TextView sub = Ui.label(WhitelistActivity.this, "", R.dimen.text_row_sub, Ui.FAINT);
                texts.addView(name);
                texts.addView(sub);
                row.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView state = Ui.label(WhitelistActivity.this, "", R.dimen.text_row_state, Ui.DIM);
                row.addView(state);

                h = new Holder();
                h.icon = ic;
                h.name = name;
                h.sub = sub;
                h.state = state;
                row.setTag(h);
            } else {
                row = (LinearLayout) convertView;
                h = (Holder) row.getTag();
            }

            Row r = rows.get(position);
            boolean on = Prefs.isWhitelisted(WhitelistActivity.this, r.pkg);
            boolean locked = Prefs.isLocked(WhitelistActivity.this, r.pkg);
            h.icon.setImageDrawable(icon(r.pkg));
            h.name.setText(r.label);
            h.sub.setText(r.pkg + (r.system ? "  · 系统" : ""));
            if (locked) {
                h.state.setText("系统关键");
                h.state.setTextColor(Ui.DIM);
            } else {
                h.state.setText(on ? "✓ 已保护" : "不保护");
                h.state.setTextColor(on ? Ui.ACCENT : Ui.OFF);
            }
            return row;
        }
    }

    private static final class Holder {
        ImageView icon;
        TextView name;
        TextView sub;
        TextView state;
    }
}
