/*
 * 
 * HorizontalVariableListView.java
 * ListView with horizontal scrolling. It can also handle items with different widths
 * 
 * @author Alessandro Crugnola
 * @email alessandro.crugnola@gmail.com
 * @version 2.0
 * 
 * DISTRIBUTED UNDER THE MIT LICENSE
 * http://opensource.org/licenses/MIT
 * 
 */

package it.sephiroth.android.library.widget;

import it.sephiroth.android.library.utils.DataSetObserverExtended;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Parcelable;
import android.support.v4.widget.EdgeEffectCompat;
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
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.OverScroller;

public class HorizontalVariableListView extends HorizontalListView {

	public enum SelectionMode {
		Single, Multiple
	};

	public static final class DataSetChange {

		static final int INVALID_POSITION = -1;

		static final int MASK_NONE = 1 << 0;
		static final int MASK_ADDED = 1 << 1;
		static final int MASK_REMOVED = 1 << 2;
		static final int MASK_REPLACED = 1 << 3;
		static final int MASK_INVALIDATED = 1 << 4;

		int viewType;
		int position;
		int status = MASK_NONE;

		public void add( int position ) {
			this.position = position;
			status = MASK_ADDED;
		}

		public void remove( int index, int type ) {
			this.position = index;
			this.viewType = type;
			status = MASK_REMOVED;
		}
		
		public void replace( int index, int type ) {
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "replace: " + index + ", viewType: " + type );
			}
			this.position = index;
			this.viewType = type;
			this.status = MASK_REPLACED;
		}

		public void invalidate() {
			this.position = INVALID_POSITION;
			this.status = MASK_INVALIDATED;
		}

		public void clear() {
			this.position = INVALID_POSITION;
			this.status = MASK_NONE;
			this.viewType = 0;
		}

		public boolean added() {
			return check( MASK_ADDED );
		}

		public boolean removed() {
			return check( MASK_REMOVED );
		}
		
		public boolean replaced() {
			return check( MASK_REPLACED );
		}

		public boolean invalidated() {
			return check( MASK_INVALIDATED );
		}

		public boolean changed() {
			return !check( MASK_NONE );
		}

		private boolean check( int mask ) {
			return status == mask;
		}
	}

	public interface OnItemClickedListener {

		/**
		 * Callback method to be invoked when an item in this AdapterView has been clicked.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need to access the data associated with the selected item.
		 * 
		 * @param parent The AdapterView where the click happened.
		 * @param view The view within the AdapterView that was clicked (this will be a view provided by the adapter)
		 * @param position The position of the view in the adapter.
		 * @param id The row id of the item that was clicked.
		 * @return if the implementation return false, then the selection will not be updated
		 */
		boolean onItemClick( AdapterView<?> parent, View view, int position, long id );
	}

	protected static boolean LOG_ENABLED = true;
	
	protected static final String LOG_TAG = "horizontal-variable-list";

	public static final int OVER_SCROLL_ALWAYS = 0;
	public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;
	public static final int OVER_SCROLL_NEVER = 2;

	private static final float WIDTH_THRESHOLD = 1.1f;

	private static final int MAX_SCROLL = Integer.MAX_VALUE / 2;
	private static final int MIN_SCROLL = Integer.MIN_VALUE / 2;

	private static final int INVALID_POINTER = -1;
	private static final long ANIMATED_SCROLL_GAP = 250;
	
	private boolean mIsDragging = false;

	/**
	 * True if the user is currently dragging this ScrollView around. This is not the same as 'is being flinged', which can be
	 * checked by mScroller.isFinished() (flinging begins when the user lifts his finger).
	 */
	private boolean mIsBeingDragged = false;

	private int mActivePointerId = -1;
	private int mLastMotionX;
	private VelocityTracker mVelocityTracker;
	private int mOverscrollDistance;
	private int mOverflingDistance;

	private int mMinimumVelocity;
	private int mMaximumVelocity;

	private int mHeightMeasureSpec;
	private int mWidthMeasureSpec;

	/** animate list during dataset changes */
	private boolean mAnimateChanges = false;

	/** align mode for children */
	protected int mAlignMode = Gravity.CENTER;

	/** array for selected positions */
	protected SparseBooleanArray mSelectedPositions = new SparseBooleanArray();

	/** the adapter */
	protected ListAdapter mAdapter;

	/** this will contains the current number of items in the adapter */
	private int mAdapterItemCount;

	/** indices for the first and last view positions */
	private int mLeftViewIndex = -1;
	private int mRightViewIndex = 0;

	private GestureDetector mGesture;

	private List<Queue<View>> mRecycleBin;
	private Hashtable<Integer, Integer> mViewTypeTable;

	private List<Integer> mChildWidths = new ArrayList<Integer>();

	private DataSetChange mDataSetChange = new DataSetChange();

	private boolean mForceLayout;

	private int mDragTolerance = 0;

	private boolean mDragScrollEnabled;

	private long mLastScroll;
	private EdgeEffectCompat mEdgeGlowLeft;
	private EdgeEffectCompat mEdgeGlowRight;

	/** overscroll type */
	private int mOverScrollMode = OVER_SCROLL_NEVER;

	private OverScroller mScroller;

	/** scroll notifier */
	private ScrollNotifier mScrollNotifier;

	/** selection type (single/multiple) */
	private SelectionMode mChoiceMode = SelectionMode.Single;

	/** maximum scroll X position */
	private int mMaxX;
	/** minimum scroll X position */
	private int mMinX;

	/** the current scroll X position */
	private int mCurrentX = 0;

	/** the touch slop */
	private int mTouchSlop;

	/** Left and Right edge objects */
	private int mRightEdge, mLeftEdge;

	private int[] nullInt = new int[2];

	// listeners
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
	 * @param mode the selection mode
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

	public HorizontalVariableListView( Context context ) {
		super( context );
		initScrollView( context, null, 0 );
	}

	public HorizontalVariableListView( Context context, AttributeSet attrs ) {
		super( context, attrs );
		initScrollView( context, attrs, 0 );
	}

	public HorizontalVariableListView( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );
		initScrollView( context, attrs, defStyle );
	}

	private void initScrollView( Context context, AttributeSet attrs, int defStyle ) {
		mScroller = new OverScroller( context );
		mGesture = new GestureDetector( getContext(), mGestureListener );
		mGesture.setIsLongpressEnabled( false );

		setFocusable( true );
		setFocusableInTouchMode( true );
		setWillNotDraw( false );

		ViewConfiguration configuration = ViewConfiguration.get( getContext() );
		mTouchSlop = configuration.getScaledTouchSlop();

		mDragTolerance = mTouchSlop;

		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mOverscrollDistance = configuration.getScaledOverscrollDistance();
		mOverflingDistance = configuration.getScaledOverflingDistance();

		resetView();
	}

	private void resetView() {
		mLeftViewIndex = -1;
		mRightViewIndex = 0;
		mMaxX = MAX_SCROLL;
		mMinX = 0;
		mRightEdge = 0;
		mLeftEdge = 0;
	}

	@Override
	public void setOverScrollMode( int mode ) {
		mOverScrollMode = mode;

		if ( mode != OVER_SCROLL_NEVER ) {
			if ( mEdgeGlowLeft == null ) {
				mEdgeGlowLeft = new EdgeEffectCompat( getContext() );
				mEdgeGlowRight = new EdgeEffectCompat( getContext() );
			}
		} else {
			mEdgeGlowLeft = mEdgeGlowRight = null;
		}
	}

	@Override
	protected void dispatchDraw( Canvas canvas ) {
		super.dispatchDraw( canvas );

		if ( getChildCount() > 0 ) {
			drawEdges( canvas );
		}
	}

	/**
	 * Draw the edges
	 */
	private void drawEdges( Canvas canvas ) {

		if ( mEdgeGlowLeft != null ) {
			final int scrollX = mCurrentX;
			if ( !mEdgeGlowLeft.isFinished() ) {
				final int restoreCount = canvas.save();
				final int height = getHeight() - getPaddingTop() - getPaddingBottom();

				canvas.rotate( 270 );
				canvas.translate( -height + getPaddingTop(), mMinX );
				mEdgeGlowLeft.setSize( height, getWidth() );
				if ( mEdgeGlowLeft.draw( canvas ) ) {
					postInvalidate();
				}
				canvas.restoreToCount( restoreCount );
			}
			if ( !mEdgeGlowRight.isFinished() ) {
				final int restoreCount = canvas.save();
				final int width = getWidth();
				final int height = getHeight() - getPaddingTop() - getPaddingBottom();

				canvas.rotate( 90 );
				canvas.translate( -getPaddingTop(), -( Math.max( mMaxX, scrollX ) + width ) );
				mEdgeGlowRight.setSize( height, width );
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
			if( LOG_ENABLED ) {
				Log.i( LOG_TAG, "onAdded: " + position );
			}
			mDataSetChange.add( position );
			handleDataSetChanged( mDataSetChange );
		};

		@Override
		public void onRemoved( int position, int viewType ) {
			if( LOG_ENABLED ) {
				Log.i( LOG_TAG, "onRemoved: " + position );
			}
			mDataSetChange.remove( position, viewType );
			handleDataSetChanged( mDataSetChange );
		};
		
		@Override
		public void onReplaced(int position, int viewType) {
			if( LOG_ENABLED ) {
				Log.i( LOG_TAG, "onReplaced: " + position + ", viewType: " + viewType );
			}
			mDataSetChange.replace( position, viewType );
			handleDataSetChanged( mDataSetChange );
		};

		@Override
		public void onChanged() {
			if( LOG_ENABLED ) {
				Log.i( LOG_TAG, "onChanged" );
			}
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

	@Override
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void setAdapter( ListAdapter adapter ) {
		
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "setAdapter: " + adapter );
		}

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
		resetView();
		removeAllViewsInLayout();
		mForceLayout = true;
		requestLayout();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		
		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "onDetachedFromWindow" );
		}

		removeCallbacks( mScrollNotifier );
		emptyRecycler();
	}

	@Override
	public void setSelection( int position ) {}

	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
		if( LOG_ENABLED ) {
			Log.w( LOG_TAG, "onMeasure. children: " + getChildCount() );
		}
		
		super.onMeasure( widthMeasureSpec, heightMeasureSpec );

		mHeightMeasureSpec = heightMeasureSpec; 
		mWidthMeasureSpec = widthMeasureSpec;
		
		final int widthMode = MeasureSpec.getMode( mWidthMeasureSpec );
		final int heightMode = MeasureSpec.getMode( mHeightMeasureSpec );
		final int widthSize = MeasureSpec.getSize( mWidthMeasureSpec );
		final int heightSize = MeasureSpec.getSize( mHeightMeasureSpec );
		
		if ( widthMode == MeasureSpec.UNSPECIFIED ) {
			Log.e( LOG_TAG, "invalid widthMode!");
			return;
		}
		
		if( getChildCount() == 0 && null != mAdapter && mAdapterItemCount > 0 && ( heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST ) ) {
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "measure the first child" );
			}
			
			int viewType = mAdapter.getItemViewType( 0 );
			View child = mAdapter.getView( 0, mRecycleBin.get( viewType ).poll(), this );
			addAndMeasureChild( child, -1 );
			
			int childHeight = Math.min( heightSize, child.getMeasuredHeight() );
			mRecycleBin.get( viewType ).offer( child );
			removeViewInLayout( child );

			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "final dimension: " + widthSize + "x" + childHeight );
			}
			setMeasuredDimension( widthSize, childHeight );
			
			
			return;
		}
		
		if ( getChildCount() > 0 ) {
			final View child = getChildAt( 0 );
			int height = getHeight();
			if ( child.getMeasuredHeight() != height ) {
				if( LOG_ENABLED ) {
					Log.e( LOG_TAG, "child height != current height: " + child.getMeasuredHeight() + " != " + height );
				}
				
				if( heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST ) {
					if( LOG_ENABLED ) {
						Log.d( LOG_TAG, "final dimension: " + widthSize + "x" + child.getMeasuredHeight() );
					}
					setMeasuredDimension( widthSize, child.getMeasuredHeight() );
				} else {
					// TODO: need to verify this case
					// layoutChildren();
				}
			} else {
				setMeasuredDimension( widthSize, getHeight() );
			}
		}
	}

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
		// measureChild( child, mWidthMeasureSpec, mHeightMeasureSpec );
	}
	
	@Override
	protected void onSizeChanged( int w, int h, int oldw, int oldh ) {
		super.onSizeChanged( w, h, oldw, oldh );
		
		if( LOG_ENABLED ) {
			Log.w( LOG_TAG, "onSizeChanged: " + w + "x" + h );
		}
	}

	@Override
	protected void onLayout( boolean changed, int left, int top, int right, int bottom ) {
		super.onLayout( changed, left, top, right, bottom );
		
		if( LOG_ENABLED ) {
			Log.w( LOG_TAG, "onLayout: changed: " + changed + ", forceLayout: " + mForceLayout );
			Log.d( LOG_TAG, "size: " + (right-left) + "x" + (bottom-top) );
		}

		if ( mAdapter == null ) {
			return;
		}

		//if ( changed ) {
		layoutChildren();
		//}

		if ( changed || mForceLayout ) {

			int viewWidth = getViewWidth();
			mRightEdge = viewWidth;
			mLeftEdge = ( getWidth() - viewWidth );
			mMaxX = MAX_SCROLL;

			mForceLayout = true;
			removeNonVisibleItems( mCurrentX, nullInt );
			fillList( mCurrentX );
			scrollTo( Math.min( mMaxX, Math.max( mMinX, mCurrentX ) ), 0 );
			mForceLayout = false;
		}

		postNotifyLayoutChange( changed, left, top, right, bottom );
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		return super.onSaveInstanceState();
	}

	@Override
	protected void onRestoreInstanceState( Parcelable state ) {
		super.onRestoreInstanceState( state );
	}

	public void layoutChildren() {

		int left, right;
		int total = getChildCount();

		for ( int i = 0; i < total; i++ ) {
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
	 * @param positionX the position x
	 */
	private void fillList( final int positionX ) {
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "fillList: " + positionX + ", real: " + getScrollX() );
		}

		int edge = 0;

		View child = getChildAt( getChildCount() - 1 );
		if ( child != null ) {
			edge = child.getRight();
		}
		fillListRight( positionX, edge );

		edge = 0;
		child = getChildAt( 0 );
		if ( child != null ) {
			edge = child.getLeft();
		}
		fillListLeft( positionX, edge );

		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "	viewIndex: " + mLeftViewIndex + ":" + mRightViewIndex );
		}
	}

	public int getViewWidth() {
		return (int) ( (float) getWidth() * WIDTH_THRESHOLD );
	}

	/**
	 * Fill list left.
	 * 
	 * @param positionX the position x
	 * @param leftEdge the left edge
	 */
	private void fillListLeft( int positionX, int leftEdge ) {
		if ( mAdapter == null ) return;
		
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "fillListLeft: " + leftEdge );
		}

		while ( ( leftEdge - positionX ) > mLeftEdge && mLeftViewIndex >= 0 ) {
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "fillListLeft. leftIndex: " + mLeftViewIndex );
			}
			int viewType = mAdapter.getItemViewType( mLeftViewIndex );
			int childWidth = mChildWidths.get( viewType );
			View child = null;
			
			if ( childWidth <= 0 ) {
				child = createNew( mLeftViewIndex, viewType, 0 );
				childWidth = child.getMeasuredWidth();
				mChildWidths.set( viewType, childWidth );
			}
			
			fillItem( child, mLeftViewIndex, viewType, 0, leftEdge - childWidth );
			leftEdge -= childWidth;
			mLeftViewIndex--;
		}

		if ( mLeftViewIndex == -1 ) {
			mMinX = leftEdge;
		}
	}

	/**
	 * Fill list right.
	 * 
	 * @param positionX the position x
	 * @param rightEdge the right edge
	 */
	private void fillListRight( int positionX, int rightEdge ) {

		boolean firstChild = getChildCount() == 0 || mForceLayout;

		if ( mAdapter == null ) return;

		final int realWidth = getWidth();

		while ( ( rightEdge - positionX ) < mRightEdge || firstChild ) {

			if ( mRightViewIndex >= mAdapterItemCount ) {
				break;
			}

			int viewType = mAdapter.getItemViewType( mRightViewIndex );
			fillItem( null, mRightViewIndex, viewType, -1, rightEdge );
			int childWidth = mChildWidths.get( viewType );

			if ( firstChild ) {
				firstChild = false;
			}

			rightEdge += childWidth;
			mRightViewIndex++;
		}

		if ( mRightViewIndex >= mAdapterItemCount ) {

			int leftEdge = mMinX > MIN_SCROLL ? mMinX : 0;

			if ( ( rightEdge - leftEdge ) > realWidth ) {
				mMaxX = rightEdge - realWidth;
			} else {
				mMaxX = rightEdge - realWidth;
				if ( mMaxX - mMinX < realWidth ) {
					mMaxX = mMinX;
				}
			}
		}
	}

	private View fillItem( View child, int position, int viewType, int layoutIndex, int left ) {
		// final boolean selected = getIsSelected( position );
		if( null == child ) {
			child = createNew( position, viewType, layoutIndex );
		}
		// child.setSelected( selected );

		int childWidth = mChildWidths.get( viewType );
		int childHeight = child.getMeasuredHeight();

		if ( childWidth <= 0 ) {
			childWidth = child.getMeasuredWidth();
			mChildWidths.set( viewType, childWidth );
		}

		mViewTypeTable.put( child.hashCode(), viewType );
		layoutChild( child, left, left + childWidth, childHeight );
		return child;
	}
	
	private View createNew( int position, int viewType, int layoutIndex ) {
		final boolean selected = getIsSelected( position );
		View child = mAdapter.getView( position, mRecycleBin.get( viewType ).poll(), this );
		child.setSelected( selected );
		addAndMeasureChild( child, layoutIndex );
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
		int childHeight = 0;

		if ( childWidth == -1 ) {
			// otherwise create the child and measure it
			View child = mAdapter.getView( position, mRecycleBin.get( viewType ).poll(), this );
			addAndMeasureChild( child, -1 );
			childWidth = child.getMeasuredWidth();
			mChildWidths.set( viewType, childWidth );
			mRecycleBin.get( viewType ).offer( child );
			removeViewInLayout( child );
		}

		return new Rect( 0, 0, childWidth, childHeight );
	}

	private void handleDataSetChanged( DataSetChange data ) {

		if ( null == mAdapter ) return;

		// update the adapter items count
		int oldCount;
		synchronized ( HorizontalVariableListView.this ) {
			oldCount = mAdapterItemCount;
			mAdapterItemCount = mAdapter.getCount();
		}

		int delta = mAdapterItemCount - oldCount;

		boolean is_last = data.position == mAdapterItemCount - delta;

		if( LOG_ENABLED ) {
			Log.w( LOG_TAG, "** delta changes: " + delta );
			Log.w( LOG_TAG, "position: " + data.position + ", is_last: " + is_last + ", old count: " + oldCount + ", new count: " + mAdapterItemCount );
		}

		if ( data.invalidated() ) {
			// whole dataset has been invalidated
			data.clear();
			reset();

		} else if ( data.added() ) {
			// data has been added
			if ( !is_last ) {
				moveAllSelections( data.position, delta );
			}
			handleItemAdded( data.position, delta );
		} else if ( data.removed() ) {
			// data has been removed
			moveAllSelections( data.position, -1 );
			handleItemRemoved( data.position, data.viewType );
		} else if( data.replaced() ) {
			handleItemReplaced( data.position, data.viewType );
		}

		data.clear();
	}
	
	/**
	 * move all the selections by an arbitrary value
	 * 
	 * @param fromPosition the first position to move from 
	 * @param amount the delta change 
	 */
	private void moveAllSelections( int fromPosition, int amount ) {
		synchronized ( mSelectedPositions ) {

			int size = mSelectedPositions.size();
			int i, index;

			if ( amount > 0 ) {
				// add "amount" from any stored position
				for ( i = size - 1; i >= 0; i-- ) {
					index = mSelectedPositions.keyAt( i );
					if( LOG_ENABLED ) {
						Log.d( LOG_TAG, "i: " + i + ", position: " + index );
					}
					if ( index >= fromPosition ) {
						if( LOG_ENABLED ) {
							Log.d( LOG_TAG, "replacing: " + index + " with: " + ( index + 1 ) );
						}
						mSelectedPositions.delete( index );
						mSelectedPositions.put( index + amount, true );
					}
				}
			} else if ( amount < 0 ) {
				// remove "amount" value from any stored position
				synchronized ( mSelectedPositions ) {

					mSelectedPositions.delete( fromPosition );

					for ( i = 0; i < size; i++ ) {
						index = mSelectedPositions.keyAt( i );
						if ( index > fromPosition ) {
							mSelectedPositions.delete( index );
							mSelectedPositions.put( index + amount, true );
						}
					}
				}
			}
		}
	}
	
	private void forceUpdateScroll() {
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "forceUpdateScroll" );
		}
		
		int newScrollX = Math.min( mMaxX, Math.max( mMinX, mCurrentX ) );
		if( newScrollX != mCurrentX ) {
			scrollTo( newScrollX, 0 );
		} else {
			onScrollChanged( newScrollX, 0, mCurrentX, 0 );
		}
		
		if( mMaxX < mCurrentX || mMinX > mCurrentX ) {
			scrollTo( Math.min( mMaxX, Math.max( mMinX, mCurrentX ) ), 0 );
		}
	}
	
	private void handleItemReplaced( int position, int viewType ) {
		if( null == mAdapter ) return;
		int newViewType = mAdapter.getItemViewType( position );
		
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "handleItemReplaced: " + position + ", viewTypes: " + viewType + " - " + newViewType );
		}
		
		final int first = getFirstVisiblePosition();
		final int last = getLastVisiblePosition();
		final boolean is_last = position == mAdapterItemCount - 1;
		final boolean is_first = position == 0;
		final boolean is_animating = isScrolling();
		View child;
		
		if( viewType == newViewType ) {
			// replaced item has the same viewType
			// offer the removed item to the recycler
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "same viewType" );
			}
			
			if( position < first || position > last ) {
				// Do nothing here
				mMinX = MIN_SCROLL;
				mMaxX = MAX_SCROLL;
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "position is before or after" );
				}
			} else {
				child = getItemAt( position );
				if( null != child ) {
					
					// offer the removed item to the recycler
					mRecycleBin.get( viewType ).offer( child );
					
					// remove the item from the layout
					removeViewInLayout( child );
					
					// calculate the layout index of the new item
					final int layoutIndex = is_last ? -1 : ( is_first ? 0 : position - first );
					
					// re-create the item
					child = fillItem( null, position, newViewType, layoutIndex, child.getLeft() );
					
					postInvalidate();
					return;
				}
			}
		} else {
			// new item is different
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "viewTypes are different" );
			}
			
			if( position > last ) {
				// position is after last visible item
				mMaxX = MAX_SCROLL;
				return;
				
			} else if( position < first ) {
				// position if before the first visible items
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "position is before" );
				}
				
				mMinX = MIN_SCROLL;
				mLeftViewIndex -= 1;
				mRightViewIndex -= 1;
				
				if( !is_animating && mAnimateChanges ) {
					// smoothScrollTo( Math.min( mMaxX, mCurrentX + childWidth ) );
				}
				postInvalidate();
				return;
				
			} else {
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "between visible items" );
				}
				int oldChildWidth = getChildBounds( position, viewType ).width();
				int newChildWidth = getChildBounds( position, newViewType ).width();
				int diffWidth = newChildWidth - oldChildWidth;
				
				child = getItemAt( position );
				
				// offer the removed item to the recycler
				mRecycleBin.get( viewType ).offer( child );
				
				// remove the item from the layout
				removeViewInLayout( child );
				
				// calculate the layout index of the new item
				final int layoutIndex = is_last ? -1 : ( is_first ? 0 : position - first );
				
				// re-create the item
				child = fillItem( null, position, newViewType, layoutIndex, child.getLeft() );
				
				for( int i = last; i > position; i-- ) {
					child = getItemAt( i );
					if( null != child ) {
						layoutChild( child, child.getLeft() + diffWidth, child.getRight() + diffWidth, child.getHeight() );
						if( LOG_ENABLED ) {
							Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) );
						}
					}
				}
				
				mMaxX = MAX_SCROLL;
				
				forceUpdateScroll();
				postInvalidate();				
			}
		}
	}

	/**
	 * Handle the item removal from the current adapter 
	 * @param position the position of the item removed
	 * @param viewType the removed item type
	 */
	private void handleItemRemoved( int position, int viewType ) {
		if ( null == mAdapter ) return;

		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "handleItemRemoved: " + position );
		}

		if ( mAdapterItemCount < 1 ) {
			reset();
			return;
		}

		final boolean is_first = position == 0;
		final boolean is_last = position == mAdapterItemCount;
		final boolean animating = isScrolling();
		final int first = getFirstVisiblePosition();
		final int last = getLastVisiblePosition();
		
		int childWidth;
		View child;

		if ( position > last ) {
			// removed item after last visible item
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "position is after" );
			}
			mMaxX = MAX_SCROLL;
			postInvalidate();
			return;
			
		} else if ( position < first ) {
			// removed item before first visible item
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "position is before" );
			}
			
			childWidth = getChildBounds( position, viewType ).width();
			
			mMinX = MIN_SCROLL;
			mLeftViewIndex -= 1;
			mRightViewIndex -= 1;
			
			if( !animating && mAnimateChanges ) {
				smoothScrollTo( Math.min( mMaxX, mCurrentX + childWidth ) );
			}
			postInvalidate();
			return;
			
		} else {
			// removed item is visible
			if( LOG_ENABLED ) {
				Log.w( LOG_TAG, "position in range" );
			}

			// get the item to be removed and remove it
			child = getItemAt( position );
			final int right = child.getRight();
			final int left = child.getLeft();
			childWidth = right - left;

			// offer the removed item to the recycler
			mRecycleBin.get( viewType ).offer( child );
			removeViewInLayout( child );

			if ( is_last ) {
				// removed item is the last
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "position is the last" );
				}
				
				mRightViewIndex -= 1;
				mMaxX = left - getWidth();
				if( mMaxX < mMinX ) {
					mMaxX = mMinX;
				}
				
				if( animating || !mAnimateChanges ) {
					forceUpdateScroll();
				} else {
					smoothScrollTo( Math.min( mMaxX, Math.max( mMinX, mCurrentX - childWidth ) ) );
				}
				postInvalidate();
				return;

			} else if ( is_first ) {
				// removed the first item of the adapter
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "position is the first" );
					Log.d( LOG_TAG, "minx: " + mMinX + ", maxx: " + mMaxX + ", current: " + mCurrentX );
				}
				
				mRightViewIndex -= 1;
				mMinX = right;
				
				if( mMinX > mMaxX ) {
					mMaxX = mMinX;
				}
				
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "isAnimating: " + animating );
					Log.d( LOG_TAG, "minx: " + mMinX + ", maxx: " + mMaxX + ", current: " + mCurrentX );
				}
				
				if( animating || !mAnimateChanges ) {
					// scrollTo( Math.max( mMinX, mCurrentX + childWidth ), 0 );
					forceUpdateScroll();
				} else {
					smoothScrollTo( Math.max( mMinX, mCurrentX + childWidth ) );
				}
				postInvalidate();
				return;

			} else {
				
				if( LOG_ENABLED ) {
					Log.w( LOG_TAG, "else" );
				}
				
				for( int i = last; i >= position; i-- ) {
					child = getItemAt( i );
					if( null != child ) {
						layoutChild( child, child.getLeft() - childWidth, child.getRight() - childWidth, child.getHeight() );
						if( LOG_ENABLED ) {
							Log.d( LOG_TAG, i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) );
						}
					}
				}
				mRightViewIndex -= 1;
				forceUpdateScroll();
				postInvalidate();
			}
		}

		postInvalidate();
	}
	
	/**
	 * Handles the items added to the dataset
	 * 
	 * @param fromPosition of the new item
	 * @param count items added
	 */
	private void handleItemAdded( int fromPosition, int count ) {

		if ( mAdapterItemCount == 1 ) {
			// first item, need a fresh setup
			reset();
			return;
		}

		// check if the added item is at the end or at the beginning of the adapter
		final boolean animating = isScrolling();
		final int first = getFirstVisiblePosition();
		final int last = getLastVisiblePosition();

		// reset the max scroll
		mMaxX = MAX_SCROLL;

		int viewType, childWidth, i;
		View child;

		if ( fromPosition > last ) {
			// position is after the visible items
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "after current position" );
			}
			forceUpdateScroll();
			return;

		} else if ( fromPosition <= first ) {
			// position if before visible items
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "before current position" );
			}

			mMinX = MIN_SCROLL;
			mLeftViewIndex += count;
			mRightViewIndex += count;
			
			if( animating || !mAnimateChanges ) {
				// Do nothing here
				return;
			} else {
				// TODO: verify this
				int totalWidth = 0;
				for ( i = 0; i < count; i++ ) {
					// we need to get the child width, in order to shift all
					// the current children by its width
					viewType = mAdapter.getItemViewType( fromPosition + i );
					childWidth = getChildBounds( fromPosition + i, viewType ).width();
					totalWidth += childWidth;
				}
				smoothScrollTo( mCurrentX - totalWidth );
				return;
			}

		} else {
			// Added item is between one of the visible items
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "between visible items" );
			}

			// get the current child at that position
			child = getItemAt( fromPosition );
			// add the new child
			viewType = mAdapter.getItemViewType( fromPosition );
			child = fillItem( null, fromPosition, viewType, fromPosition - first, child.getLeft() );
			childWidth = child.getWidth();

			// now shift the remaining items to right
			i = fromPosition + 1;
			while ( i <= last + 1 ) {
				child = getItemAt( i );
				if ( null != child ) {
					layoutChild( child, child.getLeft() + childWidth, child.getRight() + childWidth, child.getHeight() );
					if( LOG_ENABLED ) {
						Log.d( LOG_TAG, "	" + i + ", move child: " + child + " at position " + i + ", now in: " + ( child.getLeft() - mCurrentX ) );
					}
				}
				i++;
			}

			removeNonVisibleItems( mCurrentX, nullInt );
			mRightViewIndex += 1;
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "	removed children: " + nullInt[0] + ", " + nullInt[1] );
			}

			if ( count > 1 ) {
				handleItemAdded( ++fromPosition, --count );
			} else {
				fillList( mCurrentX );
			}
		}

		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "	minX: " + mMinX + ":" + mMaxX );
		}
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

	/**
	 * Returns the View current display position. From 0 to getChildCount()
	 * 
	 * @param view
	 * @return
	 */
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

	/**
	 * Return the position within the adapter of the given view
	 * 
	 * @param view
	 * @return
	 */
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
		
		int top = getPaddingTop();
		int height = getHeight();
		
		if ( mAlignMode == Gravity.BOTTOM ) {
			top = top + ( height - childHeight );
		} else if ( mAlignMode == Gravity.CENTER ) {
			top = top + ( height - childHeight ) / 2;
		}
		child.layout( left, top, right, top + childHeight );
	}

	/**
	 * Removes the non visible items.
	 * 
	 * @param positionX the position x
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
					mRecycleBin.get( viewType ).offer( child );
				}
			}
			removedCount = true;
			removed[0] += 1;
			removeViewInLayout( child );
			mLeftViewIndex++;
			child = getChildAt( 0 );
		}

		// remove to right...
		child = getChildAt( getChildCount() - 1 );

		while ( child != null && child.getLeft() - positionX > mRightEdge ) {

			if ( null != mAdapter ) {
				// int position = getPositionForView( child );
				// int viewType = mAdapter.getItemViewType( position );

				int hashCode = child.hashCode();
				if ( mViewTypeTable.containsKey( hashCode ) ) {
					int viewType = mViewTypeTable.get( hashCode );
					mRecycleBin.get( viewType ).offer( child );
				}

			}

			removedCount = true;
			removed[1] += 1;
			removeViewInLayout( child );
			mRightViewIndex--;
			child = getChildAt( getChildCount() - 1 );
		}

		if ( removedCount ) {
			if( LOG_ENABLED ) {
				Log.d( LOG_TAG, "removeNonVisibleItems: leftIndex: " + mLeftViewIndex + ", rightIndex: " + mRightViewIndex );
			}
		}
	}

	private float mTestDragX, mTestDragY;
	private boolean mCanCheckDrag;
	private boolean mWasFlinging;
	private WeakReference<View> mOriginalDragItem;

	public void fling( int velocityX ) {
		if ( mMaxX == mMinX ) return;
		if ( getChildCount() > 0 ) {
			mCanCheckDrag = false;
			mWasFlinging = true;
			mScroller.fling( mCurrentX, 0, velocityX, 0, mMinX, mMaxX, 0, 0, Math.abs( getScrollRange() / 2 ), 0 );
			// mFlingRunnable.startUsingVelocity( mCurrentX, (int) -velocityX );
			postInvalidate();
		}
	}

	private void longPress( MotionEvent e ) {
		if ( mWasFlinging ) return;

		OnItemLongClickListener listener = getOnItemLongClickListener();
		if ( null != listener ) {

			if ( !mScroller.isFinished() ) return;

			int i = getChildAtPosition( e.getX(), e.getY() );
			if ( i > -1 ) {
				View child = getChildAt( i );
				fireLongPress( child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ) );
			}
		}
	}

	private int getChildAtPosition( float x, float y ) {
		Rect viewRect = new Rect();
		int total = getChildCount();

		for ( int i = 0; i < total; i++ ) {
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

		if ( mIsDragging ) return false;

		final int action = ev.getAction();
		mGesture.onTouchEvent( ev );

		/*
		 * Shortcut the most recurring case: the user is in the dragging state and he is moving his finger. We want to intercept this
		 * motion.
		 */
		if ( action == MotionEvent.ACTION_MOVE && mIsBeingDragged ) {
			return true;
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

				if ( pointerIndex == -1 ) {
					Log.e( LOG_TAG, "Invalid pointerId=" + activePointerId + " in onInterceptTouchEvent" );
					break;
				}

				final int x = (int) ev.getX( pointerIndex );
				final int y = (int) ev.getY( pointerIndex );
				final int xDiff = Math.abs( x - mLastMotionX );

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

				mLastMotionX = x;
				mActivePointerId = ev.getPointerId( 0 );

				initOrResetVelocityTracker();
				mVelocityTracker.addMovement( ev );

				mIsBeingDragged = !mScroller.isFinished();
				mWasFlinging = !mScroller.isFinished();
				mCanCheckDrag = getDragScrollEnabled() && ( mItemDragListener != null );

				// mFlingRunnable.stop( false );

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

				if ( mScroller.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
					postInvalidate();
				}

				mCanCheckDrag = false;
				break;

			case MotionEvent.ACTION_POINTER_UP:
				onSecondaryPointerUp( ev );
				mLastMotionX = (int) ev.getX( ev.findPointerIndex( mActivePointerId ) );
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

			case MotionEvent.ACTION_DOWN: {

				if ( getChildCount() == 0 ) {
					return false;
				}

				if ( ( mIsBeingDragged = !mScroller.isFinished() ) ) {
					final ViewParent parent = getParent();
					if ( parent != null ) {
						parent.requestDisallowInterceptTouchEvent( true );
					}
				}

				if ( !mScroller.isFinished() ) {
					mScroller.abortAnimation();
				}

				mLastMotionX = (int) ev.getX();
				mActivePointerId = ev.getPointerId( 0 );

				// Remember where the motion event started
				mTestDragX = ev.getX();
				mTestDragY = ev.getY();
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				final int activePointerIndex = ev.findPointerIndex( mActivePointerId );
				if ( activePointerIndex == -1 ) {
					Log.e( LOG_TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent" );
					break;
				}

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
					mLastMotionX = x;

					final int oldX = mCurrentX;
					final int range = getScrollRange();
					final int overscrollMode = mOverScrollMode;
					final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS;

					if ( overScrollBy( deltaX, 0, mCurrentX, 0, range, 0, mOverscrollDistance, 0, true ) ) {
						mVelocityTracker.clear();
					}
					onScrollChanged( mCurrentX, 0, oldX, 0 );

					if ( canOverscroll && mEdgeGlowLeft != null ) {
						final int pulledToX = oldX + deltaX;

						if ( pulledToX < mMinX ) {
							mEdgeGlowLeft.onPull( (float) deltaX / getWidth() );
							if ( !mEdgeGlowRight.isFinished() ) {
								mEdgeGlowRight.onRelease();
							}
						} else if ( pulledToX > mMaxX ) {
							mEdgeGlowRight.onPull( (float) deltaX / getWidth() );
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
					int initialVelocity = (int) velocityTracker.getXVelocity( mActivePointerId );

					if ( getChildCount() > 0 ) {
						if ( ( Math.abs( initialVelocity ) > mMinimumVelocity ) ) {
							fling( -initialVelocity );
						} else {
							if ( mScroller.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
								postInvalidate();
							}
						}
					}

					mActivePointerId = INVALID_POINTER;
					mIsBeingDragged = false;
					recycleVelocityTracker();

					if ( mEdgeGlowLeft != null ) {
						mEdgeGlowLeft.onRelease();
						mEdgeGlowRight.onRelease();
					}

					mCanCheckDrag = false;
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL: {
				if ( mIsBeingDragged && getChildCount() > 0 ) {
					if ( mScroller.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 ) ) {
						postInvalidate();
					}
					mActivePointerId = INVALID_POINTER;
					mIsBeingDragged = false;
					recycleVelocityTracker();

					if ( mEdgeGlowLeft != null ) {
						mEdgeGlowLeft.onRelease();
						mEdgeGlowRight.onRelease();
					}
				}
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				onSecondaryPointerUp( ev );
				mLastMotionX = (int) ev.getX( ev.findPointerIndex( mActivePointerId ) );
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
			mLastMotionX = (int) ev.getX( newPointerIndex );
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

	@Override
	public void scrollTo( int x, int y ) {
		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "scrollTo: " + x + ", current: " + getScrollX() );
		}
		mCurrentX = x;

		if ( getChildCount() > 0 ) {
			super.scrollTo( x, 0 );
		}
	}

	@Override
	protected void onScrollChanged( int left, int top, int old_left, int old_top ) {
		super.onScrollChanged( left, top, old_left, old_top );
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "onScrollChanged: " + left + ", old_left: " + old_left );
		}
		mCurrentX = left;
		removeNonVisibleItems( mCurrentX, nullInt );
		fillList( mCurrentX );
	}

	@Override
	protected void onOverScrolled( int scrollX, int scrollY, boolean clampedX, boolean clampedY ) {
		
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "onOverScrolled: " + scrollX + ", clamped: " + clampedX + ", current: " + mCurrentX + ", isFinished: " + mScroller.isFinished() );
		}

		if ( !mScroller.isFinished() ) {
			mCurrentX = scrollX;
			if ( clampedX ) {
				mScroller.springBack( mCurrentX, 0, mMinX, mMaxX, 0, 0 );
			}
		} else {
			scrollTo( scrollX, scrollY );
		}
	}
	
	public boolean isScrolling() {
		return mIsBeingDragged || !mScroller.isFinished();
	}

	@SuppressLint("NewApi")
	@Override
	public void computeScroll() {
		
		final boolean more = mScroller.computeScrollOffset();
		
		if ( more ) {
			int oldX = mCurrentX;
			int x = mScroller.getCurrX();

			if ( oldX != x ) {
				final int range = getScrollRange();
				final int overscrollMode = mOverScrollMode;
				final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS;

				overScrollBy( x - oldX, 0, oldX, 0, range, 0, mOverflingDistance, 0, false );
				scrollTo( mCurrentX, 0 );

				if ( android.os.Build.VERSION.SDK_INT >= 14 ) {
					if ( canOverscroll && mEdgeGlowLeft != null ) {

						if ( x < mMinX && oldX >= mMinX ) {
							mEdgeGlowLeft.onAbsorb( (int) mScroller.getCurrVelocity() );
						} else if ( x > mMaxX && oldX <= mMaxX ) {
							mEdgeGlowRight.onAbsorb( (int) mScroller.getCurrVelocity() );
						}
					}
				}
			} else {
				if( LOG_ENABLED ) {
					Log.w( LOG_TAG, "oldx == x" );
				}
			}
			postInvalidate();
		}
	}

	@Override
	protected boolean overScrollBy( int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY,
			int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent ) {
		
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "overscrollby: " + deltaX + ", current: " + scrollX );
		}

		final int overScrollMode = mOverScrollMode;
		final boolean toLeft = deltaX > 0;
		final boolean toRight = deltaX < 0;
		final boolean overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS;
		
		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "toLeft: " + toLeft + ", toRight: " + toRight );
		}

		int newScrollX = scrollX + deltaX;
		if ( !overScrollHorizontal ) {
			maxOverScrollX = 0;
		}

		// Clamp values if at the limits and record
		final int left = mMinX == MIN_SCROLL ? mMinX : mMinX - maxOverScrollX;
		final int right = mMaxX == MAX_SCROLL ? mMaxX : ( mMaxX + maxOverScrollX );
		
		if( LOG_ENABLED ) {
			Log.d( LOG_TAG, "left: " + left );
			Log.d( LOG_TAG, "right: " + right );
		}

		boolean clampedX = false;
		if ( newScrollX > right && !toRight ) {
			newScrollX = right;
			deltaX = mMaxX - scrollX;
			clampedX = true;
			if( LOG_ENABLED ) {
				Log.e( LOG_TAG, "clamped to: " + newScrollX );
			}
		} else if ( newScrollX < left && !toLeft ) {
			newScrollX = left;
			deltaX = mMinX - scrollX;
			clampedX = true;
			if( LOG_ENABLED ) {
				Log.e( LOG_TAG, "clamped to: " + newScrollX );
			}
		}

		onOverScrolled( newScrollX, 0, clampedX, false );
		return clampedX;
	}

	int getScrollRange() {
		if ( getChildCount() > 0 ) {
			return mMaxX - mMinX;
		}
		return 0;
	}


	public void smoothScrollTo( int x ) {
		smoothScrollBy( x - mCurrentX, 0 );
	}

	public final void smoothScrollBy( int dx, int dy ) {
		if ( getChildCount() == 0 ) {
			return;
		}

		long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
		if ( duration > ANIMATED_SCROLL_GAP ) {
			final int scrollX = mCurrentX;
			dx = Math.max( mMinX, Math.min( mCurrentX + dx, mMaxX ) ) - scrollX;

			mScroller.startScroll( scrollX, 0, dx, 0 );
			postInvalidateOnAnimation();
		} else {
			if ( !mScroller.isFinished() ) {
				mScroller.abortAnimation();
			}
			scrollBy( dx, dy );
		}
		mLastScroll = AnimationUtils.currentAnimationTimeMillis();
	}

	protected void onFinishedMovement() {
		fireOnScrollFininshed();
	}

	private void itemClick( View child, int position ) {
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

	/**
	 * Returns true if the child is selected at the passed position
	 * 
	 * @param position
	 * @return
	 */
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
			HorizontalVariableListView.this.longPress( e );
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
			if ( !mScroller.isFinished() || mWasFlinging ) return false;

			Rect viewRect = new Rect();
			int total = getChildCount();

			for ( int i = 0; i < total; i++ ) {
				View child = getChildAt( i );
				int left = child.getLeft();
				int right = child.getWidth();
				int top = child.getTop();
				int bottom = child.getBottom();
				viewRect.set( left, top, left + right, bottom );
				viewRect.offset( -mCurrentX, 0 );

				if ( viewRect.contains( (int) ev.getX(), (int) ev.getY() ) ) {

					final int position = mLeftViewIndex + 1 + i;
					HorizontalVariableListView.this.itemClick( child, position );
					break;
				}
			}
			return true;
		}
	};

	/**
	 * Given x,y coordinates, it returns the child at that position
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public View getChildAt( int x, int y ) {
		Rect viewRect = new Rect();
		int total = getChildCount();
		for ( int i = 0; i < total; i++ ) {
			View child = getChildAt( i );
			int left = child.getLeft();
			int right = child.getRight();
			int top = child.getTop();
			int bottom = child.getBottom();
			viewRect.set( left, top, right, bottom );
			viewRect.offset( -mCurrentX, 0 );

			if ( viewRect.contains( x, y ) ) {
				return child;
			}
		}
		return null;
	}

	public int getMinX() {
		return mMinX;
	}

	public int getMaxX() {
		return mMaxX;
	}

	public void setDragTolerance( int value ) {
		mDragTolerance = value;
	}

	/**
	 * Change the children display gravity
	 */
	public void setGravity( int mode ) {
		mAlignMode = mode;
	}

	/**
	 * Returns the layout gravity used to display children
	 * 
	 * @return
	 */
	public int getGravity() {
		return mAlignMode;
	}

	@SuppressWarnings("unused")
	private void printSelectedPositions() {
		if( LOG_ENABLED ) {
			Log.i( LOG_TAG, "selected positions" );
		}
		synchronized ( mSelectedPositions ) {
			for ( int i = 0; i < mSelectedPositions.size(); i++ ) {
				int key = mSelectedPositions.keyAt( i );
				boolean value = mSelectedPositions.get( key );
				if( LOG_ENABLED ) {
					Log.d( LOG_TAG, "	-- selection at " + key );
				}
			}
		}
	}
}
