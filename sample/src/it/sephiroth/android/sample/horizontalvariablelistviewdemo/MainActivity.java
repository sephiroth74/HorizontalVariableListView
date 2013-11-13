package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.util.v11.MultiChoiceModeListener;
import it.sephiroth.android.library.widget.HListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;

public class MainActivity extends Activity implements OnClickListener, MultiChoiceModeListener {
	
	private static final String LOG_TAG = "MainActivity";
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
			items.add( "Image " + i );
		}
		
		mAdapter = new TestAdapter( this, R.layout.test_item_1, android.R.id.text1, items );
		listView.setHeaderDividersEnabled( true );
		listView.setFooterDividersEnabled( true );
		
		if( listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE_MODAL ) {
			listView.setMultiChoiceModeListener( this );
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
			mAdapter.mItems.add( Math.min( mAdapter.mItems.size(), 2), "Image " + mAdapter.mItems.size() );
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
		SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
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
	
	class TestAdapter extends ArrayAdapter<String> {
		
		List<String> mItems;
		
		public TestAdapter( Context context, int resourceId, int textViewResourceId, List<String> objects ) {
			super( context, resourceId, textViewResourceId, objects );
			mItems = objects;
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
			
			View view = super.getView( position, convertView, parent );
			
			ImageView image = (ImageView) view.findViewById( R.id.image );
			
			int type = getItemViewType( position );
			
			LayoutParams params = view.getLayoutParams();
			if( type == 0 ) {
				params.width = 200;
			} else if( type == 1 ) {
				params.width = 250;
			} else {
				params.width = 300;
			}
			
			return view;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public boolean onActionItemClicked( ActionMode mode, MenuItem item ) {
		Log.d( LOG_TAG, "onActionItemClicked: " + item.getItemId() );
		
		final int itemId = item.getItemId();
		if( itemId == 0 ) {
			deleteSelectedItems();
		}
		
		mode.finish();
		return false;
	}

	public boolean onCreateActionMode( ActionMode mode, Menu menu ) {
		menu.add( 0, 0, 0, "Delete" );
		return true;
	}

	public void onDestroyActionMode( ActionMode mode ) {
		
	}

	public boolean onPrepareActionMode( ActionMode mode, Menu menu ) {
		return true;
	}

	@SuppressLint("NewApi")
	public void onItemCheckedStateChanged( ActionMode mode, int position, long id, boolean checked ) {
		mode.setTitle( "What the fuck!" );
		mode.setSubtitle( "Selected items: " + listView.getCheckedItemCount() );
	}
}
