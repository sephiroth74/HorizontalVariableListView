package it.sephiroth.android.library.util;

import android.util.Log;
import android.view.View;

import it.sephiroth.android.library.util.v14.ViewHelper14;
import it.sephiroth.android.library.util.v16.ViewHelper16;
import it.sephiroth.android.library.util.v21.ViewHelper21;

public class ViewHelperFactory {
    private static final String LOG_TAG = "ViewHelper";

    public static abstract class ViewHelper {
        protected View view;

        protected ViewHelper(View view) {
            this.view = view;
        }

        public abstract void postOnAnimation(Runnable action);

        public abstract void setScrollX(int value);

        public abstract boolean isHardwareAccelerated();

        public final boolean pointInView(float localX, float localY) {
            return localX >= 0 && localX < (view.getRight() - view.getLeft())
                && localY >= 0 && localY < (view.getBottom() - view.getTop());
        }

        public boolean pointInView(float localX, float localY, float slop) {
            return localX >= -slop && localY >= -slop && localX < ((view.getRight() - view.getLeft()) + slop) && localY < (
                (view.getBottom() - view.getTop()) + slop);
        }

        public int getNestedScrollAxes() {
            return 0;
        }
    }

    public static class ViewHelperDefault extends ViewHelper {
        public ViewHelperDefault(View view) {
            super(view);
        }

        @Override
        public void postOnAnimation(Runnable action) {
            view.post(action);
        }

        @Override
        public void setScrollX(int value) {
            view.scrollTo(value, view.getScrollY());
        }

        @Override
        public boolean isHardwareAccelerated() {
            return false;
        }
    }

    public static final ViewHelper create(View view) {

        if (ApiHelper.AT_LEAST_21) {
            return new ViewHelper21(view);
        } else if (ApiHelper.AT_LEAST_16) {
            // jelly bean
            return new ViewHelper16(view);
        } else if (ApiHelper.AT_LEAST_14) {
            // ice cream sandwich
            return new ViewHelper14(view);
        } else {
            // fallback
            return new ViewHelperDefault(view);
        }
    }

}
