package it.sephiroth.android.library.widget.v14;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;

import it.sephiroth.android.library.widget.v9.ViewHelper9;

/**
 * @hide
 */
public class ViewHelper14 extends ViewHelper9 {

	public ViewHelper14( View view ) {
		super( view );
	}

	@TargetApi(14)
	@Override
	public void setScrollX( int value ) {
		view.setScrollX( value );
	}

}