package it.sephiroth.android.library.util.v14;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;

import it.sephiroth.android.library.util.ViewHelperFactory.ViewHelperDefault;

public class ViewHelper14 extends ViewHelperDefault {

	public ViewHelper14( View view ) {
		super( view );
	}
	
	@TargetApi( Build.VERSION_CODES.ICE_CREAM_SANDWICH )
	@Override
	public void setScrollX( int value ) {
		view.setScrollX( value );
	}
	
	@TargetApi( Build.VERSION_CODES.HONEYCOMB )
	@Override
	public boolean isHardwareAccelerated() {
		return view.isHardwareAccelerated();
	}
	
}