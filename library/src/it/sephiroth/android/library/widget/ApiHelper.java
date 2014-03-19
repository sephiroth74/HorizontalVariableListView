package it.sephiroth.android.library.widget;

import android.os.Build;

class ApiHelper {
	public static final boolean AT_LEAST_11 = Build.VERSION.SDK_INT >= 11;
	public static final boolean AT_LEAST_14 = Build.VERSION.SDK_INT >= 14;
	public static final boolean AT_LEAST_16 = Build.VERSION.SDK_INT >= 16;
	public static final boolean AT_LEAST_19 = Build.VERSION.SDK_INT >= 19;
}
