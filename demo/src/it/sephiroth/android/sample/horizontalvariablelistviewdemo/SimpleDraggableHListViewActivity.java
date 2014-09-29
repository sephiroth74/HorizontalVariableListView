package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import it.sephiroth.android.library.widget.AdapterView;
import it.sephiroth.android.library.widget.DraggableHorizontalListView;

/**
 * Created by robertwang on 9/11/14.
 */
public class SimpleDraggableHListViewActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final String TAG = SimpleDraggableHListViewActivity.class.getSimpleName();
    private TestAdapter mAdapter;
    private DraggableHorizontalListView listView;
    private ArrayList<String> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_draggable_hlistview);

        items = new ArrayList<String>();
        for (int i = 0; i < 50; i++) {
            items.add(String.valueOf(i));
        }

        listView.setList(items);

        mAdapter = new TestAdapter(this, R.layout.test_item_1, android.R.id.text1, items);
        listView.setHeaderDividersEnabled(true);
        listView.setFooterDividersEnabled(true);

        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);

        //if( listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE ) {
        listView.setOnItemClickListener(this);
        //}

        listView.setAdapter(mAdapter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        String finalContent = "";
        for (String item : items) {
            if (items.indexOf(item) == items.size() - 1) {
                finalContent += item;
            } else {
                finalContent += item + ",";
            }
        }
        Log.d(TAG, "Final content: " + finalContent);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        listView = (DraggableHorizontalListView) findViewById(R.id.draggable_hListView);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

    }

    class TestAdapter extends ArrayAdapter<String> {

        private static final long INVALID_ID = -1;
        List<String> mItems;
        LayoutInflater mInflater;
        int mResource;
        int mTextResId;

        public TestAdapter(Context context, int resourceId, int textViewResourceId, List<String> objects) {
            super(context, resourceId, textViewResourceId, objects);
            mInflater = LayoutInflater.from(context);
            mResource = resourceId;
            mTextResId = textViewResourceId;
            mItems = objects;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {

            if (position < 0 || position >= mItems.size()) {
                return INVALID_ID;
            }
            return getItem(position).hashCode();
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
        public View getView(int position, View convertView, ViewGroup parent) {

            if (null == convertView) {
                convertView = mInflater.inflate(mResource, parent, false);
            }

            TextView textView = (TextView) convertView.findViewById(mTextResId);
            textView.setText(getItem(position));

            int type = getItemViewType(position);

            ViewGroup.LayoutParams params = convertView.getLayoutParams();
            if (type == 0) {
                params.width = getResources().getDimensionPixelSize(R.dimen.item_size_1);
            } else if (type == 1) {
                params.width = getResources().getDimensionPixelSize(R.dimen.item_size_2);
            } else {
                params.width = getResources().getDimensionPixelSize(R.dimen.item_size_3);
            }

            return convertView;
        }
    }
}
