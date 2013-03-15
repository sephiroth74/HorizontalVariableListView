package it.sephiroth.android.library.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;

public abstract class HorizontalListView extends AdapterView<ListAdapter> {

	public HorizontalListView( Context context ) {
		super( context );
	}

	public HorizontalListView( Context context, AttributeSet attrs ) {
		super( context, attrs );
	}

	public HorizontalListView( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );
	}

	public abstract int getScreenPositionForView( View view );
	

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
	
	public interface OnScrollFinishedListener {
		/**
		 * Callback method to be invoked when the scroll has completed.
		 * 
		 * @param currentX
		 *            The current scroll position of the view
		 */
		void onScrollFinished( int currentX );
	}	
}
