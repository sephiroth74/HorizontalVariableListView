/*
 * HorizontalVariableListView.java v1.0
 * Handles horizontal variable widths item scrolling
 *
 */

package it.sephiroth.android.library.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.GestureDetector.OnGestureListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.AdapterView;
import android.widget.Checkable;
import android.widget.ListAdapter;
import it.sephiroth.android.library.utils.DataSetObserverExtended;
import it.sephiroth.android.library.utils.ReflectionUtils;
import it.sephiroth.android.library.utils.ReflectionUtils.ReflectionException;
import it.sephiroth.android.library.widget.IFlingRunnable.FlingRunnableView;

import java.lang.ref.WeakReference;
import java.util.*;

// TODO: Auto-generated Javadoc
/**
 * The Class HorizontialFixedListView.
 */
public class HorizontalVariableListView extends AdapterView<ListAdapter> implements OnGestureListener, FlingRunnableView {

	/** The Constant LOG_TAG. */
	protected static final String LOG_TAG = "horizontal-variable-list";

	private static final float WIDTH_THRESHOLD = 1.1f;

	/** The m adapter. */
	protected ListAdapter mAdapter;
	
	private int mAdapterItemCount;

	/** The m left view index. */
	private int mLeftViewIndex = -1;

	/** The m right view index. */
	private int mRightViewIndex = 0;

	/** The m gesture. */
	private GestureDetector mGesture;

	/** The m removed view queue. */
	private List<Queue<View>> mRecycleBin;

	private List<Integer> mChildWidths = new ArrayList<Integer>();

	/** The m on item selected. */
	private OnItemSelectedListener mOnItemSelected;

	/** The m on item clicked. */
	private OnItemClickListener mOnItemClicked;

	/** The m data changed. */
	private boolean mDataChanged = false;

	/** The m fling runnable. */
	private IFlingRunnable mFlingRunnable;

	/** The m force layout. */
	private boolean mForceLayout;

	private int mDragTolerance = 0;

	private boolean mDragScrollEnabled;

	protected EdgeGlow mEdgeGlowLeft, mEdgeGlowRight;

	private int mOverScrollMode = OVER_SCROLL_NEVER;

	private ScrollNotifier mScrollNotifier;

	/**
	 * Interface definition for a callback to be invoked when an item in this view has been clicked and held.
	 */
	public interface OnItemDragListener {

		/**
		 * Callback method to be invoked when an item in this view has been dragged outside the vertical tolerance area.
		 * 
		 * Implementers can call getItemAtPosition(position) if they need to access the data associated with the selected item.
		 * 
		 * @param parent
		 *           The AbsListView where the click happened
		 * @param view
		 *           The view within the AbsListView that was clicked
		 * @param position
		 *           The position of the view in the list
		 * @param id
		 *           The row id of the item that was clicked
		 * 
		 * @return true if the callback consumed the long click, false otherwise
		 */
		boolean onItemStartDrag( AdapterView<?> parent, View view, int position, long id );
	}
	
	public interface OnLayoutChangeListener {
		void onLayoutChange( boolean changed, int left, int top, int right, int bottom );
	}

	private OnItemDragListener mItemDragListener;
	private OnScrollChangedListener mScrollListener;
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

	public OnItemDragListener getOnItemDragListener() {
		return mItemDragListener;
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

	/**
	 * Inits the view.
	 */
	private synchronized void initView() {

		if ( Build.VERSION.SDK_INT > 8 ) {
			try {
				mFlingRunnable = (IFlingRunnable) ReflectionUtils.newInstance( "it.sephiroth.android.library.widget.Fling9Runnable", new Class<?>[] { FlingRunnableView.class, int.class }, this, mAnimationDuration );
			} catch ( ReflectionException e ) {
				mFlingRunnable = new Fling8Runnable( this, mAnimationDuration );
			}
		} else {
			mFlingRunnable = new Fling8Runnable( this, mAnimationDuration );
		}

		mLeftViewIndex = -1;
		mRightViewIndex = 0;
		mMaxX = Integer.MAX_VALUE;
		mMinX = 0;
		mChildHeight = 0;
		mRightEdge = 0;
		mLeftEdge = 0;
		mGesture = new GestureDetector( getContext(), mGestureListener );
		mGesture.setIsLongpressEnabled( true );

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

		scrollTo( newX, 0 );
		mCurrentX = getScrollX();
		removeNonVisibleItems( mCurrentX );
		fillList( mCurrentX );
		invalidate();
	}

	@Override
	protected void dispatchDraw( Canvas canvas ) {
		super.dispatchDraw( canvas );

		if ( getChildCount() > 0 ) {
			drawEdges( canvas );
		}
	}

	private Matrix mEdgeMatrix = new Matrix();

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
				mEdgeMatrix.postRotate( -90 );
				mEdgeMatrix.postTranslate( 0, height );

				if ( mEdgesGravityY == Gravity.BOTTOM ) {
					mEdgeMatrix.postTranslate( 0, getHeight() - height );
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView#setOnItemSelectedListener(android.widget.AdapterView.OnItemSelectedListener)
	 */
	@Override
	public void setOnItemSelectedListener( AdapterView.OnItemSelectedListener listener ) {
		mOnItemSelected = listener;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView#setOnItemClickListener(android.widget.AdapterView.OnItemClickListener)
	 */
	@Override
	public void setOnItemClickListener( AdapterView.OnItemClickListener listener ) {
		mOnItemClicked = listener;
	}

	private DataSetObserverExtended mDataObserverExtended = new DataSetObserverExtended() {

		@Override
		public void onAdded() {
			synchronized ( HorizontalVariableListView.this ) {
				mAdapterItemCount = mAdapter.getCount();
			}
			mDataChanged = true;
			mMaxX = Integer.MAX_VALUE;
			requestLayout();
		};

		@Override
		public void onRemoved() {
			this.onChanged();
		};

		@Override
		public void onChanged() {
			mAdapterItemCount = mAdapter.getCount();
			reset();
		};

		@Override
		public void onInvalidated() {
			this.onChanged();
		};
	};

	/** The m data observer. */
	private DataSetObserver mDataObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			synchronized ( HorizontalVariableListView.this ) {
				mAdapterItemCount = mAdapter.getCount();
			}
			invalidate();
			reset();
		}

		@Override
		public void onInvalidated() {
			mAdapterItemCount = mAdapter.getCount();
			invalidate();
			reset();
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

		if ( mAdapter != null ) {
			mAdapterItemCount = mAdapter.getCount();

			if ( mAdapter instanceof BaseAdapterExtended ) {
				( (BaseAdapterExtended) mAdapter ).registerDataSetObserverExtended( mDataObserverExtended );
			} else {
				mAdapter.registerDataSetObserver( mDataObserver );
			}
			int total = mAdapter.getViewTypeCount();

			mRecycleBin = Collections.synchronizedList( new ArrayList<Queue<View>>() );
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
		
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
		
		if( mChildHeight != 0 ) {
			setMeasuredDimension( parentWidth, mChildHeight );
		} else {
			setMeasuredDimension( parentWidth, 0 );
		}

		mHeightMeasureSpec = heightMeasureSpec;
		mWidthMeasureSpec = widthMeasureSpec;
	}

	/**
	 * Adds the and measure child.
	 *
     * @param child
     *           the child
     * @param viewPos
     * @param position
     */
	private void addAndMeasureChild(final View child, int viewPos, int position) {
		LayoutParams params = (LayoutParams) child.getLayoutParams();

		if ( params == null ) {
			params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
		}

        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(mCheckStates.get(position));
            } else if (Math.min(getContext().getApplicationInfo().targetSdkVersion, Build.VERSION.SDK_INT)
                    >= Build.VERSION_CODES.HONEYCOMB) {
                child.setActivated(mCheckStates.get(position));
            }
        }

		addViewInLayout( child, viewPos, params, false );
		forceChildLayout( child, params );
	}

	public void forceChildLayout( View child, LayoutParams params ) {
		int childHeightSpec = ViewGroup.getChildMeasureSpec( mHeightMeasureSpec, getPaddingTop() + getPaddingBottom(), params.height );
		int childWidthSpec = ViewGroup.getChildMeasureSpec( mWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), params.width );
		child.measure( childWidthSpec, childHeightSpec );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView#onLayout(boolean, int, int, int, int)
	 */
	@Override
	protected void onLayout( boolean changed, int left, int top, int right, int bottom ) {
		super.onLayout( changed, left, top, right, bottom );

		if ( mAdapter == null ) {
			return;
		}
		
		if ( !changed && !mDataChanged ) {
			layoutChildren();
		}

		if ( changed ) {
			mCurrentX = mOldX = 0;
			initView();
			removeAllViewsInLayout();
			trackMotionScroll( 0 );
		}

		if ( mDataChanged ) {
			trackMotionScroll( mCurrentX );
			mDataChanged = false;
		}

		if ( mForceLayout ) {
			mOldX = mCurrentX;
			initView();
			removeAllViewsInLayout();
			trackMotionScroll( mOldX );
			mForceLayout = false;
		}
		
		postNotifyLayoutChange( changed, left, top, right, bottom );

	}

	public void layoutChildren() {

		int paddingTop = getPaddingTop();
		int left, right;

		for ( int i = 0; i < getChildCount(); i++ ) {
			View child = getChildAt( i );

			forceChildLayout( child, child.getLayoutParams() );

			left = child.getLeft();
			right = child.getRight();
			child.layout( left, paddingTop, right, paddingTop + child.getMeasuredHeight() );
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
			int viewType = mAdapter.getItemViewType( mLeftViewIndex );
			View child = mAdapter.getView( mLeftViewIndex, mRecycleBin.get( viewType ).poll(), this );
			addAndMeasureChild( child, 0, mLeftViewIndex);

			int childWidth = mChildWidths.get( viewType );
			int childTop = getPaddingTop();
			child.layout( leftEdge - childWidth, childTop, leftEdge, childTop + mChildHeight );
			leftEdge -= childWidth;
			mLeftViewIndex--;
		}
	}

	public View getItemAt( int position ) {
		return getChildAt( position - ( mLeftViewIndex + 1 ) );
	}

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

	/**
	 * Fill list right.
	 * 
	 * @param positionX
	 *           the position x
	 * @param rightEdge
	 *           the right edge
	 */
	private void fillListRight( int positionX, int rightEdge ) {
		boolean firstChild = getChildCount() == 0 || mDataChanged || mForceLayout;

		if ( mAdapter == null ) return;

		final int realWidth = getWidth();
		int viewWidth = (int) ( (float) realWidth * WIDTH_THRESHOLD );

		while ( ( rightEdge - positionX ) < viewWidth || firstChild ) {

			if ( mRightViewIndex >= mAdapterItemCount ) {
				break;
			}

			int viewType = mAdapter.getItemViewType( mRightViewIndex );
			View child = mAdapter.getView( mRightViewIndex, mRecycleBin.get( viewType ).poll(), this );
			addAndMeasureChild( child, -1, mRightViewIndex);

			int childWidth = mChildWidths.get( viewType );
			if ( childWidth == -1 ) {
				childWidth = child.getMeasuredWidth();
				mChildWidths.set( viewType, childWidth );
			}

			if ( firstChild ) {
				mChildHeight = child.getMeasuredHeight();
				
				if ( mEdgesHeight == -1 ) {
					mEdgesHeight = mChildHeight;
				}
				mRightEdge = viewWidth;
				mLeftEdge = ( realWidth - viewWidth );
				mMinX = 0;
				firstChild = false;
			}

			int childTop = getPaddingTop();
			child.layout( rightEdge, childTop, rightEdge + childWidth, childTop + child.getMeasuredHeight() );
			rightEdge += childWidth;
			mRightViewIndex++;
		}
		
		if ( mRightViewIndex == mAdapterItemCount ) {
			if( rightEdge > realWidth ) {
				mMaxX = rightEdge - realWidth;
			} else {
				mMaxX = 0;
			}
		}		
		
	}

	/**
	 * Removes the non visible items.
	 * 
	 * @param positionX
	 *           the position x
	 */
	private void removeNonVisibleItems( final int positionX ) {
		View child = getChildAt( 0 );

		// remove to left...
		while ( child != null && child.getRight() - positionX <= mLeftEdge ) {


			if ( null != mAdapter ) {
				int position = getPositionForView( child );
				int viewType = mAdapter.getItemViewType( position );
				mRecycleBin.get( viewType ).offer( child );
			}
			removeViewInLayout( child );
			mLeftViewIndex++;
			child = getChildAt( 0 );
		}

		// remove to right...
		child = getChildAt( getChildCount() - 1 );
		while ( child != null && child.getLeft() - positionX >= mRightEdge ) {

			if ( null != mAdapter ) {
				int position = getPositionForView( child );
				int viewType = mAdapter.getItemViewType( position );
				mRecycleBin.get( viewType ).offer( child );
			}

			removeViewInLayout( child );
			mRightViewIndex--;
			child = getChildAt( getChildCount() - 1 );
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
		if ( mMaxX == 0 ) return false;
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
			performHapticFeedback( HapticFeedbackConstants.LONG_PRESS );
			mIsDragging = true;
			return true;
		}
		return false;
	}

	private void fireOnScrollChanged() {
		if ( mScrollListener != null ) {
			mScrollListener.onScrollChanged();
		}
	}
	
	private void fireOnLayoutChangeListener( boolean changed, int left, int top, int right, int bottom ) {
		if( mLayoutChangeListener != null ) {
			mLayoutChangeListener.onLayoutChange( changed, left, top, right, bottom );
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
		});
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
	 * @see android.view.GestureDetector.OnGestureListener#onShowPress(android.view.MotionEvent)
	 */
	@Override
	public void onShowPress( MotionEvent arg0 ) {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.GestureDetector.OnGestureListener#onSingleTapUp(android.view.MotionEvent)
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
					// If we don't have a valid id, the touch down wasn't on content.
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
				mCanCheckDrag = isLongClickable() && getDragScrollEnabled() && ( mItemDragListener != null );

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
					final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || ( overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0 );

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
				mTestDragX = mLastMotionX2 = mLastMotionX = (int) ev.getX( ev.findPointerIndex( mActivePointerId ) );
				mTestDragY = ev.getY( ev.findPointerIndex( mActivePointerId ) );
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
			mTestDragX = mLastMotionX2 = mLastMotionX = (int) ev.getX( newPointerIndex );
			mTestDragY = ev.getY( newPointerIndex );
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

			float dx = Math.abs( x - mTestDragX );

			if ( dx > mDragTolerance ) {
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
	protected boolean overScrollingBy( int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent ) {

		final int overScrollMode = mOverScrollMode;
		final boolean toLeft = deltaX > 0;
		final boolean overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS;

		int newScrollX = scrollX + deltaX;
		if ( !overScrollHorizontal ) {
			maxOverScrollX = 0;
		}

		// Clamp values if at the limits and record
		final int left = mMinX - maxOverScrollX;
		final int right = mMaxX == Integer.MAX_VALUE ? mMaxX : ( mMaxX + maxOverScrollX );
		

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
				final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || ( overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0 );
				
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
	int mMaxX, mMinX, mChildHeight;
	
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

		// boolean greater_enough = mItemCount * ( mChildWidth ) > getWidth();

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

	/**
	 * On finished movement.
	 */
	protected void onFinishedMovement() {
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
			// return HorizontalFixedListView.this.onDown( e );
		};

		@Override
		public boolean onFling( MotionEvent e1, MotionEvent e2, float velocityX, float velocityY ) {
			return false;
			// return HorizontalFixedListView.this.onFling( e1, e2, velocityX, velocityY );
		};

		@Override
		public void onLongPress( MotionEvent e ) {
			HorizontalVariableListView.this.onLongPress( e );
		};

		@Override
		public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {
			return false;
			// return HorizontalFixedListView.this.onScroll( e1, e2, distanceX, distanceY );
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
				int right = child.getRight();
				int top = child.getTop();
				int bottom = child.getBottom();
				viewRect.set( left, top, right, bottom );
				viewRect.offset( -mCurrentX, 0 );

				if ( viewRect.contains( (int) ev.getX(), (int) ev.getY() ) ) {
					if ( mOnItemClicked != null ) {
						playSoundEffect( SoundEffectConstants.CLICK );
						mOnItemClicked.onItemClick( HorizontalVariableListView.this, child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ) );
                        performItemClick(child, mLeftViewIndex + 1 + i, mAdapter.getItemId(mLeftViewIndex + 1 + i));
                    }
					if ( mOnItemSelected != null ) {
						mOnItemSelected.onItemSelected( HorizontalVariableListView.this, child, mLeftViewIndex + 1 + i, mAdapter.getItemId( mLeftViewIndex + 1 + i ) );
					}
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
		return Integer.MAX_VALUE;
	}

	public void setDragTolerance( int value ) {
		mDragTolerance = value;
	}

    /**
    * Normal list that does not indicate choices
    */
    public static final int CHOICE_MODE_NONE = 0;

    /**
     * The list allows up to one choice
     */
    public static final int CHOICE_MODE_SINGLE = 1;

    /**
     * The list allows multiple choices
     */
    public static final int CHOICE_MODE_MULTIPLE = 2;

    /**
     * The list allows multiple choices in a modal selection mode
     */
    // public static final int CHOICE_MODE_MULTIPLE_MODAL = 3;

    /**
     * Controls if/how the user may choose/check items in the list
     */
    int mChoiceMode = CHOICE_MODE_NONE;

    /**
     * Controls CHOICE_MODE_MULTIPLE_MODAL. null when inactive.
     */
    ActionMode mChoiceActionMode;

    /**
     * Running count of how many items are currently checked
     */
    int mCheckedItemCount;

    /**
     * Running state of which positions are currently checked
     */
    SparseBooleanArray mCheckStates;

    /**
     * Running state of which IDs are currently checked.
     * If there is a value for a given key, the checked state for that ID is true
     * and the value holds the last known position in the adapter for that id.
     */
    LongSparseArray<Integer> mCheckedIdStates;

    /**
     * Returns the number of items currently selected. This will only be valid
     * if the choice mode is not {@link #CHOICE_MODE_NONE} (default).
     *
     * <p>To determine the specific items that are currently selected, use one of
     * the <code>getChecked*</code> methods.
     *
     * @return The number of items currently selected
     *
     * @see #getCheckedItemPosition()
     * @see #getCheckedItemPositions()
     * @see #getCheckedItemIds()
     */
    public int getCheckedItemCount() {
        return mCheckedItemCount;
    }

    /**
     * Returns the checked state of the specified position. The result is only
     * valid if the choice mode has been set to {@link #CHOICE_MODE_SINGLE}
     * or {@link #CHOICE_MODE_MULTIPLE}.
     *
     * @param position The item whose checked state to return
     * @return The item's checked state or <code>false</code> if choice mode
     *         is invalid
     *
     * @see #setChoiceMode(int)
     */
    public boolean isItemChecked(int position) {
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            return mCheckStates.get(position);
        }

        return false;
    }

    /**
     * Returns the currently checked item. The result is only valid if the choice
     * mode has been set to {@link #CHOICE_MODE_SINGLE}.
     *
     * @return The position of the currently checked item or
     *         {@link #INVALID_POSITION} if nothing is selected
     *
     * @see #setChoiceMode(int)
     */
    public int getCheckedItemPosition() {
        if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
            return mCheckStates.keyAt(0);
        }

        return INVALID_POSITION;
    }

    /**
     * Returns the set of checked items in the list. The result is only valid if
     * the choice mode has not been set to {@link #CHOICE_MODE_NONE}.
     *
     * @return  A SparseBooleanArray which will return true for each call to
     *          get(int position) where position is a position in the list,
     *          or <code>null</code> if the choice mode is set to
     *          {@link #CHOICE_MODE_NONE}.
     */
    public SparseBooleanArray getCheckedItemPositions() {
        if (mChoiceMode != CHOICE_MODE_NONE) {
            return mCheckStates;
        }
        return null;
    }

    /**
     * Returns the set of checked items ids. The result is only valid if the
     * choice mode has not been set to {@link #CHOICE_MODE_NONE} and the adapter
     * has stable IDs. ({@link ListAdapter#hasStableIds()} == {@code true})
     *
     * @return A new array which contains the id of each checked item in the
     *         list.
     */
    public long[] getCheckedItemIds() {
        if (mChoiceMode == CHOICE_MODE_NONE || mCheckedIdStates == null || mAdapter == null) {
            return new long[0];
        }

        final LongSparseArray<Integer> idStates = mCheckedIdStates;
        final int count = idStates.size();
        final long[] ids = new long[count];

        for (int i = 0; i < count; i++) {
            ids[i] = idStates.keyAt(i);
        }

        return ids;
    }

    /**
     * Clear any choices previously set
     */
    public void clearChoices() {
        if (mCheckStates != null) {
            mCheckStates.clear();
        }
        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
        mCheckedItemCount = 0;
    }

    /**
     * Sets the checked state of the specified position. The is only valid if
     * the choice mode has been set to {@link #CHOICE_MODE_SINGLE} or
     * {@link #CHOICE_MODE_MULTIPLE}.
     *
     * @param position The item whose checked state is to be checked
     * @param value The new checked state for the item
     */
    public void setItemChecked(int position, boolean value) {
        if (mChoiceMode == CHOICE_MODE_NONE) {
            return;
        }

        // Start selection mode if needed. We don't need to if we're unchecking something.
        // if (value && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
        //     mChoiceActionMode = startActionMode(mMultiChoiceModeCallback);
        // }

        if (mChoiceMode == CHOICE_MODE_MULTIPLE /*|| mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL*/) {
            boolean oldValue = mCheckStates.get(position);
            mCheckStates.put(position, value);
            if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                if (value) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                } else {
                    mCheckedIdStates.delete(mAdapter.getItemId(position));
                }
            }
            if (oldValue != value) {
                if (value) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
            }
            // if (mChoiceActionMode != null) {
            //      final long id = mAdapter.getItemId(position);
            //      mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode, position, id, value);
            // }
        } else {
            boolean updateIds = mCheckedIdStates != null && mAdapter.hasStableIds();
            // Clear all values if we're checking something, or unchecking the currently
            // selected item
            if (value || isItemChecked(position)) {
                mCheckStates.clear();
                if (updateIds) {
                    mCheckedIdStates.clear();
                }
            }
            // this may end up selecting the value we just cleared but this way
            // we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
            if (value) {
                mCheckStates.put(position, true);
                if (updateIds) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                }
                mCheckedItemCount = 1;
            } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                mCheckedItemCount = 0;
            }
        }

        // Do not generate a data change while we are in the layout phase
        // if (!mInLayout && !mBlockLayoutRequests) {
        //      mDataChanged = true;
        //      rememberSyncState();
        // }
        requestLayout();
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        boolean handled = false;
        boolean dispatchItemClick = true;

        if (mChoiceMode != CHOICE_MODE_NONE) {
            handled = true;
            boolean checkedStateChanged = false;

            if (mChoiceMode == CHOICE_MODE_MULTIPLE /*||
                    (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode != null)*/) {
                boolean newValue = !mCheckStates.get(position, false);
                mCheckStates.put(position, newValue);
                if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                    if (newValue) {
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    } else {
                        mCheckedIdStates.delete(mAdapter.getItemId(position));
                    }
                }
                if (newValue) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
                // if (mChoiceActionMode != null) {
                //      mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode,
                //      position, id, newValue);
                //      dispatchItemClick = false;
                // }
                checkedStateChanged = true;
            } else if (mChoiceMode == CHOICE_MODE_SINGLE) {
                boolean newValue = !mCheckStates.get(position, false);
                if (newValue) {
                    mCheckStates.clear();
                    mCheckStates.put(position, true);
                    if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                        mCheckedIdStates.clear();
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    }
                    mCheckedItemCount = 1;
                } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                    mCheckedItemCount = 0;
                }
                checkedStateChanged = true;
            }

            if (checkedStateChanged) {
                updateOnScreenCheckedViews();
            }
        }

        if (dispatchItemClick) {
            handled |= super.performItemClick(view, position, id);
        }

        return handled;
    }

    /**
     * Perform a quick, in-place update of the checked or activated state
     * on all visible item views. This should only be called when a valid
     * choice mode is active.
     */
    private void updateOnScreenCheckedViews() {
        final int firstPos = mLeftViewIndex + 1;
        final int count = getChildCount();
        final boolean useActivated = Math.min(getContext().getApplicationInfo().targetSdkVersion,Build.VERSION.SDK_INT)
                >= android.os.Build.VERSION_CODES.HONEYCOMB;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final int position = firstPos + i;

            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(mCheckStates.get(position));
            } else if (useActivated) {
                child.setActivated(mCheckStates.get(position));
            }
        }
    }

    /**
     * @see #setChoiceMode(int)
     *
     * @return The current choice mode
     */
    public int getChoiceMode() {
        return mChoiceMode;
    }

    /**
     * Defines the choice behavior for the List. By default, Lists do not have any choice behavior
     * ({@link #CHOICE_MODE_NONE}). By setting the choiceMode to {@link #CHOICE_MODE_SINGLE}, the
     * List allows up to one item to  be in a chosen state. By setting the choiceMode to
     * {@link #CHOICE_MODE_MULTIPLE}, the list allows any number of items to be chosen.
     *
     * @param choiceMode One of {@link #CHOICE_MODE_NONE}, {@link #CHOICE_MODE_SINGLE}, or
     * {@link #CHOICE_MODE_MULTIPLE}
     */
    public void setChoiceMode(int choiceMode) {
        mChoiceMode = choiceMode;
        if (mChoiceActionMode != null) {
            mChoiceActionMode.finish();
            mChoiceActionMode = null;
        }
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (mCheckStates == null) {
                mCheckStates = new SparseBooleanArray();
            }
            if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
                mCheckedIdStates = new LongSparseArray<Integer>();
            }
            // Modal multi-choice mode only has choices when the mode is active. Clear them.
            // if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
            //     clearChoices();
            //     setLongClickable(true);
            // }
        }
    }


}
