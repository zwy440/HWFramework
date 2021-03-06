package com.huawei.android.text;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.text.TextUtils;

public class StaticLayoutEx {
    public static StaticLayout getStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth, Layout.Alignment align, TextDirectionHeuristic textDir, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth, int maxLines) {
        StaticLayout staticLayout = new StaticLayout(source, bufstart, bufend, paint, outerwidth, align, textDir, spacingmult, spacingadd, includepad, ellipsize, ellipsizedWidth, maxLines);
        return staticLayout;
    }
}
