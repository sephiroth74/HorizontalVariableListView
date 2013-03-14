package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

/**
 * Copyright /(c/) 2013 Quotient Solutions. All rights reserved.
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable {

    private boolean isChecked;

    public CheckableLinearLayout(Context context) {
        super(context);
    }

    public CheckableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableLinearLayout(Context context, AttributeSet attrs, int defStyleRes) {
        super(context, attrs, defStyleRes);
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;
        refreshDrawableState();
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    private static final int[] CHECKED_STATE_SET = {
            android.R.attr.state_checked,
    };


    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }
}
