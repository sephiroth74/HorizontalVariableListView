package it.sephiroth.android.library.util;

import android.os.Build;

/**
 * Created by alessandro on 21/11/14.
 */
public class ApiHelper {
    public static final boolean AT_LEAST_10 = Build.VERSION.SDK_INT >= 10;
    public static final boolean AT_LEAST_11 = Build.VERSION.SDK_INT >= 11;
    public static final boolean AT_LEAST_14 = Build.VERSION.SDK_INT >= 14;
    public static final boolean AT_LEAST_16 = Build.VERSION.SDK_INT >= 16;
    public static final boolean AT_LEAST_19 = Build.VERSION.SDK_INT >= 19;
    public static final boolean AT_LEAST_21 = Build.VERSION.SDK_INT >= 21;
}
