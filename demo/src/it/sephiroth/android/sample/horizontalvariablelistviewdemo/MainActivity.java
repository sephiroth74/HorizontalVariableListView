package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends ListActivity {

	@Override
	protected void onCreate( final Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		List<String> activities = new ArrayList<String>();
		activities.add( "Simple List" );
		activities.add( "Expandable List" );
		activities.add( "Scrollable Grid View" );


		setListAdapter( new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1, activities ) );
	}

	@Override
	protected void onListItemClick( final ListView l, final View v, final int position, final long id ) {

		switch( position ) {
			case 0:
				startActivity( new Intent( this, SimpleHListActivity.class ) );
				break;
			case 1:
				startActivity( new Intent( this, ExpandableListActivity.class ) );
				break;
			case 2:
				startActivity( new Intent( this, GridViewActivity.class ) );
				break;
		}

		super.onListItemClick( l, v, position, id );
	}
}
