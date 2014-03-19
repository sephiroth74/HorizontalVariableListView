package it.sephiroth.android.library.widget;

import android.annotation.TargetApi;
import android.view.View;

import it.sephiroth.android.library.widget.v14.ViewHelper14;
import it.sephiroth.android.library.widget.v16.ViewHelper16;
import it.sephiroth.android.library.widget.v19.ViewHelper19;
import it.sephiroth.android.library.widget.v9.ViewHelper9;

/**
 * @hide
 */
public class ViewCompatExt {

	public static abstract class ViewHelper {
		protected View view;

		protected ViewHelper( View view ) {
			this.view = view;
		}

		public abstract void postOnAnimation( Runnable action );

		public abstract void setScrollX( int value );
	}

	public static final ViewHelper create( View view ) {
		if( ApiHelper.AT_LEAST_19 ) {
			return new ViewHelper19( view );
		}
		else if( ApiHelper.AT_LEAST_16 ) {
			return new ViewHelper16( view );
		}
		else if( ApiHelper.AT_LEAST_14 ) {
			return new ViewHelper14( view );
		}
		else {
			return new ViewHelper9( view );
		}
	}


	public static boolean pointInView( View view, float localX, float localY, float slop ) {
		return localX >= - slop && localY >= - slop && localX < ( ( view.getRight() - view.getLeft() ) + slop ) &&
		       localY < ( ( view.getBottom() - view.getTop() ) + slop );
	}

	@TargetApi( 11 )
	public static boolean isHardwareAccelerated( View view ) {
		if( ApiHelper.AT_LEAST_11 ) {
			return view.isHardwareAccelerated();
		}
		return false;
	}

	public static void resolvePadding( final View view ) {
		// TODO: To Be Implemented
	}

	public static void requestAccessibilityFocus( final View view ) {
		// TODO: To Be Implemented
	}

	public static void clearAccessibilityFocus( View view ) {
		// TODO: To Be Implemented
	}

	public static boolean isAccessibilityFocused( View view ) {
		// TODO: To Be Implemented
		return false;
	}

	public static void notifySubtreeAccessibilityStateChangedIfNeeded( View view ) {
		// TODO: To Be Implemented
	}

	public static View findViewByAccessibilityIdTraversal( View view, final int accessibilityId ) {
		// TODO: To Be Implemented
		return null;
	}

	public static int getAccessibilityViewId( View view ) {
		// TODO: To Be Implemented
		return - 1;
	}

	public static void notifyViewAccessibilityStateChangedIfNeeded( View view, int changeType ) {
		// TODO: To Be Implemented
	}
}
