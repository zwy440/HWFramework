package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View.MeasureSpec;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import com.android.internal.R;

public class WeightedLinearLayout extends LinearLayout {
    private float mMajorWeightMax;
    private float mMajorWeightMin;
    private float mMinorWeightMax;
    private float mMinorWeightMin;

    public WeightedLinearLayout(Context context) {
        super(context);
    }

    public WeightedLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WeightedLinearLayout);
        this.mMajorWeightMin = a.getFloat(0, 0.0f);
        this.mMinorWeightMin = a.getFloat(1, 0.0f);
        this.mMajorWeightMax = a.getFloat(2, 0.0f);
        this.mMinorWeightMax = a.getFloat(3, 0.0f);
        a.recycle();
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        boolean isPortrait = screenWidth < metrics.heightPixels;
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        boolean measure = false;
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        float widthWeightMin = isPortrait ? this.mMinorWeightMin : this.mMajorWeightMin;
        float widthWeightMax = isPortrait ? this.mMinorWeightMax : this.mMajorWeightMax;
        if (widthMode == RtlSpacingHelper.UNDEFINED) {
            int weightedMin = (int) (((float) screenWidth) * widthWeightMin);
            int weightedMax = (int) (((float) screenWidth) * widthWeightMin);
            if (widthWeightMin > 0.0f && width < weightedMin) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(weightedMin, EditorInfo.IME_FLAG_NO_ENTER_ACTION);
                measure = true;
            } else if (widthWeightMax > 0.0f && width > weightedMax) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(weightedMax, EditorInfo.IME_FLAG_NO_ENTER_ACTION);
                measure = true;
            }
        }
        if (measure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
