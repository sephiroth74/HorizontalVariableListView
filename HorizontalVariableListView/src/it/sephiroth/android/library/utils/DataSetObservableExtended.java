package it.sephiroth.android.library.utils;

import android.database.Observable;

public class DataSetObservableExtended extends Observable<DataSetObserverExtended> {

	/**
	 * Invokes onChanged on each observer. Called when the data set being observed has changed, and which when read contains the new
	 * state of the data.
	 */
	public void notifyChanged() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onChanged();
			}
		}
	}

	/**
	 * Invokes onChanged on each observer. Called when an item in the data set being observed has added, and which when read contains
	 * the new state of the data.
	 */
	public void notifyAdded() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onAdded();
			}
		}
	}

	/**
	 * Invokes onRemoved on each observer. Called when an item in the data set being observed has removed, and which when read
	 * contains the new state of the data.
	 */
	public void notifyRemoved() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onRemoved();
			}
		}
	}

	/**
	 * Invokes onInvalidated on each observer. Called when the data set being monitored has changed such that it is no longer valid.
	 */
	public void notifyInvalidated() {
		synchronized ( mObservers ) {
			for ( DataSetObserverExtended observer : mObservers ) {
				observer.onInvalidated();
			}
		}
	}
}
