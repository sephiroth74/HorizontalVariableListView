package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.widget.BaseAdapterExtended;
import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnItemClickedListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView.SelectionMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
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
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {

	private static final String LOG_TAG = "main-activity";

	public static boolean USE_MULTIPLE_VIEWTYPES = false;
	public static final int DIVIDER_WIDTH = 30;
	public static final boolean USE_VLIST = false;

	int labelIndex = 0;
	int textIndex = 0;

	HorizontalVariableListView mList;
	ListView mList2;
	ListAdapter mAdapter;

	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		List<String> data = new ArrayList<String>();
		for ( int i = 0; i < 7; i++ ) {
			data.add( getNextValue() );
		}

		mAdapter = new ListAdapter( this, R.layout.view1, R.layout.divider, data );

		// change the selection mode: single or multiple
		mList.setSelectionMode( SelectionMode.Multiple );
		mList.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );
		
		if( USE_VLIST ) {
			mList.setVisibility( View.GONE );
			mList2.setAdapter( mAdapter );
		} else {
			mList2.setVisibility( View.GONE );
			mList.setAdapter( mAdapter );
		}

		// children gravity ( top, center, bottom )
		mList.setGravity( Gravity.CENTER );
	}

	private String getNextValue() {
		labelIndex++;
		String value;
		if ( USE_MULTIPLE_VIEWTYPES ) {
			if ( labelIndex % 4 == 0 ) {
				value = null;
			} else {
				value = String.valueOf( textIndex++ );
			}
		} else {
			value = String.valueOf( textIndex++ );
		}
		return value;
	}

	@Override
	public void onConfigurationChanged( Configuration newConfig ) {
		super.onConfigurationChanged( newConfig );
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mList = (HorizontalVariableListView) findViewById( R.id.list );
		mList2 = (ListView) findViewById( R.id.list2 );


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
				Log.d( LOG_TAG, "onItemSelected: " + position + ", selected items: " + mList.getSelectedPositions().length );
			}

			@Override
			public void onNothingSelected( android.widget.AdapterView<?> parent ) {
				Log.d( LOG_TAG, "onNothingSelected" );
			};

		} );
		
		mList2.setOnItemSelectedListener( new OnItemSelectedListener() {
			
			@Override
			public void onItemSelected( AdapterView<?> arg0, View arg1, int arg2, long arg3 ) {
				Log.d( LOG_TAG, "onItemSelected: " + arg2 );
			}
			
			@Override
			public void onNothingSelected( AdapterView<?> arg0 ) {
				Log.d( LOG_TAG, "onNothingSelected" );
			}
			
		} );
		
		mList2.setOnItemClickListener( new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick( AdapterView<?> arg0, View arg1, int arg2, long arg3 ) {
				Log.i( LOG_TAG, "onItemClick: " + arg2 );
				
			}
		} );
		

		// let's select the first item by default
		mList.setSelectedPosition( 0, false );

		findViewById( R.id.button_add_before ).setOnClickListener( this );
		findViewById( R.id.button_add_in_range ).setOnClickListener( this );
		findViewById( R.id.button_add_after ).setOnClickListener( this );
		findViewById( R.id.button_delete_before ).setOnClickListener( this );
		findViewById( R.id.button_delete_in_range ).setOnClickListener( this );
		findViewById( R.id.button_delete_after ).setOnClickListener( this );
		findViewById( R.id.button_adds_before ).setOnClickListener( this );
		findViewById( R.id.button_adds_in_range ).setOnClickListener( this );
		findViewById( R.id.button_adds_after ).setOnClickListener( this );
		findViewById( R.id.button_replace_before ).setOnClickListener( this );
		findViewById( R.id.button_replace_after ).setOnClickListener( this );
		findViewById( R.id.button_replace_in_range ).setOnClickListener( this );
		findViewById( R.id.button_smoothscroll ).setOnClickListener( this );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate( R.menu.activity_main, menu );
		return true;
	}

	class ListAdapter extends BaseAdapterExtended<String> {

		Object mLock = new Object();
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

			if ( getViewTypeCount() > 1 ) {
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
		public String getItem( int position ) {
			return objects.get( position );
		}

		@Override
		public long getItemId( int position ) {
			if ( position >= 0 && position < getCount() ) {
				String value = this.objects.get( position );
				if ( null != value )
					return value.hashCode();
				else
					return -1;
			}
			return -1;
		}

		/**
		 * new item added
		 * 
		 * @param value
		 */
		public void add( String value ) {
			synchronized ( mLock ) {
				Log.i( LOG_TAG, "add value: " + value );
				this.objects.add( value );
			}
			this.notifyDataSetAdded( this.objects.size() - 1 );
		}

		public void addAll( Collection<String> values ) {
			synchronized ( mLock ) {
				this.objects.addAll( values );
			}
			this.notifyDataSetAdded( this.objects.size() - values.size() );
		}

		public void addAll( int position, Collection<String> values ) {
			synchronized ( mLock ) {
				this.objects.addAll( position, values );
			}
			this.notifyDataSetAdded( position );
		}
		
		public void replace( int position, String newValue ) {
			final int viewType = getItemViewType( position );
			synchronized ( mLock ) {
				this.objects.remove( position );
				this.objects.add( position, newValue );
			}
			this.notifyDataSetReplaced( position, viewType );
		}

		/**
		 * Item has been removed, we need to dispatch also the current item viewType because otherwise it is not more available from
		 * the adapter
		 * 
		 * @param position
		 */
		public void remove( int position ) {
			int viewType;
			synchronized ( mLock ) {
				Log.i( LOG_TAG, "remove position: " + position );
				viewType = getItemViewType( position );
				this.objects.remove( position );
			}
			this.notifyDataSetRemoved( position, viewType );
		}

		/**
		 * Item has been inserted
		 * 
		 * @param value
		 * @param position
		 */
		public void insert( String value, int position ) {
			synchronized ( mLock ) {
				Log.i( LOG_TAG, "insert value: " + value + " at position: " + position );
				this.objects.add( position, value );
			}
			this.notifyDataSetAdded( position );
		}

	}

	@Override
	public void onClick( View v ) {
		final int id = v.getId();
		ListAdapter adapter = mAdapter;
		Random random = new Random( System.currentTimeMillis() );
		int count = adapter.getCount();
		int next;
		String value;
		List<String> collection;

		int first, last;
		
		if( USE_VLIST ) {
			first = mList2.getFirstVisiblePosition();
			last = mList2.getLastVisiblePosition();
		} else {
			first = mList.getFirstVisiblePosition();
			last = mList.getLastVisiblePosition();
		}

		switch ( id ) {
		// add single
			case R.id.button_add_before:
				if ( count > 0 )
					adapter.insert( getNextValue(), first == 0 ? 0 : first - 1 );
				else
					adapter.add( getNextValue() );
				break;

			case R.id.button_add_in_range:
				if ( count > 0 )
					adapter.insert( getNextValue(), first + ( last - first ) / 2 );
				else
					adapter.add( getNextValue() );
				break;

			case R.id.button_add_after:
				adapter.add( getNextValue() );
				break;

			// delete single
			case R.id.button_delete_before:
				if ( count > 0 ) {
					adapter.remove( first > 0 ? first - 1 : 0 );
				}
				break;

			case R.id.button_delete_in_range:
				if ( count > 0 ) {
					adapter.remove( first + ( last - first ) / 2 );
				}
				break;

			case R.id.button_delete_after:
				if ( count > 0 ) {
					adapter.remove( count - 1 );
				}
				break;

			// add multiple
			case R.id.button_adds_before:
				collection = new ArrayList<String>();
				for ( int i = 0; i < 3; i++ ) {
					collection.add( getNextValue() );
				}
				if ( count > 0 )
					adapter.addAll( first == 0 ? 0 : first - 1, collection );
				else
					adapter.addAll( collection );
				break;

			case R.id.button_adds_in_range:
				collection = new ArrayList<String>();
				for ( int i = 0; i < 3; i++ ) {
					collection.add( getNextValue() );
				}
				adapter.addAll( first + ( last - first ) / 2, collection );
				break;

			case R.id.button_adds_after:
				collection = new ArrayList<String>();
				for ( int i = 0; i < 3; i++ ) {
					collection.add( getNextValue() );
				}
				adapter.addAll( last + 1, collection );
				break;
				
			case R.id.button_replace_before:
				adapter.replace( 0, getNextValue() );
				break;
				
			case R.id.button_replace_in_range:
				adapter.replace( first + ( last - first ) / 2, getNextValue() );
				break;
				
			case R.id.button_replace_after:
				adapter.replace( last, getNextValue() );
				break;
				
			case R.id.button_smoothscroll:
				
				if( USE_VLIST ) {
					mList2.smoothScrollBy( mList2.getChildAt( 0 ).getWidth() * 20, 250 );
				} else {
					
					final int finalX = (int) (mList.getChildAt( 0 ).getWidth() * 3.5 );
					
					mList.post( new Runnable() {
						
						@Override
						public void run() {
							int clamped = mList.computeScroll( finalX );
							Log.d( LOG_TAG, "run to: " + finalX + ", clamp: " + clamped );
							
							if( clamped != 0 ) {
								mList.smoothScrollBy( finalX, 60 * 50 );
							} else {
								clamped = mList.scrollTo( finalX );
							
								if( clamped != 0 ) {
									mList.post( this );
								}
							}
						}
					} );
					
					// int clamped = mList.scrollTo( mList.getChildAt( 0 ).getWidth() * 20 );
					//Log.d( LOG_TAG, "clamped: " + clamped );
					// mList.smoothScrollBy( mList.getChildAt( 0 ).getWidth() * 20, 500 );
				}
				break;
		}
	}
}
