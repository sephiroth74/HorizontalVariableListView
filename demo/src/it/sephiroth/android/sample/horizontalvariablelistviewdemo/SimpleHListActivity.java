package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.util.v11.MultiChoiceModeListener;
import it.sephiroth.android.library.widget.AdapterView;
import it.sephiroth.android.library.widget.AdapterView.OnItemClickListener;
import it.sephiroth.android.library.widget.HListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.util.SparseArrayCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

@TargetApi(11)
public class SimpleHListActivity extends Activity implements OnClickListener, OnItemClickListener {
	
	private static final String LOG_TAG = "SimpleHListActivity";
	HListView listView;
	Button mButton1;
	Button mButton2;
	Button mButton3;
	TestAdapter mAdapter;

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		
		setContentView( R.layout.activity_main );
		
		List<String> items = new ArrayList<String>();
		for( int i = 0; i < 50; i++ ) {
			items.add( String.valueOf( i ) );
		}
		mAdapter = new TestAdapter( this, R.layout.test_item_1, android.R.id.text1, items );
		listView.setHeaderDividersEnabled( true );
		listView.setFooterDividersEnabled( true );
		
		if( listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE_MODAL ) {
			listView.setMultiChoiceModeListener( new MultiChoiceModeListener() {
				
				@Override
				public boolean onPrepareActionMode( ActionMode mode, Menu menu ) {
					return true;
				}
				
				@Override
				public void onDestroyActionMode( ActionMode mode ) {
				}
				
				@Override
				public boolean onCreateActionMode( ActionMode mode, Menu menu ) {
					menu.add( 0, 0, 0, "Delete" );
					return true;
				}
				
				@Override
				public boolean onActionItemClicked( ActionMode mode, MenuItem item ) {
					Log.d( LOG_TAG, "onActionItemClicked: " + item.getItemId() );
					
					final int itemId = item.getItemId();
					if( itemId == 0 ) {
						deleteSelectedItems();
					}
					
					mode.finish();
					return false;
				}
				
				@Override
				public void onItemCheckedStateChanged( ActionMode mode, int position, long id, boolean checked ) {
					mode.setTitle( "What the fuck!" );
					mode.setSubtitle( "Selected items: " + listView.getCheckedItemCount() );
				}
			} );
		} else if( listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE ) {
			listView.setOnItemClickListener( this );
		}
		
		
		listView.setAdapter( mAdapter );
		
		mButton1.setOnClickListener( this );
		mButton2.setOnClickListener( this );
		mButton3.setOnClickListener( this );
		
		Log.i( LOG_TAG, "choice mode: " + listView.getChoiceMode() );
	}
	
	@Override
	public void onContentChanged() {
		super.onContentChanged();
		listView = (HListView) findViewById( R.id.hListView1 );
		mButton1 = (Button) findViewById( R.id.button1 );
		mButton2 = (Button) findViewById( R.id.button2 );
		mButton3 = (Button) findViewById( R.id.button3 );
		
	}

	
	@Override
	public void onClick( View v ) {
		final int id = v.getId();
		
		if( id == mButton1.getId() ) {
			addElements();
		} else if( id == mButton2.getId() ) {
			removeElements();
		} else if( id == mButton3.getId() ) {
			scrollList();
		}
	}	
	
	private void scrollList() {
		listView.smoothScrollBy( 1500, 300 );
	}
	
	private void addElements() {
		for( int i = 0; i < 5; i++ ) {
			mAdapter.mItems.add( Math.min( mAdapter.mItems.size(), 2), String.valueOf( mAdapter.mItems.size() ) );
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private void removeElements() {
		for( int i = 0; i < 5; i++ ) {
			if( mAdapter.mItems.size() > 0 ) {
				mAdapter.mItems.remove( Math.min( mAdapter.mItems.size()-1, 2 ) );
			}
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private void deleteSelectedItems() {
		SparseArrayCompat<Boolean> checkedItems = listView.getCheckedItemPositions();
		ArrayList<Integer> sorted = new ArrayList<Integer>( checkedItems.size() );
		
		Log.i( LOG_TAG, "deleting: " + checkedItems.size() );
		
		for( int i = 0; i < checkedItems.size(); i++ ) {
			if( checkedItems.valueAt( i ) ) {
				sorted.add( checkedItems.keyAt( i ) );
			}
		}

		Collections.sort( sorted );
		
		for( int i = sorted.size()-1; i >= 0; i-- ) {
			int position = sorted.get( i );
			Log.d( LOG_TAG, "Deleting item at: " + position );
			mAdapter.mItems.remove( position );
		}
		mAdapter.notifyDataSetChanged();
	}
	
	@Override
	public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
		Log.i( LOG_TAG, "onItemClick: " + position );
		Log.d( LOG_TAG, "checked items: " + listView.getCheckedItemCount() );
		Log.d( LOG_TAG, "checked positions: " + listView.getCheckedItemPositions() );
	}
	
	class TestAdapter extends ArrayAdapter<String> {
		
		List<String> mItems;
		LayoutInflater mInflater;
		int mResource;
		int mTextResId;
		
		public TestAdapter( Context context, int resourceId, int textViewResourceId, List<String> objects ) {
			super( context, resourceId, textViewResourceId, objects );
			mInflater = LayoutInflater.from( context );
			mResource = resourceId;
			mTextResId = textViewResourceId;
			mItems = objects;
		}
		
		@Override
		public boolean hasStableIds() {
			return true;
		}
		
		@Override
		public long getItemId( int position ) {
			return getItem( position ).hashCode();
		}
		
		@Override
		public int getViewTypeCount() {
			return 3;
		}
		
		@Override
		public int getItemViewType( int position ) {
			return position%3;
		}
		
		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {
			
			if( null == convertView ) {
				convertView = mInflater.inflate( mResource, parent, false );
			}
			
			TextView textView = (TextView) convertView.findViewById( mTextResId );
			textView.setText( getItem( position ) );
			
			int type = getItemViewType( position );
			
			LayoutParams params = convertView.getLayoutParams();
			if( type == 0 ) {
				params.width = getResources().getDimensionPixelSize( R.dimen.item_size_1 );
			} else if( type == 1 ) {
				params.width = getResources().getDimensionPixelSize( R.dimen.item_size_2 );
			} else {
				params.width = getResources().getDimensionPixelSize( R.dimen.item_size_3 );
			}
			
			return convertView;
		}
	}
}
