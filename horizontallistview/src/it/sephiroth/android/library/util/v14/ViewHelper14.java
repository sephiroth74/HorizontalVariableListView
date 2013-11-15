package it.sephiroth.android.library.util.v14;

import it.sephiroth.android.library.util.ViewHelperFactory.ViewHelperDefault;
import android.view.View;

public class ViewHelper14 extends ViewHelperDefault {

	public ViewHelper14( View view ) {
		super( view );
	}
	
	@Override
	public void setScrollX( int value ) {
		view.setScrollX( value );
	}
	
	@Override
	public boolean isHardwareAccelerated() {
		return view.isHardwareAccelerated();
	}
	
}