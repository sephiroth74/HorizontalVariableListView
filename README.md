Deprecated
==========================
This widget is now *deprecated* and it won't be updated anymore. Use [RecyclerView][5] instead

Horizontal Variable ListView
==========================

Horizontal ListView for Android. Based on the official [ListView][3] google code.
The library includes also an ExpandableHListView, also based on the official [ExpandableListView][4]. <br />
See the demo project for sample implementations

## Usage (gradle)
Add this line to your dependency group:


	compile 'it.sephiroth.android.library.horizontallistview:hlistview:1.2.2'

## Features
It supports almost all the features of the ListView widget.
There are minor differences in the attributes supported like "hlv_dividerWidth" instead of the default "dividerHeight".

This is the styleable used for the HListView class:
<pre>
    &lt;declare-styleable name="HListView">
        &lt;attr name="android:entries" />
        &lt;attr name="android:divider" />
        &lt;attr name="hlv_dividerWidth" format="dimension" />
        &lt;attr name="hlv_headerDividersEnabled" format="boolean" />
        &lt;attr name="hlv_footerDividersEnabled" format="boolean" />
        &lt;attr name="hlv_overScrollHeader" format="reference|color" />
        &lt;attr name="hlv_overScrollFooter" format="reference|color" />
        
        &lt;!-- 
        When "wrap_content" is used as value of the layout_height property.
        Pass the position, inside the adapter, of the view being used to measure the view
        or '-1' to use the default behavior ( default is -1 )
        -->
        &lt;attr name="hlv_measureWithChild" format="integer" />
    &lt;/declare-styleable>
    

    &lt;declare-styleable name="AbsHListView">
        &lt;attr name="android:listSelector" />
        &lt;attr name="android:smoothScrollbar" />
        &lt;attr name="android:drawSelectorOnTop" />
        &lt;attr name="android:cacheColorHint" />
        &lt;attr name="android:scrollingCache" />
        &lt;attr name="android:choiceMode" />
        
        &lt;attr name="hlv_stackFromRight" format="boolean" />
        &lt;attr name="hlv_transcriptMode">
            &lt;enum name="disabled" value="0"/>
            &lt;enum name="normal" value="1" />
            &lt;enum name="alwaysScroll" value="2" />
        &lt;/attr>
        
    &lt;/declare-styleable>  

</pre>

## ChangeLog

* 1.2.0 Added the **ExpandableHListView**

## API Requirements
The minimum supported Android version is android 2.3 (API Level 9)

## License
This software is distributed under Apache License 2.0:
http://www.apache.org/licenses/LICENSE-2.0

---

> Author
> [Alessandro Crugnola][2]



[2]: http://www.sephiroth.it

[3]: http://developer.android.com/reference/android/widget/ListView.html

[4]: http://developer.android.com/reference/android/widget/ExpandableListView.html

[5]: https://developer.android.com/reference/android/support/v7/widget/RecyclerView.html
