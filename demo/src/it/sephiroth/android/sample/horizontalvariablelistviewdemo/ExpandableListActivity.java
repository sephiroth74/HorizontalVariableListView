package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.sephiroth.android.library.widget.ExpandableHListView;

public class ExpandableListActivity extends Activity
		implements ExpandableHListView.OnGroupExpandListener, ExpandableHListView.OnGroupCollapseListener, ExpandableHListView.OnGroupClickListener, View.OnClickListener {

	private static final String LOG_TAG = ExpandableListActivity.class.getSimpleName();

	ExpandableHListAdapter mAdapter;
	ExpandableHListView mListView;
	Button mButton1;
	Button mButton2;
	Button mButton3;
	List<String> listDataHeader;
	HashMap<String, List<String>> listDataChild;

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main2 );

		prepareListData();
		mAdapter = new ExpandableHListAdapter( this, listDataHeader, listDataChild );
		mListView.setAdapter( mAdapter );
		mListView.setOnGroupExpandListener( this );
		mListView.setOnGroupCollapseListener( this );
		mListView.setOnGroupClickListener( this );

		mButton1.setOnClickListener( this );
		mButton2.setOnClickListener( this );
		mButton3.setOnClickListener( this );

	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mListView = (ExpandableHListView) findViewById( R.id.list );
		mButton1 = (Button) findViewById( R.id.button1 );
		mButton2 = (Button) findViewById( R.id.button2 );
		mButton3 = (Button) findViewById( R.id.button3 );

	}

	private void prepareListData() {
		listDataHeader = new ArrayList<String>();
		listDataChild = new HashMap<String, List<String>>();
		for( int i = 0; i < 7; i++ ) {
			addRandomData( i );
		}
	}

	private void addRandomData( int groupPosition ) {
		listDataHeader.add( groupPosition, "Group " + ( listDataHeader.size() + 1 ) );

		final int count = groupPosition + 5;

		List<String> child = new ArrayList<String>();
		for( int k = 0; k < count; k++ ) {
			child.add( "Child " + ( k + 1 ) );
		}

		listDataChild.put( listDataHeader.get( groupPosition ), child );
	}

	@Override
	public void onGroupExpand( final int groupPosition ) {
		Log.i( LOG_TAG, "onGroupExpand: " + groupPosition );
	}

	@Override
	public void onGroupCollapse( final int groupPosition ) {
		Log.i( LOG_TAG, "onGroupCollapse: " + groupPosition );
	}

	@Override
	public boolean onGroupClick( final ExpandableHListView parent, final View v, final int groupPosition, final long id ) {
		Log.i( LOG_TAG, "onGroupClick: " + groupPosition );

		// return true if you want ho handle the listener
		// false for the default implementation ( expand/collapse )
		return false;
	}

	@Override
	public void onClick( final View view ) {
		final int id = view.getId();

		if( id == mButton1.getId() ) {
			addElements();
		}
		else if( id == mButton2.getId() ) {
			removeElements();
		}
		else if( id == mButton3.getId() ) {
			scrollList();
		}
	}

	private void scrollList() {
		mListView.smoothScrollBy( 1500, 300 );
	}

	private void addElements() {
		addRandomData( 0 );
		mAdapter.notifyDataSetChanged();
	}

	private void removeElements() {
		String child = listDataHeader.get( 0 );
		listDataHeader.remove( 0 );
		listDataChild.remove( child );

		mAdapter.notifyDataSetChanged();
	}
}
