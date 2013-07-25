Horizontal Variable ListView
==========================

Horizontal ListView for Android. Based on the official [ListView][3] google code.

## Features
It supports almost all the features of the ListView widget.
There are minor differences in the attributes supported like "dividerWidth" instead of the default "dividerHeight".

This is the styleable used for the HListView class:
<pre>
    &lt;declare-styleable name="HListView">
        &lt;attr name="android:entries" />
        &lt;attr name="android:divider" />
        &lt;attr name="dividerWidth" format="dimension" />
        &lt;attr name="headerDividersEnabled" format="boolean" />
        &lt;attr name="footerDividersEnabled" format="boolean" />
        &lt;attr name="overScrollHeader" format="reference|color" />
        &lt;attr name="overScrollFooter" format="reference|color" />
        
        &lt;!-- 
        When "wrap_content" is used as value of the layout_height property.
        Pass the position, inside the adapter, of the view being used to measure the view
        or '-1' to use the default behavior ( default is -1 )
        -->
        &lt;attr name="measureWithChild" format="integer" />        
    &lt;/declare-styleable>
</pre>

### API Requirements
The minimum supported Android version is android 2.3

### Example
See the Demo [MainActivity][1] for a working sample.

## License
This software is distributed under the MIT License:
http://opensource.org/licenses/MIT

---

> Author
> [Alessandro Crugnola][2]


[1]: https://github.com/sephiroth74/HorizontalVariableListView/blob/master/Demo/src/it/sephiroth/android/sample/horizontalvariablelistviewdemo/MainActivity.java        "MainActivity"

[2]: http://www.sephiroth.it

[3]: http://developer.android.com/reference/android/widget/ListView.html