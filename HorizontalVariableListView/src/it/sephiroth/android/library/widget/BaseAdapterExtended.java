package it.sephiroth.android.library.widget;

import it.sephiroth.android.library.utils.DataSetObservableExtended;
import it.sephiroth.android.library.utils.DataSetObserverExtended;
import android.widget.BaseAdapter;

public abstract class BaseAdapterExtended extends BaseAdapter {

	private final DataSetObservableExtended mDataSetObservableExtended = new DataSetObservableExtended();

	public void registerDataSetObserverExtended( DataSetObserverExtended observer ) {
		mDataSetObservableExtended.registerObserver( observer );
	}

	public void unregisterDataSetObserverExtended( DataSetObserverExtended observer ) {
		mDataSetObservableExtended.unregisterObserver( observer );
	}

	/**
	 * Notifies the attached observers that the underlying data has been changed and any View reflecting the data set should refresh
	 * itself.
	 */
	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
		mDataSetObservableExtended.notifyChanged();
	}

	/**
	 * Notifies the attached observers that the underlying data is no longer valid or available. Once invoked this adapter is no
	 * longer valid and should not report further data set changes.
	 */
	@Override
	public void notifyDataSetInvalidated() {
		super.notifyDataSetInvalidated();
		mDataSetObservableExtended.notifyInvalidated();
	}

	public void notifyDataSetAdded() {
		mDataSetObservableExtended.notifyAdded();
	}

	public void notifyDataSetRemoved() {
		mDataSetObservableExtended.notifyRemoved();
	}
}
