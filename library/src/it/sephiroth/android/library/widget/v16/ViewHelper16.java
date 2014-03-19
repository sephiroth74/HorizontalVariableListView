package it.sephiroth.android.library.widget.v16;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;

import it.sephiroth.android.library.widget.v14.ViewHelper14;

/**
 * @hide
 */
public class ViewHelper16 extends ViewHelper14 {
	public ViewHelper16( View view ) {
		super( view );
	}

	@TargetApi( 16 )
	@Override
	public void postOnAnimation( Runnable action ) {
		view.postOnAnimation( action );
	}

}