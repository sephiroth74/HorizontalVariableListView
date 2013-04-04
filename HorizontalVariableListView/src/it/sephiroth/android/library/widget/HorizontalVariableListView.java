/*
 * HorizontalVariableListView.java
 * Handles horizontal variable widths and heights item scrolling
 * 
 * @version 2.0
 */

package it.sephiroth.android.library.widget;

import it.sephiroth.android.library.utils.DataSetObserverExtended;
import it.sephiroth.android.library.widget.IFlingRunnable.FlingRunnableView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.AdapterView;
import android.widget.ListAdapter;

public class HorizontalVariableListView extends HorizontalListView implements OnGestureListener, FlingRunnableView {

	public enum SelectionMode {
		Single, Multiple
	};

	public static final class DataSetChange {

		static final int INVALID_POSITION = -1;

		static final int MASK_NONE = 1 << 0;
		static final int MASK_ADDED = 1 << 1;
		static final int MASK_REMOVED = 1 << 2;
		static final int MASK_INVALIDATED = 1 << 3;

		int viewType;
		int position;
		int status = MASK_NONE;

		public void add( int position ) {
			this.position = position;
			status |= MASK_ADDED;
		}

		public void remove( int position, int viewType ) {
			this.position = position;
			this.viewType = viewType;
			status |= MASK_REMOVED;
		}

		public void invalidate() {
			this.position = INVALID_POSITION;
			status |= MASK_INVALIDATED;
		}

		public void clear() {
			this.position = INVALID_POSITION;
			this.status = MASK_NONE;
			this.viewType = 0;
		}

		public void toggle( int mask ) {
			this.status ^= mask;
		}

		public boolean added() {
			return check( MASK_ADDED );
		}

		public boolean removed() {
			return check( MASK_REMOVED );
		}

		public boolean invalidated() {
			return check( MASK_INVALIDATED );
		}

		public boolean changed() {
			return !check( MASK_NONE );
		}

		private boolean check( int mask ) {
			return ( status & mask ) == mask;
		}
	}

	public interface OnItemClickedListener {

		/**
		 * Callback method to be invoked when an item in this AdapterView has been clicked.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need to access the data associated with the selected item.
		 * 
		 * @param parent
		 *           The AdapterView where the click happened.
		 * @param view
		 *           The view within the AdapterView that was clicked (this will be a view provided by the adapter)
		 * @param position
		 *           The position of the view in the adapter.
		 * @param id
		 *           The row id of the item that was clicked.
		 * @return if the implementation return false, then the selection will not be updated
		 */
		boolean onItemClick( AdapterView<?> parent, View view, int position, long id );
	}

	public static final int OVER_SCROLL_ALWAYS = 0;
	public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;
	public static final int OVER_SCROLL_NEVER = 2;

	protected static final String LOG_TAG = "horizontal-variable-list";

	private static final float WIDTH_THRESHOLD = 1.1f;

	private static final int MAX_SCROLL = Integer.MAX_VALUE / 2;
	private static final int MIN_SCROLL = Integer.MIN_VALUE / 2;

	protected int mAlignMode = Gravity.CENTER;

	protected SparseBooleanArray mSelectedPositions = new SparseBooleanArray();

	protected int mHeight = 0;
	protected int mPaddingTop = 0;
	protected int mPaddingBottom = 0;

	protected ListAdapter mAdapter;

	private int mAdapterItemCount;

	private int mLeftViewIndex = -1;
	private int mRightViewIndex = 0;

	private GestureDetector mGesture;

	private List<Queue<View>> mRecycleBin;
	private Hashtable<Integer, Integer> mViewTypeTable;

	private List<Integer> mChildWidths = new ArrayList<Integer>();
	private List<Integer> mChildHeights = new ArrayList<Integer>();

	private IFlingRunnable mFlingRunnable;

	private DataSetChange mDataSetChange = new DataSetChange();

	private boolean mForceLayout;

	private int mDragTolerance = 0;

	private boolean mDragScrollEnabled;

	protected EdgeGlow mEdgeGlowLeft, mEdgeGlowRight;

	private int mOverScrollMode = OVER_SCROLL_NEVER;

	private Matrix mEdgeMatrix = new Matrix();

	private ScrollNotifier mScrollNotifier;

	private SelectionMode mChoiceMode = SelectionMode.Single;

	private int[] nullInt = new int[2];

	private OnItemSelectedListener mOnItemSelected;
	private OnItemClickedListener mOnItemClicked;
	private OnItemDragListener mItemDragListener;
	private OnScrollChangedListener mScrollListener;
	private OnScrollFinishedListener mScrollFinishedListener;
	private OnLayoutChangeListener mLayoutChangeListener;

	public void setOnItemDragListener( OnItemDragListener listener ) {
		mItemDragListener = listener;
	}

	public void setOnScrollListener( OnScrollChangedListener listener ) {
		mScrollListener = listener;
	}

	public void setOnLayoutChangeListener( OnLayoutChangeListener listener ) {
		mLayoutChangeListener = listener;
	}

	public void setOnScrollFinishedListener( OnScrollFinishedListener listener ) {
		mScrollFinishedListener = listener;
	}

	public OnItemDragListener getOnItemDragListener() {
		return mItemDragListener;
	}

	/**
	 * Controls how selection is managed within the list.<br />
	 * Single or multiple selections are supported
	 * 
	 * @see SelectionMode
	 * @param mode
	 *           the selection mode
	 */
	public void setSelectionMode( SelectionMode mode ) {
		mChoiceMode = mode;
	}

	/**
	 * Returns the current selection mode
	 * 
	 * @see SelectionMode
	 * @return
	 */
	public SelectionMode getChoiceMode() {
		return mChoiceMode;
	}

	/**
	 * Instantiates a new horizontial fixed list view.
	 * 
	 * @param context
	 *           the context
	 * @param attrs
	 *           the attrs
	 */
	public HorizontalVariableListView( Context context, AttributeSet attrs ) {
		super( context, attrs );
		initView();
	}

	public HorizontalVariableListView( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );
		initView();
	}

	private synchronized void initView() {
		mFlingRunnable = new Fling9Runnable( this, mAnimationDuration );
		mLeftViewIndex = -1;
		mRightViewIndex = 0;
		mMaxX = MAX_SCROLL;
		mMinX = MIN_SCROLL;
		mRightEdge = 0;
		mLeftEdge = 0;
		mGesture = new GestureDetector( getContext(), mGestureListener );
		mGesture.setIsLongpressEnabled( false );

		setFocusable( true );
		setFocusableInTouchMode( true );

		ViewConfiguration configuration = ViewConfiguration.get( getContext() );
		mTouchSlop = configuration.getScaledTouchSlop();
		mDragTolerance = mTouchSlop;
		mOverscrollDistance = 10;
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();

	}

	@Override
	public void setOverScrollMode( int mode ) {
		mOverScrollMode = mode;

		if ( mode != OVER_SCROLL_NEVER ) {
			if ( mEdgeGlowLeft == null ) {
				Drawable glow = getContext().getResources().getDrawable( R.drawable.overscroll_glow );
				Drawable edge = getContext().getResources().getDrawable( R.drawable.overscroll_edge );
				mEdgeGlowLeft = new EdgeGlow( edge, glow );
				mEdgeGlowRight = new EdgeGlow( edge, glow );
				mEdgeGlowLeft.setColorFilter( 0xFF33b5e5, Mode.MULTIPLY );
			}
		} else {
			mEdgeGlowLeft = mEdgeGlowRight = null;
		}
	}

	public void setEdgeHeight( int value ) {
		mEdgesHeight = value;
	}

	public void setEdgeGravityY( int value ) {
		mEdgesGravityY = value;
	}

	@Override
	public void trackMotionScroll( int newX ) {

		Log.i( LOG_TAG, "trackMotionScroll: " + newX + " (" + mMinX + " : " + mMaxX + " ) - " + "viewIndex (" + mLeftViewIndex + " : "
				+ mRightViewIndex + ")" );

		scrollTo( newX, 0 );
		mCurrentX = getScrollX();
		removeNonVisibleItems( mCurrentX, nullInt );
		fillList( mCurrentX );
		invalidate();

		Log.w( LOG_TAG, "	total children: " + getChildCount() );
	}

	@Override
	protected void dispatchDraw( Canvas canvas ) {
		super.dispatchDraw( canvas );

		if ( getChildCount() > 0 ) {
			drawEdges( canvas );
		}
	}

	/**
	 * Draw glow edges.
	 * 
	 * @param canvas
	 *           the canvas
	 */
	private void drawEdges( Canvas canvas ) {

		if ( mEdgeGlowLeft != null ) {
			if ( !mEdgeGlowLeft.isFinished() ) {
				final int restoreCount = canvas.save();
				final int height = mEdgesHeight;

				mEdgeMatrix.reset();
				mEdgeMatrix.postScale( 1, -1 );
				mEdgeMatrix.postRotate( 90 );
				mEdgeMatrix.postTranslate( mMinX, 0 );

				if ( mEdgesGravityY == Gravity.BOTTOM ) {
					mEdgeMatrix.postTranslate( 0, getHeight() - height );
				} else if ( mEdgesGravityY == Gravity.CENTER ) {
					mEdgeMatrix.postTranslate( 0, ( getHeight() - height ) / 2 );
				}

				canvas.concat( mEdgeMatrix );

				mEdgeGlowLeft.setSize( height, height / 5 );

				if ( mEdgeGlowLeft.draw( canvas ) ) {
					postInvalidate();
				}
				canvas.restoreToCount( restoreCount );
			}
			if ( !mEdgeGlowRight.isFinished() ) {
				final int restoreCount = canvas.save();
				final int width = getWidth();
				final int height = mEdgesHeight;

				mEdgeMatrix.reset();
				mEdgeMatrix.postRotate( 90 );
				mEdgeMatrix.postTranslate( mCurrentX + width, 0 );

				if ( mEdgesGravityY == Gravity.BOTTOM ) {
					mEdgeMatrix.postTranslate( 0, getHeight() - height );
				} else if ( mEdgesGravityY == Gravity.CENTER ) {
					mEdgeMatrix.postTranslate( 0, ( getHeight() - height ) / 2 );
				}
				canvas.concat( mEdgeMatrix );

				mEdgeGlowRight.setSize( height, height / 5 );

				if ( mEdgeGlowRight.draw( canvas ) ) {
					postInvalidate();
				}
				canvas.restoreToCount( restoreCount );
			}
		}
	}

	/**
	 * Set if a vertical scroll movement will trigger a long click event
	 * 
	 * @param value
	 */
	public void setDragScrollEnabled( boolean value ) {
		mDragScrollEnabled = value;
	}

	public boolean getDragScrollEnabled() {
		return mDragScrollEnabled;
	}

	public int getSelectedPosition() {
		if ( mSelectedPositions.size() > 0 ) return mSelectedPositions.keyAt( 0 );
		return INVALID_POSITION;
	}

	public int[] getSelectedPositions() {

		int[] result;

		if ( mSelectedPositions.size() > 0 ) {

			// multiple
			if ( mChoiceMode == SelectionMode.Multiple ) {
				result = new int[mSelectedPositions.size()];
				for ( int i = 0; i < mSelectedPositions.size(); i++ ) {
					result[i] = mSelectedPositions.keyAt( i );
				}

			} else {
				// single
				result = new int[] { mSelectedPositions.keyAt( 0 ) };
			}
		} else {
			result = new int[] { INVALID_POSITION };
		}

		return result;
	}

	public void setSelectedPosition( int position, boolean fireEvent ) {

		if ( position >= mAdapterItemCount ) {
			Log.w( LOG_TAG, "Position " + position + " is out of bounds" );
			return;
		}

		if ( position == INVALID_POSITION ) {
			setSelectedItem( null, INVALID_POSITION, false, fireEvent );
		} else {
			View child = getItemAt( position );
			setSelectedItem( child, position, true, fireEvent );
		}
	}

	public void setSelectedPositions( int[] positions, boolean fireEvent ) {

		if ( mChoiceMode == SelectionMode.Multiple ) {

			View child;
			int position;

			// first clear all current selection
			synchronized ( mSelectedPositions ) {

				for ( int i = 0; i < mSelectedPositions.size(); i++ ) {
					position = mSelectedPositions.keyAt( i );
					child = getChildAt( position );

					if ( null != child ) {
						child.setSelected( false );
					}
				}

				mSelectedPositions.clear();
			}

			if ( null != positions && positions.length > 0 ) {

				for ( int i = 0; i < positions.length - 1; i++ ) {
					position = positions[i];
					child = getItemAt( position );
					setSelectedItem( child, position, true, false );
				}

				position = positions[positions.length - 1];
				child = getItemAt( position );
				setSelectedItem( child, position, true, fireEvent );
			}

		} else {
			Log.w( LOG_TAG, "This method has no effect on single selection list" );
		}
	}

	@Override
	public void setOnItemSelectedListener( AdapterView.OnItemSelectedListener listener ) {
		mOnItemSelected = listener;
	}

	/**
	 * Toggle the item clicked listener. See the {@link OnItemClickedListener} interface
	 * 
	 * @param listener
	 */
	public void setOnItemClickedListener( OnItemClickedListener listener ) {
		mOnItemClicked = listener;
	}

	private DataSetObserverExtended mDataObserverExtended = new DataSetObserverExtended() {

		@Override
		public void onAdded( int position ) {
			mDataSetChange.add( position );
			handleDataSetChanged( mDataSetChange );
		};

		@Override
		public void onRemoved( int position, int viewType ) {
			mDataSetChange.remove( position, viewType );
			handleDataSetChanged( mDataSetChange );
		};

		@Override
		public void onChanged() {
			mDataSetChange.invalidate();
			handleDataSetChanged( mDataSetChange );
		};

		@Override
		public void onInvalidated() {
			this.onChanged();
		};
	};

	private DataSetObserver mDataObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			mDataSetChange.invalidate();
			handleDataSetChanged( mDataSetChange );
		}

		@Override
		public void onInvalidated() {
			mDataSetChange.invalidate();
			handleDataSetChanged( mDataSetChange );
		}
	};

	public void requestFullLayout() {
		mForceLayout = true;
		invalidate();
		requestLayout();
	}

	/** The m height measure spec. */
	private int mHeightMeasureSpec;

	/** The m width measure spec. */
	private int mWidthMeasureSpec;

	/** The m left edge. */
	private int mRightEdge, mLeftEdge;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView#getAdapter()
	 */
	@Override
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		return null;
	}

	@Override
	public void setAdapter( ListAdapter adapter ) {

		if ( mAdapter != null ) {

			if ( mAdapter instanceof BaseAdapterExtended ) {
				( (BaseAdapterExtended) mAdapter ).unregisterDataSetObserverExtended( mDataObserverExtended );
			} else {
				mAdapter.unregisterDataSetObserver( mDataObserver );
			}

			emptyRecycler();
			mAdapterItemCount = 0;
		}

		mAdapter = adapter;
		mChildWidths.clear();
		mChildHeights.clear();

		if ( mAdapter != null ) {
			mAdapterItemCount = mAdapter.getCount();

			if ( mAdapter instanceof BaseAdapterExtended ) {
				( (BaseAdapterExtended) mAdapter ).registerDataSetObserverExtended( mDataObserverExtended );
			} else {
				mAdapter.registerDataSetObserver( mDataObserver );
			}
			int total = mAdapter.getViewTypeCount();

			mRecycleBin = Collections.synchronizedList( new ArrayList<Queue<View>>() );
			mViewTypeTable = new Hashtable<Integer, Integer>();
			for ( int i = 0; i < total; i++ ) {
				mRecycleBin.add( new LinkedList<View>() );
				mChildWidths.add( -1 );
				mChildHeights.add( -1 );
			}
		}
		reset();
	}

	private void emptyRecycler() {
		if ( null != mRecycleBin ) {
			while ( mRecycleBin.size() > 0 ) {
				Queue<View> recycler = mRecycleBin.remove( 0 );
				recycler.clear();
			}
			mRecycleBin.clear();
			mViewTypeTable.clear();
		}
	}

	/**
	 * Reset.
	 */
	private synchronized void reset() {
		mCurrentX = 0;
		initView();
		removeAllViewsInLayout();
		mForceLayout = true;
		requestLayout();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		Log.d( LOG_TAG, "onDetachedFromWindow" );

		removeCallbacks( mScrollNotifier );
		emptyRecycler();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView#setSelection(int)
	 */
	@Override
	public void setSelection( int position ) {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.View#onMeasure(int, int)
	 */
	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
		super.onMeasure( widthMeasureSpec, heightMeasureSpec );

		mHeightMeasureSpec = heightMeasureSpec;
		mWidthMeasureSpec = widthMeasureSpec;
	}

	/**
	 * Adds the and measure child.
	 * 
	 * @param child
	 *           the child
	 * @param viewPos
	 *           the view pos
	 */
	private void addAndMeasureChild( final View child, int viewPos ) {
		LayoutParams params = child.getLayoutParams();

		if ( params == null ) {
			params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT );
		}

		addViewInLayout( child, viewPos, params, false );
		forceChildLayout( child, params );
	}

	public void forceChildLayout( View child, LayoutParams params ) {
		int childHeightSpec = ViewGroup.getChildMeasureSpec( mHeightMeasureSpec, getPaddingTop() + getPaddingBottom(), params.height );
		int childWidthSpec = ViewGroup.getChildMeasureSpec( mWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), params.width );
		child.measure( childWidthSpec, childHeightSpec );
	}

	@SuppressWarnings("unused")
	@Override
	protected void onLayout( boolean changed, int left, int top, int right, int bottom ) {
		super.onLayout( changed, left, top, right, bottom );

		Log.i( LOG_TAG, "onLayout" );

		if ( mAdapter == null ) {
			return;
		}

		if ( !changed ) {
			layoutChildren();
		}

		mHeight = bottom - top;
		int trackValue;

		if ( changed ) {
			mPaddingTop = getPaddingTop();
			mPaddingBottom = getPaddingBottom();

			trackValue = mCurrentX = mOldX = 0;
			initView();
			removeAllViewsInLayout();
		}

		if ( mForceLayout ) {
			mOldX = mCurrentX;
			trackValue = mOldX;
			initView();
			removeAllViewsInLayout();
		}

		if ( changed || mForceLayout ) {
			trackMotionScroll( mOldX );
			mForceLayout = false;
		}

		postNotifyLayoutChange( changed, left, top, right, bottom );
	}

	public void layoutChildren() {

		int left, right;

		for ( int i = 0; i < getChildCount(); i++ ) {
			View child = getChildAt( i );

			forceChildLayout( child, child.getLayoutParams() );

			left = child.getLeft();
			right = child.getRight();

			int childHeight = child.getHeight();

			layoutChild( child, left, right, childHeight );
		}
	}

	/**
	 * Fill list.
	 * 
	 * @param positionX
	 *           the position x
	 */
	private void fillList( final int positionX ) {

		int edge = 0;

		View child = getChildAt( getChildCount() - 1 );
		if ( child != null ) {
			edge = child.getRight();
		}
		fillListRight( mCurrentX, edge );

		edge = 0;
		child = getChildAt( 0 );
		if ( child != null ) {
			edge = child.getLeft();
		}
		fillListLeft( mCurrentX, edge );
		
		Log.d( LOG_TAG, "	viewIndex: " + mLeftViewIndex + ":" + mRightViewIndex );
	}

	public int getViewWidth() {
		return (int) ( (float) getWidth() * WIDTH_THRESHOLD );
	}

	/**
	 * Fill list left.
	 * 
	 * @param positionX
	 *           the position x
	 * @param leftEdge
	 *           the left edge
	 */
	private void fillListLeft( int positionX, int leftEdge ) {
		if ( mAdapter == null ) return;

		while ( ( leftEdge - positionX ) > mLeftEdge && mLeftViewIndex >= 0 ) {
			Log.d( LOG_TAG, "fillListLeft. leftIndex: " + mLeftViewIndex );
			int viewType = mAdapter.getItemViewType( mLeftViewIndex );
			int childWidth = mChildWidths.get( viewType );
			fillItem( mLeftViewIndex, viewType, 0, leftEdge - childWidth );
			leftEdge -= childWidth;
			mLeftViewIndex--;
		}

		if ( mLeftViewIndex == -1 ) {
			mMinX = leftEdge;
			Log.d( LOG_TAG, "(6) minX: " + mMinX );
		}
	}

	/**
	 * Fill list right.
	 * 
	 * @param positionX
	 *           the position x
	 * @param rightEdge
	 *           the right edge
	 */
	private void fillListRight( int positionX, int rightEdge ) {

		boolean firstChild = getChildCount() == 0 || mForceLayout;

		if ( mAdapter == null ) return;

		final int realWidth = getWidth();
		int viewWidth = getViewWidth();

		while ( ( rightEdge - positionX ) < mRightEdge || firstChild ) {

			if ( mRightViewIndex >= mAdapterItemCount ) {
				break;
			}

			Log.d( LOG_TAG, "fillListRight. rightIndex: " + mRightViewIndex );

			int viewType = mAdapter.getItemViewType( mRightViewIndex );
			fillItem( mRightViewIndex, viewType, -1, rightEdge );

			int childWidth = mChildWidths.get( viewType );

			if ( firstChild ) {
				if ( mEdgesHeight == -1 ) {
					mEdgesHeight = mChildHeights.get( viewType );
				}
				mRightEdge = viewWidth;
				mLeftEdge = ( realWidth - viewWidth );

				mMinX = 0;
				firstChild = false;
			}

			rightEdge += childWidth;
			mRightViewIndex++;
		}

		if ( mRightViewIndex == mAdapterItemCount ) {

			int leftEdge = mMinX > MIN_SCROLL ? mMinX : 0;

			if ( ( rightEdge - leftEdge ) > realWidth ) {
				mMaxX = rightEdge - realWidth;
				Log.d( LOG_TAG, "(4) right edge: " + rightEdge + ", realWidth: " + realWidth + ", maxX: " + mMaxX + ", minX: " + mMinX );
			} else {
				mMaxX = rightEdge - realWidth;
				if ( mMaxX - mMinX < realWidth ) {
					mMaxX = mMinX;
				}
				Log.d( LOG_TAG, "(5) right edge: " + rightEdge + ", realWidth: " + realWidth + ", maxX: " + mMaxX + ", minX: " + mMinX );
			}
		}
	}

	private View fillItem( int position, int viewType, int layoutIndex, int left ) {
		final boolean selected = getIsSelected( position );
		View child = mAdapter.getView( position, mRecycleBin.get( viewType ).poll(), this );
		Log.d( LOG_TAG, "	adding child: " + child.getId() + " at position " + position );
		child.setSelected( selected );
		addAndMeasureChild( child, layoutIndex );

		int childWidth = mChildWidths.get( viewType );
		int childHeight = mChildHeights.get( viewType );

		if ( childWidth == -1 ) {
			childWidth = child.getMeasuredWidth();
			childHeight = child.getMeasuredHeight();

			mChildWidths.set( viewType, childWidth );
			mChildHeights.set( viewType, childHeight );
		}

		Log.d( LOG_TAG, "child hashCode: " + child.hashCode() );

		mViewTypeTable.put( child.hashCode(), viewType );

		layoutChild( child, left, left + childWidth, childHeight );
		return child;
	}

	/**
	 * Returns the child width and height.
	 * 
	 * @param position
	 * @param viewType
	 * @return
	 */
	private Rect getChildBounds( int position, int viewType ) {

		// first check if we already have this values in our cache
		int childWidth = mChildWidths.get( viewType );
		int childHeight = mChildHeights.get( viewType );

		if ( childWidth == -1 ) {
			// otherwise create the child and measure it
			View child = mAdapter.getView( position, mRecycleBin.get( viewType ).poll(), this );
			addAndMeasureChild( child, -1 );
			childWidth = child.getMeasuredWidth();
			childHeight = child.getMeasuredHeight();

			mChildWidths.set( viewType, childWidth );
			mChildHeights.set( viewType, childHeight );

			// then remove it, we don't need it anymore
			removeViewInLayout( child );
		}

		return new Rect( 0, 0, childWidth, childHeight );
	}

	private void handleDataSetChanged( DataSetChange data ) {
		// update the adapter items count
		synchronized ( HorizontalVariableListView.this ) {
			mAdapterItemCount = mAdapter.getCount();
		}

		if ( data.invalidated() ) {

			// whole dataset has been invalidated
			data.clear();
			reset();

		} else if ( data.added() ) {

			// data has been added
			data.toggle( DataSetChange.MASK_ADDED );
			handleItemAdded( data.position );
		} else if ( data.removed() ) {

			// data has been removed
			data.toggle( DataSetChange.MASK_REMOVED );
			handleItemRemoved( data.position, data.viewType );
		}
	}

	private void handleItemRemoved( int position, int viewType ) {
		Log.i( LOG_TAG, "handleItemRemoved: " + position );

		if ( mAdapterItemCount < 1 ) {
			reset();
			return;
		}

		// remove from selected status
		// TODO: doesnt work
		synchronized ( mSelectedPositions ) {
			mSelectedPositions.delete( position );
			
			int size = mSelectedPositions.size();
			for ( int i = size - 1; i >= 0; i-- ) {
				int index = mSelectedPositions.keyAt( i );
				if ( index >= position ) {
					Log.d( LOG_TAG, "replacing: " + index + " with: " + ( index - 1 ) );
					mSelectedPositions.delete( index );
					mSelectedPositions.put( index - 1, true );
				}
			}
		}

		removeChild( position, viewType );
	}

	private void handleItemAdded( int position ) {

		if ( mAdapterItemCount == 1 ) {
			// first item, need a fresh setup
			reset();
			return;
		}

		// invalidate the maxX
		mMaxX = MAX_SCROLL;
		boolean is_last = position == mAdapterItemCount - 1;
		Log.i( LOG_TAG, "handleItemAdded: " + position + ", count: " + mAdapterItemCount + ", is_last: " + is_last );

		// if not last, then update all the current selections
		if ( !is_last ) {
			// update the selected positions
			synchronized ( mSelectedPositions ) {
				int size = mSelectedPositions.size();
				for ( int i = size - 1; i >= 0; i-- ) {
					int index = mSelectedPositions.keyAt( i );
					Log.d( LOG_TAG, "i: " + i + ", position: " + index );
					if ( index >= position ) {
						Log.d( LOG_TAG, "replacing: " + index + " with: " + ( index + 1 ) );
						mSelectedPositions.delete( index );
						mSelectedPositions.put( index + 1, true );
					}
				}
			}
			addChild( position );
		} else {
			// new item added at the end, no need to compute
			trackMotionScroll( mCurrentX );
		}
	}

	private void removeChild( int position, int viewType ) {
		if ( null == mAdapter ) return;

		boolean is_first = position == 0;
		boolean is_last = position == mAdapterItemCount;

		Log.i( LOG_TAG, "removeChildren, from: " + position );
		Log.d( LOG_TAG, "is_first: " + is_first + ", is_last: " + is_last );

		// current displayRange
		int first = getFirstVisiblePosition();
		int last = getLastVisiblePosition();

		int childWidth;
		View child;

		Log.d( LOG_TAG, "first: " + first + ", last: " + last + ", currentX: " + mCurrentX );
		Log.d( LOG_TAG, "(1) minX:" + mMinX + ", maxX: " + mMaxX );

		if ( position > last ) {
			// position is above the current range
			Log.w( LOG_TAG, "position is after" );

			// reset the max scroll position
			mMaxX = MAX_SCROLL;

		} else if ( position < first ) {
			// position if before the current range
			Log.w( LOG_TAG, "position is before" );

			// reset the min x
			mMinX = MIN_SCROLL;

			// we need to get the child width, in order to shift all
			// the current children by its width
			childWidth = getChildBounds( position, viewType ).width();

			// using animation
			mLeftViewIndex -= 1;
			mRightViewIndex -= 1;
			mMinX = MIN_SCROLL;
			Log.d( LOG_TAG, "viewIndex: " + mLeftViewIndex + ":" + mRightViewIndex );
			smoothScrollTo( Math.min( mMaxX, mCurrentX + childWidth ) );
			return;

			/*
			 * 
			 * // get the first item width childWidth = getItemAt( first ).getWidth();
			 * 
			 * // move left all items by the same width for ( int i = first; i <= last; i++ ) { Log.d( LOG_TAG,
			 * "get child at position: " + i + ", total: " + mAdapterItemCount ); child = getItemAt( i ); layoutChild( child,
			 * child.getLeft() - childWidth, child.getRight() - childWidth, child.getHeight() ); }
			 * 
			 * // remove the non visible items removeNonVisibleItems( mCurrentX, nullInt );
			 * 
			 * Log.d( LOG_TAG, "removed left: " + nullInt[0] + ", right: " + nullInt[1] );
			 * 
			 * mLeftViewIndex -= nullInt[0] > 0 ? 1 : 0; mRightViewIndex -= nullInt[0] > 0 ? 1 : 0;
			 */

		} else {
			// position is in current range
			Log.w( LOG_TAG, "position is in range" );

			// if the position is > count/2 then shift
			// children to right, otherwise shift them to left
			int direction = position > mAdapterItemCount / 2 ? 1 : -1;

			// get the item to be removed and remove it
			child = getItemAt( position );
			int right = child.getRight();
			int left = child.getLeft();
			childWidth = right - left;

			Log.d( LOG_TAG, "offer(4): " + viewType + ", " + child );
			mRecycleBin.get( viewType ).offer( child );

			Log.d( LOG_TAG, "removing child at " + position + ", with a width of " + childWidth );
			removeViewInLayout( child );

			Log.w( LOG_TAG, "direction: " + direction );
			
			
			if( is_last ) {
				// removed the last item
				mRightViewIndex -= 1;
				mMaxX = left;
				smoothScrollTo( Math.min( mMaxX, Math.max( mMinX, mCurrentX - childWidth ) ) );
				
			} else if( is_first ) {
				// removed the first item
				mRightViewIndex -= 1;
				mMinX = right;
				smoothScrollTo( Math.max( mMinX, mCurrentX + childWidth ) );
			} else {
				
	
				if ( direction > 0 ) {
					// shift left items to right
					for ( int i = first; i < position; i++ ) {
						child = getItemAt( i );
						if ( null != child ) {
							layoutChild( child, child.getLeft() + childWidth, child.getRight() + childWidth, child.getHeight() );
							Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: "
									+ ( child.getLeft() - mCurrentX ) );
						}
					}
	
					mRightViewIndex -= 1;
	
				} else {
					// shift right items to left
					for ( int i = last; i >= position; i-- ) {
						child = getItemAt( i );
						if ( null != child ) {
							layoutChild( child, child.getLeft() - childWidth, child.getRight() - childWidth, child.getHeight() );
							Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) );
						}
					}
	
					mRightViewIndex -= 1;
				}
			}

			/*
			 * if ( position > 0 ) { // shift left all the current visible children // only if the position removed is not 0 for ( int
			 * i = position; i <= last; i++ ) { child = getItemAt( i ); if ( null != child ) { layoutChild( child, child.getLeft() -
			 * childWidth, child.getRight() - childWidth, child.getHeight() ); } } } else { // removed first item, recompute the min x
			 * mMinX = right; }
			 * 
			 * // decrease the right index mRightViewIndex -= 1;
			 * 
			 * // remove out of bounds items removeNonVisibleItems( mCurrentX, nullInt );
			 * 
			 * Log.d( LOG_TAG, "removed left: " + nullInt[0] + ", right: " + nullInt[1] );
			 */
		}

		Log.d( LOG_TAG, "(2) minX:" + mMinX + ", maxX: " + mMaxX + ", current: " + mCurrentX );
		Log.d( LOG_TAG, "viewIndex: " + mLeftViewIndex + ":" + mRightViewIndex );

		fillList( mCurrentX );

		Log.d( LOG_TAG, "(3) minX:" + mMinX + ", maxX: " + mMaxX + ", current: " + mCurrentX );

		// if scroll is out of bounds, restore the correct position
		if ( mCurrentX > mMaxX ) {
			smoothScrollTo( mMaxX );
		} else if ( mCurrentX < mMinX ) {
			smoothScrollTo( mMinX );
		}
		postInvalidate();
	}

	private void addChild( int fromPosition ) {
		if ( null == mAdapter ) return;

		Log.i( LOG_TAG, "layoutChildren, from: " + fromPosition );

		boolean is_last = fromPosition == mAdapterItemCount - 1;
		boolean is_first = fromPosition == 0;
		int total = getChildCount();
		int first = getFirstVisiblePosition();
		int last = getLastVisiblePosition();
		int viewType, childWidth, i;
		View child;

		Log.d( LOG_TAG, "first: " + first + ", last: " + last + ", total: " + total );
		Log.d( LOG_TAG, "is_first: " + is_first + ", is_last: " + is_last );

		if ( fromPosition > last ) {
			Log.w( LOG_TAG, "position is after" );
			// nothing to do here..

		} else if ( fromPosition <= first ) {
			Log.w( LOG_TAG, "position is before" );

			// we need to get the child width, in order to shift all
			// the current children by its width
			viewType = mAdapter.getItemViewType( fromPosition );
			childWidth = getChildBounds( fromPosition, viewType ).width();

			// using animation
			mLeftViewIndex += 1;
			mRightViewIndex += 1;
			mMinX = MIN_SCROLL;
			smoothScrollTo( mCurrentX - childWidth );

			/*
			 * 
			 * // without animation // now shift all the current views by childWidth for( i = first; i <= last; i++ ) { child =
			 * getItemAt( i ); layoutChild( child, child.getLeft() + childWidth, child.getRight() + childWidth, child.getHeight() );
			 * Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) ); }
			 * 
			 * removeNonVisibleItems( mCurrentX, nullInt ); mLeftViewIndex += 1; mRightViewIndex += 1; mMinX = MIN_SCROLL; Log.d(
			 * LOG_TAG, "removed children: " + nullInt[0] + ", " + nullInt[1] );
			 */

		} else {
			Log.w( LOG_TAG, "position is in range" );

			// get the current child at that position
			child = getItemAt( fromPosition );
			// add the new child
			viewType = mAdapter.getItemViewType( fromPosition );
			child = fillItem( fromPosition, viewType, fromPosition - first, child.getLeft() );
			Log.d( LOG_TAG, "added child: " + child + " at " + ( fromPosition - first ) );
			childWidth = child.getWidth();

			// now move the remaining items..
			i = fromPosition + 1;
			while ( i <= last + 1 ) {
				child = getItemAt( i );
				if ( null != child ) {
					layoutChild( child, child.getLeft() + childWidth, child.getRight() + childWidth, child.getHeight() );
					Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) );
				}
				i++;
			}

			removeNonVisibleItems( mCurrentX, nullInt );
			mRightViewIndex += 1;
			Log.d( LOG_TAG, "removed children: " + nullInt[0] + ", " + nullInt[1] );
		}

		// fillList( mCurrentX );

		Log.d( LOG_TAG, "minX: " + mMinX + ":" + mMaxX );

		if ( true ) return;

		if ( fromPosition <= last ) {

			i = last;
			if ( fromPosition > first ) {
				first = fromPosition;
			}

			Log.d( LOG_TAG, "child at: " + first );
			child = getItemAt( first );
			int rightEdge = 0;
			if ( null != child ) {
				rightEdge = child.getLeft();
			}

			Log.d( LOG_TAG, "right edge: " + rightEdge );

			// remove items
			for ( ; i >= first; i-- ) {
				child = getItemAt( i );
				Log.d( LOG_TAG, i + ", remove child: " + child );
				viewType = mAdapter.getItemViewType( i );
				Log.d( LOG_TAG, "offer(1): " + viewType + ", " + child );
				mRecycleBin.get( viewType ).offer( child );
				removeViewInLayout( child );
			}

			// restore items
			for ( i = first; i <= last; i++ ) {
				viewType = mAdapter.getItemViewType( i );
				fillItem( i, viewType, -1, rightEdge );
				childWidth = mChildWidths.get( viewType );
				rightEdge += childWidth;
			}
		}

		fillList( mCurrentX );
		invalidate();
	}

	@Override
	public int getFirstVisiblePosition() {
		return mLeftViewIndex + 1;
	}

	@Override
	public int getLastVisiblePosition() {
		return mRightViewIndex - 1;
	}

	public View getItemAt( int position ) {
		return getChildAt( position - ( mLeftViewIndex + 1 ) );
	}

	@Override
	public int getScreenPositionForView( View view ) {
		View listItem = view;
		try {
			View v;
			while ( !this.equals( ( v = (View) listItem.getParent() ) ) ) {
				listItem = v;
			}
		} catch ( ClassCastException e ) {
			// We made it up to the window without find this list view
			return INVALID_POSITION;
		} catch ( NullPointerException e ) {
			return INVALID_POSITION;
		}

		// Search the children for the list item
		final int childCount = getChildCount();
		for ( int i = 0; i < childCount; i++ ) {
			if ( getChildAt( i ).equals( listItem ) ) {
				return i;
			}
		}

		// Child not found!
		return INVALID_POSITION;
	}

	@Override
	public int getPositionForView( View view ) {
		View listItem = view;
		try {
			View v;
			while ( !( v = (View) listItem.getParent() ).equals( this ) ) {
				listItem = v;
			}
		} catch ( ClassCastException e ) {
			// We made it up to the window without find this list view
			return INVALID_POSITION;
		}

		// Search the children for the list item
		final int childCount = getChildCount();
		for ( int i = 0; i < childCount; i++ ) {
			if ( getChildAt( i ).equals( listItem ) ) {
				return mLeftViewIndex + i + 1;
			}
		}

		// Child not found!
		return INVALID_POSITION;
	}

	protected void layoutChild( View child, int left, int right, int childHeight ) {

		int top = mPaddingTop;
		if ( mAlignMode == Gravity.BOTTOM ) {
			top = top + ( mHeight - childHeight );
		} else if ( mAlignMode == Gravity.CENTER ) {
			top = top + ( mHeight - childHeight ) / 2;
		}
		child.layout( left, top, right, top + childHeight );
	}

	/**
	 * Removes the non visible items.
	 * 
	 * @param positionX
	 *           the position x
	 */
	private void removeNonVisibleItems( final int positionX, int[] removed ) {

		removed[0] = 0;
		removed[1] = 0;

		View child = getChildAt( 0 );
		boolean removedCount = false;

		// remove to left...
		while ( child != null && child.getRight() - positionX < mLeftEdge ) {

			if ( null != mAdapter ) {
				// int position = getPositionForView( child );
				int hashCode = child.hashCode();
				if ( mViewTypeTable.containsKey( hashCode ) ) {
					// int viewType = mAdapter.getItemViewType( position );
					int viewType = mViewTypeTable.get( hashCode );
					Log.d( LOG_TAG, "offer(2): " + viewType + ", " + child );
					mRecycleBin.get( viewType ).offer( child );
				}
			}
			Log.d( LOG_TAG, "removing left " + child );
			removedCount = true;
			removed[0] += 1;
			removeViewInLayout( child );
			mLeftViewIndex++;
			child = getChildAt( 0 );
		}

		// remove to right...
		child = getChildAt( getChildCount() - 1 );

		if ( null != child ) Log.d( LOG_TAG, child + ", left: " + ( child.getLeft() - positionX ) );

		while ( child != null && child.getLeft() - positionX > mRightEdge ) {

			if ( null != mAdapter ) {
				// int position = getPositionForView( child );
				// int viewType = mAdapter.getItemViewType( position );

				int hashCode = child.hashCode();
				if ( mViewTypeTable.containsKey( hashCode ) ) {
					int viewType = mViewTypeTable.get( hashCode );
					Log.d( LOG_TAG, "offer(3): " + viewType + ", " + child );
					mRecycleBin.get( viewType ).offer( child );
				}

			}

			Log.d( LOG_TAG, "removing right " + child );
			removedCount = true;
			removed[1] += 1;
			removeViewInLayout( child );
			mRightViewIndex--;
			child = getChildAt( getChildCount() - 1 );
		}

		if ( removedCount ) {
			Log.d( LOG_TAG, "removeNonVisibleItems: leftIndex: " + mLeftViewIndex + ", rightIndex: " + mRightViewIndex );
		}
	}

	private float mTestDragX, mTestDragY;
	private boolean mCanCheckDrag;
	private boolean mWasFlinging;
	private WeakReference<View> mOriginalDragItem;

	@Override
	public boolean onDown( MotionEvent event ) {
		return true;
	}

	@Override
	public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {
		return true;
	}

	@Override
	public boolean onFling( MotionEvent event0, MotionEvent event1, float velocityX, float velocityY ) {
		if ( mMaxX == mMinX ) return false;
		mCanCheckDrag = false;
		mWasFlinging = true;
		mFlingRunnable.startUsingVelocity( mCurrentX, (int) -velocityX );
		return true;
	}

	@Override
	public void onLongPress( MotionEvent e ) {
		if ( mWasFlinging ) return;

		OnItemLongClickListener listener = getOnItemLongClickListener();
		if ( null != listener ) {

			if ( !mFlingRunnable.isFinished() ) return;

			int i = getChildAtPosition( e.getX(), e.getY() );
			if ( i > -1 ) {
				View child = getChildAt( i );
				fireLongPress( child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ) );
			}
		}
	}

	private int getChildAtPosition( float x, float y ) {
		Rect viewRect = new Rect();

		for ( int i = 0; i < getChildCount(); i++ ) {
			View child = getChildAt( i );
			int left = child.getLeft();
			int right = child.getRight();
			int top = child.getTop();
			int bottom = child.getBottom();
			viewRect.set( left, top, right, bottom );
			viewRect.offset( -mCurrentX, 0 );

			if ( viewRect.contains( (int) x, (int) y ) ) {
				return i;
			}
		}
		return -1;
	}

	private boolean fireLongPress( View item, int position, long id ) {
		if ( getOnItemLongClickListener().onItemLongClick( HorizontalVariableListView.this, item, position, id ) ) {
			performHapticFeedback( HapticFeedbackConstants.LONG_PRESS );
			return true;
		}
		return false;
	}

	private boolean fireItemDragStart( View item, int position, long id ) {

		mCanCheckDrag = false;
		mIsBeingDragged = false;

		if ( mItemDragListener.onItemStartDrag( HorizontalVariableListView.this, item, position, id ) ) {
			mIsDragging = true;
			performHapticFeedback( HapticFeedbackConstants.LONG_PRESS );
			return true;
		}
		return false;
	}

	private void fireOnLayoutChangeListener( boolean changed, int left, int top, int right, int bottom ) {
		if ( mLayoutChangeListener != null ) {
			mLayoutChangeListener.onLayoutChange( changed, left, top, right, bottom );
		}
	}

	private void fireOnScrollChanged() {
		if ( mScrollListener != null ) {
			mScrollListener.onScrollChanged();
		}
	}

	private void fireOnScrollFininshed() {
		if ( null != mScrollFinishedListener ) {
			mScrollFinishedListener.onScrollFinished( mCurrentX );
		}
	}

	private void postScrollNotifier() {
		if ( mScrollListener != null ) {
			if ( mScrollNotifier == null ) {
				mScrollNotifier = new ScrollNotifier();
			}
			post( mScrollNotifier );
		}
	}

	private void postNotifyLayoutChange( final boolean changed, final int left, final int top, final int right, final int bottom ) {
		post( new Runnable() {

			@Override
			public void run() {
				fireOnLayoutChangeListener( changed, left, top, right, bottom );
			}
		} );
	}

	private class ScrollNotifier implements Runnable {

		@Override
		public void run() {
			fireOnScrollChanged();
		}
	}

	public void setIsDragging( boolean value ) {
		mIsDragging = value;
	}

	private int getItemIndex( View view ) {
		final int total = getChildCount();
		for ( int i = 0; i < total; i++ ) {
			if ( view == getChildAt( i ) ) {
				return i;
			}
		}
		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.OnGestureListener#onShowPress(android.view .MotionEvent)
	 */
	@Override
	public void onShowPress( MotionEvent arg0 ) {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.OnGestureListener#onSingleTapUp(android.view .MotionEvent)
	 */
	@Override
	public boolean onSingleTapUp( MotionEvent arg0 ) {
		return false;
	}

	private boolean mIsDragging = false;
	private boolean mIsBeingDragged = false;
	private int mActivePointerId = -1;
	private int mLastMotionX;
	private float mLastMotionX2;
	private VelocityTracker mVelocityTracker;
	private static final int INVALID_POINTER = -1;
	private int mOverscrollDistance;
	private int mMinimumVelocity;
	private int mMaximumVelocity;

	private void initOrResetVelocityTracker() {
		if ( mVelocityTracker == null ) {
			mVelocityTracker = VelocityTracker.obtain();
		} else {
			mVelocityTracker.clear();
		}
	}

	private void initVelocityTrackerIfNotExists() {
		if ( mVelocityTracker == null ) {
			mVelocityTracker = VelocityTracker.obtain();
		}
	}

	private void recycleVelocityTracker() {
		if ( mVelocityTracker != null ) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
	}

	@Override
	public void requestDisallowInterceptTouchEvent( boolean disallowIntercept ) {
		if ( disallowIntercept ) {
			recycleVelocityTracker();
		}
		super.requestDisallowInterceptTouchEvent( disallowIntercept );
	}

	@Override
	public boolean onInterceptTouchEvent( MotionEvent ev ) {

		getParent().requestDisallowInterceptTouchEvent( true );

		if ( mIsDragging ) return false;

		final int action = ev.getAction();
		mGesture.onTouchEvent( ev );

		/*
		 * Shortcut the most recurring case: the user is in the dragging state and he is moving his finger. We want to intercept this
		 * motion.
		 */
		if ( action == MotionEvent.ACTION_MOVE ) {
			if ( mIsBeingDragged ) {
				return true;
			}
		}

		switch ( action & MotionEvent.ACTION_MASK ) {
			case MotionEvent.ACTION_MOVE: {
				/*
				 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check whether the user has moved far enough
				 * from his original down touch.
				 */
				final int activePointerId = mActivePointerId;
				if ( activePointerId == INVALID_POINTER ) {
					// If we don't have a valid id, the touch down wasn't on
					// content.
					break;
				}

				final int pointerIndex = ev.findPointerIndex( activePointerId );
				final int x = (int) ev.getX( pointerIndex );
				final int y = (int) ev.getY( pointerIndex );
				final int xDiff = Math.abs( x - mLastMotionX );
				mLastMotionX2 = x;

				if ( checkDrag( x, y ) ) {
					return false;
				}

				if ( xDiff > mTouchSlop ) {

					mIsBeingDragged = true;
					mLastMotionX = x;
					initVelocityTrackerIfNotExists();
					mVelocityTracker.addMovement( ev );
					final ViewParent parent = getParent();
					if ( parent != null ) {
						parent.requestDisallowInterceptTouchEvent( true );
					}
					postScrollNotifier();
				}
				break;
			}

			case MotionEvent.ACTION_DOWN: {

				final int x = (int) ev.getX();
				final int y = (int) ev.getY();

				mTestDragX = x;
				mTestDragY = y;

				/*
				 * Remember location of down touch. ACTION_DOWN always refers to pointer index 0.
				 */
				mLastMotionX = x;
				mLastMotionX2 = x;
				mActivePointerId = ev.getPointerId( 0 );

				initOrResetVelocityTracker();
				mVelocityTracker.addMovement( ev );

				/*
				 * If being flinged and user touches the screen, initiate drag; otherwise don't. mScroller.isFinished should be false
				 * when being flinged.
				 */
				mIsBeingDragged = !mFlingRunnable.isFinished();

				mWasFlinging = !mFlingRunnable.isFinished();
				mFlingRunnable.stop( false );
				mCanCheckDrag = getDragScrollEnabled() && ( mItemDragListener != null );

				if ( mCanCheckDrag ) {
					int i = getChildAtPosition( x, y );
					if ( i > -1 ) {
						mOriginalDragItem = new WeakReference<View>( getChildAt( i ) );
					}
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				/* Release the drag */
				mIsBeingDragged = false;
				mActivePointerId = INVALID_POINTER;
				recycleVelocityTracker();

				if ( mFlingRunnable.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
					postInvalidate();
				}

				mCanCheckDrag = false;
				break;

			case MotionEvent.ACTION_POINTER_UP:
				onSecondaryPointerUp( ev );
				break;
		}

		return mIsBeingDragged;
	}

	@Override
	public boolean onTouchEvent( MotionEvent ev ) {

		initVelocityTrackerIfNotExists();
		mVelocityTracker.addMovement( ev );

		final int action = ev.getAction();

		switch ( action & MotionEvent.ACTION_MASK ) {

			case MotionEvent.ACTION_DOWN: { // DOWN

				if ( getChildCount() == 0 ) {
					return false;
				}

				if ( ( mIsBeingDragged = !mFlingRunnable.isFinished() ) ) {
					final ViewParent parent = getParent();
					if ( parent != null ) {
						parent.requestDisallowInterceptTouchEvent( true );
					}
				}

				/*
				 * If being flinged and user touches, stop the fling. isFinished will be false if being flinged.
				 */
				if ( !mFlingRunnable.isFinished() ) {
					mFlingRunnable.stop( false );
				}

				// Remember where the motion event started
				mTestDragX = ev.getX();
				mTestDragY = ev.getY();
				mLastMotionX2 = mLastMotionX = (int) ev.getX();
				mActivePointerId = ev.getPointerId( 0 );
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				// MOVE
				final int activePointerIndex = ev.findPointerIndex( mActivePointerId );
				final int x = (int) ev.getX( activePointerIndex );
				final int y = (int) ev.getY( activePointerIndex );
				int deltaX = mLastMotionX - x;
				if ( !mIsBeingDragged && Math.abs( deltaX ) > mTouchSlop ) {
					final ViewParent parent = getParent();
					if ( parent != null ) {
						parent.requestDisallowInterceptTouchEvent( true );
					}
					mIsBeingDragged = true;
					if ( deltaX > 0 ) {
						deltaX -= mTouchSlop;
					} else {
						deltaX += mTouchSlop;
					}

					postScrollNotifier();

				}

				// first check if we can drag the item
				if ( checkDrag( x, y ) ) {
					return false;
				}

				if ( mIsBeingDragged ) {
					// Scroll to follow the motion event
					mLastMotionX = x;

					final float deltaX2 = mLastMotionX2 - x;
					final int oldX = getScrollX();
					final int range = getScrollRange();
					final int overscrollMode = mOverScrollMode;
					final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
							|| ( overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0 );

					if ( overScrollingBy( deltaX, 0, mCurrentX, 0, range, 0, 0, mOverscrollDistance, true ) ) {
						mVelocityTracker.clear();
					}

					if ( canOverscroll && mEdgeGlowLeft != null ) {
						final int pulledToX = oldX + deltaX;

						if ( pulledToX < mMinX ) {
							float overscroll = ( (float) -deltaX2 * 1.5f ) / getWidth();
							mEdgeGlowLeft.onPull( overscroll );
							if ( !mEdgeGlowRight.isFinished() ) {
								mEdgeGlowRight.onRelease();
							}
						} else if ( pulledToX > mMaxX ) {
							float overscroll = ( (float) deltaX2 * 1.5f ) / getWidth();

							mEdgeGlowRight.onPull( overscroll );
							if ( !mEdgeGlowLeft.isFinished() ) {
								mEdgeGlowLeft.onRelease();
							}
						}
						if ( mEdgeGlowLeft != null && ( !mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished() ) ) {
							postInvalidate();
						}
					}

				}
				break;
			}

			case MotionEvent.ACTION_UP: {

				if ( mIsBeingDragged ) {
					final VelocityTracker velocityTracker = mVelocityTracker;
					velocityTracker.computeCurrentVelocity( 1000, mMaximumVelocity );

					final float velocityY = velocityTracker.getYVelocity();
					final float velocityX = velocityTracker.getXVelocity();

					if ( getChildCount() > 0 ) {
						if ( ( Math.abs( velocityX ) > mMinimumVelocity ) ) {
							onFling( ev, null, velocityX, velocityY );
						} else {
							if ( mFlingRunnable.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
								postInvalidate();
							}
						}
					}

					mActivePointerId = INVALID_POINTER;
					endDrag();

					mCanCheckDrag = false;
					if ( mFlingRunnable.isFinished() ) {
						scrollIntoSlots();
					}
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL: {
				if ( mIsBeingDragged && getChildCount() > 0 ) {
					if ( mFlingRunnable.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
						postInvalidate();
					}
					mActivePointerId = INVALID_POINTER;
					endDrag();
				}
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				onSecondaryPointerUp( ev );
				mLastMotionX2 = mLastMotionX = (int) ev.getX( ev.findPointerIndex( mActivePointerId ) );
				mTestDragY = -1;
				mTestDragX = -1;
				break;
			}
		}
		return true;
	}

	private void onSecondaryPointerUp( MotionEvent ev ) {
		final int pointerIndex = ( ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK ) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		final int pointerId = ev.getPointerId( pointerIndex );
		if ( pointerId == mActivePointerId ) {
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastMotionX2 = mLastMotionX = (int) ev.getX( newPointerIndex );
			mTestDragY = -1;
			mTestDragX = -1;
			mActivePointerId = ev.getPointerId( newPointerIndex );
			if ( mVelocityTracker != null ) {
				mVelocityTracker.clear();
			}
		}
	}

	/**
	 * Check if the movement will fire a drag start event
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	private boolean checkDrag( int x, int y ) {

		if ( mCanCheckDrag && !mIsDragging ) {

			if ( mTestDragX < 0 || mTestDragY < 0 ) return false;

			float dx = Math.abs( x - mTestDragX );

			if ( dx > mDragTolerance ) {
				mTestDragY = -1;
				mCanCheckDrag = false;
			} else {
				float dy = Math.abs( y - mTestDragY );
				if ( dy > ( (double) mDragTolerance * 1.5 ) ) {

					if ( mOriginalDragItem != null && mAdapter != null ) {

						View view = mOriginalDragItem.get();
						int position = getItemIndex( view );
						if ( null != view && position > -1 ) {
							getParent().requestDisallowInterceptTouchEvent( false );
							if ( mItemDragListener != null ) {
								fireItemDragStart( view, mLeftViewIndex + 1 + position, mAdapter.getItemId( mLeftViewIndex + 1 + position ) );
								return true;
							}
						}
					}
					mCanCheckDrag = false;
				}
			}
		}
		return false;
	}

	private void endDrag() {
		mIsBeingDragged = false;
		recycleVelocityTracker();

		if ( mEdgeGlowLeft != null ) {
			mEdgeGlowLeft.onRelease();
			mEdgeGlowRight.onRelease();
		}
	}

	/**
	 * Scroll the view with standard behavior for scrolling beyond the normal content boundaries. Views that call this method should
	 * override {@link #onOverScrolled(int, int, boolean, boolean)} to respond to the results of an over-scroll operation.
	 * 
	 * Views can use this method to handle any touch or fling-based scrolling.
	 * 
	 * @param deltaX
	 *           Change in X in pixels
	 * @param deltaY
	 *           Change in Y in pixels
	 * @param scrollX
	 *           Current X scroll value in pixels before applying deltaX
	 * @param scrollY
	 *           Current Y scroll value in pixels before applying deltaY
	 * @param scrollRangeX
	 *           Maximum content scroll range along the X axis
	 * @param scrollRangeY
	 *           Maximum content scroll range along the Y axis
	 * @param maxOverScrollX
	 *           Number of pixels to overscroll by in either direction along the X axis.
	 * @param maxOverScrollY
	 *           Number of pixels to overscroll by in either direction along the Y axis.
	 * @param isTouchEvent
	 *           true if this scroll operation is the result of a touch event.
	 * @return true if scrolling was clamped to an over-scroll boundary along either axis, false otherwise.
	 */
	protected boolean overScrollingBy( int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY,
			int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent ) {

		final int overScrollMode = mOverScrollMode;
		final boolean toLeft = deltaX > 0;
		final boolean overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS;

		int newScrollX = scrollX + deltaX;
		if ( !overScrollHorizontal ) {
			maxOverScrollX = 0;
		}

		// Clamp values if at the limits and record
		final int left = mMinX - maxOverScrollX;
		final int right = mMaxX == MAX_SCROLL ? mMaxX : ( mMaxX + maxOverScrollX );

		boolean clampedX = false;
		if ( newScrollX > right && toLeft ) {
			newScrollX = right;
			deltaX = mMaxX - scrollX;
			clampedX = true;
		} else if ( newScrollX < left && !toLeft ) {
			newScrollX = left;
			deltaX = mMinX - scrollX;
			clampedX = true;
		}

		onScrolling( newScrollX, deltaX, clampedX );
		return clampedX;
	}

	public boolean onScrolling( int scrollX, int deltaX, boolean clampedX ) {
		if ( mAdapter == null ) return true;

		if ( !mFlingRunnable.isFinished() ) {
			mCurrentX = getScrollX();
			if ( clampedX ) {
				mFlingRunnable.springBack( scrollX, 0, mMinX, mMaxX, 0, 0 );
			}
		} else {
			trackMotionScroll( scrollX );
		}

		return true;
	}

	@Override
	public void computeScroll() {

		if ( mFlingRunnable.computeScrollOffset() ) {
			int oldX = mCurrentX;
			int x = mFlingRunnable.getCurrX();

			if ( oldX != x ) {
				final int range = getScrollRange();
				final int overscrollMode = mOverScrollMode;
				final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
						|| ( overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0 );

				overScrollingBy( x - oldX, 0, oldX, 0, range, 0, mOverscrollDistance, 0, false );

				if ( canOverscroll && mEdgeGlowLeft != null ) {
					if ( x < 0 && oldX >= 0 ) {
						mEdgeGlowLeft.onAbsorb( (int) mFlingRunnable.getCurrVelocity() );
					} else if ( x > range && oldX <= range ) {
						mEdgeGlowRight.onAbsorb( (int) mFlingRunnable.getCurrVelocity() );
					}
				}
			}
			postInvalidate();
		}
	}

	int getScrollRange() {
		if ( getChildCount() > 0 ) {
			return mMaxX - mMinX;
		}
		return 0;
	}

	/** The m animation duration. */
	int mAnimationDuration = 400;

	/** The m child height. */
	int mMaxX, mMinX;

	/** The m should stop fling. */
	boolean mShouldStopFling;

	/** The m to left. */
	boolean mToLeft;

	/** The m current x. */
	int mCurrentX = 0;

	/** The m old x. */
	int mOldX = 0;

	/** The m touch slop. */
	int mTouchSlop;

	int mEdgesHeight = -1;

	int mEdgesGravityY = Gravity.CENTER;

	@Override
	public void scrollIntoSlots() {
		if ( !mFlingRunnable.isFinished() ) {
			return;
		}

		if ( mCurrentX > mMaxX || mCurrentX < mMinX ) {
			if ( mCurrentX > mMaxX ) {
				if ( mMaxX < 0 ) {
					mFlingRunnable.startUsingDistance( mCurrentX, mMinX - mCurrentX );
				} else {
					mFlingRunnable.startUsingDistance( mCurrentX, mMaxX - mCurrentX );
				}
				return;
			} else {
				mFlingRunnable.startUsingDistance( mCurrentX, mMinX - mCurrentX );
				return;
			}
		}
		onFinishedMovement();
	}

	public void smoothScrollTo( int targetX ) {
		Log.i( LOG_TAG, "smoothScroll: " + targetX );
		mFlingRunnable.startUsingDistance( mCurrentX, targetX - mCurrentX );
	}

	/**
	 * On finished movement.
	 */
	protected void onFinishedMovement() {
		fireOnScrollFininshed();
	}

	private void onItemClick( View child, int position ) {

		boolean clickValid = true;

		// always dispatch the item click listener
		if ( mOnItemClicked != null ) {
			playSoundEffect( SoundEffectConstants.CLICK );
			clickValid = mOnItemClicked.onItemClick( this, child, position, mAdapter.getItemId( position ) );
		}

		if ( clickValid ) {
			// now check the selected status
			if ( !getIsSelected( position ) ) {
				setSelectedItem( child, position, true, true );
			} else {
				setSelectedItem( child, position, false, true );
			}
		}
	}

	protected void setSelectedItem( final View newView, final int position, boolean selected, boolean fireEvent ) {

		if ( mChoiceMode == SelectionMode.Single ) {
			if ( mSelectedPositions.size() > 0 ) {
				int pos = mSelectedPositions.keyAt( 0 );
				View child = getItemAt( pos );
				if ( null != child ) {
					child.setSelected( false );
				}
			}

			mSelectedPositions.clear();
		}

		if ( selected ) {
			mSelectedPositions.put( position, true );
		} else {
			mSelectedPositions.delete( position );
		}

		if ( null != newView ) {
			newView.setSelected( selected );
		}

		if ( fireEvent && mOnItemSelected != null ) {
			if ( mSelectedPositions.size() > 0 ) {
				mOnItemSelected.onItemSelected( this, newView, position, mAdapter.getItemId( position ) );
			} else {
				mOnItemSelected.onNothingSelected( this );
			}
		}
	}

	public boolean getIsSelected( int position ) {
		return mSelectedPositions.get( position, false );
	}

	/** The m gesture listener. */
	private OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

		@Override
		public boolean onDoubleTap( MotionEvent e ) {
			return false;
		};

		@Override
		public boolean onSingleTapUp( MotionEvent e ) {
			return onItemClick( e );
		};

		@Override
		public boolean onDown( MotionEvent e ) {
			return false;
		};

		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
			return false;
		};

		@Override
		public void onLongPress( MotionEvent e ) {
			HorizontalVariableListView.this.onLongPress( e );
		};

		@Override
		public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {
			return false;
		};

		@Override
		public void onShowPress( MotionEvent e ) {};

		@Override
		public boolean onSingleTapConfirmed( MotionEvent e ) {
			return true;
		}

		private boolean onItemClick( MotionEvent ev ) {
			if ( !mFlingRunnable.isFinished() || mWasFlinging ) return false;

			Rect viewRect = new Rect();

			for ( int i = 0; i < getChildCount(); i++ ) {
				View child = getChildAt( i );
				int left = child.getLeft();
				int right = child.getWidth();
				int top = child.getTop();
				int bottom = child.getBottom();
				viewRect.set( left, top, left + right, bottom );
				viewRect.offset( -mCurrentX, 0 );

				if ( viewRect.contains( (int) ev.getX(), (int) ev.getY() ) ) {

					final int position = mLeftViewIndex + 1 + i;
					HorizontalVariableListView.this.onItemClick( child, position );
					break;
				}
			}
			return true;
		}
	};

	public View getChild( MotionEvent e ) {
		Rect viewRect = new Rect();
		for ( int i = 0; i < getChildCount(); i++ ) {
			View child = getChildAt( i );
			int left = child.getLeft();
			int right = child.getRight();
			int top = child.getTop();
			int bottom = child.getBottom();
			viewRect.set( left, top, right, bottom );
			viewRect.offset( -mCurrentX, 0 );

			if ( viewRect.contains( (int) e.getX(), (int) e.getY() ) ) {
				return child;
			}
		}
		return null;
	}

	@Override
	public int getMinX() {
		return mMinX;
	}

	@Override
	public int getMaxX() {
		return mMaxX;
	}

	public void setDragTolerance( int value ) {
		mDragTolerance = value;
	}

	public void setGravity( int mode ) {
		mAlignMode = mode;
	}

	public int getGravity() {
		return mAlignMode;
	}
}
