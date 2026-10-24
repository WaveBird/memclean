package io.github.wavebird.memclean;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 清理记录：每次清理的时间、触发应用、清理个数、内存变化 */
public class HistoryActivity extends Activity {

    private final List<History.Entry> rows = new ArrayList<>();
    private final Map<String, String> labelCache = new HashMap<>();
    private RecordAdapter adapter;
    private TextView headerCount;
    private TextView empty;
    private ListView list;

    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat md = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int bh = Ui.dimen(this, R.dimen.bar_pad_h);
        bar.setPadding(bh, Ui.dimen(this, R.dimen.bar_pad_v), bh,
                Ui.dimen(this, R.dimen.bar_pad_bottom));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.title(this, "清理记录", R.dimen.text_title));

        headerCount = Ui.label(this, "", R.dimen.text_sub, Ui.DIM);
        headerCount.setPadding(0, Ui.dimen(this, R.dimen.gap_sub_top), 0, 0);
        titles.addView(headerCount);

        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = Ui.button(this, "清空记录", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rows.isEmpty()) {
                    Toast.makeText(HistoryActivity.this, "还没有记录", Toast.LENGTH_SHORT).show();
                    return;
                }
                History.clear(HistoryActivity.this);
                reload();
                Toast.makeText(HistoryActivity.this, "已清空清理记录", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(clear, new LinearLayout.LayoutParams(
                Ui.dimen(this, R.dimen.btn_small_w), ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bar);

        TextView hint = Ui.label(this,
                "按「返回」键回到主界面。清理记录只保存在本机，最多保留 200 条。",
                R.dimen.text_hint, Ui.FAINT);
        int lh = Ui.dimen(this, R.dimen.list_pad_h);
        hint.setPadding(lh, 0, lh, Ui.dimen(this, R.dimen.hint_pad_bottom));
        root.addView(hint);

        // 空状态与列表叠在同一个容器里，谁有内容显示谁
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, 0, 0, 0);

        list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(Ui.roundRect(this, Ui.alpha(Ui.ACCENT, 0x22), Ui.ACCENT,
                R.dimen.radius_row_sel));
        list.setPadding(lh, 0, lh, Ui.dimen(this, R.dimen.list_pad_bottom));
        list.setClipToPadding(false);
        list.setCacheColorHint(0);

        empty = Ui.label(this, "还没有清理记录\n\n切换到别的应用后，这里会自动出现一条",
                R.dimen.text_hint, Ui.FAINT);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = Ui.dimen(this, R.dimen.gap_tip_top) * 3;

        body.addView(empty, ep);
        body.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new RecordAdapter();
        list.setAdapter(adapter);

        setContentView(root);
        clear.setId(View.generateViewId());
        list.setId(View.generateViewId());
        clear.setNextFocusDownId(list.getId());
        list.setNextFocusUpId(clear.getId());
        reload();
    }

    private void reload() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<History.Entry> loaded = History.list(HistoryActivity.this);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        rows.clear();
                        rows.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        headerCount.setText(rows.isEmpty()
                                ? "暂无记录"
                                : "共 " + rows.size() + " 条 · 最新在最上");
                        boolean isEmpty = rows.isEmpty();
                        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                        if (!isEmpty) {
                            list.requestFocus();
                        }
                    }
                });
            }
        }).start();
    }

    /** 包名 → 应用名，解析不到的（已卸载）就直接显示包名 */
    private String label(String pkg) {
        if ("手动".equals(pkg)) {
            return "手动清理";
        }
        String cached = labelCache.get(pkg);
        if (cached != null) {
            return cached;
        }
        String out = pkg;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence lb = ai.loadLabel(pm);
            if (lb != null && lb.length() > 0) {
                out = lb.toString();
            }
        } catch (Throwable ignored) {
        }
        labelCache.put(pkg, out);
        return out;
    }

    private String when(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);
        boolean sameDay = now.get(Calendar.YEAR) == t.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return "今天 " + hm.format(new Date(ts));
        }
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_YEAR, -1);
        boolean yesterday = y.get(Calendar.YEAR) == t.get(Calendar.YEAR)
                && y.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR);
        if (yesterday) {
            return "昨天 " + hm.format(new Date(ts));
        }
        return md.format(new Date(ts));
    }

    private final class RecordAdapter extends BaseAdapter {
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
                row = new LinearLayout(HistoryActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rh = Ui.dimen(HistoryActivity.this, R.dimen.list_row_pad_h);
                int rv = Ui.dimen(HistoryActivity.this, R.dimen.list_row_pad_v);
                row.setPadding(rh, rv, rh, rv);

                LinearLayout texts = new LinearLayout(HistoryActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);

                TextView name = Ui.label(HistoryActivity.this, "", R.dimen.text_row_name, Ui.TEXT);
                TextView sub = Ui.label(HistoryActivity.this, "", R.dimen.text_row_sub, Ui.FAINT);
                sub.setPadding(0, Ui.dimen(HistoryActivity.this, R.dimen.gap_sub_top), 0, 0);
                texts.addView(name);
                texts.addView(sub);
                row.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout right = new LinearLayout(HistoryActivity.this);
                right.setOrientation(LinearLayout.VERTICAL);
                right.setGravity(Gravity.END);

                TextView delta = Ui.label(HistoryActivity.this, "", R.dimen.text_row_state, Ui.ACCENT);
                TextView time = Ui.label(HistoryActivity.this, "", R.dimen.text_row_sub, Ui.FAINT);
                time.setPadding(0, Ui.dimen(HistoryActivity.this, R.dimen.gap_sub_top), 0, 0);
                right.addView(delta);
                right.addView(time);
                row.addView(right);

                h = new Holder();
                h.name = name;
                h.sub = sub;
                h.delta = delta;
                h.time = time;
                row.setTag(h);
            } else {
                row = (LinearLayout) convertView;
                h = (Holder) row.getTag();
            }

            History.Entry e = rows.get(position);
            h.name.setText(label(e.trigger));
            h.sub.setText("清理 " + e.count + " 个应用 · " + e.beforeMb + " → " + e.afterMb + " MB");

            long d = e.freedMb();
            if (d > 0) {
                h.delta.setText("+" + d + " MB");
                h.delta.setTextColor(Ui.ACCENT);
            } else if (d < 0) {
                h.delta.setText(d + " MB");
                h.delta.setTextColor(Ui.DANGER);
            } else {
                h.delta.setText("±0 MB");
                h.delta.setTextColor(Ui.DIM);
            }
            h.time.setText(when(e.ts));
            return row;
        }
    }

    private static final class Holder {
        TextView name;
        TextView sub;
        TextView delta;
        TextView time;
    }
}
