package it.sephiroth.android.library.widget;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * This class performs the glow effect used at the edges of scrollable widgets.
 * 
 * @hide
 */
public class EdgeGlow {

	// Time it will take the effect to fully recede in ms
	/** The Constant RECEDE_TIME. */
	private static final int RECEDE_TIME = 1000;

	// Time it will take before a pulled glow begins receding
	/** The Constant PULL_TIME. */
	private static final int PULL_TIME = 167;

	// Time it will take for a pulled glow to decay to partial strength before release
	/** The Constant PULL_DECAY_TIME. */
	private static final int PULL_DECAY_TIME = 1000;

	/** The Constant MAX_ALPHA. */
	private static final float MAX_ALPHA = 0.8f;

	/** The Constant HELD_EDGE_SCALE_Y. */
	private static final float HELD_EDGE_SCALE_Y = 0.5f;

	/** The Constant MAX_GLOW_HEIGHT. */
	private static final float MAX_GLOW_HEIGHT = 3.f;

	/** The Constant PULL_GLOW_BEGIN. */
	private static final float PULL_GLOW_BEGIN = 1.f;

	/** The Constant PULL_EDGE_BEGIN. */
	private static final float PULL_EDGE_BEGIN = 0.6f;

	// Minimum velocity that will be absorbed
	/** The Constant MIN_VELOCITY. */
	private static final int MIN_VELOCITY = 100;

	/** The Constant EPSILON. */
	private static final float EPSILON = 0.001f;

	/** The m edge. */
	private final Drawable mEdge;

	/** The m glow. */
	private final Drawable mGlow;

	/** The m width. */
	private int mWidth;

	/** The m height. */
	private int mHeight;

	/** The m edge alpha. */
	private float mEdgeAlpha;

	/** The m edge scale y. */
	private float mEdgeScaleY;

	/** The m glow alpha. */
	private float mGlowAlpha;

	/** The m glow scale y. */
	private float mGlowScaleY;

	/** The m edge alpha start. */
	private float mEdgeAlphaStart;

	/** The m edge alpha finish. */
	private float mEdgeAlphaFinish;

	/** The m edge scale y start. */
	private float mEdgeScaleYStart;

	/** The m edge scale y finish. */
	private float mEdgeScaleYFinish;

	/** The m glow alpha start. */
	private float mGlowAlphaStart;

	/** The m glow alpha finish. */
	private float mGlowAlphaFinish;

	/** The m glow scale y start. */
	private float mGlowScaleYStart;

	/** The m glow scale y finish. */
	private float mGlowScaleYFinish;

	/** The m start time. */
	private long mStartTime;

	/** The m duration. */
	private float mDuration;

	/** The m interpolator. */
	private final Interpolator mInterpolator;

	/** The Constant STATE_IDLE. */
	private static final int STATE_IDLE = 0;

	/** The Constant STATE_PULL. */
	private static final int STATE_PULL = 1;

	/** The Constant STATE_ABSORB. */
	private static final int STATE_ABSORB = 2;

	/** The Constant STATE_RECEDE. */
	private static final int STATE_RECEDE = 3;

	/** The Constant STATE_PULL_DECAY. */
	private static final int STATE_PULL_DECAY = 4;

	// How much dragging should effect the height of the edge image.
	// Number determined by user testing.
	/** The Constant PULL_DISTANCE_EDGE_FACTOR. */
	private static final int PULL_DISTANCE_EDGE_FACTOR = 1;

	// How much dragging should effect the height of the glow image.
	// Number determined by user testing.
	/** The Constant PULL_DISTANCE_GLOW_FACTOR. */
	private static final int PULL_DISTANCE_GLOW_FACTOR = 1;

	/** The Constant PULL_DISTANCE_ALPHA_GLOW_FACTOR. */
	private static final float PULL_DISTANCE_ALPHA_GLOW_FACTOR = 0.8f;

	/** The Constant VELOCITY_EDGE_FACTOR. */
	private static final int VELOCITY_EDGE_FACTOR = 8;

	/** The Constant VELOCITY_GLOW_FACTOR. */
	private static final int VELOCITY_GLOW_FACTOR = 16;

	/** The m state. */
	private int mState = STATE_IDLE;

	/** The m pull distance. */
	private float mPullDistance;

	/**
	 * Instantiates a new edge glow.
	 * 
	 * @param edge
	 *           the edge
	 * @param glow
	 *           the glow
	 */
	public EdgeGlow( Drawable edge, Drawable glow ) {
		mEdge = edge;
		mGlow = glow;
		mInterpolator = new DecelerateInterpolator();
	}

	public void setColorFilter( int color, PorterDuff.Mode mode ) {
		if ( null != mEdge ) mEdge.setColorFilter( color, mode );

		if ( null != mGlow ) mGlow.setColorFilter( color, mode );
	}

	/**
	 * Sets the size.
	 * 
	 * @param width
	 *           the width
	 * @param height
	 *           the height
	 */
	public void setSize( int width, int height ) {
		mWidth = width;
		mHeight = height;
	}

	/**
	 * Checks if is finished.
	 * 
	 * @return true, if is finished
	 */
	public boolean isFinished() {
		return mState == STATE_IDLE;
	}

	/**
	 * Finish.
	 */
	public void finish() {
		mState = STATE_IDLE;
	}

	/**
	 * Call when the object is pulled by the user.
	 * 
	 * @param deltaDistance
	 *           Change in distance since the last call
	 */
	public void onPull( float deltaDistance ) {
		final long now = AnimationUtils.currentAnimationTimeMillis();
		if ( mState == STATE_PULL_DECAY && now - mStartTime < mDuration ) {
			return;
		}
		if ( mState != STATE_PULL ) {
			mGlowScaleY = PULL_GLOW_BEGIN;
		}
		mState = STATE_PULL;

		mStartTime = now;
		mDuration = PULL_TIME;

		mPullDistance += deltaDistance;
		float distance = Math.abs( mPullDistance );

		mEdgeAlpha = mEdgeAlphaStart = Math.max( PULL_EDGE_BEGIN, Math.min( distance, MAX_ALPHA ) );
		mEdgeScaleY = mEdgeScaleYStart = Math.max( HELD_EDGE_SCALE_Y, Math.min( distance * PULL_DISTANCE_EDGE_FACTOR, 1.f ) );

		mGlowAlpha = mGlowAlphaStart = Math.min( MAX_ALPHA, mGlowAlpha
				+ ( Math.abs( deltaDistance ) * PULL_DISTANCE_ALPHA_GLOW_FACTOR ) );

		float glowChange = Math.abs( deltaDistance );
		if ( deltaDistance > 0 && mPullDistance < 0 ) {
			glowChange = -glowChange;
		}
		if ( mPullDistance == 0 ) {
			mGlowScaleY = 0;
		}

		// Do not allow glow to get larger than MAX_GLOW_HEIGHT.
		mGlowScaleY = mGlowScaleYStart = Math.min( MAX_GLOW_HEIGHT, Math.max( 0, glowChange * PULL_DISTANCE_GLOW_FACTOR ) );

		mEdgeAlphaFinish = mEdgeAlpha;
		mEdgeScaleYFinish = mEdgeScaleY;
		mGlowAlphaFinish = mGlowAlpha;
		mGlowScaleYFinish = mGlowScaleY;
	}

	/**
	 * Call when the object is released after being pulled.
	 */
	public void onRelease() {
		mPullDistance = 0;

		if ( mState != STATE_PULL && mState != STATE_PULL_DECAY ) {
			return;
		}

		mState = STATE_RECEDE;
		mEdgeAlphaStart = mEdgeAlpha;
		mEdgeScaleYStart = mEdgeScaleY;
		mGlowAlphaStart = mGlowAlpha;
		mGlowScaleYStart = mGlowScaleY;

		mEdgeAlphaFinish = 0.f;
		mEdgeScaleYFinish = 0.f;
		mGlowAlphaFinish = 0.f;
		mGlowScaleYFinish = 0.f;

		mStartTime = AnimationUtils.currentAnimationTimeMillis();
		mDuration = RECEDE_TIME;
	}

	/**
	 * Call when the effect absorbs an impact at the given velocity.
	 * 
	 * @param velocity
	 *           Velocity at impact in pixels per second.
	 */
	public void onAbsorb( int velocity ) {
		mState = STATE_ABSORB;
		velocity = Math.max( MIN_VELOCITY, Math.abs( velocity ) );

		mStartTime = AnimationUtils.currentAnimationTimeMillis();
		mDuration = 0.1f + ( velocity * 0.03f );

		// The edge should always be at least partially visible, regardless
		// of velocity.
		mEdgeAlphaStart = 0.f;
		mEdgeScaleY = mEdgeScaleYStart = 0.f;
		// The glow depends more on the velocity, and therefore starts out
		// nearly invisible.
		mGlowAlphaStart = 0.5f;
		mGlowScaleYStart = 0.f;

		// Factor the velocity by 8. Testing on device shows this works best to
		// reflect the strength of the user's scrolling.
		mEdgeAlphaFinish = Math.max( 0, Math.min( velocity * VELOCITY_EDGE_FACTOR, 1 ) );
		// Edge should never get larger than the size of its asset.
		mEdgeScaleYFinish = Math.max( HELD_EDGE_SCALE_Y, Math.min( velocity * VELOCITY_EDGE_FACTOR, 1.f ) );

		// Growth for the size of the glow should be quadratic to properly
		// respond
		// to a user's scrolling speed. The faster the scrolling speed, the more
		// intense the effect should be for both the size and the saturation.
		mGlowScaleYFinish = Math.min( 0.025f + ( velocity * ( velocity / 100 ) * 0.00015f ), 1.75f );
		// Alpha should change for the glow as well as size.
		mGlowAlphaFinish = Math.max( mGlowAlphaStart, Math.min( velocity * VELOCITY_GLOW_FACTOR * .00001f, MAX_ALPHA ) );
	}

	/**
	 * Draw into the provided canvas. Assumes that the canvas has been rotated accordingly and the size has been set. The effect will
	 * be drawn the full width of X=0 to X=width, emitting from Y=0 and extending to some factor < 1.f of height.
	 * 
	 * @param canvas
	 *           Canvas to draw into
	 * @return true if drawing should continue beyond this frame to continue the animation
	 */
	public boolean draw( Canvas canvas ) {
		update();

		final int glowHeight = mGlow.getIntrinsicHeight();
		final float distScale = (float) mHeight / mWidth;

		mGlow.setAlpha( (int) ( Math.max( 0, Math.min( mGlowAlpha, 1 ) ) * 255 ) );

		mGlow.setBounds( 0, 0, mWidth, (int) Math.min( glowHeight * mGlowScaleY * distScale * 0.6f, mHeight * MAX_GLOW_HEIGHT ) );
		mGlow.draw( canvas );

		if ( mEdge != null ) {
			final int edgeHeight = mEdge.getIntrinsicHeight();
			mEdge.setAlpha( (int) ( Math.max( 0, Math.min( mEdgeAlpha, 1 ) ) * 255 ) );
			mEdge.setBounds( 0, 0, mWidth, (int) ( edgeHeight * mEdgeScaleY ) );
			mEdge.draw( canvas );
		}

		return mState != STATE_IDLE;
	}

	/**
	 * Update.
	 */
	private void update() {
		final long time = AnimationUtils.currentAnimationTimeMillis();
		final float t = Math.min( ( time - mStartTime ) / mDuration, 1.f );

		final float interp = mInterpolator.getInterpolation( t );

		mEdgeAlpha = mEdgeAlphaStart + ( mEdgeAlphaFinish - mEdgeAlphaStart ) * interp;
		mEdgeScaleY = mEdgeScaleYStart + ( mEdgeScaleYFinish - mEdgeScaleYStart ) * interp;
		mGlowAlpha = mGlowAlphaStart + ( mGlowAlphaFinish - mGlowAlphaStart ) * interp;
		mGlowScaleY = mGlowScaleYStart + ( mGlowScaleYFinish - mGlowScaleYStart ) * interp;

		if ( t >= 1.f - EPSILON ) {
			switch ( mState ) {
				case STATE_ABSORB:
					mState = STATE_RECEDE;
					mStartTime = AnimationUtils.currentAnimationTimeMillis();
					mDuration = RECEDE_TIME;

					mEdgeAlphaStart = mEdgeAlpha;
					mEdgeScaleYStart = mEdgeScaleY;
					mGlowAlphaStart = mGlowAlpha;
					mGlowScaleYStart = mGlowScaleY;

					// After absorb, the glow and edge should fade to nothing.
					mEdgeAlphaFinish = 0.f;
					mEdgeScaleYFinish = 0.f;
					mGlowAlphaFinish = 0.f;
					mGlowScaleYFinish = 0.f;
					break;
				case STATE_PULL:
					mState = STATE_PULL_DECAY;
					mStartTime = AnimationUtils.currentAnimationTimeMillis();
					mDuration = PULL_DECAY_TIME;

					mEdgeAlphaStart = mEdgeAlpha;
					mEdgeScaleYStart = mEdgeScaleY;
					mGlowAlphaStart = mGlowAlpha;
					mGlowScaleYStart = mGlowScaleY;

					// After pull, the glow and edge should fade to nothing.
					mEdgeAlphaFinish = 0.f;
					mEdgeScaleYFinish = 0.f;
					mGlowAlphaFinish = 0.f;
					mGlowScaleYFinish = 0.f;
					break;
				case STATE_PULL_DECAY:
					// When receding, we want edge to decrease more slowly
					// than the glow.
					float factor = mGlowScaleYFinish != 0 ? 1 / ( mGlowScaleYFinish * mGlowScaleYFinish ) : Float.MAX_VALUE;
					mEdgeScaleY = mEdgeScaleYStart + ( mEdgeScaleYFinish - mEdgeScaleYStart ) * interp * factor;
					break;
				case STATE_RECEDE:
					mState = STATE_IDLE;
					break;
			}
		}
	}
}
