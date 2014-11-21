package it.sephiroth.android.library.util.v21;

import android.view.View;
import android.view.ViewGroup;

import it.sephiroth.android.library.util.v16.ViewHelper16;

/**
 * Created by alessandro on 21/11/14.
 */
public class ViewHelper21 extends ViewHelper16 {
    public ViewHelper21(final View view) {
        super(view);
    }

    @Override
    public int getNestedScrollAxes() {
        return ((ViewGroup) view).getNestedScrollAxes();
    }
}
