package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.widget.HorizontalListView.OnLayoutChangeListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnItemClickedListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView.SelectionMode;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String LOG_TAG = "main-activity";

	HorizontalVariableListView mList;
	TextView mText;

	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		List<String> data = new ArrayList<String>();
		for ( int i = 0; i < 40; i++ ) {
			data.add( String.valueOf( i ) );
		}

		ListAdapter adapter = new ListAdapter( this, R.layout.view1, R.layout.divider, data );

		// change the selection mode: single or multiple
		mList.setSelectionMode( SelectionMode.Single );

		mList.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );
		mList.setEdgeGravityY( Gravity.CENTER );
		mList.setAdapter( adapter );
		
		// children gravity ( top, center, bottom )
		mList.setGravity( Gravity.CENTER );

	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mList = (HorizontalVariableListView) findViewById( R.id.list );
		mText = (TextView) findViewById( R.id.text );

		mList.setOnLayoutChangeListener( new OnLayoutChangeListener() {

			@Override
			public void onLayoutChange( boolean changed, int left, int top, int right, int bottom ) {
				Log.d( LOG_TAG, "onLayoutChange: " + changed + ", " + bottom + ", " + top );
				if ( changed ) {
					mList.setEdgeHeight( bottom - top );
				}
			}
		} );

		mList.setOnItemClickedListener( new OnItemClickedListener() {

			@Override
			public boolean onItemClick( AdapterView<?> parent, View view, int position, long id ) {
				Log.i( LOG_TAG, "onItemClick: " + position );

				// item has been clicked, return true if you want the
				// HorizontalVariableList to handle the event
				// false otherwise
				return true;
			}
		} );

		mList.setOnItemSelectedListener( new OnItemSelectedListener() {

			@Override
			public void onItemSelected( AdapterView<?> parent, View view, int position, long id ) {
				mText.setText( "item selected: " + position + ", selected items: " + mList.getSelectedPositions().length );
			}

			@Override
			public void onNothingSelected( android.widget.AdapterView<?> parent ) {
				mText.setText( "nothing selected" );
			};

		} );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate( R.menu.activity_main, menu );
		return true;
	}

	class ListAdapter extends ArrayAdapter<String> {

		int resId1;
		int resId2;

		public ListAdapter( Context context, int textViewResourceId, int dividerResourceId, List<String> objects ) {
			super( context, textViewResourceId, objects );
			resId1 = textViewResourceId;
			resId2 = dividerResourceId;
		}

		@Override
		public int getItemViewType( int position ) {
			if( getViewTypeCount() > 1 ) {
				if ( position % 4 == 1 ) {
					return 1;
				}
			}
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {

			View view;
			int type = getItemViewType( position );

			if ( convertView == null ) {
				view = LayoutInflater.from( getContext() ).inflate( type == 0 ? resId1 : resId2, parent, false );
				view.setLayoutParams( new HorizontalVariableListView.LayoutParams(
						HorizontalVariableListView.LayoutParams.WRAP_CONTENT, HorizontalVariableListView.LayoutParams.WRAP_CONTENT ) );
			} else {
				view = convertView;
			}

			if ( type == 0 ) {
				TextView text = (TextView) view.findViewById( R.id.text );
				text.setText( "Image " + String.valueOf( position ) );
			}

			return view;
		}

	}
}
