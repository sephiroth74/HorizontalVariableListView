package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import it.sephiroth.android.library.widget.AdapterView;
import it.sephiroth.android.library.widget.AdapterView.OnItemClickListener;
import it.sephiroth.android.library.widget.HListView;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

@TargetApi(11)
public class GridViewActivity
		extends Activity
		implements OnItemClickListener {

	private static final String LOG_TAG = "GridViewActivity";

	private ListView listView;
	private BaseAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_grid_view);

		List<Row> rows = new ArrayList<Row>();
		for (int i = 0; i < 50; i++) {
			Row row = new Row("Row " + i);
			for (int j = 0; j < 50; j++) {
				row.mItems.add(new Column(i + ":" + j, i * 50 + j));
			}
			rows.add(row);
		}
		
		mAdapter = new VerticalAdapter(this, rows);
		
		listView = (ListView)findViewById(R.id.list_view);
		listView.setAdapter(mAdapter);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		Log.i(LOG_TAG, "onItemClick: " + position);
		Log.d(LOG_TAG, "checked items: " + listView.getCheckedItemCount());
		Log.d(LOG_TAG, "checked positions: " + listView.getCheckedItemPositions());
	}
	
	private static class Row {
		public String mTitle;
		public List<Column> mItems;
		
		public Row(String title) {
			mTitle = title;
			mItems = new ArrayList<Column>();
		}
	}
	
	private static class Column {
		public String mTitle;
		public long mId;
		
		public Column(String title, long id) {
			mTitle = title;
			mId = id;
		}
	}
	
	private static class VerticalAdapter
			extends ArrayAdapter<Row> {

		private static class ViewHolder {
			public TextView mTitle;
			public HListView mHListView;
		}

		private List<HorizontalAdapter> mHorizontalAdapters;
		private LayoutInflater mInflater;
		private int mResource;

		public VerticalAdapter(Context context, List<Row> rows) {
			super(context, R.layout.grid_view_row, R.id.title, rows);

			mInflater = LayoutInflater.from(context);
			mResource = R.layout.grid_view_row;

			mHorizontalAdapters = new ArrayList<HorizontalAdapter>();

			for (Row row : rows) {
				mHorizontalAdapters.add(new HorizontalAdapter(context, row.mItems));
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder vh;
			if (convertView == null) {
				convertView = mInflater.inflate(mResource, parent, false);
				convertView.setTag(vh = new ViewHolder());

				vh.mTitle = (TextView) convertView.findViewById(R.id.title);
				vh.mHListView = (HListView)convertView.findViewById(R.id.horizontal_list);
			} else {
				vh = (ViewHolder) convertView.getTag();
			}

			Row row = getItem(position);
			vh.mTitle.setText(row.mTitle);
			
			HorizontalAdapter adapter = mHorizontalAdapters.get(position);
			vh.mHListView.setAdapter(adapter);
			vh.mHListView.setOnItemClickListener(adapter);

			return convertView;
		}
	}

	private static class HorizontalAdapter
			extends ArrayAdapter<Column>
			implements OnItemClickListener {

		private Resources mRes;
		private LayoutInflater mInflater;
		private int mResource;
		private int mTextResId;

		public HorizontalAdapter(Context context, List<Column> objects) {
			super(context, R.layout.test_item_1, android.R.id.text1, objects);

			mInflater = LayoutInflater.from(context);
			mRes = context.getResources();
			mResource = R.layout.test_item_1;
			mTextResId = android.R.id.text1;
		}

		@Override
		public int getViewTypeCount() {
			return 3;
		}

		@Override
		public int getItemViewType(int position) {
			return position % 3;
		}
		
		@Override
		public long getItemId(int position) {
			return getItem(position).mId;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = mInflater.inflate(mResource, parent, false);
			}

			TextView textView = (TextView) convertView.findViewById(mTextResId);
			textView.setText(getItem(position).mTitle);

			int type = getItemViewType(position);

			LayoutParams params = convertView.getLayoutParams();
			if (type == 0) {
				params.width = mRes.getDimensionPixelSize(R.dimen.item_size_1);
			} else if (type == 1) {
				params.width = mRes.getDimensionPixelSize(R.dimen.item_size_2);
			} else {
				params.width = mRes.getDimensionPixelSize(R.dimen.item_size_3);
			}

			return convertView;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Log.v(LOG_TAG, String.format("Clicked on \"%s\" - item id %d",
					getItem(position).mTitle, id));
		}
	}
}
