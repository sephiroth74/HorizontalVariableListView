package it.sephiroth.android.library.util;

import it.sephiroth.android.library.util.v14.ViewHelper14;
import it.sephiroth.android.library.util.v16.ViewHelper16;
import android.util.Log;
import android.view.View;

public class ViewHelperFactory {

	public static abstract class ViewHelper {

		private static final String LOG_TAG = "ViewHelper";
		
		protected View view;

		protected ViewHelper( View view ) {
			this.view = view;
			Log.i( LOG_TAG, this.getClass().getSimpleName() );
		}

		public abstract void postOnAnimation( Runnable action );
		public abstract void setScrollX( int value );
		public abstract boolean isHardwareAccelerated();
	}

	public static class ViewHelperDefault extends ViewHelper {
		
		public ViewHelperDefault( View view ) {
			super( view );
		}

		@Override
		public void postOnAnimation( Runnable action ) {
			view.post( action );
		}

		@Override
		public void setScrollX( int value ) {
			view.scrollTo( value, view.getScrollY() );
		}

		@Override
		public boolean isHardwareAccelerated() {
			return false;
		}
	}

	public static final ViewHelper create( View view ) {
		final int version = android.os.Build.VERSION.SDK_INT;

		if ( version >= 16 ) {
			// jelly bean
			return new ViewHelper16( view );
		} else if ( version >= 14 ) {
			// ice cream sandwich
			return new ViewHelper14( view );
		} else {
			// fallback
			return new ViewHelperDefault( view );
		}
	}

}
