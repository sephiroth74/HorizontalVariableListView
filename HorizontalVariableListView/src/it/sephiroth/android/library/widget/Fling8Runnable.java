package it.sephiroth.android.library.widget;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

class Fling8Runnable extends IFlingRunnable {

	private Scroller mScroller;

	public Fling8Runnable( FlingRunnableView parent, int animationDuration ) {
		super( parent, animationDuration );
		mScroller = new Scroller( ( (View) parent ).getContext(), new DecelerateInterpolator() );
	}

	@Override
	public float getCurrVelocity() {
		return mScroller.getCurrVelocity();
	}

	@Override
	public boolean isFinished() {
		return mScroller.isFinished();
	}

	@Override
	protected void _startUsingVelocity( int initialX, int velocity ) {
		mScroller.fling( initialX, 0, velocity, 0, mParent.getMinX(), mParent.getMaxX(), 0, Integer.MAX_VALUE );
	}

	@Override
	protected void _startUsingDistance( int initialX, int distance ) {
		mScroller.startScroll( initialX, 0, distance, 0, mAnimationDuration );
	}

	@Override
	protected void forceFinished( boolean finished ) {
		mScroller.forceFinished( finished );
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
	public boolean springBack( int startX, int startY, int minX, int maxX, int minY, int maxY ) {
		return false;
	}
}