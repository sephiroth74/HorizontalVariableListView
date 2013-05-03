package it.sephiroth.android.library.utils;

import android.database.Observable;

public class DataSetObservableExtended extends Observable<DataSetObserverExtended> {

	/**
	 * Invokes onChanged on each observer. <br />
	 * Called when the data set being observed has changed, and which when read contains the new state of the data.
	 */
	public void notifyChanged() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onChanged();
			}
		}
	}

	/**
	 * Invokes onChanged on each observer. <br />
	 * Called when an item in the data set being observed has added, and which when read contains the new state of the data.
	 * 
	 * @param position position of the added item
	 */
	public void notifyAdded( int position ) {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onAdded( position );
			}
		}
	}

	/**
	 * Invokes onRemoved on each observer. <br />
	 * Called when an item in the data set being observed has removed, and which when read contains the new state of the data.
	 * 
	 * @param position
	 *           the position of the removed item
	 * @param viewType
	 *           the viewType of the item removed
	 */
	public void notifyRemoved( int position, int viewType ) {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onRemoved( position, viewType );
			}
		}
	}
	
	/**
	 * Invokes onReplaced on each observer. <br />
	 * Called when an item in the data set being observed has replaced, and which when read contains the new state of the data.
	 * 
	 * @param position
	 *           the position of the replaced item
	 * @param viewType
	 *           the viewType of the item removed
	 */
	public void notifyReplaced( int position, int viewType ) {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onReplaced( position, viewType );
			}
		}
	}	

	/**
	 * Invokes onInvalidated on each observer.<br />
	 * Called when the data set being monitored has changed such that it is no longer valid.
	 */
	public void notifyInvalidated() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onInvalidated();
			}
		}
	}
}
