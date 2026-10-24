package com.mibox3.memclean;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 统一的视觉规范：颜色、圆角卡片、电视可聚焦按钮。
 *
 * 所有尺寸都从 res/values*&#47;dimens.xml 读取，由 smallest-width 限定符
 * （<600dp / ≥600dp / ≥900dp）挑选合适的档位，因此换一台分辨率或密度
 * 不同的电视时布局会自动缩放，而不是靠一套写死的 dp 硬扛。
 */
public final class Ui {

    public static final int BG = 0xFF0E1116;
    public static final int CARD = 0xFF171D24;
    public static final int LINE = 0xFF232B35;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int DIM = 0xFF8A97A6;
    public static final int FAINT = 0xFF5C6B7A;
    public static final int OFF = 0xFF4A5563;
    public static final int ACCENT = 0xFF35E0A1;
    public static final int DANGER = 0xFFFF7A6B;
    public static final int BTN_BG = 0xFF1F272F;
    public static final int BTN_BG_FOCUS = 0xFF153A30;

    private Ui() {
    }

    /** 少量运行时算出来的间距用这个（例如按行数算的偏移） */
    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    /** 读 dimens.xml 里的长度值（px） */
    public static int dimen(Context c, int res) {
        return c.getResources().getDimensionPixelSize(res);
    }

    /**
     * 把 sp 尺寸写进 TextView。
     * dimens.xml 里声明的 sp 已经被系统按 scaledDensity 换算成 px，
     * 所以这里必须用 COMPLEX_UNIT_PX，否则会被再放大一次。
     */
    public static void applyTextSize(TextView t, Context c, int res) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(res));
    }

    public static GradientDrawable roundRect(Context c, int fill, int stroke, int radiusRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dimen(c, radiusRes));
        if (stroke != 0) {
            d.setStroke(Math.max(1, dimen(c, R.dimen.stroke_focus)), stroke);
        }
        return d;
    }

    public static GradientDrawable cardBg(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(CARD);
        d.setCornerRadius(dimen(c, R.dimen.radius_card));
        d.setStroke(Math.max(1, dimen(c, R.dimen.stroke_card)), LINE);
        return d;
    }

    public static GradientDrawable pill(Context c, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dimen(c, R.dimen.radius_btn));
        if (stroke != 0) {
            d.setStroke(Math.max(1, dimen(c, R.dimen.stroke_focus)), stroke);
        }
        return d;
    }

    public static TextView title(Context c, String text, int sizeRes) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT);
        applyTextSize(t, c, sizeRes);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView label(Context c, String text, int sizeRes, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(color);
        applyTextSize(t, c, sizeRes);
        return t;
    }

    /** 电视上必须够大、且有明确焦点态，遥控器才知道选到哪了 */
    public static TextView button(final Context c, String text, final View.OnClickListener onClick) {
        final TextView b = new TextView(c);
        b.setText(text);
        applyTextSize(b, c, R.dimen.text_btn);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL);
        int ph = dimen(c, R.dimen.btn_pad_h);
        int pv = dimen(c, R.dimen.btn_pad_v);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(dimen(c, R.dimen.btn_height));
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setBackground(pill(c, BTN_BG, 0));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setClickable(true);
        b.setOnClickListener(onClick);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                b.setBackground(has
                        ? pill(c, BTN_BG_FOCUS, ACCENT)
                        : pill(c, BTN_BG, 0));
                b.setTextColor(has ? ACCENT : TEXT);
            }
        });
        return b;
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    /** 统计小卡片 */
    public static LinearLayout statCard(Context c, String caption, String value) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int ph = dimen(c, R.dimen.card_pad_h);
        int pv = dimen(c, R.dimen.card_pad_v);
        box.setPadding(ph, pv, ph, pv);
        box.setBackground(cardBg(c));

        TextView cap = label(c, caption, R.dimen.text_card_cap, DIM);
        box.addView(cap);

        TextView val = title(c, value, R.dimen.text_card_val);
        val.setTextColor(ACCENT);
        val.setPadding(0, dimen(c, R.dimen.gap_sub_top), 0, 0);
        box.addView(val);
        return box;
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
