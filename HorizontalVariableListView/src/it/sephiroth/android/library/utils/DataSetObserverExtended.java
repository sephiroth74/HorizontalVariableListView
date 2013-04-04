package it.sephiroth.android.library.utils;

import android.database.DataSetObserver;

public abstract class DataSetObserverExtended extends DataSetObserver {

	public void onAdded( int position ) {}

	public void onRemoved( int position, int viewType ) {}
}
