package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnLayoutChangeListener;
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
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String LOG_TAG = "main-activity";

	HorizontalVariableListView mList;

	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		List<String> data = new ArrayList<String>();
		for ( int i = 0; i < 20; i++ ) {
			data.add( String.valueOf( i ) );
		}

		ListAdapter adapter = new ListAdapter( this, R.layout.view1, R.layout.divider, data );
		mList.setOverScrollMode( View.OVER_SCROLL_ALWAYS );
		mList.setEdgeGravityY( Gravity.BOTTOM );
		mList.setAdapter( adapter );

	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mList = (HorizontalVariableListView) findViewById( R.id.list );
		mList.setOnLayoutChangeListener( new OnLayoutChangeListener() {

			@Override
			public void onLayoutChange( boolean changed, int left, int top, int right, int bottom ) {
				Log.d( LOG_TAG, "onLayoutChange: " + changed + ", " + bottom + ", " + top );
				if ( changed ) {
					mList.setEdgeHeight( bottom - top );
				}
			}
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
			if ( position % 4 == 1 ) {
				return 1;
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
						HorizontalVariableListView.LayoutParams.WRAP_CONTENT, 200 ) );
			} else {
				view = convertView;
			}

			if ( type == 0 ) {
				TextView text = (TextView) view.findViewById( R.id.text );
				text.setText( String.valueOf( position ) );
			}

			return view;
		}

	}
}
