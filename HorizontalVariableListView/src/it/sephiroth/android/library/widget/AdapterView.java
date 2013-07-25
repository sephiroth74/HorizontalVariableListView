/*
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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Adapter;

/**
 * An AdapterView is a view whose children are determined by an {@link Adapter}.
 */
public abstract class AdapterView<T extends Adapter> extends ViewGroup {

	/**
	 * The item view type returned by {@link Adapter#getItemViewType(int)} when the adapter does not want the item's view recycled.
	 */
	public static final int ITEM_VIEW_TYPE_IGNORE = -1;

	/**
	 * The item view type returned by {@link Adapter#getItemViewType(int)} when the item is a header or footer.
	 */
	public static final int ITEM_VIEW_TYPE_HEADER_OR_FOOTER = -2;

	/**
	 * The position of the first child displayed
	 */
	@ViewDebug.ExportedProperty(category = "scrolling")
	protected int mFirstPosition = 0;

	/**
	 * The offset in pixels from the left of the AdapterView to the left of the view to select during the next layout.
	 */
	protected int mSpecificLeft;

	/**
	 * Position from which to start looking for mSyncRowId
	 */
	protected int mSyncPosition;

	/**
	 * Col id to look for when data has changed
	 */
	protected long mSyncColId = INVALID_COL_ID;

	/**
	 * Width of the view when mSyncPosition and mSyncColId where set
	 */
	protected long mSyncWidth;

	/**
	 * True if we need to sync to mSyncColId
	 */
	protected boolean mNeedSync = false;

	/**
	 * Indicates whether to sync based on the selection or position. Possible values are {@link #SYNC_SELECTED_POSITION} or
	 * {@link #SYNC_FIRST_POSITION}.
	 */
	int mSyncMode;

	/**
	 * Our width after the last layout
	 */
	private int mLayoutWidth;

	/**
	 * Sync based on the selected child
	 */
	static final int SYNC_SELECTED_POSITION = 0;

	/**
	 * Sync based on the first child displayed
	 */
	static final int SYNC_FIRST_POSITION = 1;

	/**
	 * Maximum amount of time to spend in {@link #findSyncPosition()}
	 */
	static final int SYNC_MAX_DURATION_MILLIS = 100;

	/**
	 * Indicates that this view is currently being laid out.
	 */
	protected boolean mInLayout = false;

	/**
	 * The listener that receives notifications when an item is selected.
	 */
	OnItemSelectedListener mOnItemSelectedListener;

	/**
	 * The listener that receives notifications when an item is clicked.
	 */
	OnItemClickListener mOnItemClickListener;

	/**
	 * The listener that receives notifications when an item is long clicked.
	 */
	OnItemLongClickListener mOnItemLongClickListener;

	/**
	 * True if the data has changed since the last layout
	 * @hide
	 */
	public boolean mDataChanged;

	/**
	 * The position within the adapter's data set of the item to select during the next layout.
	 */
	@ViewDebug.ExportedProperty(category = "list")
	protected int mNextSelectedPosition = INVALID_POSITION;

	/**
	 * The item id of the item to select during the next layout.
	 */
	protected long mNextSelectedColId = INVALID_COL_ID;

	/**
	 * The position within the adapter's data set of the currently selected item.
	 */
	@ViewDebug.ExportedProperty(category = "list")
	protected int mSelectedPosition = INVALID_POSITION;

	/**
	 * The item id of the currently selected item.
	 */
	protected long mSelectedColId = INVALID_COL_ID;

	/**
	 * View to show if there are no items to show.
	 */
	private View mEmptyView;

	/**
	 * The number of items in the current adapter.
	 */
	@ViewDebug.ExportedProperty(category = "list")
	protected int mItemCount;

	/**
	 * The number of items in the adapter before a data changed event occurred.
	 */
	protected int mOldItemCount;

	AccessibilityManager mAccessibilityManager;

	/**
	 * Represents an invalid position. All valid positions are in the range 0 to 1 less than the number of items in the current
	 * adapter.
	 */
	public static final int INVALID_POSITION = -1;

	/**
	 * Represents an empty or invalid col id
	 */
	public static final long INVALID_COL_ID = Long.MIN_VALUE;

	/**
	 * The last selected position we used when notifying
	 */
	protected int mOldSelectedPosition = INVALID_POSITION;

	/**
	 * The id of the last selected position we used when notifying
	 */
	protected long mOldSelectedColId = INVALID_COL_ID;

	/**
	 * Indicates what focusable state is requested when calling setFocusable(). In addition to this, this view has other criteria for
	 * actually determining the focusable state (such as whether its empty or the text filter is shown).
	 * 
	 * @see #setFocusable(boolean)
	 * @see #checkFocus()
	 */
	private boolean mDesiredFocusableState;
	private boolean mDesiredFocusableInTouchModeState;

	private SelectionNotifier mSelectionNotifier;
	/**
	 * When set to true, calls to requestLayout() will not propagate up the parent hierarchy. This is used to layout the children
	 * during a layout pass.
	 */
	protected boolean mBlockLayoutRequests = false;

	public AdapterView( Context context ) {
		super( context );
	}

	public AdapterView( Context context, AttributeSet attrs ) {
		super( context, attrs );
	}

	public AdapterView( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );

		if( android.os.Build.VERSION.SDK_INT >= 16 ) {
			// If not explicitly specified this view is important for accessibility.
			if ( getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO ) {
				setImportantForAccessibility( IMPORTANT_FOR_ACCESSIBILITY_YES );
			}
		}

		mAccessibilityManager = (AccessibilityManager) getContext().getSystemService( Context.ACCESSIBILITY_SERVICE );
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this AdapterView has been clicked.
	 */
	public interface OnItemClickListener {

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
		 *           The col id of the item that was clicked.
		 */
		void onItemClick( AdapterView<?> parent, View view, int position, long id );
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been clicked.
	 * 
	 * @param listener
	 *           The callback that will be invoked.
	 */
	public void setOnItemClickListener( OnItemClickListener listener ) {
		mOnItemClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has been clicked, or null id no callback has been set.
	 */
	public final OnItemClickListener getOnItemClickListener() {
		return mOnItemClickListener;
	}

	/**
	 * Call the OnItemClickListener, if it is defined.
	 * 
	 * @param view
	 *           The view within the AdapterView that was clicked.
	 * @param position
	 *           The position of the view in the adapter.
	 * @param id
	 *           The col id of the item that was clicked.
	 * @return True if there was an assigned OnItemClickListener that was called, false otherwise is returned.
	 */
	public boolean performItemClick( View view, int position, long id ) {
		if ( mOnItemClickListener != null ) {
			playSoundEffect( SoundEffectConstants.CLICK );
			if ( view != null ) {
				view.sendAccessibilityEvent( AccessibilityEvent.TYPE_VIEW_CLICKED );
			}
			mOnItemClickListener.onItemClick( this, view, position, id );
			return true;
		}

		return false;
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this view has been clicked and held.
	 */
	public interface OnItemLongClickListener {

		/**
		 * Callback method to be invoked when an item in this view has been clicked and held.
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
		 *           The col id of the item that was clicked
		 * 
		 * @return true if the callback consumed the long click, false otherwise
		 */
		boolean onItemLongClick( AdapterView<?> parent, View view, int position, long id );
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been clicked and held
	 * 
	 * @param listener
	 *           The callback that will run
	 */
	public void setOnItemLongClickListener( OnItemLongClickListener listener ) {
		if ( !isLongClickable() ) {
			setLongClickable( true );
		}
		mOnItemLongClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has been clicked and held, or null id no callback as been
	 *         set.
	 */
	public final OnItemLongClickListener getOnItemLongClickListener() {
		return mOnItemLongClickListener;
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this view has been selected.
	 */
	public interface OnItemSelectedListener {

		/**
		 * <p>
		 * Callback method to be invoked when an item in this view has been selected. This callback is invoked only when the newly
		 * selected position is different from the previously selected position or if there was no selected item.
		 * </p>
		 * 
		 * Impelmenters can call getItemAtPosition(position) if they need to access the data associated with the selected item.
		 * 
		 * @param parent
		 *           The AdapterView where the selection happened
		 * @param view
		 *           The view within the AdapterView that was clicked
		 * @param position
		 *           The position of the view in the adapter
		 * @param id
		 *           The col id of the item that is selected
		 */
		void onItemSelected( AdapterView<?> parent, View view, int position, long id );

		/**
		 * Callback method to be invoked when the selection disappears from this view. The selection can disappear for instance when
		 * touch is activated or when the adapter becomes empty.
		 * 
		 * @param parent
		 *           The AdapterView that now contains no selected item.
		 */
		void onNothingSelected( AdapterView<?> parent );
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been selected.
	 * 
	 * @param listener
	 *           The callback that will run
	 */
	public void setOnItemSelectedListener( OnItemSelectedListener listener ) {
		mOnItemSelectedListener = listener;
	}

	public final OnItemSelectedListener getOnItemSelectedListener() {
		return mOnItemSelectedListener;
	}

	/**
	 * Extra menu information provided to the
	 * {@link android.view.View.OnCreateContextMenuListener#onCreateContextMenu(ContextMenu, View, ContextMenuInfo) } callback when a
	 * context menu is brought up for this AdapterView.
	 * 
	 */
	public static class AdapterContextMenuInfo implements ContextMenu.ContextMenuInfo {

		public AdapterContextMenuInfo( View targetView, int position, long id ) {
			this.targetView = targetView;
			this.position = position;
			this.id = id;
		}

		/**
		 * The child view for which the context menu is being displayed. This will be one of the children of this AdapterView.
		 */
		public View targetView;

		/**
		 * The position in the adapter for which the context menu is being displayed.
		 */
		public int position;

		/**
		 * The col id of the item for which the context menu is being displayed.
		 */
		public long id;
	}

	/**
	 * Returns the adapter currently associated with this widget.
	 * 
	 * @return The adapter used to provide this view's content.
	 */
	public abstract T getAdapter();

	/**
	 * Sets the adapter that provides the data and the views to represent the data in this widget.
	 * 
	 * @param adapter
	 *           The adapter to use to create this view's content.
	 */
	public abstract void setAdapter( T adapter );

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param child
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void addView( View child ) {
		throw new UnsupportedOperationException( "addView(View) is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param child
	 *           Ignored.
	 * @param index
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void addView( View child, int index ) {
		throw new UnsupportedOperationException( "addView(View, int) is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param child
	 *           Ignored.
	 * @param params
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void addView( View child, LayoutParams params ) {
		throw new UnsupportedOperationException( "addView(View, LayoutParams) "
				+ "is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param child
	 *           Ignored.
	 * @param index
	 *           Ignored.
	 * @param params
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void addView( View child, int index, LayoutParams params ) {
		throw new UnsupportedOperationException( "addView(View, int, LayoutParams) "
				+ "is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param child
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void removeView( View child ) {
		throw new UnsupportedOperationException( "removeView(View) is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @param index
	 *           Ignored.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void removeViewAt( int index ) {
		throw new UnsupportedOperationException( "removeViewAt(int) is not supported in AdapterView" );
	}

	/**
	 * This method is not supported and throws an UnsupportedOperationException when called.
	 * 
	 * @throws UnsupportedOperationException
	 *            Every time this method is invoked.
	 */
	@Override
	public void removeAllViews() {
		throw new UnsupportedOperationException( "removeAllViews() is not supported in AdapterView" );
	}

	@Override
	protected void onLayout( boolean changed, int left, int top, int right, int bottom ) {
		mLayoutWidth = getWidth();
	}

	/**
	 * Return the position of the currently selected item within the adapter's data set
	 * 
	 * @return int Position (starting at 0), or {@link #INVALID_POSITION} if there is nothing selected.
	 */
	@ViewDebug.CapturedViewProperty
	public int getSelectedItemPosition() {
		return mNextSelectedPosition;
	}

	/**
	 * @return The id corresponding to the currently selected item, or {@link #INVALID_ROW_ID} if nothing is selected.
	 */
	@ViewDebug.CapturedViewProperty
	public long getSelectedItemId() {
		return mNextSelectedColId;
	}

	/**
	 * @return The view corresponding to the currently selected item, or null if nothing is selected
	 */
	public abstract View getSelectedView();

	/**
	 * @return The data corresponding to the currently selected item, or null if there is nothing selected.
	 */
	public Object getSelectedItem() {
		T adapter = getAdapter();
		int selection = getSelectedItemPosition();
		if ( adapter != null && adapter.getCount() > 0 && selection >= 0 ) {
			return adapter.getItem( selection );
		} else {
			return null;
		}
	}

	/**
	 * @return The number of items owned by the Adapter associated with this AdapterView. (This is the number of data items, which
	 *         may be larger than the number of visible views.)
	 */
	@ViewDebug.CapturedViewProperty
	public int getCount() {
		return mItemCount;
	}

	/**
	 * Get the position within the adapter's data set for the view, where view is a an adapter item or a descendant of an adapter
	 * item.
	 * 
	 * @param view
	 *           an adapter item, or a descendant of an adapter item. This must be visible in this AdapterView at the time of the
	 *           call.
	 * @return the position within the adapter's data set of the view, or {@link #INVALID_POSITION} if the view does not correspond
	 *         to a list item (or it is not currently visible).
	 */
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
				return mFirstPosition + i;
			}
		}

		// Child not found!
		return INVALID_POSITION;
	}

	/**
	 * Returns the position within the adapter's data set for the first item displayed on screen.
	 * 
	 * @return The position within the adapter's data set
	 */
	public int getFirstVisiblePosition() {
		return mFirstPosition;
	}

	/**
	 * Returns the position within the adapter's data set for the last item displayed on screen.
	 * 
	 * @return The position within the adapter's data set
	 */
	public int getLastVisiblePosition() {
		return mFirstPosition + getChildCount() - 1;
	}

	/**
	 * Sets the currently selected item. To support accessibility subclasses that override this method must invoke the overriden
	 * super method first.
	 * 
	 * @param position
	 *           Index (starting at 0) of the data item to be selected.
	 */
	public abstract void setSelection( int position );

	/**
	 * Sets the view to show if the adapter is empty
	 */
	public void setEmptyView( View emptyView ) {
		mEmptyView = emptyView;

		if( android.os.Build.VERSION.SDK_INT >= 16 ) {
			// If not explicitly specified this view is important for accessibility.
			if ( emptyView != null && emptyView.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO ) {
				emptyView.setImportantForAccessibility( IMPORTANT_FOR_ACCESSIBILITY_YES );
			}
		}

		final T adapter = getAdapter();
		final boolean empty = ( ( adapter == null ) || adapter.isEmpty() );
		updateEmptyStatus( empty );
	}

	/**
	 * When the current adapter is empty, the AdapterView can display a special view call the empty view. The empty view is used to
	 * provide feedback to the user that no data is available in this AdapterView.
	 * 
	 * @return The view to show if the adapter is empty.
	 */
	public View getEmptyView() {
		return mEmptyView;
	}

	/**
	 * Indicates whether this view is in filter mode. Filter mode can for instance be enabled by a user when typing on the keyboard.
	 * 
	 * @return True if the view is in filter mode, false otherwise.
	 */
	boolean isInFilterMode() {
		return false;
	}

	@Override
	public void setFocusable( boolean focusable ) {
		final T adapter = getAdapter();
		final boolean empty = adapter == null || adapter.getCount() == 0;

		mDesiredFocusableState = focusable;
		if ( !focusable ) {
			mDesiredFocusableInTouchModeState = false;
		}

		super.setFocusable( focusable && ( !empty || isInFilterMode() ) );
	}

	@Override
	public void setFocusableInTouchMode( boolean focusable ) {
		final T adapter = getAdapter();
		final boolean empty = adapter == null || adapter.getCount() == 0;

		mDesiredFocusableInTouchModeState = focusable;
		if ( focusable ) {
			mDesiredFocusableState = true;
		}

		super.setFocusableInTouchMode( focusable && ( !empty || isInFilterMode() ) );
	}

	protected void checkFocus() {
		final T adapter = getAdapter();
		final boolean empty = adapter == null || adapter.getCount() == 0;
		final boolean focusable = !empty || isInFilterMode();
		// The order in which we set focusable in touch mode/focusable may matter
		// for the client, see View.setFocusableInTouchMode() comments for more
		// details
		super.setFocusableInTouchMode( focusable && mDesiredFocusableInTouchModeState );
		super.setFocusable( focusable && mDesiredFocusableState );
		if ( mEmptyView != null ) {
			updateEmptyStatus( ( adapter == null ) || adapter.isEmpty() );
		}
	}

	/**
	 * Update the status of the list based on the empty parameter. If empty is true and we have an empty view, display it. In all the
	 * other cases, make sure that the listview is VISIBLE and that the empty view is GONE (if it's not null).
	 */
	@SuppressLint("WrongCall")
	private void updateEmptyStatus( boolean empty ) {
		if ( isInFilterMode() ) {
			empty = false;
		}

		if ( empty ) {
			if ( mEmptyView != null ) {
				mEmptyView.setVisibility( View.VISIBLE );
				setVisibility( View.GONE );
			} else {
				// If the caller just removed our empty view, make sure the list view is visible
				setVisibility( View.VISIBLE );
			}

			// We are now GONE, so pending layouts will not be dispatched.
			// Force one here to make sure that the state of the list matches
			// the state of the adapter.
			if ( mDataChanged ) {
				this.onLayout( false, getLeft(), getTop(), getRight(), getBottom() );
			}
		} else {
			if ( mEmptyView != null ) mEmptyView.setVisibility( View.GONE );
			setVisibility( View.VISIBLE );
		}
	}

	/**
	 * Gets the data associated with the specified position in the list.
	 * 
	 * @param position
	 *           Which data to get
	 * @return The data associated with the specified position in the list
	 */
	public Object getItemAtPosition( int position ) {
		T adapter = getAdapter();
		return ( adapter == null || position < 0 ) ? null : adapter.getItem( position );
	}

	public long getItemIdAtPosition( int position ) {
		T adapter = getAdapter();
		return ( adapter == null || position < 0 ) ? INVALID_COL_ID : adapter.getItemId( position );
	}

	@Override
	public void setOnClickListener( OnClickListener l ) {
		throw new RuntimeException( "Don't call setOnClickListener for an AdapterView. "
				+ "You probably want setOnItemClickListener instead" );
	}

	/**
	 * Override to prevent freezing of any views created by the adapter.
	 */
	@Override
	protected void dispatchSaveInstanceState( SparseArray<Parcelable> container ) {
		dispatchFreezeSelfOnly( container );
	}

	/**
	 * Override to prevent thawing of any views created by the adapter.
	 */
	@Override
	protected void dispatchRestoreInstanceState( SparseArray<Parcelable> container ) {
		dispatchThawSelfOnly( container );
	}

	class AdapterDataSetObserver extends DataSetObserver {

		private Parcelable mInstanceState = null;

		@Override
		public void onChanged() {
			Log.i( VIEW_LOG_TAG, "onChanged" );
			mDataChanged = true;
			mOldItemCount = mItemCount;
			mItemCount = getAdapter().getCount();

			// Detect the case where a cursor that was previously invalidated has
			// been repopulated with new data.
			if ( AdapterView.this.getAdapter().hasStableIds() && mInstanceState != null
					&& mOldItemCount == 0 && mItemCount > 0 ) {
				Log.d( VIEW_LOG_TAG, "calling onRestoreInstanceState");
				AdapterView.this.onRestoreInstanceState( mInstanceState );
				mInstanceState = null;
			} else {
				Log.d( VIEW_LOG_TAG, "else calling rememberSyncState" );
				rememberSyncState();
			}
			checkFocus();
			requestLayout();
		}

		@Override
		public void onInvalidated() {
			Log.i( VIEW_LOG_TAG, "onInvalidated" );
			mDataChanged = true;

			if ( AdapterView.this.getAdapter().hasStableIds() ) {
				// Remember the current state for the case where our hosting activity is being
				// stopped and later restarted
				mInstanceState = AdapterView.this.onSaveInstanceState();
			}

			// Data is invalid so we should reset our state
			mOldItemCount = mItemCount;
			mItemCount = 0;
			mSelectedPosition = INVALID_POSITION;
			mSelectedColId = INVALID_COL_ID;
			mNextSelectedPosition = INVALID_POSITION;
			mNextSelectedColId = INVALID_COL_ID;
			mNeedSync = false;

			checkFocus();
			requestLayout();
		}

		public void clearSavedState() {
			mInstanceState = null;
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		removeCallbacks( mSelectionNotifier );
	}

	private class SelectionNotifier implements Runnable {

		@Override
		public void run() {
			if ( mDataChanged ) {
				// Data has changed between when this SelectionNotifier
				// was posted and now. We need to wait until the AdapterView
				// has been synched to the new data.
				if ( getAdapter() != null ) {
					post( this );
				}
			} else {
				fireOnSelected();
				performAccessibilityActionsOnSelected();
			}
		}
	}

	void selectionChanged() {

		if ( mOnItemSelectedListener != null
				|| mAccessibilityManager.isEnabled() ) {
			if ( mInLayout || mBlockLayoutRequests ) {
				// If we are in a layout traversal, defer notification
				// by posting. This ensures that the view tree is
				// in a consistent state and is able to accomodate
				// new layout or invalidate requests.
				if ( mSelectionNotifier == null ) {
					mSelectionNotifier = new SelectionNotifier();
				}
				post( mSelectionNotifier );
			} else {
				fireOnSelected();
				performAccessibilityActionsOnSelected();
			}
		}
	}

	private void fireOnSelected() {
		if ( mOnItemSelectedListener == null ) {
			return;
		}
		final int selection = getSelectedItemPosition();
		if ( selection >= 0 ) {
			View v = getSelectedView();
			mOnItemSelectedListener.onItemSelected( this, v, selection,
					getAdapter().getItemId( selection ) );
		} else {
			mOnItemSelectedListener.onNothingSelected( this );
		}
	}

	private void performAccessibilityActionsOnSelected() {
		if ( !mAccessibilityManager.isEnabled() ) {
			return;
		}
		final int position = getSelectedItemPosition();
		if ( position >= 0 ) {
			// we fire selection events here not in View
			sendAccessibilityEvent( AccessibilityEvent.TYPE_VIEW_SELECTED );
		}
	}

	@Override
	public boolean dispatchPopulateAccessibilityEvent( AccessibilityEvent event ) {
		View selectedView = getSelectedView();
		if ( selectedView != null && selectedView.getVisibility() == VISIBLE
				&& selectedView.dispatchPopulateAccessibilityEvent( event ) ) {
			return true;
		}
		return false;
	}

	@TargetApi(14)
	@Override
	public boolean onRequestSendAccessibilityEvent( View child, AccessibilityEvent event ) {
		if ( super.onRequestSendAccessibilityEvent( child, event ) ) {
			// Add a record for ourselves as well.
			AccessibilityEvent record = AccessibilityEvent.obtain();
			onInitializeAccessibilityEvent( record );
			// Populate with the text of the requesting child.
			child.dispatchPopulateAccessibilityEvent( record );
			event.appendRecord( record );
			return true;
		}
		return false;
	}

	@TargetApi(14)
	@Override
	public void onInitializeAccessibilityNodeInfo( AccessibilityNodeInfo info ) {
		super.onInitializeAccessibilityNodeInfo( info );
		info.setClassName( AdapterView.class.getName() );
		info.setScrollable( isScrollableForAccessibility() );
		View selectedView = getSelectedView();
		if ( selectedView != null ) {
			info.setEnabled( selectedView.isEnabled() );
		}
	}

	@TargetApi(14)
	@Override
	public void onInitializeAccessibilityEvent( AccessibilityEvent event ) {
		super.onInitializeAccessibilityEvent( event );
		event.setClassName( AdapterView.class.getName() );
		event.setScrollable( isScrollableForAccessibility() );
		View selectedView = getSelectedView();
		if ( selectedView != null ) {
			event.setEnabled( selectedView.isEnabled() );
		}
		event.setCurrentItemIndex( getSelectedItemPosition() );
		event.setFromIndex( getFirstVisiblePosition() );
		event.setToIndex( getLastVisiblePosition() );
		event.setItemCount( getCount() );
	}

	private boolean isScrollableForAccessibility() {
		T adapter = getAdapter();
		if ( adapter != null ) {
			final int itemCount = adapter.getCount();
			return itemCount > 0
					&& ( getFirstVisiblePosition() > 0 || getLastVisiblePosition() < itemCount - 1 );
		}
		return false;
	}

	@Override
	protected boolean canAnimate() {
		return super.canAnimate() && mItemCount > 0;
	}

	void handleDataChanged() {
		final int count = mItemCount;
		boolean found = false;

		if ( count > 0 ) {

			int newPos;

			// Find the col we are supposed to sync to
			if ( mNeedSync ) {
				// Update this first, since setNextSelectedPositionInt inspects
				// it
				mNeedSync = false;

				// See if we can find a position in the new data with the same
				// id as the old selection
				newPos = findSyncPosition();
				if ( newPos >= 0 ) {
					// Verify that new selection is selectable
					int selectablePos = lookForSelectablePosition( newPos, true );
					if ( selectablePos == newPos ) {
						// Same col id is selected
						setNextSelectedPositionInt( newPos );
						found = true;
					}
				}
			}
			if ( !found ) {
				// Try to use the same position if we can't find matching data
				newPos = getSelectedItemPosition();

				// Pin position to the available range
				if ( newPos >= count ) {
					newPos = count - 1;
				}
				if ( newPos < 0 ) {
					newPos = 0;
				}

				// Make sure we select something selectable -- first look down
				int selectablePos = lookForSelectablePosition( newPos, true );
				if ( selectablePos < 0 ) {
					// Looking down didn't work -- try looking up
					selectablePos = lookForSelectablePosition( newPos, false );
				}
				if ( selectablePos >= 0 ) {
					setNextSelectedPositionInt( selectablePos );
					checkSelectionChanged();
					found = true;
				}
			}
		}
		if ( !found ) {
			// Nothing is selected
			mSelectedPosition = INVALID_POSITION;
			mSelectedColId = INVALID_COL_ID;
			mNextSelectedPosition = INVALID_POSITION;
			mNextSelectedColId = INVALID_COL_ID;
			mNeedSync = false;
			checkSelectionChanged();
		}

		// TODO: Hmm, we do not know the old state so this is sub-optimal
		
		// TODO: implement this ( WTF Google, why you use the @hide tag?? )
		// notifyAccessibilityStateChanged();
	}

	protected void checkSelectionChanged() {
		if ( ( mSelectedPosition != mOldSelectedPosition ) || ( mSelectedColId != mOldSelectedColId ) ) {
			selectionChanged();
			mOldSelectedPosition = mSelectedPosition;
			mOldSelectedColId = mSelectedColId;
		}
	}

	/**
	 * Searches the adapter for a position matching mSyncColId. The search starts at mSyncPosition and then alternates between moving
	 * up and moving down until 1) we find the right position, or 2) we run out of time, or 3) we have looked at every position
	 * 
	 * @return Position of the col that matches mSyncColId, or {@link #INVALID_POSITION} if it can't be found
	 */
	int findSyncPosition() {
		int count = mItemCount;

		if ( count == 0 ) {
			return INVALID_POSITION;
		}

		long idToMatch = mSyncColId;
		int seed = mSyncPosition;

		// If there isn't a selection don't hunt for it
		if ( idToMatch == INVALID_COL_ID ) {
			return INVALID_POSITION;
		}

		// Pin seed to reasonable values
		seed = Math.max( 0, seed );
		seed = Math.min( count - 1, seed );

		long endTime = SystemClock.uptimeMillis() + SYNC_MAX_DURATION_MILLIS;

		long colId;

		// first position scanned so far
		int first = seed;

		// last position scanned so far
		int last = seed;

		// True if we should move down on the next iteration
		boolean next = false;

		// True when we have looked at the first item in the data
		boolean hitFirst;

		// True when we have looked at the last item in the data
		boolean hitLast;

		// Get the item ID locally (instead of getItemIdAtPosition), so
		// we need the adapter
		T adapter = getAdapter();
		if ( adapter == null ) {
			return INVALID_POSITION;
		}

		while ( SystemClock.uptimeMillis() <= endTime ) {
			colId = adapter.getItemId( seed );
			if ( colId == idToMatch ) {
				// Found it!
				return seed;
			}

			hitLast = last == count - 1;
			hitFirst = first == 0;

			if ( hitLast && hitFirst ) {
				// Looked at everything
				break;
			}

			if ( hitFirst || ( next && !hitLast ) ) {
				// Either we hit the top, or we are trying to move down
				last++;
				seed = last;
				// Try going up next time
				next = false;
			} else if ( hitLast || ( !next && !hitFirst ) ) {
				// Either we hit the bottom, or we are trying to move up
				first--;
				seed = first;
				// Try going down next time
				next = true;
			}

		}

		return INVALID_POSITION;
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
	protected int lookForSelectablePosition( int position, boolean lookDown ) {
		return position;
	}

	/**
	 * Utility to keep mSelectedPosition and mSelectedColId in sync
	 * 
	 * @param position
	 *           Our current position
	 */
	protected void setSelectedPositionInt( int position ) {
		mSelectedPosition = position;
		mSelectedColId = getItemIdAtPosition( position );
	}

	/**
	 * Utility to keep mNextSelectedPosition and mNextSelectedColId in sync
	 * 
	 * @param position
	 *           Intended value for mSelectedPosition the next time we go through layout
	 */
	protected void setNextSelectedPositionInt( int position ) {
		mNextSelectedPosition = position;
		mNextSelectedColId = getItemIdAtPosition( position );
		// If we are trying to sync to the selection, update that too
		if ( mNeedSync && mSyncMode == SYNC_SELECTED_POSITION && position >= 0 ) {
			mSyncPosition = position;
			mSyncColId = mNextSelectedColId;
		}
	}

	/**
	 * Remember enough information to restore the screen state when the data has changed.
	 * @hide
	 */
	public void rememberSyncState() {
		if ( getChildCount() > 0 ) {
			mNeedSync = true;
			mSyncWidth = mLayoutWidth;
			if ( mSelectedPosition >= 0 ) {
				// Sync the selection state
				View v = getChildAt( mSelectedPosition - mFirstPosition );
				mSyncColId = mNextSelectedColId;
				mSyncPosition = mNextSelectedPosition;
				if ( v != null ) {
					mSpecificLeft = v.getLeft();
				}
				mSyncMode = SYNC_SELECTED_POSITION;
			} else {
				// Sync the based on the offset of the first view
				View v = getChildAt( 0 );
				T adapter = getAdapter();
				if ( mFirstPosition >= 0 && mFirstPosition < adapter.getCount() ) {
					mSyncColId = adapter.getItemId( mFirstPosition );
				} else {
					mSyncColId = NO_ID;
				}
				mSyncPosition = mFirstPosition;
				if ( v != null ) {
					mSpecificLeft = v.getLeft();
				}
				mSyncMode = SYNC_FIRST_POSITION;
			}
		}
	}
}
