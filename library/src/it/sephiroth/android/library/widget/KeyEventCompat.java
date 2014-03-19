package it.sephiroth.android.library.widget;

import android.view.KeyEvent;

class KeyEventCompat {
	/**
	 * Whether key will, by default, trigger a click on the focused view.
	 */
	public static final boolean isConfirmKey( int keyCode ) {
		switch( keyCode ) {
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
				return true;
			default:
				return false;
		}
	}
}
