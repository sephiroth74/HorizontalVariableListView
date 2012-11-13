Horizontal Variable ListView
==========================

## Description
 Horizontal list view for Android which allows variable items widths.

## Features
* It extends the AdapterView in order to give the full support to adapters.
* Customizable left and right edges. A left and right edge glow effect will be shown when the list reaches one of the edges. It can be enabled by setting the overscroll mode to OVER_SCROLL_ALWAYS:

		list.setOverScrollMode( View.OVER_SCROLL_ALWAYS );
		
	
* It supports multiple items type. Just override the ```getViewTypeCount()``` in your adapter. Each item (by type) can have a different width.
* It uses a recycler in order to reuse recycled items instead of creating new ones every time.

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
