package io.github.wavebird.memclean;

import android.content.Context;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 清理历史，追加写入 files/clean_history.txt。
 *
 * 不用 SharedPreferences 的 StringSet：它无序、每次 putStringSet 都要整体重写，
 * 而且没法保证"最新的在最前面"。这里用行式纯文本，一行一条：
 *
 *   <时间戳>|<触发来源>|<清理个数>|<清理前可用MB>|<清理后可用MB>
 *
 * 只保留最近 MAX 条，超出时从尾部截断。
 */
public final class History {

    private static final String FILE = "clean_history.txt";
    private static final int MAX = 200;

    /** 服务与界面同进程，用静态缓存省掉每次刷新都读文件 */
    private static int cachedCount = -1;

    public static final class Entry {
        public long ts;
        public String trigger;
        public int count;
        public long beforeMb;
        public long afterMb;

        /** 回收了多少 MB，可能为负（清理后系统又启动别的进程） */
        public long freedMb() {
            return afterMb - beforeMb;
        }
    }

    private History() {
    }

    public static synchronized void add(Context ctx, String trigger, int count,
                                        long beforeMb, long afterMb) {
        // 一个包都没清到就不占记录了，否则历史里全是噪音
        if (count <= 0) {
            return;
        }
        List<Entry> all = read(ctx);
        Entry e = new Entry();
        e.ts = System.currentTimeMillis();
        e.trigger = trigger == null ? "未知" : trigger;
        e.count = count;
        e.beforeMb = beforeMb;
        e.afterMb = afterMb;
        all.add(0, e);                      // 最新的放最前
        write(ctx, all);
        cachedCount = Math.min(all.size(), MAX);
    }

    /** 按时间倒序返回，最新的在 index 0 */
    public static synchronized List<Entry> list(Context ctx) {
        List<Entry> all = read(ctx);
        cachedCount = all.size();
        return all;
    }

    public static synchronized int count(Context ctx) {
        if (cachedCount < 0) {
            cachedCount = read(ctx).size();
        }
        return cachedCount;
    }

    public static synchronized void clear(Context ctx) {
        try {
            ctx.deleteFile(FILE);
        } catch (Throwable ignored) {
        }
        cachedCount = 0;
    }

    private static List<Entry> read(Context ctx) {
        List<Entry> out = new ArrayList<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(ctx.openFileInput(FILE), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length < 5) {
                    continue;
                }
                try {
                    Entry e = new Entry();
                    e.ts = Long.parseLong(p[0]);
                    e.trigger = p[1];
                    e.count = Integer.parseInt(p[2]);
                    e.beforeMb = Long.parseLong(p[3]);
                    e.afterMb = Long.parseLong(p[4]);
                    out.add(e);
                } catch (NumberFormatException ignored) {
                    // 单行损坏就跳过，不要让整个历史读不出来
                }
            }
        } catch (Throwable ignored) {
            // 文件还不存在
        } finally {
            close(r);
        }
        return out;
    }

    private static void write(Context ctx, List<Entry> all) {
        FileOutputStream fos = null;
        try {
            fos = ctx.openFileOutput(FILE, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            int n = Math.min(all.size(), MAX);
            for (int i = 0; i < n; i++) {
                Entry e = all.get(i);
                sb.append(e.ts).append('|')
                        .append(sanitize(e.trigger)).append('|')
                        .append(e.count).append('|')
                        .append(e.beforeMb).append('|')
                        .append(e.afterMb).append('\n');
            }
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            close(fos);
        }
    }

    /** 分隔符和换行会把行式格式搞坏，写入前剔掉 */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
