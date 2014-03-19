package it.sephiroth.android.library.widget.v9;

import android.view.View;

import it.sephiroth.android.library.widget.ViewCompatExt;

/**
 * @hide
 */
public class ViewHelper9 extends ViewCompatExt.ViewHelper {

	public ViewHelper9( View view ) {
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

}
