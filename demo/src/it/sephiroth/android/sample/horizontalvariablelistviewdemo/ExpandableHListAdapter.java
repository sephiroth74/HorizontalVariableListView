package it.sephiroth.android.sample.horizontalvariablelistviewdemo;

import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

public class ExpandableHListAdapter extends BaseExpandableListAdapter {

	private Context _context;
	private List<String> _listDataHeader; // header titles
	// child data in format of header title, child title
	private HashMap<String, List<String>> _listDataChild;

	public ExpandableHListAdapter(
			Context context, List<String> listDataHeader, HashMap<String, List<String>> listChildData ) {
		this._context = context;
		this._listDataHeader = listDataHeader;
		this._listDataChild = listChildData;
	}

	@Override
	public Object getChild( int groupPosition, int childPosititon ) {
		return this._listDataChild.get( this._listDataHeader.get( groupPosition ) ).get( childPosititon );
	}

	@Override
	public View getChildView(
			int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent ) {

		final String childText = (String) getChild( groupPosition, childPosition );

		if( convertView == null ) {
			LayoutInflater infalInflater = (LayoutInflater) this._context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
			convertView = infalInflater.inflate( R.layout.test_item_1, null );
		}

		TextView txtListChild = (TextView) convertView.findViewById( android.R.id.text1 );

		txtListChild.setText( childText );
		return convertView;
	}

	@Override
	public int getChildrenCount( int groupPosition ) {
		return this._listDataChild.get( this._listDataHeader.get( groupPosition ) ).size();
	}

	@Override
	public Object getGroup( int groupPosition ) {
		return this._listDataHeader.get( groupPosition );
	}

	@Override
	public int getGroupCount() {
		return this._listDataHeader.size();
	}

	@Override
	public long getGroupId( int groupPosition ) {
		return getGroup( groupPosition ).hashCode();
	}

	@Override
	public long getChildId( int groupPosition, int childPosition ) {
		return ( getGroup( groupPosition ) + "_" + getChild( groupPosition, childPosition ) ).hashCode();
	}

	@Override
	public View getGroupView(
			int groupPosition, boolean isExpanded, View convertView, ViewGroup parent ) {
		String headerTitle = (String) getGroup( groupPosition );
		if( convertView == null ) {
			LayoutInflater infalInflater = (LayoutInflater) this._context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
			convertView = infalInflater.inflate( R.layout.test_item_1, null );
		}

		TextView lblListHeader = (TextView) convertView.findViewById( android.R.id.text1 );
		lblListHeader.setTypeface( null, Typeface.BOLD );
		lblListHeader.setText( headerTitle );

		return convertView;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public long getCombinedChildId( final long groupId, final long childId ) {
		return super.getCombinedChildId( groupId, childId );
	}

	@Override
	public long getCombinedGroupId( final long groupId ) {
		return super.getCombinedGroupId( groupId );
	}

	@Override
	public boolean isChildSelectable( int groupPosition, int childPosition ) {
		return true;
	}
}