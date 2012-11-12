package it.sephiroth.android.library.widget;


abstract class IFlingRunnable implements Runnable {

	public static interface FlingRunnableView {

		boolean removeCallbacks( Runnable action );

		boolean post( Runnable action );

		void scrollIntoSlots();

		void trackMotionScroll( int newX );

		int getMinX();

		int getMaxX();
	}

	protected int mLastFlingX;
	protected boolean mShouldStopFling;
	protected FlingRunnableView mParent;
	protected int mAnimationDuration;

	protected static final String LOG_TAG = "fling";

	public IFlingRunnable( FlingRunnableView parent, int animationDuration ) {
		mParent = parent;
		mAnimationDuration = animationDuration;
	}

	public int getLastFlingX() {
		return mLastFlingX;
	}

	protected void startCommon() {
		mParent.removeCallbacks( this );
	}

	public void stop( boolean scrollIntoSlots ) {
		mParent.removeCallbacks( this );
		endFling( scrollIntoSlots );
	}

	public void startUsingDistance( int initialX, int distance ) {
		if ( distance == 0 ) return;
		startCommon();
		mLastFlingX = initialX;
		_startUsingDistance( mLastFlingX, distance );
		mParent.post( this );
	}

	public void startUsingVelocity( int initialX, int initialVelocity ) {
		if ( initialVelocity == 0 ) return;
		startCommon();
		mLastFlingX = initialX;
		_startUsingVelocity( mLastFlingX, initialVelocity );
		mParent.post( this );
	}

	protected void endFling( boolean scrollIntoSlots ) {
		forceFinished( true );
		mLastFlingX = 0;

		if ( scrollIntoSlots ) {
			mParent.scrollIntoSlots();
		}
	}

	@Override
	public void run() {
		mShouldStopFling = false;

		final boolean more = computeScrollOffset();
		int x = getCurrX();

		mParent.trackMotionScroll( x );

		if ( more && !mShouldStopFling ) {
			mLastFlingX = x;
			mParent.post( this );
		} else {
			endFling( true );
		}
	}

	public abstract boolean springBack( int startX, int startY, int minX, int maxX, int minY, int maxY );

	protected abstract boolean computeScrollOffset();

	protected abstract int getCurrX();

	public abstract float getCurrVelocity();

	protected abstract void forceFinished( boolean finished );

	protected abstract void _startUsingVelocity( int initialX, int velocity );

	protected abstract void _startUsingDistance( int initialX, int distance );

	public abstract boolean isFinished();
}
