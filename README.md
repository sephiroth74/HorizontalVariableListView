Horizontal Variable ListView
==========================

## Description
 Horizontal list view for Android which allows variable items widths and heights.

## Features
* It extends the AdapterView in order to give the full support to adapters.
* Customizable left and right edges. A left and right edge glow effect will be shown when the list reaches one of the edges. It can be enabled by setting the overscroll mode to OVER_SCROLL_ALWAYS:

		list.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );
		
	
* It supports multiple items type. Just override the ```getViewTypeCount()``` in your adapter. Each item (by type) can have a different width.
* It uses a recycler in order to reuse recycled items instead of creating new ones every time.

* It supports for single/multiple selection using the `setSelectionMode` method.

## Selection

You can control if a single item can be clicked and how it will handle the selected status inside the list.

First, assign the `setOnItemClickedListener` in this way:
    		
    mList.setOnItemClickedListener( new OnItemClickedListener() {

		@Override
		public boolean onItemClick( AdapterView<?> parent, View view, int position, long id ) {
			Log.i( LOG_TAG, "onItemClick: " + position );
			return true;
		}
	});
	
In this way every time an item has clicked, this method will be triggered. If this method will return `true` then the selection process will continue inside the HorizontalVariableListView, otherwise the event is stopped and.

If the previous method returned a `true` you will receive a `onItemSelected` or a `onNothingSelected` of the `onItemSelectedListener`. Example:

	mList.setOnItemSelectedListener( new OnItemSelectedListener() {

		@Override
		public void onItemSelected( AdapterView<?> parent, View view, int position, long id ) {
				// if you allow multiple selection, then you can
				// query the total selected items:
				int[] positions = mList.getSelectedPositions().length );
			}

			@Override
			public void onNothingSelected( android.widget.AdapterView<?> parent ) {
				// nothing is selected
			};
	});


## Example
See the Demo [MainActivity][1] for a working sample.

## License
This software is distributed under the MIT License:
http://opensource.org/licenses/MIT

---

> Author
> [Alessandro Crugnola][2]


[1]: https://github.com/sephiroth74/HorizontalVariableListView/blob/master/Demo/src/it/sephiroth/android/sample/horizontalvariablelistviewdemo/MainActivity.java        "MainActivity"

[2]: http://www.sephiroth.it
