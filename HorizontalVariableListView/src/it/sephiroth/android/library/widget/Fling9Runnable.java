package it.sephiroth.android.library.widget;

import android.annotation.TargetApi;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

@TargetApi(9)
class Fling9Runnable extends IFlingRunnable {

	private OverScroller mScroller;

	public Fling9Runnable( FlingRunnableView parent, int animationDuration ) {
		super( parent, animationDuration );
		mScroller = new OverScroller( ( (View) parent ).getContext(), new DecelerateInterpolator( 1.0f ) );
	}

	@TargetApi(14)
	@Override
	public float getCurrVelocity() {
		return mScroller.getCurrVelocity();
	}

	@Override
	public boolean isFinished() {
		return mScroller.isFinished();
	}

	@Override
	public boolean springBack( int startX, int startY, int minX, int maxX, int minY, int maxY ) {
		return mScroller.springBack( startX, startY, minX, maxX, minY, maxY );
	}

	@Override
	protected void _startUsingVelocity( int initialX, int velocity ) {
		mScroller.fling( initialX, 0, velocity, 0, mParent.getMinX(), mParent.getMaxX(), 0, Integer.MAX_VALUE, 10, 0 );
	}

	@Override
	protected void _startUsingDistance( int initialX, int distance ) {
		mScroller.startScroll( initialX, 0, distance, 0, mAnimationDuration );
	}

	@Override
	protected boolean computeScrollOffset() {
		return mScroller.computeScrollOffset();
	}

	@Override
	protected int getCurrX() {
		return mScroller.getCurrX();
	}

	@Override
	protected void forceFinished( boolean finished ) {
		mScroller.abortAnimation();
	}
}