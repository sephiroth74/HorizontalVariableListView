package it.sephiroth.android.library.util.v16;

import it.sephiroth.android.library.util.v14.ViewHelper14;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;

public class ViewHelper16 extends ViewHelper14 {
	public ViewHelper16( View view ) {
		super( view );
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN )
	@Override
	public void postOnAnimation( Runnable action ) {
		view.postOnAnimation(action);
	}
}