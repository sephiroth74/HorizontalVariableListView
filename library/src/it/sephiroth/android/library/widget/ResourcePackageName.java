package it.sephiroth.android.library.widget;

import android.content.Context;

public final class ResourcePackageName {
    private static String resourcePackageName;

    /**
     * Not intended for instantiation
     */
    private ResourcePackageName() {
    }

    public static String getResourcePackageName(Context context) {
        if ( resourcePackageName == null ) {
            String contextPackageName = context.getPackageName();
            return contextPackageName;
        } else {
            return resourcePackageName;
        }
    }

    public static void setResourcePackageName(String resourcePackageName) {
        ResourcePackageName.resourcePackageName = resourcePackageName;
    }
}
