package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ExpandableListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.sephiroth.android.library.widget.ExpandableHListView;

public class ExpandableListActivity extends Activity {

	ExpandableHListAdapter listAdapter;
	ExpandableHListView expListView;
	List<String> listDataHeader;
	HashMap<String, List<String>> listDataChild;

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main2 );

		expListView = (ExpandableHListView) findViewById( R.id.list );
		prepareListData();

		listAdapter = new ExpandableHListAdapter( this, listDataHeader, listDataChild );

		expListView.setAdapter( listAdapter );
	}

	/*
	 * Preparing the list data
	 */
	private void prepareListData() {
		listDataHeader = new ArrayList<String>();
		listDataChild = new HashMap<String, List<String>>();

		// Adding child data
		for( int i = 0; i < 7; i++ ) {
			listDataHeader.add( "Group " + ( i + 1 ) );
			List<String> child = new ArrayList<String>();

			for( int k = 0; k < 6; k++ ) {
				child.add( "Child " + ( k + 1 ) );
			}
			listDataChild.put( listDataHeader.get( i ), child );
		}
	}
}
