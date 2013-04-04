package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.widget.BaseAdapterExtended;
import it.sephiroth.android.library.widget.HorizontalListView.OnLayoutChangeListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnItemClickedListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView.SelectionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {

	private static final String LOG_TAG = "main-activity";

	int labelIndex = 0;
	HorizontalVariableListView mList;
	TextView mText;

	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		List<String> data = new ArrayList<String>();
		String value;
		for ( int i = 0; i < 20; i++ ) {
			value = String.valueOf( labelIndex++ );
			if( i > 0 && ( i%4 == 0 ) ) {
				//value = null;
				//labelIndex--;
			}
			data.add( value );
		}

		ListAdapter adapter = new ListAdapter( this, R.layout.view1, R.layout.divider, data );

		// change the selection mode: single or multiple
		mList.setSelectionMode( SelectionMode.Multiple );

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
					mList.setEdgeHeight( 200 );
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
		
		// let's select the first item by default
		mList.setSelectedPosition( 0, false );
		
		findViewById( R.id.button_add_before ).setOnClickListener( this );
		findViewById( R.id.button_add_in_range ).setOnClickListener( this );
		findViewById( R.id.button_add_after ).setOnClickListener( this );
		findViewById( R.id.button_delete_before ).setOnClickListener( this );
		findViewById( R.id.button_delete_in_range ).setOnClickListener( this );
		findViewById( R.id.button_delete_after ).setOnClickListener( this );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate( R.menu.activity_main, menu );
		return true;
	}

	class ListAdapter extends BaseAdapterExtended {

		Context context;
		List<String> objects;
		int resId1;
		int resId2;

		public ListAdapter( Context context, int textViewResourceId, int dividerResourceId, List<String> objects ) {
			this.context = context;
			this.objects = objects;
			resId1 = textViewResourceId;
			resId2 = dividerResourceId;
		}

		@Override
		public int getItemViewType( int position ) {
			
			if( getViewTypeCount() > 1 ) {
				return getItem( position ) == null ? 1 : 0;
			}
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {
			Log.i( LOG_TAG, "getView: " + position + ", type: " + getItemViewType( position ) + ", converView: " + convertView );

			View view;
			int type = getItemViewType( position );

			if ( convertView == null ) {
				view = LayoutInflater.from( context ).inflate( type == 0 ? resId1 : resId2, parent, false );
				view.setLayoutParams( new HorizontalVariableListView.LayoutParams(
						HorizontalVariableListView.LayoutParams.WRAP_CONTENT, HorizontalVariableListView.LayoutParams.WRAP_CONTENT ) );
			} else {
				view = convertView;
			}

			if ( type == 0 ) {
				TextView text = (TextView) view.findViewById( R.id.text );
				text.setText( "Image " + getItem( position ) );
				Log.d( LOG_TAG, "text: " + text.getText() );
			}

			return view;
		}
		
		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public int getCount() {
			return objects.size();
		}

		@Override
		public Object getItem( int position ) {
			return objects.get( position );
		}

		@Override
		public long getItemId( int position ) {
			return this.objects.get( position ).hashCode();
		}

		public void add( String value ) {
			Log.i( LOG_TAG, "add value: " + value );
			this.objects.add( value );
			this.notifyDataSetAdded( this.objects.size()-1 );
		}

		public void remove( int position ) {
			Log.i( LOG_TAG, "remove position: " + position );
			int viewType = getItemViewType( position );
			this.objects.remove( position );
			this.notifyDataSetRemoved( position, viewType );
		}

		public void insert( String value, int position ) {
			Log.i( LOG_TAG, "insert value: " + value + " at position: " + position );
			this.objects.add( position, value );
			this.notifyDataSetAdded( position );
		}

	}

	@Override
	public void onClick( View v ) {
		final int id = v.getId();
		ListAdapter adapter = (ListAdapter) mList.getAdapter();
		Random random = new Random( System.currentTimeMillis() );
		int count = adapter.getCount();
		int next;
		String value;
		
		int first = mList.getFirstVisiblePosition();
		int last = mList.getLastVisiblePosition();
		
		switch( id ) {
			case R.id.button_add_before:
				value = String.valueOf( labelIndex++ );
				//value = null;
				adapter.insert( value, first == 0 ? 0 : first - 1 );
				break;
				
			case R.id.button_add_in_range:
				value = String.valueOf( labelIndex++ );
				//value = null;
				adapter.insert( value, first + (last-first)/2 );
				break;
				
			case R.id.button_add_after:
				value = String.valueOf( labelIndex++ );
				//value = null;
				adapter.add( value );
				break;
				
				
			case R.id.button_delete_before:
				if( count > 0 ) {
					adapter.remove( first > 0 ? first - 1 : 0 );
				}
				break;
				
			case R.id.button_delete_in_range:
				if( count > 0 ) {
					adapter.remove( first + ( last-first)/2 );
				}
				break;
				
			case R.id.button_delete_after:
				if( count > 0 ) {
					adapter.remove( count - 1 );
				}
				break;
		}
	}
}
