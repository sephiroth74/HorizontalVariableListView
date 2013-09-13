/*
 * Author: alessandro crugnola
 * alessandro.crugnola@gmail.com
 * 
 * Fork based on the google opensource framework
 * 
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.sephiroth.android.library.widget;

import it.sephiroth.android.library.R;
import java.util.ArrayList;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RemoteViews.RemoteView;
import android.widget.WrapperListAdapter;

/*
 * Implementation Notes:
 *
 * Some terminology:
 *
 *     index    - index of the items that are currently visible
 *     position - index of the items in the cursor
 */

/**
 * A view that shows items in a vertically scrolling list. The items come from the {@link ListAdapter} associated with this view.
 * 
 * <p>
 * See the <a href="{@docRoot}guide/topics/ui/layout/listview.html">List View</a> guide.
 * </p>
 * 
 * @attr ref android.R.styleable#ListView_entries
 * @attr ref android.R.styleable#ListView_divider
 * @attr ref android.R.styleable#ListView_dividerHeight
 * @attr ref android.R.styleable#ListView_headerDividersEnabled
 * @attr ref android.R.styleable#ListView_footerDividersEnabled
 */
@RemoteView
public class HListView extends AbsHListView {

	/**
	 * Used to indicate a no preference for a position type.
	 */
	static final int NO_POSITION = -1;

	/**
	 * When arrow scrolling, ListView will never scroll more than this factor times the height of the list.
	 */
	private static final float MAX_SCROLL_FACTOR = 0.33f;

	/**
	 * When arrow scrolling, need a certain amount of pixels to preview next items. This is usually the fading edge, but if that is
	 * small enough, we want to make sure we preview at least this many pixels.
	 */
	private static final int MIN_SCROLL_PREVIEW_PIXELS = 2;

	private static final String LOG_TAG = "HListView";

	/**
	 * A class that represents a fixed view in a list, for example a header at the top or a footer at the bottom.
	 */
	public static class FixedViewInfo {

		/** The view to add to the list */
		public View view;
		/** The data backing the view. This is returned from {@link ListAdapter#getItem(int)}. */
		public Object data;
		/** <code>true</code> if the fixed view should be selectable in the list */
		public boolean isSelectable;
	}

	private ArrayList<FixedViewInfo> mHeaderViewInfos = new ArrayList<HListView.FixedViewInfo>();
	private ArrayList<FixedViewInfo> mFooterViewInfos = new ArrayList<HListView.FixedViewInfo>();

	Drawable mDivider;
	int mDividerWidth;
	int mMeasureWithChild;

	Drawable mOverScrollHeader;
	Drawable mOverScrollFooter;

	private boolean mIsCacheColorOpaque;
	private boolean mDividerIsOpaque;

	private boolean mHeaderDividersEnabled;
	private boolean mFooterDividersEnabled;

	private boolean mAreAllItemsSelectable = true;

	private boolean mItemsCanFocus = false;

	// used for temporary calculations.
	private final Rect mTempRect = new Rect();
	private Paint mDividerPaint;

	// the single allocated result per list view; kinda cheesey but avoids
	// allocating these thingies too often.
	private final ArrowScrollFocusResult mArrowScrollFocusResult = new ArrowScrollFocusResult();

	// Keeps focused children visible through resizes
	private FocusSelector mFocusSelector;

	public HListView( Context context ) {
		this( context, null );
	}

	public HListView( Context context, AttributeSet attrs ) {
		this( context, attrs, R.attr.sephiroth_listViewStyle );
	}

	public HListView( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );

		TypedArray a = context.obtainStyledAttributes( attrs, R.styleable.HListView, defStyle, 0 );

		CharSequence[] entries = a.getTextArray( R.styleable.HListView_android_entries );
		if ( entries != null ) {
			setAdapter( new ArrayAdapter<CharSequence>( context, android.R.layout.simple_list_item_1, entries ) );
		}

		final Drawable d = a.getDrawable( R.styleable.HListView_android_divider );
		if ( d != null ) {
			// If a divider is specified use its intrinsic height for divider height
			setDivider( d );
		}

		final Drawable osHeader = a.getDrawable( R.styleable.HListView_overScrollHeader );
		if ( osHeader != null ) {
			setOverscrollHeader( osHeader );
		}

		final Drawable osFooter = a.getDrawable( R.styleable.HListView_overScrollFooter );
		if ( osFooter != null ) {
			setOverscrollFooter( osFooter );
		}

		// Use the height specified, zero being the default
		final int dividerWidth = a.getDimensionPixelSize( R.styleable.HListView_dividerWidth, 0 );
		if ( dividerWidth != 0 ) {
			setDividerWidth( dividerWidth );
		}

		mHeaderDividersEnabled = a.getBoolean( R.styleable.HListView_headerDividersEnabled, true );
		mFooterDividersEnabled = a.getBoolean( R.styleable.HListView_footerDividersEnabled, true );
		mMeasureWithChild = a.getInteger( R.styleable.HListView_measureWithChild, -1 );
		
		Log.d( LOG_TAG, "mMeasureWithChild: " + mMeasureWithChild );

		a.recycle();
	}

	/**
	 * @return The maximum amount a list view will scroll in response to an arrow event.
	 */
	public int getMaxScrollAmount() {
		return (int) ( MAX_SCROLL_FACTOR * ( getRight() - getLeft() ) );
	}

	/**
	 * Make sure views are touching the top or bottom edge, as appropriate for our gravity
	 */
	private void adjustViewsLeftOrRight() {
		final int childCount = getChildCount();
		int delta;

		if ( childCount > 0 ) {
			View child;

			if ( !mStackFromRight ) {
				// Uh-oh -- we came up short. Slide all views up to make them
				// align with the top
				child = getChildAt( 0 );
				delta = child.getLeft() - mListPadding.left;
				if ( mFirstPosition != 0 ) {
					// It's OK to have some space above the first item if it is
					// part of the vertical spacing
					delta -= mDividerWidth;
				}
				if ( delta < 0 ) {
					// We only are looking to see if we are too low, not too high
					delta = 0;
				}
			} else {
				// we are too high, slide all views down to align with bottom
				child = getChildAt( childCount - 1 );
				delta = child.getRight() - ( getWidth() - mListPadding.right );

				if ( mFirstPosition + childCount < mItemCount ) {
					// It's OK to have some space below the last item if it is
					// part of the vertical spacing
					delta += mDividerWidth;
				}

				if ( delta > 0 ) {
					delta = 0;
				}
			}

			if ( delta != 0 ) {
				offsetChildrenLeftAndRight( -delta );
			}
		}
	}

	/**
	 * Add a fixed view to appear at the top of the list. If addHeaderView is called more than once, the views will appear in the
	 * order they were added. Views added using this call can take focus if they want.
	 * <p>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap the supplied cursor with one that will also account
	 * for header and footer views.
	 * 
	 * @param v
	 *           The view to add.
	 * @param data
	 *           Data to associate with this view
	 * @param isSelectable
	 *           whether the item is selectable
	 */
	public void addHeaderView( View v, Object data, boolean isSelectable ) {

		if ( mAdapter != null && !( mAdapter instanceof HeaderViewListAdapter ) ) {
			throw new IllegalStateException(
					"Cannot add header view to list -- setAdapter has already been called." );
		}

		FixedViewInfo info = new FixedViewInfo();
		info.view = v;
		info.data = data;
		info.isSelectable = isSelectable;
		mHeaderViewInfos.add( info );

		// in the case of re-adding a header view, or adding one later on,
		// we need to notify the observer
		if ( mAdapter != null && mDataSetObserver != null ) {
			mDataSetObserver.onChanged();
		}
	}

	/**
	 * Add a fixed view to appear at the top of the list. If addHeaderView is called more than once, the views will appear in the
	 * order they were added. Views added using this call can take focus if they want.
	 * <p>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap the supplied cursor with one that will also account
	 * for header and footer views.
	 * 
	 * @param v
	 *           The view to add.
	 */
	public void addHeaderView( View v ) {
		addHeaderView( v, null, true );
	}

	@Override
	public int getHeaderViewsCount() {
		return mHeaderViewInfos.size();
	}

	/**
	 * Removes a previously-added header view.
	 * 
	 * @param v
	 *           The view to remove
	 * @return true if the view was removed, false if the view was not a header view
	 */
	public boolean removeHeaderView( View v ) {
		if ( mHeaderViewInfos.size() > 0 ) {
			boolean result = false;
			if ( mAdapter != null && ( (HeaderViewListAdapter) mAdapter ).removeHeader( v ) ) {
				if ( mDataSetObserver != null ) {
					mDataSetObserver.onChanged();
				}
				result = true;
			}
			removeFixedViewInfo( v, mHeaderViewInfos );
			return result;
		}
		return false;
	}

	private void removeFixedViewInfo( View v, ArrayList<FixedViewInfo> where ) {
		int len = where.size();
		for ( int i = 0; i < len; ++i ) {
			FixedViewInfo info = where.get( i );
			if ( info.view == v ) {
				where.remove( i );
				break;
			}
		}
	}

	/**
	 * Add a fixed view to appear at the bottom of the list. If addFooterView is called more than once, the views will appear in the
	 * order they were added. Views added using this call can take focus if they want.
	 * <p>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap the supplied cursor with one that will also account
	 * for header and footer views.
	 * 
	 * @param v
	 *           The view to add.
	 * @param data
	 *           Data to associate with this view
	 * @param isSelectable
	 *           true if the footer view can be selected
	 */
	public void addFooterView( View v, Object data, boolean isSelectable ) {

		// NOTE: do not enforce the adapter being null here, since unlike in
		// addHeaderView, it was never enforced here, and so existing apps are
		// relying on being able to add a footer and then calling setAdapter to
		// force creation of the HeaderViewListAdapter wrapper

		FixedViewInfo info = new FixedViewInfo();
		info.view = v;
		info.data = data;
		info.isSelectable = isSelectable;
		mFooterViewInfos.add( info );

		// in the case of re-adding a footer view, or adding one later on,
		// we need to notify the observer
		if ( mAdapter != null && mDataSetObserver != null ) {
			mDataSetObserver.onChanged();
		}
	}

	/**
	 * Add a fixed view to appear at the bottom of the list. If addFooterView is called more than once, the views will appear in the
	 * order they were added. Views added using this call can take focus if they want.
	 * <p>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap the supplied cursor with one that will also account
	 * for header and footer views.
	 * 
	 * 
	 * @param v
	 *           The view to add.
	 */
	public void addFooterView( View v ) {
		addFooterView( v, null, true );
	}

	@Override
	public int getFooterViewsCount() {
		return mFooterViewInfos.size();
	}

	/**
	 * Removes a previously-added footer view.
	 * 
	 * @param v
	 *           The view to remove
	 * @return true if the view was removed, false if the view was not a footer view
	 */
	public boolean removeFooterView( View v ) {
		if ( mFooterViewInfos.size() > 0 ) {
			boolean result = false;
			if ( mAdapter != null && ( (HeaderViewListAdapter) mAdapter ).removeFooter( v ) ) {
				if ( mDataSetObserver != null ) {
					mDataSetObserver.onChanged();
				}
				result = true;
			}
			removeFixedViewInfo( v, mFooterViewInfos );
			return result;
		}
		return false;
	}

	/**
	 * Returns the adapter currently in use in this ListView. The returned adapter might not be the same adapter passed to
	 * {@link #setAdapter(ListAdapter)} but might be a {@link WrapperListAdapter}.
	 * 
	 * @return The adapter currently used to display data in this ListView.
	 * 
	 * @see #setAdapter(ListAdapter)
	 */
	@Override
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	/**
	 * Sets the data behind this ListView.
	 * 
	 * The adapter passed to this method may be wrapped by a {@link WrapperListAdapter}, depending on the ListView features currently
	 * in use. For instance, adding headers and/or footers will cause the adapter to be wrapped.
	 * 
	 * @param adapter
	 *           The ListAdapter which is responsible for maintaining the data backing this list and for producing a view to
	 *           represent an item in that data set.
	 * 
	 * @see #getAdapter()
	 */
	@Override
	public void setAdapter( ListAdapter adapter ) {
		if ( mAdapter != null && mDataSetObserver != null ) {
			mAdapter.unregisterDataSetObserver( mDataSetObserver );
		}

		resetList();
		mRecycler.clear();

		if ( mHeaderViewInfos.size() > 0 || mFooterViewInfos.size() > 0 ) {
			mAdapter = new HeaderViewListAdapter( mHeaderViewInfos, mFooterViewInfos, adapter );
		} else {
			mAdapter = adapter;
		}

		mOldSelectedPosition = INVALID_POSITION;
		mOldSelectedColId = INVALID_COL_ID;

		// AbsListView#setAdapter will update choice mode states.
		super.setAdapter( adapter );

		if ( mAdapter != null ) {
			mAreAllItemsSelectable = mAdapter.areAllItemsEnabled();
			mOldItemCount = mItemCount;
			mItemCount = mAdapter.getCount();
			checkFocus();

			mDataSetObserver = new AdapterDataSetObserver();
			mAdapter.registerDataSetObserver( mDataSetObserver );

			mRecycler.setViewTypeCount( mAdapter.getViewTypeCount() );

			int position;
			if ( mStackFromRight ) {
				position = lookForSelectablePosition( mItemCount - 1, false );
			} else {
				position = lookForSelectablePosition( 0, true );
			}
			setSelectedPositionInt( position );
			setNextSelectedPositionInt( position );

			if ( mItemCount == 0 ) {
				// Nothing selected
				checkSelectionChanged();
			}
		} else {
			mAreAllItemsSelectable = true;
			checkFocus();
			// Nothing selected
			checkSelectionChanged();
		}

		requestLayout();
	}

	/**
	 * The list is empty. Clear everything out.
	 */
	@Override
	protected void resetList() {
		// The parent's resetList() will remove all views from the layout so we need to
		// cleanup the state of our footers and headers
		clearRecycledState( mHeaderViewInfos );
		clearRecycledState( mFooterViewInfos );

		super.resetList();

		mLayoutMode = LAYOUT_NORMAL;
	}

	private void clearRecycledState( ArrayList<FixedViewInfo> infos ) {
		if ( infos != null ) {
			final int count = infos.size();

			for ( int i = 0; i < count; i++ ) {
				final View child = infos.get( i ).view;
				final LayoutParams p = (LayoutParams) child.getLayoutParams();
				if ( p != null ) {
					p.recycledHeaderFooter = false;
				}
			}
		}
	}

	/**
	 * @return Whether the list needs to show the top fading edge
	 */
	private boolean showingLeftFadingEdge() {
		final int listLeft = getScrollX() + mListPadding.left;
		return ( mFirstPosition > 0 ) || ( getChildAt( 0 ).getLeft() > listLeft );
	}

	/**
	 * @return Whether the list needs to show the bottom fading edge
	 */
	private boolean showingRightFadingEdge() {
		final int childCount = getChildCount();
		final int rightOfRightChild = getChildAt( childCount - 1 ).getRight();
		final int lastVisiblePosition = mFirstPosition + childCount - 1;

		final int listRight = getScrollX() + getWidth() - mListPadding.right;

		return ( lastVisiblePosition < mItemCount - 1 )
				|| ( rightOfRightChild < listRight );
	}

	@Override
	public boolean requestChildRectangleOnScreen( View child, Rect rect, boolean immediate ) {

		int rectLeftWithinChild = rect.left;

		// offset so rect is in coordinates of the this view
		rect.offset( child.getLeft(), child.getTop() );
		rect.offset( -child.getScrollX(), -child.getScrollY() );

		final int width = getWidth();
		int listUnfadedLeft = getScrollX();
		int listUnfadedRight = listUnfadedLeft + width;
		final int fadingEdge = getHorizontalFadingEdgeLength();

		if ( showingLeftFadingEdge() ) {
			// leave room for top fading edge as long as rect isn't at very top
			if ( ( mSelectedPosition > 0 ) || ( rectLeftWithinChild > fadingEdge ) ) {
				listUnfadedLeft += fadingEdge;
			}
		}

		int childCount = getChildCount();
		int rightOfRightChild = getChildAt( childCount - 1 ).getRight();

		if ( showingRightFadingEdge() ) {
			// leave room for bottom fading edge as long as rect isn't at very bottom
			if ( ( mSelectedPosition < mItemCount - 1 )
					|| ( rect.right < ( rightOfRightChild - fadingEdge ) ) ) {
				listUnfadedRight -= fadingEdge;
			}
		}

		int scrollXDelta = 0;

		if ( rect.right > listUnfadedRight && rect.left > listUnfadedLeft ) {
			// need to MOVE DOWN to get it in view: move down just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if ( rect.width() > width ) {
				// just enough to get screen size chunk on
				scrollXDelta += ( rect.left - listUnfadedLeft );
			} else {
				// get entire rect at bottom of screen
				scrollXDelta += ( rect.right - listUnfadedRight );
			}

			// make sure we aren't scrolling beyond the end of our children
			int distanceToRight = rightOfRightChild - listUnfadedRight;
			scrollXDelta = Math.min( scrollXDelta, distanceToRight );
		} else if ( rect.left < listUnfadedLeft && rect.right < listUnfadedRight ) {
			// need to MOVE UP to get it in view: move up just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if ( rect.width() > width ) {
				// screen size chunk
				scrollXDelta -= ( listUnfadedRight - rect.right );
			} else {
				// entire rect at top
				scrollXDelta -= ( listUnfadedLeft - rect.left );
			}

			// make sure we aren't scrolling any further than the top our children
			int left = getChildAt( 0 ).getLeft();
			int deltaToLeft = left - listUnfadedLeft;
			scrollXDelta = Math.max( scrollXDelta, deltaToLeft );
		}

		final boolean scroll = scrollXDelta != 0;
		if ( scroll ) {
			scrollListItemsBy( -scrollXDelta );
			positionSelector( INVALID_POSITION, child );
			mSelectedLeft = child.getTop();
			invalidate();
		}
		return scroll;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void fillGap( boolean down ) {
		final int count = getChildCount();
		if ( down ) {
			int paddingLeft = 0;
			// if ( ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ) {
			// paddingTop = getListPaddingTop();
			// }
			final int startOffset = count > 0 ? getChildAt( count - 1 ).getRight() + mDividerWidth : paddingLeft;
			fillRight( mFirstPosition + count, startOffset );
			correctTooWide( getChildCount() );
		} else {
			int paddingRight = 0;
			// if ( ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ) {
			// paddingBottom = getListPaddingBottom();
			// }
			final int startOffset = count > 0 ? getChildAt( 0 ).getLeft() - mDividerWidth : getWidth() - paddingRight;
			fillLeft( mFirstPosition - 1, startOffset );
			correctTooSmall( getChildCount() );
		}
	}

	/**
	 * Fills the list from pos down to the end of the list view.
	 * 
	 * @param pos
	 *           The first position to put in the list
	 * 
	 * @param nextLeft
	 *           The location where the left of the item associated with pos should be drawn
	 * 
	 * @return The view that is currently selected, if it happens to be in the range that we draw.
	 */
	private View fillRight( int pos, int nextLeft ) {
		View selectedView = null;

		int end = ( getRight() - getLeft() );
		// if ( ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ) {
		// end -= mListPadding.bottom;
		// }

		while ( nextLeft < end && pos < mItemCount ) {
			// is this the selected item?
			boolean selected = pos == mSelectedPosition;
			View child = makeAndAddView( pos, nextLeft, true, mListPadding.top, selected );

			nextLeft = child.getRight() + mDividerWidth;
			if ( selected ) {
				selectedView = child;
			}
			pos++;
		}

		setVisibleRangeHint( mFirstPosition, mFirstPosition + getChildCount() - 1 );
		return selectedView;
	}

	/**
	 * Fills the list from pos up to the top of the list view.
	 * 
	 * @param pos
	 *           The first position to put in the list
	 * 
	 * @param nextRight
	 *           The location where the right of the item associated with pos should be drawn
	 * 
	 * @return The view that is currently selected
	 */
	private View fillLeft( int pos, int nextRight ) {
		View selectedView = null;

		int end = 0;
		// if ( ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ) {
		// end = mListPadding.top;
		// }

		while ( nextRight > end && pos >= 0 ) {
			// is this the selected item?
			boolean selected = pos == mSelectedPosition;
			View child = makeAndAddView( pos, nextRight, false, mListPadding.top, selected );
			nextRight = child.getLeft() - mDividerWidth;
			if ( selected ) {
				selectedView = child;
			}
			pos--;
		}

		mFirstPosition = pos + 1;
		setVisibleRangeHint( mFirstPosition, mFirstPosition + getChildCount() - 1 );
		return selectedView;
	}

	/**
	 * Fills the list from top to bottom, starting with mFirstPosition
	 * 
	 * @param nextLeft
	 *           The location where the left of the first item should be drawn
	 * 
	 * @return The view that is currently selected
	 */
	private View fillFromLeft( int nextLeft ) {
		mFirstPosition = Math.min( mFirstPosition, mSelectedPosition );
		mFirstPosition = Math.min( mFirstPosition, mItemCount - 1 );
		if ( mFirstPosition < 0 ) {
			mFirstPosition = 0;
		}
		return fillRight( mFirstPosition, nextLeft );
	}

	/**
	 * Put mSelectedPosition in the middle of the screen and then build up and down from there. This method forces mSelectedPosition
	 * to the center.
	 * 
	 * @param childrenLeft
	 *           Left of the area in which children can be drawn, as measured in pixels
	 * @param childrenRight
	 *           Right of the area in which children can be drawn, as measured in pixels
	 * @return Currently selected view
	 */
	private View fillFromMiddle( int childrenLeft, int childrenRight ) {
		int width = childrenRight - childrenLeft;

		int position = reconcileSelectedPosition();

		View sel = makeAndAddView( position, childrenLeft, true, mListPadding.top, true );
		mFirstPosition = position;

		int selWidth = sel.getMeasuredWidth();
		if ( selWidth <= width ) {
			sel.offsetLeftAndRight( ( width - selWidth ) / 2 );
		}

		fillBeforeAndAfter( sel, position );

		if ( !mStackFromRight ) {
			correctTooWide( getChildCount() );
		} else {
			correctTooSmall( getChildCount() );
		}

		return sel;
	}

	/**
	 * Once the selected view as been placed, fill up the visible area above and below it.
	 * 
	 * @param sel
	 *           The selected view
	 * @param position
	 *           The position corresponding to sel
	 */
	private void fillBeforeAndAfter( View sel, int position ) {
		final int dividerWidth = mDividerWidth;
		if ( !mStackFromRight ) {
			fillLeft( position - 1, sel.getLeft() - dividerWidth );
			adjustViewsLeftOrRight();
			fillRight( position + 1, sel.getRight() + dividerWidth );
		} else {
			fillRight( position + 1, sel.getRight() + dividerWidth );
			adjustViewsLeftOrRight();
			fillLeft( position - 1, sel.getLeft() - dividerWidth );
		}
	}

	/**
	 * Fills the grid based on positioning the new selection at a specific location. The selection may be moved so that it does not
	 * intersect the faded edges. The grid is then filled upwards and downwards from there.
	 * 
	 * @param selectedLeft
	 *           Where the selected item should be
	 * @param childrenLeft
	 *           Where to start drawing children
	 * @param childrenRight
	 *           Last pixel where children can be drawn
	 * @return The view that currently has selection
	 */
	private View fillFromSelection( int selectedLeft, int childrenLeft, int childrenRight ) {
		int fadingEdgeLength = getHorizontalFadingEdgeLength();
		final int selectedPosition = mSelectedPosition;

		View sel;

		final int leftSelectionPixel = getLeftSelectionPixel( childrenLeft, fadingEdgeLength, selectedPosition );
		final int rightSelectionPixel = getRightSelectionPixel( childrenRight, fadingEdgeLength, selectedPosition );

		sel = makeAndAddView( selectedPosition, selectedLeft, true, mListPadding.top, true );

		// Some of the newly selected item extends below the bottom of the list
		if ( sel.getRight() > rightSelectionPixel ) {
			// Find space available above the selection into which we can scroll
			// upwards
			final int spaceBefore = sel.getLeft() - leftSelectionPixel;

			// Find space required to bring the bottom of the selected item
			// fully into view
			final int spaceAfter = sel.getRight() - rightSelectionPixel;
			final int offset = Math.min( spaceBefore, spaceAfter );

			// Now offset the selected item to get it into view
			sel.offsetLeftAndRight( -offset );
		} else if ( sel.getLeft() < leftSelectionPixel ) {
			// Find space required to bring the top of the selected item fully
			// into view
			final int spaceBefore = leftSelectionPixel - sel.getLeft();

			// Find space available below the selection into which we can scroll
			// downwards
			final int spaceAfter = rightSelectionPixel - sel.getRight();
			final int offset = Math.min( spaceBefore, spaceAfter );

			// Offset the selected item to get it into view
			sel.offsetLeftAndRight( offset );
		}

		// Fill in views above and below
		fillBeforeAndAfter( sel, selectedPosition );

		if ( !mStackFromRight ) {
			correctTooWide( getChildCount() );
		} else {
			correctTooSmall( getChildCount() );
		}

		return sel;
	}

	/**
	 * Calculate the bottom-most pixel we can draw the selection into
	 * 
	 * @param childrenRight
	 *           Right pixel were children can be drawn
	 * @param fadingEdgeLength
	 *           Length of the fading edge in pixels, if present
	 * @param selectedPosition
	 *           The position that will be selected
	 * @return The bottom-most pixel we can draw the selection into
	 */
	private int getRightSelectionPixel( int childrenRight, int fadingEdgeLength, int selectedPosition ) {
		int rightSelectionPixel = childrenRight;
		if ( selectedPosition != mItemCount - 1 ) {
			rightSelectionPixel -= fadingEdgeLength;
		}
		return rightSelectionPixel;
	}

	/**
	 * Calculate the left-most pixel we can draw the selection into
	 * 
	 * @param childrenLeft
	 *           Left pixel were children can be drawn
	 * @param fadingEdgeLength
	 *           Length of the fading edge in pixels, if present
	 * @param selectedPosition
	 *           The position that will be selected
	 * @return The left-most pixel we can draw the selection into
	 */
	private int getLeftSelectionPixel( int childrenLeft, int fadingEdgeLength, int selectedPosition ) {
		// first pixel we can draw the selection into
		int leftSelectionPixel = childrenLeft;
		if ( selectedPosition > 0 ) {
			leftSelectionPixel += fadingEdgeLength;
		}
		return leftSelectionPixel;
	}

	/**
	 * Smoothly scroll to the specified adapter position. The view will scroll such that the indicated position is displayed.
	 * 
	 * @param position
	 *           Scroll to this adapter position.
	 */
	@Override
	public void smoothScrollToPosition( int position ) {
		super.smoothScrollToPosition( position );
	}

	/**
	 * Smoothly scroll to the specified adapter position offset. The view will scroll such that the indicated position is displayed.
	 * 
	 * @param offset
	 *           The amount to offset from the adapter position to scroll to.
	 */
	@Override
	public void smoothScrollByOffset( int offset ) {
		super.smoothScrollByOffset( offset );
	}

	/**
	 * Fills the list based on positioning the new selection relative to the old selection. The new selection will be placed at,
	 * above, or below the location of the new selection depending on how the selection is moving. The selection will then be pinned
	 * to the visible part of the screen, excluding the edges that are faded. The list is then filled upwards and downwards from
	 * there.
	 * 
	 * @param oldSel
	 *           The old selected view. Useful for trying to put the new selection in the same place
	 * @param newSel
	 *           The view that is to become selected. Useful for trying to put the new selection in the same place
	 * @param delta
	 *           Which way we are moving
	 * @param childrenLeft
	 *           Where to start drawing children
	 * @param childrenRight
	 *           Last pixel where children can be drawn
	 * @return The view that currently has selection
	 */
	private View moveSelection( View oldSel, View newSel, int delta, int childrenLeft, int childrenRight ) {
		int fadingEdgeLength = getHorizontalFadingEdgeLength();
		final int selectedPosition = mSelectedPosition;

		View sel;

		final int leftSelectionPixel = getLeftSelectionPixel( childrenLeft, fadingEdgeLength, selectedPosition );
		final int rightSelectionPixel = getRightSelectionPixel( childrenLeft, fadingEdgeLength, selectedPosition );

		if ( delta > 0 ) {
			/*
			 * Case 1: Scrolling down.
			 */

			/*
			 * Before After | | | | +-------+ +-------+ | A | | A | | 1 | => +-------+ +-------+ | B | | B | | 2 | +-------+ +-------+
			 * | | | |
			 * 
			 * Try to keep the top of the previously selected item where it was. oldSel = A sel = B
			 */

			// Put oldSel (A) where it belongs
			oldSel = makeAndAddView( selectedPosition - 1, oldSel.getLeft(), true, mListPadding.top, false );

			final int dividerWidth = mDividerWidth;

			// Now put the new selection (B) below that
			sel = makeAndAddView( selectedPosition, oldSel.getRight() + dividerWidth, true, mListPadding.top, true );

			// Some of the newly selected item extends below the bottom of the list
			if ( sel.getRight() > rightSelectionPixel ) {

				// Find space available above the selection into which we can scroll upwards
				int spaceBefore = sel.getLeft() - leftSelectionPixel;

				// Find space required to bring the bottom of the selected item fully into view
				int spaceAfter = sel.getRight() - rightSelectionPixel;

				// Don't scroll more than half the height of the list
				int halfHorizontalSpace = ( childrenRight - childrenLeft ) / 2;
				int offset = Math.min( spaceBefore, spaceAfter );
				offset = Math.min( offset, halfHorizontalSpace );

				// We placed oldSel, so offset that item
				oldSel.offsetLeftAndRight( -offset );
				// Now offset the selected item to get it into view
				sel.offsetLeftAndRight( -offset );
			}

			// Fill in views above and below
			if ( !mStackFromRight ) {
				fillLeft( mSelectedPosition - 2, sel.getLeft() - dividerWidth );
				adjustViewsLeftOrRight();
				fillRight( mSelectedPosition + 1, sel.getRight() + dividerWidth );
			} else {
				fillRight( mSelectedPosition + 1, sel.getRight() + dividerWidth );
				adjustViewsLeftOrRight();
				fillLeft( mSelectedPosition - 2, sel.getLeft() - dividerWidth );
			}
		} else if ( delta < 0 ) {
			/*
			 * Case 2: Scrolling up.
			 */

			/*
			 * Before After | | | | +-------+ +-------+ | A | | A | +-------+ => | 1 | | B | +-------+ | 2 | | B | +-------+ +-------+
			 * | | | |
			 * 
			 * Try to keep the top of the item about to become selected where it was. newSel = A olSel = B
			 */

			if ( newSel != null ) {
				// Try to position the top of newSel (A) where it was before it was selected
				sel = makeAndAddView( selectedPosition, newSel.getLeft(), true, mListPadding.top, true );
			} else {
				// If (A) was not on screen and so did not have a view, position
				// it above the oldSel (B)
				sel = makeAndAddView( selectedPosition, oldSel.getLeft(), false, mListPadding.top, true );
			}

			// Some of the newly selected item extends above the top of the list
			if ( sel.getLeft() < leftSelectionPixel ) {
				// Find space required to bring the top of the selected item fully into view
				int spaceBefore = leftSelectionPixel - sel.getLeft();

				// Find space available below the selection into which we can scroll downwards
				int spaceAfter = rightSelectionPixel - sel.getRight();

				// Don't scroll more than half the height of the list
				int halfHorizontalSpace = ( childrenRight - childrenLeft ) / 2;
				int offset = Math.min( spaceBefore, spaceAfter );
				offset = Math.min( offset, halfHorizontalSpace );

				// Offset the selected item to get it into view
				sel.offsetLeftAndRight( offset );
			}

			// Fill in views above and below
			fillBeforeAndAfter( sel, selectedPosition );
		} else {

			int oldLeft = oldSel.getLeft();

			/*
			 * Case 3: Staying still
			 */
			sel = makeAndAddView( selectedPosition, oldLeft, true, mListPadding.top, true );

			// We're staying still...
			if ( oldLeft < childrenLeft ) {
				// ... but the top of the old selection was off screen.
				// (This can happen if the data changes size out from under us)
				int newRight = sel.getRight();
				if ( newRight < childrenLeft + 20 ) {
					// Not enough visible -- bring it onscreen
					sel.offsetLeftAndRight( childrenLeft - sel.getLeft() );
				}
			}

			// Fill in views above and below
			fillBeforeAndAfter( sel, selectedPosition );
		}

		return sel;
	}

	private class FocusSelector implements Runnable {

		private int mPosition;
		private int mPositionLeft;

		public FocusSelector setup( int position, int left ) {
			mPosition = position;
			mPositionLeft = left;
			return this;
		}

		@Override
		public void run() {
			setSelectionFromLeft( mPosition, mPositionLeft );
		}
	}

	@Override
	protected void onSizeChanged( int w, int h, int oldw, int oldh ) {
		if ( getChildCount() > 0 ) {
			View focusedChild = getFocusedChild();
			if ( focusedChild != null ) {
				final int childPosition = mFirstPosition + indexOfChild( focusedChild );
				final int childRight = focusedChild.getRight();
				final int offset = Math.max( 0, childRight - ( w - getPaddingLeft() ) );
				final int left = focusedChild.getLeft() - offset;
				if ( mFocusSelector == null ) {
					mFocusSelector = new FocusSelector();
				}
				post( mFocusSelector.setup( childPosition, left ) );
			}
		}
		super.onSizeChanged( w, h, oldw, oldh );
	}

	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
		// Sets up mListPadding
		super.onMeasure( widthMeasureSpec, heightMeasureSpec );

		int widthMode = MeasureSpec.getMode( widthMeasureSpec );
		int heightMode = MeasureSpec.getMode( heightMeasureSpec );
		int widthSize = MeasureSpec.getSize( widthMeasureSpec );
		int heightSize = MeasureSpec.getSize( heightMeasureSpec );
		
		int childWidth = 0;
		int childHeight = 0;
		int childState = 0;

		mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
		
		if ( mItemCount > 0 && ( widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED ) ) {
			Log.d( LOG_TAG, "let's measure a scrap child" );
			
			final View child = obtainView( 0, mIsScrap );

			measureScrapChildWidth( child, 0, heightMeasureSpec );

			childWidth = child.getMeasuredWidth();
			childHeight = child.getMeasuredHeight();
			
			if( android.os.Build.VERSION.SDK_INT >= 11 ) {
				childState = combineMeasuredStates( childState, child.getMeasuredState() );
			}

			if ( recycleOnMeasure() && mRecycler.shouldRecycleViewType( ( (LayoutParams) child.getLayoutParams() ).viewType ) ) {
				mRecycler.addScrapView( child, -1 );
			}
		}
		
		if ( heightMode == MeasureSpec.UNSPECIFIED ) {
			heightSize = mListPadding.top + mListPadding.bottom + childHeight + getHorizontalScrollbarHeight();
		} else if( heightMode == MeasureSpec.AT_MOST && mItemCount > 0 && mMeasureWithChild > -1 ) {
			
			// TODO: need a better way in case of "wrap_content"
			int[] result = measureWithLargeChildren( heightMeasureSpec, mMeasureWithChild, mMeasureWithChild, widthSize, heightSize, -1 );
			heightSize = result[1];
			
		} else { // match_parent, dimension
			if( android.os.Build.VERSION.SDK_INT >= 11 ) {
				heightSize |= ( childState & MEASURED_STATE_MASK );
			}
		}

		if ( widthMode == MeasureSpec.UNSPECIFIED ) {
			widthSize = mListPadding.left + mListPadding.right + childWidth + getHorizontalFadingEdgeLength() * 2;
		}

		if ( widthMode == MeasureSpec.AT_MOST ) {
			widthSize = measureWidthOfChildren( heightMeasureSpec, 0, NO_POSITION, widthSize, -1 );
		}
		
		// Log.d( LOG_TAG, "final size: " + widthSize + "x" + heightSize );

		setMeasuredDimension( widthSize, heightSize );
		mHeightMeasureSpec = heightMeasureSpec;
	}

	private void measureScrapChildWidth( View child, int position, int heightMeasureSpec ) {
		LayoutParams p = (LayoutParams) child.getLayoutParams();
		if ( p == null ) {
			p = (AbsHListView.LayoutParams) generateDefaultLayoutParams();
			child.setLayoutParams( p );
		}
		p.viewType = mAdapter.getItemViewType( position );
		p.forceAdd = true;

		int childHeightSpec = ViewGroup.getChildMeasureSpec( heightMeasureSpec, mListPadding.top + mListPadding.bottom, p.height );
		int lpWidth = p.width;
		int childWidthSpec;
		if ( lpWidth > 0 ) {
			childWidthSpec = MeasureSpec.makeMeasureSpec( lpWidth, MeasureSpec.EXACTLY );
		} else {
			childWidthSpec = MeasureSpec.makeMeasureSpec( 0, MeasureSpec.UNSPECIFIED );
		}
		child.measure( childWidthSpec, childHeightSpec );
	}
	
	public int[] measureChild( View child ) {
		measureItem( child );
		
		int w = child.getMeasuredWidth();
		int h = child.getMeasuredHeight();
		
		return new int[]{ w, h };
	}

	/**
	 * @return True to recycle the views used to measure this ListView in UNSPECIFIED/AT_MOST modes, false otherwise.
	 * @hide
	 */
	@ViewDebug.ExportedProperty(category = "list")
	protected boolean recycleOnMeasure() {
		return true;
	}

	/**
	 * Measures the height of the given range of children (inclusive) and returns the height with this ListView's padding and divider
	 * heights included. If maxHeight is provided, the measuring will stop when the current height reaches maxHeight.
	 * 
	 * @param heightMeasureSpec
	 *           The height measure spec to be given to a child's {@link View#measure(int, int)}.
	 * @param startPosition
	 *           The position of the first child to be shown.
	 * @param endPosition
	 *           The (inclusive) position of the last child to be shown. Specify {@link #NO_POSITION} if the last child should be the
	 *           last available child from the adapter.
	 * @param maxWidth
	 *           The maximum height that will be returned (if all the children don't fit in this value, this value will be returned).
	 * @param disallowPartialChildPosition
	 *           In general, whether the returned height should only contain entire children. This is more powerful--it is the first
	 *           inclusive position at which partial children will not be allowed. Example: it looks nice to have at least 3
	 *           completely visible children, and in portrait this will most likely fit; but in landscape there could be times when
	 *           even 2 children can not be completely shown, so a value of 2 (remember, inclusive) would be good (assuming
	 *           startPosition is 0).
	 * @return The height of this ListView with the given children.
	 */
	final int measureWidthOfChildren( int heightMeasureSpec, int startPosition, int endPosition,
			final int maxWidth, int disallowPartialChildPosition ) {
		Log.i( LOG_TAG, "measureWidthOfChildren, from " + startPosition + " to " + endPosition );

		final ListAdapter adapter = mAdapter;
		if ( adapter == null ) {
			return mListPadding.left + mListPadding.right;
		}

		// Include the padding of the list
		int returnedWidth = mListPadding.left + mListPadding.right;
		final int dividerWidth = ( ( mDividerWidth > 0 ) && mDivider != null ) ? mDividerWidth : 0;
		// The previous height value that was less than maxHeight and contained
		// no partial children
		int prevWidthWithoutPartialChild = 0;
		int i;
		View child;

		// mItemCount - 1 since endPosition parameter is inclusive
		endPosition = ( endPosition == NO_POSITION ) ? adapter.getCount() - 1 : endPosition;
		final AbsHListView.RecycleBin recycleBin = mRecycler;
		final boolean recyle = recycleOnMeasure();
		final boolean[] isScrap = mIsScrap;

		for ( i = startPosition; i <= endPosition; ++i ) {
			child = obtainView( i, isScrap );

			measureScrapChildWidth( child, i, heightMeasureSpec );

			if ( i > 0 ) {
				// Count the divider for all but one child
				returnedWidth += dividerWidth;
			}

			// Recycle the view before we possibly return from the method
			if ( recyle && recycleBin.shouldRecycleViewType( ( (LayoutParams) child.getLayoutParams() ).viewType ) ) {
				recycleBin.addScrapView( child, -1 );
			}

			returnedWidth += child.getMeasuredWidth();

			if ( returnedWidth >= maxWidth ) {
				// We went over, figure out which height to return. If returnedHeight > maxHeight,
				// then the i'th position did not fit completely.
				return ( disallowPartialChildPosition >= 0 ) // Disallowing is enabled (> -1)
						&& ( i > disallowPartialChildPosition ) // We've past the min pos
						&& ( prevWidthWithoutPartialChild > 0 ) // We have a prev height
						&& ( returnedWidth != maxWidth ) // i'th child did not fit completely
				? prevWidthWithoutPartialChild : maxWidth;
			}

			if ( ( disallowPartialChildPosition >= 0 ) && ( i >= disallowPartialChildPosition ) ) {
				prevWidthWithoutPartialChild = returnedWidth;
			}
		}

		// At this point, we went through the range of children, and they each
		// completely fit, so return the returnedHeight
		return returnedWidth;
	}
	
	final int[] measureWithLargeChildren( int heightMeasureSpec, int startPosition, int endPosition, final int maxWidth, final int maxHeight, int disallowPartialChildPosition ) {
		Log.i( LOG_TAG, "measureWithLargeChildren, from " + startPosition + " to " + endPosition );

		final ListAdapter adapter = mAdapter;
		if ( adapter == null ) {
			return new int[]{ mListPadding.left + mListPadding.right, mListPadding.top + mListPadding.bottom };
		}

		// Include the padding of the list
		int returnedWidth = mListPadding.left + mListPadding.right;
		int returnedHeight = mListPadding.top + mListPadding.bottom;
		
		final int dividerWidth = ( ( mDividerWidth > 0 ) && mDivider != null ) ? mDividerWidth : 0;
		
		int childWidth = 0;
		int childHeight = 0;
		
		int i;
		View child;

		// mItemCount - 1 since endPosition parameter is inclusive
		endPosition = ( endPosition == NO_POSITION ) ? adapter.getCount() - 1 : endPosition;
		final AbsHListView.RecycleBin recycleBin = mRecycler;
		final boolean recyle = recycleOnMeasure();
		final boolean[] isScrap = mIsScrap;

		for ( i = startPosition; i <= endPosition; ++i ) {
			child = obtainView( i, isScrap );

			measureScrapChildWidth( child, i, heightMeasureSpec );

			// Recycle the view before we possibly return from the method
			if ( recyle && recycleBin.shouldRecycleViewType( ( (LayoutParams) child.getLayoutParams() ).viewType ) ) {
				recycleBin.addScrapView( child, -1 );
			}

			childWidth = Math.max( childWidth, child.getMeasuredWidth() + dividerWidth );
			childHeight = Math.max( childHeight, child.getMeasuredHeight() );
		}
		
		returnedWidth += childWidth;
		returnedHeight += childHeight;

		return new int[]{ Math.min( returnedWidth, maxWidth ), Math.min( returnedHeight, maxHeight ) };
	}	
	

	@Override
	protected int findMotionCol( int x ) {
		int childCount = getChildCount();
		if ( childCount > 0 ) {
			if ( !mStackFromRight ) {
				for ( int i = 0; i < childCount; i++ ) {
					View v = getChildAt( i );
					if ( x <= v.getRight() ) {
						return mFirstPosition + i;
					}
				}
			} else {
				for ( int i = childCount - 1; i >= 0; i-- ) {
					View v = getChildAt( i );
					if ( x >= v.getLeft() ) {
						return mFirstPosition + i;
					}
				}
			}
		}
		return INVALID_POSITION;
	}

	/**
	 * Put a specific item at a specific location on the screen and then build up and down from there.
	 * 
	 * @param position
	 *           The reference view to use as the starting point
	 * @param left
	 *           Pixel offset from the left of this view to the top of the reference view.
	 * 
	 * @return The selected view, or null if the selected view is outside the visible area.
	 */
	private View fillSpecific( int position, int left ) {
		boolean tempIsSelected = position == mSelectedPosition;
		View temp = makeAndAddView( position, left, true, mListPadding.top, tempIsSelected );
		// Possibly changed again in fillUp if we add rows above this one.
		mFirstPosition = position;

		View before;
		View after;

		final int dividerWidth = mDividerWidth;
		if ( !mStackFromRight ) {
			before = fillLeft( position - 1, temp.getLeft() - dividerWidth );
			// This will correct for the top of the first view not touching the top of the list
			adjustViewsLeftOrRight();
			after = fillRight( position + 1, temp.getRight() + dividerWidth );
			int childCount = getChildCount();
			if ( childCount > 0 ) {
				correctTooWide( childCount );
			}
		} else {
			after = fillRight( position + 1, temp.getRight() + dividerWidth );
			// This will correct for the bottom of the last view not touching the bottom of the list
			adjustViewsLeftOrRight();
			before = fillLeft( position - 1, temp.getLeft() - dividerWidth );
			int childCount = getChildCount();
			if ( childCount > 0 ) {
				correctTooSmall( childCount );
			}
		}

		if ( tempIsSelected ) {
			return temp;
		} else if ( before != null ) {
			return before;
		} else {
			return after;
		}
	}

	/**
	 * Check if we have dragged the right of the list too wide (we have pushed the left element off the left of the screen when we did
	 * not need to). Correct by sliding everything back down.
	 * 
	 * @param childCount
	 *           Number of children
	 */
	private void correctTooWide( int childCount ) {
		// First see if the last item is visible. If it is not, it is OK for the
		// top of the list to be pushed up.
		int lastPosition = mFirstPosition + childCount - 1;
		if ( lastPosition == mItemCount - 1 && childCount > 0 ) {

			// Get the last child ...
			final View lastChild = getChildAt( childCount - 1 );

			// ... and its bottom edge
			final int lastRight = lastChild.getRight();

			// This is bottom of our drawable area
			final int end = ( getRight() - getLeft() ) - mListPadding.right;

			// This is how far the bottom edge of the last view is from the bottom of the
			// drawable area
			int rightOffset = end - lastRight;
			View firstChild = getChildAt( 0 );
			final int firstLeft = firstChild.getLeft();

			// Make sure we are 1) Too high, and 2) Either there are more rows above the
			// first row or the first row is scrolled off the top of the drawable area
			if ( rightOffset > 0 && ( mFirstPosition > 0 || firstLeft < mListPadding.top ) ) {
				if ( mFirstPosition == 0 ) {
					// Don't pull the top too far down
					rightOffset = Math.min( rightOffset, mListPadding.top - firstLeft );
				}
				// Move everything down
				offsetChildrenLeftAndRight( rightOffset );
				if ( mFirstPosition > 0 ) {
					// Fill the gap that was opened above mFirstPosition with more rows, if
					// possible
					fillLeft( mFirstPosition - 1, firstChild.getLeft() - mDividerWidth );
					// Close up the remaining gap
					adjustViewsLeftOrRight();
				}

			}
		}
	}

	/**
	 * Check if we have dragged the right of the list too low (we have pushed the right element off the right of the screen when
	 * we did not need to). Correct by sliding everything back up.
	 * 
	 * @param childCount
	 *           Number of children
	 */
	private void correctTooSmall( int childCount ) {
		// First see if the first item is visible. If it is not, it is OK for the
		// bottom of the list to be pushed down.
		if ( mFirstPosition == 0 && childCount > 0 ) {

			// Get the first child ...
			final View firstChild = getChildAt( 0 );

			// ... and its top edge
			final int firstLeft = firstChild.getLeft();

			// This is top of our drawable area
			final int start = mListPadding.left;

			// This is bottom of our drawable area
			final int end = ( getRight() - getLeft() ) - mListPadding.right;

			// This is how far the top edge of the first view is from the top of the
			// drawable area
			int leftOffset = firstLeft - start;
			View lastChild = getChildAt( childCount - 1 );
			final int lastRight = lastChild.getRight();
			int lastPosition = mFirstPosition + childCount - 1;

			// Make sure we are 1) Too low, and 2) Either there are more rows below the
			// last row or the last row is scrolled off the bottom of the drawable area
			if ( leftOffset > 0 ) {
				if ( lastPosition < mItemCount - 1 || lastRight > end ) {
					if ( lastPosition == mItemCount - 1 ) {
						// Don't pull the bottom too far up
						leftOffset = Math.min( leftOffset, lastRight - end );
					}
					// Move everything up
					offsetChildrenLeftAndRight( -leftOffset );
					if ( lastPosition < mItemCount - 1 ) {
						// Fill the gap that was opened below the last position with more rows, if
						// possible
						fillRight( lastPosition + 1, lastChild.getRight() + mDividerWidth );
						// Close up the remaining gap
						adjustViewsLeftOrRight();
					}
				} else if ( lastPosition == mItemCount - 1 ) {
					adjustViewsLeftOrRight();
				}
			}
		}
	}

	@Override
	protected void layoutChildren() {
		final boolean blockLayoutRequests = mBlockLayoutRequests;
		if ( !blockLayoutRequests ) {
			mBlockLayoutRequests = true;
		} else {
			return;
		}

		try {
			super.layoutChildren();

			invalidate();

			if ( mAdapter == null ) {
				resetList();
				invokeOnItemScrollListener();
				return;
			}

			int childrenLeft = mListPadding.left;
			int childrenRight = getRight() - getLeft() - mListPadding.right;

			int childCount = getChildCount();
			int index = 0;
			int delta = 0;

			View sel;
			View oldSel = null;
			View oldFirst = null;
			View newSel = null;

			View focusLayoutRestoreView = null;

			// AccessibilityNodeInfo accessibilityFocusLayoutRestoreNode = null;
			// View accessibilityFocusLayoutRestoreView = null;
			// int accessibilityFocusPosition = INVALID_POSITION;

			// Remember stuff we will need down below
			switch ( mLayoutMode ) {
				case LAYOUT_SET_SELECTION:
					index = mNextSelectedPosition - mFirstPosition;
					if ( index >= 0 && index < childCount ) {
						newSel = getChildAt( index );
					}
					break;
				case LAYOUT_FORCE_LEFT:
				case LAYOUT_FORCE_RIGHT:
				case LAYOUT_SPECIFIC:
				case LAYOUT_SYNC:
					break;
				case LAYOUT_MOVE_SELECTION:
				default:
					// Remember the previously selected view
					index = mSelectedPosition - mFirstPosition;
					if ( index >= 0 && index < childCount ) {
						oldSel = getChildAt( index );
					}

					// Remember the previous first child
					oldFirst = getChildAt( 0 );

					if ( mNextSelectedPosition >= 0 ) {
						delta = mNextSelectedPosition - mSelectedPosition;
					}

					// Caution: newSel might be null
					newSel = getChildAt( index + delta );
			}

			boolean dataChanged = mDataChanged;
			if ( dataChanged ) {
				handleDataChanged();
			}

			// Handle the empty set by removing all views that are visible
			// and calling it a day
			if ( mItemCount == 0 ) {
				resetList();
				invokeOnItemScrollListener();
				return;
			} else if ( mItemCount != mAdapter.getCount() ) {
				throw new IllegalStateException( "The content of the adapter has changed but "
						+ "ListView did not receive a notification. Make sure the content of "
						+ "your adapter is not modified from a background thread, but only "
						+ "from the UI thread. [in ListView(" + getId() + ", " + getClass()
						+ ") with Adapter(" + mAdapter.getClass() + ")]" );
			}

			setSelectedPositionInt( mNextSelectedPosition );

			// Pull all children into the RecycleBin.
			// These views will be reused if possible
			final int firstPosition = mFirstPosition;
			final RecycleBin recycleBin = mRecycler;

			// reset the focus restoration
			View focusLayoutRestoreDirectChild = null;

			// Don't put header or footer views into the Recycler. Those are
			// already cached in mHeaderViews;
			if ( dataChanged ) {
				for ( int i = 0; i < childCount; i++ ) {
					recycleBin.addScrapView( getChildAt( i ), firstPosition + i );
				}
			} else {
				recycleBin.fillActiveViews( childCount, firstPosition );
			}

			// take focus back to us temporarily to avoid the eventual
			// call to clear focus when removing the focused child below
			// from messing things up when ViewAncestor assigns focus back
			// to someone else
			final View focusedChild = getFocusedChild();
			if ( focusedChild != null ) {
				// TODO: in some cases focusedChild.getParent() == null

				// we can remember the focused view to restore after relayout if the
				// data hasn't changed, or if the focused position is a header or footer
				if ( !dataChanged || isDirectChildHeaderOrFooter( focusedChild ) ) {
					focusLayoutRestoreDirectChild = focusedChild;
					// remember the specific view that had focus
					focusLayoutRestoreView = findFocus();
					if ( focusLayoutRestoreView != null ) {
						// tell it we are going to mess with it
						focusLayoutRestoreView.onStartTemporaryDetach();
					}
				}
				requestFocus();
			}

			/*
			 * // Remember which child, if any, had accessibility focus. final ViewRootImpl viewRootImpl = getViewRootImpl(); if (
			 * viewRootImpl != null ) { final View accessFocusedView = viewRootImpl.getAccessibilityFocusedHost(); if (
			 * accessFocusedView != null ) { final View accessFocusedChild = findAccessibilityFocusedChild( accessFocusedView ); if (
			 * accessFocusedChild != null ) { if ( !dataChanged || isDirectChildHeaderOrFooter( accessFocusedChild ) ) { // If the
			 * views won't be changing, try to maintain // focus on the current view host and (if // applicable) its virtual view.
			 * accessibilityFocusLayoutRestoreView = accessFocusedView; accessibilityFocusLayoutRestoreNode = viewRootImpl
			 * .getAccessibilityFocusedVirtualView(); } else { // Otherwise, try to maintain focus at the same // position.
			 * accessibilityFocusPosition = getPositionForView( accessFocusedChild ); } } } }
			 */

			// Clear out old views
			detachAllViewsFromParent();
			recycleBin.removeSkippedScrap();

			switch ( mLayoutMode ) {
				case LAYOUT_SET_SELECTION:
					if ( newSel != null ) {
						sel = fillFromSelection( newSel.getLeft(), childrenLeft, childrenRight );
					} else {
						sel = fillFromMiddle( childrenLeft, childrenRight );
					}
					break;
				case LAYOUT_SYNC:
					sel = fillSpecific( mSyncPosition, mSpecificLeft );
					break;
				case LAYOUT_FORCE_RIGHT:
					sel = fillLeft( mItemCount - 1, childrenRight );
					adjustViewsLeftOrRight();
					break;
				case LAYOUT_FORCE_LEFT:
					mFirstPosition = 0;
					sel = fillFromLeft( childrenLeft );
					adjustViewsLeftOrRight();
					break;
				case LAYOUT_SPECIFIC:
					sel = fillSpecific( reconcileSelectedPosition(), mSpecificLeft );
					break;
				case LAYOUT_MOVE_SELECTION:
					sel = moveSelection( oldSel, newSel, delta, childrenLeft, childrenRight );
					break;
				default:
					if ( childCount == 0 ) {
						if ( !mStackFromRight ) {
							final int position = lookForSelectablePosition( 0, true );
							setSelectedPositionInt( position );
							sel = fillFromLeft( childrenLeft );
						} else {
							final int position = lookForSelectablePosition( mItemCount - 1, false );
							setSelectedPositionInt( position );
							sel = fillLeft( mItemCount - 1, childrenRight );
						}
					} else {
						if ( mSelectedPosition >= 0 && mSelectedPosition < mItemCount ) {
							sel = fillSpecific( mSelectedPosition, oldSel == null ? childrenLeft : oldSel.getLeft() );
						} else if ( mFirstPosition < mItemCount ) {
							sel = fillSpecific( mFirstPosition, oldFirst == null ? childrenLeft : oldFirst.getLeft() );
						} else {
							sel = fillSpecific( 0, childrenLeft );
						}
					}
					break;
			}

			// Flush any cached views that did not get reused above
			recycleBin.scrapActiveViews();

			if ( sel != null ) {
				// the current selected item should get focus if items
				// are focusable
				if ( mItemsCanFocus && hasFocus() && !sel.hasFocus() ) {
					final boolean focusWasTaken = ( sel == focusLayoutRestoreDirectChild &&
							focusLayoutRestoreView != null &&
							focusLayoutRestoreView.requestFocus() ) || sel.requestFocus();
					if ( !focusWasTaken ) {
						// selected item didn't take focus, fine, but still want
						// to make sure something else outside of the selected view
						// has focus
						final View focused = getFocusedChild();
						if ( focused != null ) {
							focused.clearFocus();
						}
						positionSelector( INVALID_POSITION, sel );
					} else {
						sel.setSelected( false );
						mSelectorRect.setEmpty();
					}
				} else {
					positionSelector( INVALID_POSITION, sel );
				}
				mSelectedLeft = sel.getLeft();
			} else {
				if ( mTouchMode > TOUCH_MODE_DOWN && mTouchMode < TOUCH_MODE_SCROLL ) {
					View child = getChildAt( mMotionPosition - mFirstPosition );
					if ( child != null ) positionSelector( mMotionPosition, child );
				} else {
					mSelectedLeft = 0;
					mSelectorRect.setEmpty();
				}

				// even if there is not selected position, we may need to restore
				// focus (i.e. something focusable in touch mode)
				if ( hasFocus() && focusLayoutRestoreView != null ) {
					focusLayoutRestoreView.requestFocus();
				}
			}

			// Attempt to restore accessibility focus.
			// if ( accessibilityFocusLayoutRestoreNode != null ) {
			// accessibilityFocusLayoutRestoreNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS );
			// } else if ( accessibilityFocusLayoutRestoreView != null ) {
			// this method is marked as hidden. Shame on you google!
			// accessibilityFocusLayoutRestoreView.requestAccessibilityFocus();
			// } else if ( accessibilityFocusPosition != INVALID_POSITION ) {
			// Bound the position within the visible children.
			// final int position = MathUtils.constrain( ( accessibilityFocusPosition - mFirstPosition ), 0, ( getChildCount() - 1 ) );
			// final View restoreView = getChildAt( position );
			// if ( restoreView != null ) {
			// this method is marked as hidden. Shame on you google!
			// restoreView.requestAccessibilityFocus();
			// }
			// }

			// tell focus view we are done mucking with it, if it is still in
			// our view hierarchy.
			if ( focusLayoutRestoreView != null
					&& focusLayoutRestoreView.getWindowToken() != null ) {
				focusLayoutRestoreView.onFinishTemporaryDetach();
			}
			
			mLayoutMode = LAYOUT_NORMAL;
			mDataChanged = false;
			if ( mPositionScrollAfterLayout != null ) {
				post( mPositionScrollAfterLayout );
				mPositionScrollAfterLayout = null;
			}
			mNeedSync = false;
			setNextSelectedPositionInt( mSelectedPosition );

			updateScrollIndicators();

			if ( mItemCount > 0 ) {
				checkSelectionChanged();
			}

			invokeOnItemScrollListener();
		} finally {
			if ( !blockLayoutRequests ) {
				mBlockLayoutRequests = false;
			}
		}
	}

	/**
	 * @param focusedView
	 *           the view that has accessibility focus.
	 * @return the direct child that contains accessibility focus.
	 */
	@SuppressWarnings("unused")
	private View findAccessibilityFocusedChild( View focusedView ) {
		ViewParent viewParent = focusedView.getParent();
		while ( ( viewParent instanceof View ) && ( viewParent != this ) ) {
			focusedView = (View) viewParent;
			viewParent = viewParent.getParent();
		}
		if ( !( viewParent instanceof View ) ) {
			return null;
		}
		return focusedView;
	}

	/**
	 * @param child
	 *           a direct child of this list.
	 * @return Whether child is a header or footer view.
	 */
	private boolean isDirectChildHeaderOrFooter( View child ) {

		final ArrayList<FixedViewInfo> headers = mHeaderViewInfos;
		final int numHeaders = headers.size();
		for ( int i = 0; i < numHeaders; i++ ) {
			if ( child == headers.get( i ).view ) {
				return true;
			}
		}
		final ArrayList<FixedViewInfo> footers = mFooterViewInfos;
		final int numFooters = footers.size();
		for ( int i = 0; i < numFooters; i++ ) {
			if ( child == footers.get( i ).view ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Obtain the view and add it to our list of children. The view can be made fresh, converted from an unused view, or used as is
	 * if it was in the recycle bin.
	 * 
	 * @param position
	 *           Logical position in the list
	 * @param x
	 *           Left or right edge of the view to add
	 * @param flow
	 *           If flow is true, align left edge to x. If false, align right edge to x.
	 * @param childrenTop
	 *           Top edge where children should be positioned
	 * @param selected
	 *           Is this position selected?
	 * @return View that was added
	 */
	private View makeAndAddView( int position, int x, boolean flow, int childrenTop, boolean selected ) {
		View child;

		if ( !mDataChanged ) {
			// Try to use an existing view for this position
			child = mRecycler.getActiveView( position );
			if ( child != null ) {
				// Found it -- we're using an existing child
				// This just needs to be positioned
				setupChild( child, position, x, flow, childrenTop, selected, true );

				return child;
			}
		}

		// Make a new view for this position, or convert an unused view if possible
		child = obtainView( position, mIsScrap );

		// This needs to be positioned and measured
		setupChild( child, position, x, flow, childrenTop, selected, mIsScrap[0] );

		return child;
	}

	/**
	 * Add a view as a child and make sure it is measured (if necessary) and positioned properly.
	 * 
	 * @param child
	 *           The view to add
	 * @param position
	 *           The position of this child
	 * @param x
	 *           The x position relative to which this view will be positioned
	 * @param flowDown
	 *           If true, align left edge to x. If false, align right edge to x.
	 * @param childrenTop
	 *           Top edge where children should be positioned
	 * @param selected
	 *           Is this position selected?
	 * @param recycled
	 *           Has this view been pulled from the recycle bin? If so it does not need to be remeasured.
	 */
	private void setupChild( View child, int position, int x, boolean flowDown, int childrenTop, boolean selected, boolean recycled ) {
		final boolean isSelected = selected && shouldShowSelector();
		final boolean updateChildSelected = isSelected != child.isSelected();
		final int mode = mTouchMode;
		final boolean isPressed = mode > TOUCH_MODE_DOWN && mode < TOUCH_MODE_SCROLL && mMotionPosition == position;
		final boolean updateChildPressed = isPressed != child.isPressed();
		final boolean needToMeasure = !recycled || updateChildSelected || child.isLayoutRequested();

		// Respect layout params that are already in the view. Otherwise make some up...
		// noinspection unchecked
		AbsHListView.LayoutParams p = (AbsHListView.LayoutParams) child.getLayoutParams();
		if ( p == null ) {
			p = (AbsHListView.LayoutParams) generateDefaultLayoutParams();
		}
		p.viewType = mAdapter.getItemViewType( position );

		if ( ( recycled && !p.forceAdd ) || ( p.recycledHeaderFooter && p.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER ) ) {
			attachViewToParent( child, flowDown ? -1 : 0, p );
		} else {
			p.forceAdd = false;
			if ( p.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER ) {
				p.recycledHeaderFooter = true;
			}
			addViewInLayout( child, flowDown ? -1 : 0, p, true );
		}

		if ( updateChildSelected ) {
			child.setSelected( isSelected );
		}

		if ( updateChildPressed ) {
			child.setPressed( isPressed );
		}

		if ( mChoiceMode != ListView.CHOICE_MODE_NONE && mCheckStates != null ) {
			if ( child instanceof Checkable ) {
				( (Checkable) child ).setChecked( mCheckStates.get( position ) );
			} else if ( android.os.Build.VERSION.SDK_INT >= 11 ) {
				child.setActivated( mCheckStates.get( position ) );
			}
		}

		if ( needToMeasure ) {
			int childHeightSpec = ViewGroup.getChildMeasureSpec( mHeightMeasureSpec, mListPadding.top + mListPadding.bottom, p.height );
			int lpWidth = p.width;
			int childWidthSpec;
			if ( lpWidth > 0 ) {
				childWidthSpec = MeasureSpec.makeMeasureSpec( lpWidth, MeasureSpec.EXACTLY );
			} else {
				childWidthSpec = MeasureSpec.makeMeasureSpec( 0, MeasureSpec.UNSPECIFIED );
			}
			child.measure( childWidthSpec, childHeightSpec );
		} else {
			cleanupLayoutState( child );
		}

		final int w = child.getMeasuredWidth();
		final int h = child.getMeasuredHeight();
		final int childLeft = flowDown ? x : x - w;

		if ( needToMeasure ) {
			final int childBottom = childrenTop + h;
			final int childRight = childLeft + w;
			child.layout( childLeft, childrenTop, childRight, childBottom );
		} else {
			child.offsetLeftAndRight( childLeft - child.getLeft() );
			child.offsetTopAndBottom( childrenTop - child.getTop() );
		}

		if ( mCachingStarted && !child.isDrawingCacheEnabled() ) {
			child.setDrawingCacheEnabled( true );
		}

		if( android.os.Build.VERSION.SDK_INT >= 11 ) {
			if ( recycled && ( ( (AbsHListView.LayoutParams) child.getLayoutParams() ).scrappedFromPosition ) != position ) {
				child.jumpDrawablesToCurrentState();
			}
		}
	}

	@Override
	protected boolean canAnimate() {
		return super.canAnimate() && mItemCount > 0;
	}

	/**
	 * Sets the currently selected item. If in touch mode, the item will not be selected but it will still be positioned
	 * appropriately. If the specified selection position is less than 0, then the item at position 0 will be selected.
	 * 
	 * @param position
	 *           Index (starting at 0) of the data item to be selected.
	 */
	@Override
	public void setSelection( int position ) {
		setSelectionFromLeft( position, 0 );
	}

	/**
	 * Sets the selected item and positions the selection x pixels from the left edge of the ListView. (If in touch mode, the item
	 * will not be selected but it will still be positioned appropriately.)
	 * 
	 * @param position
	 *           Index (starting at 0) of the data item to be selected.
	 * @param x
	 *           The distance from the left edge of the ListView (plus padding) that the item will be positioned.
	 */
	public void setSelectionFromLeft( int position, int x ) {
		if ( mAdapter == null ) {
			return;
		}

		if ( !isInTouchMode() ) {
			position = lookForSelectablePosition( position, true );
			if ( position >= 0 ) {
				setNextSelectedPositionInt( position );
			}
		} else {
			mResurrectToPosition = position;
		}

		if ( position >= 0 ) {
			mLayoutMode = LAYOUT_SPECIFIC;
			mSpecificLeft = mListPadding.left + x;

			if ( mNeedSync ) {
				mSyncPosition = position;
				mSyncColId = mAdapter.getItemId( position );
			}

			if ( mPositionScroller != null ) {
				mPositionScroller.stop();
			}
			requestLayout();
		}
	}

	/**
	 * Makes the item at the supplied position selected.
	 * 
	 * @param position
	 *           the position of the item to select
	 */
	@Override
	public void setSelectionInt( int position ) {
		setNextSelectedPositionInt( position );
		boolean awakeScrollbars = false;

		final int selectedPosition = mSelectedPosition;

		if ( selectedPosition >= 0 ) {
			if ( position == selectedPosition - 1 ) {
				awakeScrollbars = true;
			} else if ( position == selectedPosition + 1 ) {
				awakeScrollbars = true;
			}
		}

		if ( mPositionScroller != null ) {
			mPositionScroller.stop();
		}

		layoutChildren();

		if ( awakeScrollbars ) {
			awakenScrollBars();
		}
	}

	/**
	 * Find a position that can be selected (i.e., is not a separator).
	 * 
	 * @param position
	 *           The starting position to look at.
	 * @param lookDown
	 *           Whether to look down for other positions.
	 * @return The next selectable position starting at position and then searching either up or down. Returns
	 *         {@link #INVALID_POSITION} if nothing can be found.
	 */
	@Override
	protected int lookForSelectablePosition( int position, boolean lookDown ) {
		final ListAdapter adapter = mAdapter;
		if ( adapter == null || isInTouchMode() ) {
			return INVALID_POSITION;
		}

		final int count = adapter.getCount();
		if ( !mAreAllItemsSelectable ) {
			if ( lookDown ) {
				position = Math.max( 0, position );
				while ( position < count && !adapter.isEnabled( position ) ) {
					position++;
				}
			} else {
				position = Math.min( position, count - 1 );
				while ( position >= 0 && !adapter.isEnabled( position ) ) {
					position--;
				}
			}

			if ( position < 0 || position >= count ) {
				return INVALID_POSITION;
			}
			return position;
		} else {
			if ( position < 0 || position >= count ) {
				return INVALID_POSITION;
			}
			return position;
		}
	}

	/**
	 * setSelectionAfterHeaderView set the selection to be the first list item after the header views.
	 */
	public void setSelectionAfterHeaderView() {
		final int count = mHeaderViewInfos.size();
		if ( count > 0 ) {
			mNextSelectedPosition = 0;
			return;
		}

		if ( mAdapter != null ) {
			setSelection( count );
		} else {
			mNextSelectedPosition = count;
			mLayoutMode = LAYOUT_SET_SELECTION;
		}

	}

	@Override
	public boolean dispatchKeyEvent( KeyEvent event ) {
		// Dispatch in the normal way
		boolean handled = super.dispatchKeyEvent( event );
		if ( !handled ) {
			// If we didn't handle it...
			View focused = getFocusedChild();
			if ( focused != null && event.getAction() == KeyEvent.ACTION_DOWN ) {
				// ... and our focused child didn't handle it
				// ... give it to ourselves so we can scroll if necessary
				handled = onKeyDown( event.getKeyCode(), event );
			}
		}
		return handled;
	}

	@Override
	public boolean onKeyDown( int keyCode, KeyEvent event ) {
		return commonKey( keyCode, 1, event );
	}

	@Override
	public boolean onKeyMultiple( int keyCode, int repeatCount, KeyEvent event ) {
		return commonKey( keyCode, repeatCount, event );
	}

	@Override
	public boolean onKeyUp( int keyCode, KeyEvent event ) {
		return commonKey( keyCode, 1, event );
	}

	@TargetApi(11)
	private boolean commonKey( int keyCode, int count, KeyEvent event ) {
		if ( mAdapter == null || !mIsAttached ) {
			return false;
		}

		if ( mDataChanged ) {
			layoutChildren();
		}
		
		if( android.os.Build.VERSION.SDK_INT < 11 ) {
			return false;
		}

		boolean handled = false;
		int action = event.getAction();

		if ( action != KeyEvent.ACTION_UP ) {
			switch ( keyCode ) {
				case KeyEvent.KEYCODE_DPAD_UP:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded();
						if ( !handled ) {
							while ( count-- > 0 ) {
								if ( arrowScroll( FOCUS_UP ) ) {
									handled = true;
								} else {
									break;
								}
							}
						}
					} else if ( event.hasModifiers( KeyEvent.META_ALT_ON ) ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_UP );
					}
					break;

				case KeyEvent.KEYCODE_DPAD_DOWN:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded();
						if ( !handled ) {
							while ( count-- > 0 ) {
								if ( arrowScroll( FOCUS_DOWN ) ) {
									handled = true;
								} else {
									break;
								}
							}
						}
					} else if ( event.hasModifiers( KeyEvent.META_ALT_ON ) ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_DOWN );
					}
					break;

				case KeyEvent.KEYCODE_DPAD_LEFT:
					if ( event.hasNoModifiers() ) {
						handled = handleHorizontalFocusWithinListItem( View.FOCUS_LEFT );
					}
					break;

				case KeyEvent.KEYCODE_DPAD_RIGHT:
					if ( event.hasNoModifiers() ) {
						handled = handleHorizontalFocusWithinListItem( View.FOCUS_RIGHT );
					}
					break;

				case KeyEvent.KEYCODE_DPAD_CENTER:
				case KeyEvent.KEYCODE_ENTER:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded();
						if ( !handled
								&& event.getRepeatCount() == 0 && getChildCount() > 0 ) {
							keyPressed();
							handled = true;
						}
					}
					break;

				case KeyEvent.KEYCODE_SPACE:

					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded() || pageScroll( FOCUS_DOWN );
					} else if ( event.hasModifiers( KeyEvent.META_SHIFT_ON ) ) {
						handled = resurrectSelectionIfNeeded() || pageScroll( FOCUS_UP );
					}
					handled = true;
					break;

				case KeyEvent.KEYCODE_PAGE_UP:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded() || pageScroll( FOCUS_UP );
					} else if ( event.hasModifiers( KeyEvent.META_ALT_ON ) ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_UP );
					}
					break;

				case KeyEvent.KEYCODE_PAGE_DOWN:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded() || pageScroll( FOCUS_DOWN );
					} else if ( event.hasModifiers( KeyEvent.META_ALT_ON ) ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_DOWN );
					}
					break;

				case KeyEvent.KEYCODE_MOVE_HOME:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_UP );
					}
					break;

				case KeyEvent.KEYCODE_MOVE_END:
					if ( event.hasNoModifiers() ) {
						handled = resurrectSelectionIfNeeded() || fullScroll( FOCUS_DOWN );
					}
					break;

				case KeyEvent.KEYCODE_TAB:
					break;
			}
		}

		if ( handled ) {
			return true;
		}

		switch ( action ) {
			case KeyEvent.ACTION_DOWN:
				return super.onKeyDown( keyCode, event );

			case KeyEvent.ACTION_UP:
				return super.onKeyUp( keyCode, event );

			case KeyEvent.ACTION_MULTIPLE:
				return super.onKeyMultiple( keyCode, count, event );

			default: // shouldn't happen
				return false;
		}
	}

	/**
	 * Scrolls left or right by the number of items currently present on screen.
	 * 
	 * @param direction
	 *           either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
	 * @return whether selection was moved
	 */
	boolean pageScroll( int direction ) {
		int nextPage = -1;
		boolean down = false;

		if ( direction == FOCUS_UP ) { nextPage = Math.max( 0, mSelectedPosition - getChildCount() - 1 );
		} else if ( direction == FOCUS_DOWN ) {
			nextPage = Math.min( mItemCount - 1, mSelectedPosition + getChildCount() - 1 );
			down = true;
		}

		if ( nextPage >= 0 ) {
			int position = lookForSelectablePosition( nextPage, down );
			if ( position >= 0 ) {
				mLayoutMode = LAYOUT_SPECIFIC;
				mSpecificLeft = getPaddingLeft() + getHorizontalFadingEdgeLength();

				if ( down && position > mItemCount - getChildCount() ) {
					mLayoutMode = LAYOUT_FORCE_RIGHT;
				}

				if ( !down && position < getChildCount() ) {
					mLayoutMode = LAYOUT_FORCE_LEFT;
				}

				setSelectionInt( position );
				invokeOnItemScrollListener();
				if ( !awakenScrollBars() ) {
					invalidate();
				}

				return true;
			}
		}

		return false;
	}

	/**
	 * Go to the last or first item if possible (not worrying about panning across or navigating within the internal focus of the
	 * currently selected item.)
	 * 
	 * @param direction
	 *           either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
	 * 
	 * @return whether selection was moved
	 */
	boolean fullScroll( int direction ) {
		boolean moved = false;
		if ( direction == FOCUS_UP ) {
			if ( mSelectedPosition != 0 ) {
				int position = lookForSelectablePosition( 0, true );
				if ( position >= 0 ) {
					mLayoutMode = LAYOUT_FORCE_LEFT;
					setSelectionInt( position );
					invokeOnItemScrollListener();
				}
				moved = true;
			}
		} else if ( direction == FOCUS_DOWN ) {
			if ( mSelectedPosition < mItemCount - 1 ) {
				int position = lookForSelectablePosition( mItemCount - 1, true );
				if ( position >= 0 ) {
					mLayoutMode = LAYOUT_FORCE_RIGHT;
					setSelectionInt( position );
					invokeOnItemScrollListener();
				}
				moved = true;
			}
		}

		if ( moved && !awakenScrollBars() ) {
			awakenScrollBars();
			invalidate();
		}

		return moved;
	}

	/**
	 * To avoid horizontal focus searches changing the selected item, we manually focus search within the selected item (as
	 * applicable), and prevent focus from jumping to something within another item.
	 * 
	 * @param direction
	 *           one of {View.FOCUS_LEFT, View.FOCUS_RIGHT}
	 * @return Whether this consumes the key event.
	 */
	private boolean handleHorizontalFocusWithinListItem( int direction ) {
		// TODO: implement this
		if ( direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT ) {
			throw new IllegalArgumentException( "direction must be one of"
					+ " {View.FOCUS_LEFT, View.FOCUS_RIGHT}" );
		}

		final int numChildren = getChildCount();
		if ( mItemsCanFocus && numChildren > 0 && mSelectedPosition != INVALID_POSITION ) {
			final View selectedView = getSelectedView();
			if ( selectedView != null && selectedView.hasFocus() &&
					selectedView instanceof ViewGroup ) {

				final View currentFocus = selectedView.findFocus();
				final View nextFocus = FocusFinder.getInstance().findNextFocus(
						(ViewGroup) selectedView, currentFocus, direction );
				if ( nextFocus != null ) {
					// do the math to get interesting rect in next focus' coordinates
					currentFocus.getFocusedRect( mTempRect );
					offsetDescendantRectToMyCoords( currentFocus, mTempRect );
					offsetRectIntoDescendantCoords( nextFocus, mTempRect );
					if ( nextFocus.requestFocus( direction, mTempRect ) ) {
						return true;
					}
				}
				// we are blocking the key from being handled (by returning true)
				// if the global result is going to be some other view within this
				// list. this is to acheive the overall goal of having
				// horizontal d-pad navigation remain in the current item.
				final View globalNextFocus = FocusFinder.getInstance().findNextFocus(
						(ViewGroup) getRootView(), currentFocus, direction );
				if ( globalNextFocus != null ) {
					return isViewAncestorOf( globalNextFocus, this );
				}
			}
		}
		return false;
	}

	/**
	 * Scrolls to the next or previous item if possible.
	 * 
	 * @param direction
	 *           either {@link View#FOCUS_UP} or {@link View#FOCUS_DOWN}
	 * 
	 * @return whether selection was moved
	 */
	boolean arrowScroll( int direction ) {
		try {
			mInLayout = true;
			final boolean handled = arrowScrollImpl( direction );
			if ( handled ) {
				playSoundEffect( SoundEffectConstants.getContantForFocusDirection( direction ) );
			}
			return handled;
		} finally {
			mInLayout = false;
		}
	}

	/**
	 * Handle an arrow scroll going up or down. Take into account whether items are selectable, whether there are focusable items
	 * etc.
	 * 
	 * @param direction
	 *           Either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @return Whether any scrolling, selection or focus change occured.
	 */
	private boolean arrowScrollImpl( int direction ) {
		if ( getChildCount() <= 0 ) {
			return false;
		}

		View selectedView = getSelectedView();
		int selectedPos = mSelectedPosition;

		int nextSelectedPosition = lookForSelectablePositionOnScreen( direction );
		int amountToScroll = amountToScroll( direction, nextSelectedPosition );

		// if we are moving focus, we may OVERRIDE the default behavior
		final ArrowScrollFocusResult focusResult = mItemsCanFocus ? arrowScrollFocused( direction ) : null;
		if ( focusResult != null ) {
			nextSelectedPosition = focusResult.getSelectedPosition();
			amountToScroll = focusResult.getAmountToScroll();
		}

		boolean needToRedraw = focusResult != null;
		if ( nextSelectedPosition != INVALID_POSITION ) {
			handleNewSelectionChange( selectedView, direction, nextSelectedPosition, focusResult != null );
			setSelectedPositionInt( nextSelectedPosition );
			setNextSelectedPositionInt( nextSelectedPosition );
			selectedView = getSelectedView();
			selectedPos = nextSelectedPosition;
			if ( mItemsCanFocus && focusResult == null ) {
				// there was no new view found to take focus, make sure we
				// don't leave focus with the old selection
				final View focused = getFocusedChild();
				if ( focused != null ) {
					focused.clearFocus();
				}
			}
			needToRedraw = true;
			checkSelectionChanged();
		}

		if ( amountToScroll > 0 ) {
			scrollListItemsBy( ( direction == View.FOCUS_UP ) ? amountToScroll : -amountToScroll );
			needToRedraw = true;
		}

		// if we didn't find a new focusable, make sure any existing focused
		// item that was panned off screen gives up focus.
		if ( mItemsCanFocus && ( focusResult == null ) && selectedView != null && selectedView.hasFocus() ) {
			final View focused = selectedView.findFocus();
			if ( !isViewAncestorOf( focused, this ) || distanceToView( focused ) > 0 ) {
				focused.clearFocus();
			}
		}

		// if the current selection is panned off, we need to remove the selection
		if ( nextSelectedPosition == INVALID_POSITION && selectedView != null && !isViewAncestorOf( selectedView, this ) ) {
			selectedView = null;
			hideSelector();

			// but we don't want to set the ressurect position (that would make subsequent
			// unhandled key events bring back the item we just scrolled off!)
			mResurrectToPosition = INVALID_POSITION;
		}

		if ( needToRedraw ) {
			if ( selectedView != null ) {
				positionSelector( selectedPos, selectedView );
				mSelectedLeft = selectedView.getLeft();
			}
			if ( !awakenScrollBars() ) {
				invalidate();
			}
			invokeOnItemScrollListener();
			return true;
		}

		return false;
	}

	/**
	 * When selection changes, it is possible that the previously selected or the next selected item will change its size. If so, we
	 * need to offset some folks, and re-layout the items as appropriate.
	 * 
	 * @param selectedView
	 *           The currently selected view (before changing selection). should be <code>null</code> if there was no previous
	 *           selection.
	 * @param direction
	 *           Either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @param newSelectedPosition
	 *           The position of the next selection.
	 * @param newFocusAssigned
	 *           whether new focus was assigned. This matters because when something has focus, we don't want to show selection
	 *           (ugh).
	 */
	private void handleNewSelectionChange( View selectedView, int direction, int newSelectedPosition,
			boolean newFocusAssigned ) {
		if ( newSelectedPosition == INVALID_POSITION ) {
			throw new IllegalArgumentException( "newSelectedPosition needs to be valid" );
		}

		// whether or not we are moving down or up, we want to preserve the
		// top of whatever view is on top:
		// - moving down: the view that had selection
		// - moving up: the view that is getting selection
		View leftView;
		View rightView;
		int leftViewIndex, rightViewIndex;
		boolean leftSelected = false;
		final int selectedIndex = mSelectedPosition - mFirstPosition;
		final int nextSelectedIndex = newSelectedPosition - mFirstPosition;
		
		if ( direction == View.FOCUS_UP ) {
			leftViewIndex = nextSelectedIndex;
			rightViewIndex = selectedIndex;
			leftView = getChildAt( leftViewIndex );
			rightView = selectedView;
			leftSelected = true;
		} else {
			leftViewIndex = selectedIndex;
			rightViewIndex = nextSelectedIndex;
			leftView = selectedView;
			rightView = getChildAt( rightViewIndex );
		}

		final int numChildren = getChildCount();

		// start with top view: is it changing size?
		if ( leftView != null ) {
			leftView.setSelected( !newFocusAssigned && leftSelected );
			measureAndAdjustRight( leftView, leftViewIndex, numChildren );
		}

		// is the bottom view changing size?
		if ( rightView != null ) {
			rightView.setSelected( !newFocusAssigned && !leftSelected );
			measureAndAdjustRight( rightView, rightViewIndex, numChildren );
		}
	}

	/**
	 * Re-measure a child, and if its height changes, lay it out preserving its top, and adjust the children below it appropriately.
	 * 
	 * @param child
	 *           The child
	 * @param childIndex
	 *           The view group index of the child.
	 * @param numChildren
	 *           The number of children in the view group.
	 */
	private void measureAndAdjustRight( View child, int childIndex, int numChildren ) {
		int oldWidth = child.getWidth();
		measureItem( child );
		if ( child.getMeasuredWidth() != oldWidth ) {
			// lay out the view, preserving its top
			relayoutMeasuredItem( child );

			// adjust views below appropriately
			final int widthDelta = child.getMeasuredWidth() - oldWidth;
			for ( int i = childIndex + 1; i < numChildren; i++ ) {
				getChildAt( i ).offsetLeftAndRight( widthDelta );
			}
		}
	}

	/**
	 * Measure a particular list child. TODO: unify with setUpChild.
	 * 
	 * @param child
	 *           The child.
	 */
	private void measureItem( View child ) {
		ViewGroup.LayoutParams p = child.getLayoutParams();
		if ( p == null ) {
			p = new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.MATCH_PARENT );
		}

		int childHeightSpec = ViewGroup.getChildMeasureSpec( mHeightMeasureSpec, mListPadding.top + mListPadding.bottom, p.height );
		int lpWidth = p.width;
		int childWidthSpec;
		if ( lpWidth > 0 ) {
			childWidthSpec = MeasureSpec.makeMeasureSpec( lpWidth, MeasureSpec.EXACTLY );
		} else {
			childWidthSpec = MeasureSpec.makeMeasureSpec( 0, MeasureSpec.UNSPECIFIED );
		}
		child.measure( childWidthSpec, childHeightSpec );
	}

	/**
	 * Layout a child that has been measured, preserving its top position. TODO: unify with setUpChild.
	 * 
	 * @param child
	 *           The child.
	 */
	private void relayoutMeasuredItem( View child ) {
		final int w = child.getMeasuredWidth();
		final int h = child.getMeasuredHeight();
		
		final int childTop = mListPadding.top;
		final int childBottom = childTop + h;
		
		final int childLeft = child.getLeft();
		final int childRight = childLeft + w;
		
		child.layout( childLeft, childTop, childRight, childBottom );
	}

	/**
	 * @return The amount to preview next items when arrow srolling.
	 */
	private int getArrowScrollPreviewLength() {
		return Math.max( MIN_SCROLL_PREVIEW_PIXELS, getHorizontalFadingEdgeLength() );
	}

	/**
	 * Determine how much we need to scroll in order to get the next selected view visible, with a fading edge showing below as
	 * applicable. The amount is capped at {@link #getMaxScrollAmount()} .
	 * 
	 * @param direction
	 *           either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @param nextSelectedPosition
	 *           The position of the next selection, or {@link #INVALID_POSITION} if there is no next selectable position
	 * @return The amount to scroll. Note: this is always positive! Direction needs to be taken into account when actually scrolling.
	 */
	private int amountToScroll( int direction, int nextSelectedPosition ) {
		final int listRight = getWidth() - mListPadding.right;
		final int listLeft = mListPadding.left;

		final int numChildren = getChildCount();

		if ( direction == View.FOCUS_DOWN ) {
			int indexToMakeVisible = numChildren - 1;
			if ( nextSelectedPosition != INVALID_POSITION ) {
				indexToMakeVisible = nextSelectedPosition - mFirstPosition;
			}

			final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
			final View viewToMakeVisible = getChildAt( indexToMakeVisible );

			int goalRight = listRight;
			if ( positionToMakeVisible < mItemCount - 1 ) {
				goalRight -= getArrowScrollPreviewLength();
			}

			if ( viewToMakeVisible.getRight() <= goalRight ) {
				// item is fully visible.
				return 0;
			}

			if ( nextSelectedPosition != INVALID_POSITION
					&& ( goalRight - viewToMakeVisible.getLeft() ) >= getMaxScrollAmount() ) {
				// item already has enough of it visible, changing selection is good enough
				return 0;
			}

			int amountToScroll = ( viewToMakeVisible.getRight() - goalRight );

			if ( ( mFirstPosition + numChildren ) == mItemCount ) {
				// last is last in list -> make sure we don't scroll past it
				final int max = getChildAt( numChildren - 1 ).getRight() - listRight;
				amountToScroll = Math.min( amountToScroll, max );
			}

			return Math.min( amountToScroll, getMaxScrollAmount() );
		} else {
			int indexToMakeVisible = 0;
			if ( nextSelectedPosition != INVALID_POSITION ) {
				indexToMakeVisible = nextSelectedPosition - mFirstPosition;
			}
			final int positionToMakeVisible = mFirstPosition + indexToMakeVisible;
			final View viewToMakeVisible = getChildAt( indexToMakeVisible );
			int goalLeft = listLeft;
			if ( positionToMakeVisible > 0 ) {
				goalLeft += getArrowScrollPreviewLength();
			}
			if ( viewToMakeVisible.getLeft() >= goalLeft ) {
				// item is fully visible.
				return 0;
			}

			if ( nextSelectedPosition != INVALID_POSITION &&
					( viewToMakeVisible.getRight() - goalLeft ) >= getMaxScrollAmount() ) {
				// item already has enough of it visible, changing selection is good enough
				return 0;
			}

			int amountToScroll = ( goalLeft - viewToMakeVisible.getLeft() );
			if ( mFirstPosition == 0 ) {
				// first is first in list -> make sure we don't scroll past it
				final int max = listLeft - getChildAt( 0 ).getLeft();
				amountToScroll = Math.min( amountToScroll, max );
			}
			return Math.min( amountToScroll, getMaxScrollAmount() );
		}
	}

	/**
	 * Holds results of focus aware arrow scrolling.
	 */
	static private class ArrowScrollFocusResult {

		private int mSelectedPosition;
		private int mAmountToScroll;

		/**
		 * How {@link it.sephiroth.android.library.widget.HListView#arrowScrollFocused} returns its values.
		 */
		void populate( int selectedPosition, int amountToScroll ) {
			mSelectedPosition = selectedPosition;
			mAmountToScroll = amountToScroll;
		}

		public int getSelectedPosition() {
			return mSelectedPosition;
		}

		public int getAmountToScroll() {
			return mAmountToScroll;
		}
	}

	/**
	 * @param direction
	 *           either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @return The position of the next selectable position of the views that are currently visible, taking into account the fact
	 *         that there might be no selection. Returns {@link #INVALID_POSITION} if there is no selectable view on screen in the
	 *         given direction.
	 */
	private int lookForSelectablePositionOnScreen( int direction ) {
		final int firstPosition = mFirstPosition;
		if ( direction == View.FOCUS_DOWN ) {
			int startPos = ( mSelectedPosition != INVALID_POSITION ) ?
					mSelectedPosition + 1 :
					firstPosition;
			if ( startPos >= mAdapter.getCount() ) {
				return INVALID_POSITION;
			}
			if ( startPos < firstPosition ) {
				startPos = firstPosition;
			}

			final int lastVisiblePos = getLastVisiblePosition();
			final ListAdapter adapter = getAdapter();
			for ( int pos = startPos; pos <= lastVisiblePos; pos++ ) {
				if ( adapter.isEnabled( pos )
						&& getChildAt( pos - firstPosition ).getVisibility() == View.VISIBLE ) {
					return pos;
				}
			}
		} else {
			int last = firstPosition + getChildCount() - 1;
			int startPos = ( mSelectedPosition != INVALID_POSITION ) ?
					mSelectedPosition - 1 :
					firstPosition + getChildCount() - 1;
			if ( startPos < 0 || startPos >= mAdapter.getCount() ) {
				return INVALID_POSITION;
			}
			if ( startPos > last ) {
				startPos = last;
			}

			final ListAdapter adapter = getAdapter();
			for ( int pos = startPos; pos >= firstPosition; pos-- ) {
				if ( adapter.isEnabled( pos )
						&& getChildAt( pos - firstPosition ).getVisibility() == View.VISIBLE ) {
					return pos;
				}
			}
		}
		return INVALID_POSITION;
	}

	/**
	 * Do an arrow scroll based on focus searching. If a new view is given focus, return the selection delta and amount to scroll via
	 * an {@link ArrowScrollFocusResult}, otherwise, return null.
	 * 
	 * @param direction
	 *           either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @return The result if focus has changed, or <code>null</code>.
	 */
	private ArrowScrollFocusResult arrowScrollFocused( final int direction ) {
		final View selectedView = getSelectedView();
		View newFocus;
		if ( selectedView != null && selectedView.hasFocus() ) {
			View oldFocus = selectedView.findFocus();
			newFocus = FocusFinder.getInstance().findNextFocus( this, oldFocus, direction );
		} else {
			if ( direction == View.FOCUS_DOWN ) {
				final boolean leftFadingEdgeShowing = ( mFirstPosition > 0 );
				final int listLeft = mListPadding.left +
						( leftFadingEdgeShowing ? getArrowScrollPreviewLength() : 0 );
				final int xSearchPoint =
						( selectedView != null && selectedView.getLeft() > listLeft ) ?
								selectedView.getLeft() :
								listLeft;
				mTempRect.set( xSearchPoint, 0, xSearchPoint, 0 );
			} else {
				final boolean rightFadingEdgeShowing =
						( mFirstPosition + getChildCount() - 1 ) < mItemCount;
				final int listRight = getWidth() - mListPadding.right -
						( rightFadingEdgeShowing ? getArrowScrollPreviewLength() : 0 );
				final int xSearchPoint =
						( selectedView != null && selectedView.getRight() < listRight ) ?
								selectedView.getRight() :
								listRight;
				mTempRect.set( xSearchPoint, 0, xSearchPoint, 0 );
			}
			newFocus = FocusFinder.getInstance().findNextFocusFromRect( this, mTempRect, direction );
		}

		if ( newFocus != null ) {
			final int positionOfNewFocus = positionOfNewFocus( newFocus );

			// if the focus change is in a different new position, make sure
			// we aren't jumping over another selectable position
			if ( mSelectedPosition != INVALID_POSITION && positionOfNewFocus != mSelectedPosition ) {
				final int selectablePosition = lookForSelectablePositionOnScreen( direction );
				if ( selectablePosition != INVALID_POSITION &&
						( ( direction == View.FOCUS_DOWN && selectablePosition < positionOfNewFocus ) ||
						( direction == View.FOCUS_UP && selectablePosition > positionOfNewFocus ) ) ) {
					return null;
				}
			}

			int focusScroll = amountToScrollToNewFocus( direction, newFocus, positionOfNewFocus );

			final int maxScrollAmount = getMaxScrollAmount();
			if ( focusScroll < maxScrollAmount ) {
				// not moving too far, safe to give next view focus
				newFocus.requestFocus( direction );
				mArrowScrollFocusResult.populate( positionOfNewFocus, focusScroll );
				return mArrowScrollFocusResult;
			} else if ( distanceToView( newFocus ) < maxScrollAmount ) {
				// Case to consider:
				// too far to get entire next focusable on screen, but by going
				// max scroll amount, we are getting it at least partially in view,
				// so give it focus and scroll the max ammount.
				newFocus.requestFocus( direction );
				mArrowScrollFocusResult.populate( positionOfNewFocus, maxScrollAmount );
				return mArrowScrollFocusResult;
			}
		}
		return null;
	}

	/**
	 * @param newFocus
	 *           The view that would have focus.
	 * @return the position that contains newFocus
	 */
	private int positionOfNewFocus( View newFocus ) {
		final int numChildren = getChildCount();
		for ( int i = 0; i < numChildren; i++ ) {
			final View child = getChildAt( i );
			if ( isViewAncestorOf( newFocus, child ) ) {
				return mFirstPosition + i;
			}
		}
		throw new IllegalArgumentException( "newFocus is not a child of any of the"
				+ " children of the list!" );
	}

	/**
	 * Return true if child is an ancestor of parent, (or equal to the parent).
	 */
	private boolean isViewAncestorOf( View child, View parent ) {
		if ( child == parent ) {
			return true;
		}

		final ViewParent theParent = child.getParent();
		return ( theParent instanceof ViewGroup ) && isViewAncestorOf( (View) theParent, parent );
	}

	/**
	 * Determine how much we need to scroll in order to get newFocus in view.
	 * 
	 * @param direction
	 *           either {@link android.view.View#FOCUS_UP} or {@link android.view.View#FOCUS_DOWN}.
	 * @param newFocus
	 *           The view that would take focus.
	 * @param positionOfNewFocus
	 *           The position of the list item containing newFocus
	 * @return The amount to scroll. Note: this is always positive! Direction needs to be taken into account when actually scrolling.
	 */
	private int amountToScrollToNewFocus( int direction, View newFocus, int positionOfNewFocus ) {
		int amountToScroll = 0;
		newFocus.getDrawingRect( mTempRect );
		offsetDescendantRectToMyCoords( newFocus, mTempRect );
		if ( direction == View.FOCUS_UP ) {
			if ( mTempRect.left < mListPadding.left ) {
				amountToScroll = mListPadding.left - mTempRect.left;
				if ( positionOfNewFocus > 0 ) {
					amountToScroll += getArrowScrollPreviewLength();
				}
			}
		} else {
			final int listRight = getWidth() - mListPadding.right;
			if ( mTempRect.bottom > listRight ) {
				amountToScroll = mTempRect.right - listRight;
				if ( positionOfNewFocus < mItemCount - 1 ) {
					amountToScroll += getArrowScrollPreviewLength();
				}
			}
		}
		return amountToScroll;
	}

	/**
	 * Determine the distance to the nearest edge of a view in a particular direction.
	 * 
	 * @param descendant
	 *           A descendant of this list.
	 * @return The distance, or 0 if the nearest edge is already on screen.
	 */
	private int distanceToView( View descendant ) {
		int distance = 0;
		descendant.getDrawingRect( mTempRect );
		offsetDescendantRectToMyCoords( descendant, mTempRect );
		final int listRight = getRight() - getLeft() - mListPadding.right;
		if ( mTempRect.right < mListPadding.left ) {
			distance = mListPadding.left - mTempRect.right;
		} else if ( mTempRect.left > listRight ) {
			distance = mTempRect.left - listRight;
		}
		return distance;
	}

	/**
	 * Scroll the children by amount, adding a view at the end and removing views that fall off as necessary.
	 * 
	 * @param amount
	 *           The amount (positive or negative) to scroll.
	 */
	private void scrollListItemsBy( int amount ) {
		offsetChildrenLeftAndRight( amount );

		final int listRight = getWidth() - mListPadding.right;
		final int listLeft = mListPadding.left;
		final AbsHListView.RecycleBin recycleBin = mRecycler;

		if ( amount < 0 ) {
			// shifted items up

			// may need to pan views into the bottom space
			int numChildren = getChildCount();
			View last = getChildAt( numChildren - 1 );
			while ( last.getRight() < listRight ) {
				final int lastVisiblePosition = mFirstPosition + numChildren - 1;
				if ( lastVisiblePosition < mItemCount - 1 ) {
					last = addViewAfter( last, lastVisiblePosition );
					numChildren++;
				} else {
					break;
				}
			}

			// may have brought in the last child of the list that is skinnier
			// than the fading edge, thereby leaving space at the end. need
			// to shift back
			if ( last.getBottom() < listRight ) {
				offsetChildrenLeftAndRight( listRight - last.getRight() );
			}

			// top views may be panned off screen
			View first = getChildAt( 0 );
			while ( first.getRight() < listLeft ) {
				AbsHListView.LayoutParams layoutParams = (LayoutParams) first.getLayoutParams();
				if ( recycleBin.shouldRecycleViewType( layoutParams.viewType ) ) {
					detachViewFromParent( first );
					recycleBin.addScrapView( first, mFirstPosition );
				} else {
					removeViewInLayout( first );
				}
				first = getChildAt( 0 );
				mFirstPosition++;
			}
		} else {
			// shifted items down
			View first = getChildAt( 0 );

			// may need to pan views into top
			while ( ( first.getLeft() > listLeft ) && ( mFirstPosition > 0 ) ) {
				first = addViewBefore( first, mFirstPosition );
				mFirstPosition--;
			}

			// may have brought the very first child of the list in too far and
			// need to shift it back
			if ( first.getLeft() > listLeft ) {
				offsetChildrenLeftAndRight( listLeft - first.getLeft() );
			}

			int lastIndex = getChildCount() - 1;
			View last = getChildAt( lastIndex );

			// bottom view may be panned off screen
			while ( last.getLeft() > listRight ) {
				AbsHListView.LayoutParams layoutParams = (LayoutParams) last.getLayoutParams();
				if ( recycleBin.shouldRecycleViewType( layoutParams.viewType ) ) {
					detachViewFromParent( last );
					recycleBin.addScrapView( last, mFirstPosition + lastIndex );
				} else {
					removeViewInLayout( last );
				}
				last = getChildAt( --lastIndex );
			}
		}
	}

	private View addViewBefore( View theView, int position ) {
		int abovePosition = position - 1;
		View view = obtainView( abovePosition, mIsScrap );
		int edgeOfNewChild = theView.getLeft() - mDividerWidth;
		setupChild( view, abovePosition, edgeOfNewChild, false, mListPadding.top, false, mIsScrap[0] );
		return view;
	}

	private View addViewAfter( View theView, int position ) {
		int belowPosition = position + 1;
		View view = obtainView( belowPosition, mIsScrap );
		int edgeOfNewChild = theView.getRight() + mDividerWidth;
		setupChild( view, belowPosition, edgeOfNewChild, true, mListPadding.top, false, mIsScrap[0] );
		return view;
	}

	/**
	 * Indicates that the views created by the ListAdapter can contain focusable items.
	 * 
	 * @param itemsCanFocus
	 *           true if items can get focus, false otherwise
	 */
	public void setItemsCanFocus( boolean itemsCanFocus ) {
		mItemsCanFocus = itemsCanFocus;
		if ( !itemsCanFocus ) {
			setDescendantFocusability( ViewGroup.FOCUS_BLOCK_DESCENDANTS );
		}
	}

	/**
	 * @return Whether the views created by the ListAdapter can contain focusable items.
	 */
	public boolean getItemsCanFocus() {
		return mItemsCanFocus;
	}

	@Override
	public boolean isOpaque() {
		boolean retValue = ( mCachingActive && mIsCacheColorOpaque && mDividerIsOpaque ) || super.isOpaque();
		if ( retValue ) {
			// only return true if the list items cover the entire area of the view
			final int listLeft = mListPadding != null ? mListPadding.left : getPaddingLeft();
			View first = getChildAt( 0 );
			if ( first == null || first.getLeft() > listLeft ) {
				return false;
			}
			final int listRight = getWidth() - ( mListPadding != null ? mListPadding.right: getPaddingRight() );
			View last = getChildAt( getChildCount() - 1 );
			if ( last == null || last.getRight() < listRight ) {
				return false;
			}
		}
		return retValue;
	}

	@Override
	public void setCacheColorHint( int color ) {
		final boolean opaque = ( color >>> 24 ) == 0xFF;
		mIsCacheColorOpaque = opaque;
		if ( opaque ) {
			if ( mDividerPaint == null ) {
				mDividerPaint = new Paint();
			}
			mDividerPaint.setColor( color );
		}
		super.setCacheColorHint( color );
	}

	void drawOverscrollHeader( Canvas canvas, Drawable drawable, Rect bounds ) {
		final int width = drawable.getMinimumWidth();

		canvas.save();
		canvas.clipRect( bounds );

		final int span = bounds.right - bounds.left;
		if ( span < width ) {
			bounds.left = bounds.right - width;
		}

		drawable.setBounds( bounds );
		drawable.draw( canvas );

		canvas.restore();
	}

	void drawOverscrollFooter( Canvas canvas, Drawable drawable, Rect bounds ) {
		final int width = drawable.getMinimumWidth();

		canvas.save();
		canvas.clipRect( bounds );

		final int span = bounds.right - bounds.left;
		if ( span < width ) {
			bounds.right = bounds.left + width;
		}

		drawable.setBounds( bounds );
		drawable.draw( canvas );

		canvas.restore();
	}

	@Override
	protected void dispatchDraw( Canvas canvas ) {
		if ( mCachingStarted ) {
			mCachingActive = true;
		}

		// Draw the dividers
		final int dividerWidth = mDividerWidth;
		final Drawable overscrollHeader = mOverScrollHeader;
		final Drawable overscrollFooter = mOverScrollFooter;
		final boolean drawOverscrollHeader = overscrollHeader != null;
		final boolean drawOverscrollFooter = overscrollFooter != null;
		final boolean drawDividers = dividerWidth > 0 && mDivider != null;

		if ( drawDividers || drawOverscrollHeader || drawOverscrollFooter ) {
			// Only modify the top and bottom in the loop, we set the left and right here
			final Rect bounds = mTempRect;
			bounds.top = getPaddingTop();
			bounds.bottom = getBottom() - getTop() - getPaddingBottom();

			final int count = getChildCount();
			final int headerCount = mHeaderViewInfos.size();
			final int itemCount = mItemCount;
			final int footerLimit = itemCount - mFooterViewInfos.size() - 1;
			final boolean headerDividers = mHeaderDividersEnabled;
			final boolean footerDividers = mFooterDividersEnabled;
			final int first = mFirstPosition;
			final boolean areAllItemsSelectable = mAreAllItemsSelectable;
			final ListAdapter adapter = mAdapter;
			// If the list is opaque *and* the background is not, we want to
			// fill a rect where the dividers would be for non-selectable items
			// If the list is opaque and the background is also opaque, we don't
			// need to draw anything since the background will do it for us
			final boolean fillForMissingDividers = isOpaque() && !super.isOpaque();

			if ( fillForMissingDividers && mDividerPaint == null && mIsCacheColorOpaque ) {
				mDividerPaint = new Paint();
				mDividerPaint.setColor( getCacheColorHint() );
			}
			final Paint paint = mDividerPaint;

			int effectivePaddingLeft = 0;
			int effectivePaddingRight = 0;
			// if ( ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ) {
			// effectivePaddingTop = mListPadding.top;
			// effectivePaddingBottom = mListPadding.bottom;
			// }

			final int listRight = getRight() - getLeft() - effectivePaddingRight + getScrollX();
			if ( !mStackFromRight ) {
				int right = 0;

				// Draw top divider or header for overscroll
				final int scrollX = getScrollX();
				if ( count > 0 && scrollX < 0 ) {
					if ( drawOverscrollHeader ) {
						bounds.right = 0;
						bounds.left = scrollX;
						drawOverscrollHeader( canvas, overscrollHeader, bounds );
					} else if ( drawDividers ) {
						bounds.right = 0;
						bounds.left = -dividerWidth;
						drawDivider( canvas, bounds, -1 );
					}
				}

				for ( int i = 0; i < count; i++ ) {
					if ( ( headerDividers || first + i >= headerCount ) &&
							( footerDividers || first + i < footerLimit ) ) {
						View child = getChildAt( i );
						right = child.getRight();
						// Don't draw dividers next to items that are not enabled

						if ( drawDividers &&
								( right < listRight && !( drawOverscrollFooter && i == count - 1 ) ) ) {
							if ( ( areAllItemsSelectable || ( adapter.isEnabled( first + i ) && ( i == count - 1 || adapter
									.isEnabled( first + i + 1 ) ) ) ) ) {
								bounds.left = right;
								bounds.right = right + dividerWidth;
								drawDivider( canvas, bounds, i );
							} else if ( fillForMissingDividers ) {
								bounds.left = right;
								bounds.right = right + dividerWidth;
								canvas.drawRect( bounds, paint );
							}
						}
					}
				}

				final int overFooterBottom = getRight() + getScrollX();
				if ( drawOverscrollFooter && first + count == itemCount &&
						overFooterBottom > right ) {
					bounds.left = right;
					bounds.right = overFooterBottom;
					drawOverscrollFooter( canvas, overscrollFooter, bounds );
				}
			} else {
				int left;

				final int scrollX = getScrollX();

				if ( count > 0 && drawOverscrollHeader ) {
					bounds.left = scrollX;
					bounds.right = getChildAt( 0 ).getLeft();
					drawOverscrollHeader( canvas, overscrollHeader, bounds );
				}

				final int start = drawOverscrollHeader ? 1 : 0;
				for ( int i = start; i < count; i++ ) {
					if ( ( headerDividers || first + i >= headerCount ) &&
							( footerDividers || first + i < footerLimit ) ) {
						View child = getChildAt( i );
						left = child.getLeft();
						// Don't draw dividers next to items that are not enabled
						if ( left > effectivePaddingLeft ) {
							if ( ( areAllItemsSelectable || ( adapter.isEnabled( first + i ) && ( i == count - 1 || adapter
									.isEnabled( first + i + 1 ) ) ) ) ) {
								bounds.left = left - dividerWidth;
								bounds.right = left;
								// Give the method the child ABOVE the divider, so we
								// subtract one from our child
								// position. Give -1 when there is no child above the
								// divider.
								drawDivider( canvas, bounds, i - 1 );
							} else if ( fillForMissingDividers ) {
								bounds.left = left - dividerWidth;
								bounds.right = left;
								canvas.drawRect( bounds, paint );
							}
						}
					}
				}

				if ( count > 0 && scrollX > 0 ) {
					if ( drawOverscrollFooter ) {
						final int absListRight = getRight();
						bounds.left = absListRight;
						bounds.right = absListRight + scrollX;
						drawOverscrollFooter( canvas, overscrollFooter, bounds );
					} else if ( drawDividers ) {
						bounds.left = listRight;
						bounds.right = listRight + dividerWidth;
						drawDivider( canvas, bounds, -1 );
					}
				}
			}
		}

		// Draw the indicators (these should be drawn above the dividers) and children
		super.dispatchDraw( canvas );
	}

	@Override
	protected boolean drawChild( Canvas canvas, View child, long drawingTime ) {
		boolean more = super.drawChild( canvas, child, drawingTime );
		if ( mCachingActive /* && child.mCachingFailed */) {
			mCachingActive = false;
		}
		return more;
	}

	/**
	 * Draws a divider for the given child in the given bounds.
	 * 
	 * @param canvas
	 *           The canvas to draw to.
	 * @param bounds
	 *           The bounds of the divider.
	 * @param childIndex
	 *           The index of child (of the View) above the divider. This will be -1 if there is no child above the divider to be
	 *           drawn.
	 */
	void drawDivider( Canvas canvas, Rect bounds, int childIndex ) {
		// This widget draws the same divider for all children
		final Drawable divider = mDivider;

		divider.setBounds( bounds );
		divider.draw( canvas );
	}

	/**
	 * Returns the drawable that will be drawn between each item in the list.
	 * 
	 * @return the current drawable drawn between list elements
	 */
	public Drawable getDivider() {
		return mDivider;
	}

	/**
	 * Sets the drawable that will be drawn between each item in the list. If the drawable does not have an intrinsic height, you
	 * should also call {@link #setDividerHeight(int)}
	 * 
	 * @param divider
	 *           The drawable to use.
	 */
	public void setDivider( Drawable divider ) {
		Log.i( LOG_TAG, "setDivider: " + divider );
		
		if ( divider != null ) {
			mDividerWidth = divider.getIntrinsicWidth();
		} else {
			mDividerWidth = 0;
		}
		mDivider = divider;
		mDividerIsOpaque = divider == null || divider.getOpacity() == PixelFormat.OPAQUE;
		requestLayout();
		invalidate();
	}

	/**
	 * @return Returns the height of the divider that will be drawn between each item in the list.
	 */
	public int getDividerWidth() {
		return mDividerWidth;
	}

	/**
	 * Sets the height of the divider that will be drawn between each item in the list. Calling this will override the intrinsic
	 * height as set by {@link #setDivider(Drawable)}
	 * 
	 * @param height
	 *           The new height of the divider in pixels.
	 */
	public void setDividerWidth( int width ) {
		Log.i( LOG_TAG, "setDividerWidth: " + width );
		mDividerWidth = width;
		requestLayout();
		invalidate();
	}

	/**
	 * Enables or disables the drawing of the divider for header views.
	 * 
	 * @param headerDividersEnabled
	 *           True to draw the headers, false otherwise.
	 * 
	 * @see #setFooterDividersEnabled(boolean)
	 * @see #addHeaderView(android.view.View)
	 */
	public void setHeaderDividersEnabled( boolean headerDividersEnabled ) {
		mHeaderDividersEnabled = headerDividersEnabled;
		invalidate();
	}

	/**
	 * Enables or disables the drawing of the divider for footer views.
	 * 
	 * @param footerDividersEnabled
	 *           True to draw the footers, false otherwise.
	 * 
	 * @see #setHeaderDividersEnabled(boolean)
	 * @see #addFooterView(android.view.View)
	 */
	public void setFooterDividersEnabled( boolean footerDividersEnabled ) {
		mFooterDividersEnabled = footerDividersEnabled;
		invalidate();
	}

	/**
	 * Sets the drawable that will be drawn above all other list content. This area can become visible when the user overscrolls the
	 * list.
	 * 
	 * @param header
	 *           The drawable to use
	 */
	public void setOverscrollHeader( Drawable header ) {
		mOverScrollHeader = header;
		if ( getScrollX() < 0 ) {
			invalidate();
		}
	}

	/**
	 * @return The drawable that will be drawn above all other list content
	 */
	public Drawable getOverscrollHeader() {
		return mOverScrollHeader;
	}

	/**
	 * Sets the drawable that will be drawn below all other list content. This area can become visible when the user overscrolls the
	 * list, or when the list's content does not fully fill the container area.
	 * 
	 * @param footer
	 *           The drawable to use
	 */
	public void setOverscrollFooter( Drawable footer ) {
		mOverScrollFooter = footer;
		invalidate();
	}

	/**
	 * @return The drawable that will be drawn below all other list content
	 */
	public Drawable getOverscrollFooter() {
		return mOverScrollFooter;
	}

	@Override
	protected void onFocusChanged( boolean gainFocus, int direction, Rect previouslyFocusedRect ) {
		super.onFocusChanged( gainFocus, direction, previouslyFocusedRect );

		final ListAdapter adapter = mAdapter;
		int closetChildIndex = -1;
		int closestChildLeft = 0;
		if ( adapter != null && gainFocus && previouslyFocusedRect != null ) {
			previouslyFocusedRect.offset( getScrollX(), getScrollY() );

			// Don't cache the result of getChildCount or mFirstPosition here,
			// it could change in layoutChildren.
			if ( adapter.getCount() < getChildCount() + mFirstPosition ) {
				mLayoutMode = LAYOUT_NORMAL;
				layoutChildren();
			}

			// figure out which item should be selected based on previously
			// focused rect
			Rect otherRect = mTempRect;
			int minDistance = Integer.MAX_VALUE;
			final int childCount = getChildCount();
			final int firstPosition = mFirstPosition;

			for ( int i = 0; i < childCount; i++ ) {
				// only consider selectable views
				if ( !adapter.isEnabled( firstPosition + i ) ) {
					continue;
				}

				View other = getChildAt( i );
				other.getDrawingRect( otherRect );
				offsetDescendantRectToMyCoords( other, otherRect );
				int distance = getDistance( previouslyFocusedRect, otherRect, direction );

				if ( distance < minDistance ) {
					minDistance = distance;
					closetChildIndex = i;
					closestChildLeft = other.getLeft();
				}
			}
		}

		if ( closetChildIndex >= 0 ) {
			setSelectionFromLeft( closetChildIndex + mFirstPosition, closestChildLeft );
		} else {
			requestLayout();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * Children specified in XML are assumed to be header views. After we have parsed them move them out of the children list and
	 * into mHeaderViews.
	 */
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		int count = getChildCount();
		if ( count > 0 ) {
			for ( int i = 0; i < count; ++i ) {
				addHeaderView( getChildAt( i ) );
			}
			removeAllViews();
		}
	}

	/*
	 * @Override protected View findViewByPredicateTraversal( Predicate<View> predicate, View childToSkip ) { View v; v =
	 * super.findViewByPredicateTraversal( predicate, childToSkip ); if ( v == null ) { v = findViewByPredicateInHeadersOrFooters(
	 * mHeaderViewInfos, predicate, childToSkip ); if ( v != null ) { return v; }
	 * 
	 * v = findViewByPredicateInHeadersOrFooters( mFooterViewInfos, predicate, childToSkip ); if ( v != null ) { return v; } } return
	 * v; }
	 */

	/*
	 * View findViewByPredicateInHeadersOrFooters( ArrayList<FixedViewInfo> where, Predicate<View> predicate, View childToSkip ) { if
	 * ( where != null ) { int len = where.size(); View v;
	 * 
	 * for ( int i = 0; i < len; i++ ) { v = where.get( i ).view;
	 * 
	 * if ( v != childToSkip && !v.isRootNamespace() ) { v = v.findViewByPredicate( predicate );
	 * 
	 * if ( v != null ) { return v; } } } } return null; }
	 */

	/**
	 * Returns the set of checked items ids. The result is only valid if the choice mode has not been set to
	 * {@link #CHOICE_MODE_NONE}.
	 * 
	 * @return A new array which contains the id of each checked item in the list.
	 * 
	 * @deprecated Use {@link #getCheckedItemIds()} instead.
	 */
	@Deprecated
	public long[] getCheckItemIds() {
		// Use new behavior that correctly handles stable ID mapping.
		if ( mAdapter != null && mAdapter.hasStableIds() ) {
			return getCheckedItemIds();
		}

		// Old behavior was buggy, but would sort of work for adapters without stable IDs.
		// Fall back to it to support legacy apps.
		if ( mChoiceMode != ListView.CHOICE_MODE_NONE && mCheckStates != null && mAdapter != null ) {
			final SparseBooleanArray states = mCheckStates;
			final int count = states.size();
			final long[] ids = new long[count];
			final ListAdapter adapter = mAdapter;

			int checkedCount = 0;
			for ( int i = 0; i < count; i++ ) {
				if ( states.valueAt( i ) ) {
					ids[checkedCount++] = adapter.getItemId( states.keyAt( i ) );
				}
			}

			// Trim array if needed. mCheckStates may contain false values
			// resulting in checkedCount being smaller than count.
			if ( checkedCount == count ) {
				return ids;
			} else {
				final long[] result = new long[checkedCount];
				System.arraycopy( ids, 0, result, 0, checkedCount );

				return result;
			}
		}
		return new long[0];
	}

	@Override
	public void onInitializeAccessibilityEvent( AccessibilityEvent event ) {
		super.onInitializeAccessibilityEvent( event );
		event.setClassName( HListView.class.getName() );
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onInitializeAccessibilityNodeInfo( AccessibilityNodeInfo info ) {
		super.onInitializeAccessibilityNodeInfo( info );
		info.setClassName( HListView.class.getName() );
	}

	@Override
	public void onGlobalLayout() {}
}
