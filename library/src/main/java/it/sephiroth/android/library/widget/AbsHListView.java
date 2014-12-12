package it.sephiroth.android.library.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.SparseArrayCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.Checkable;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import it.sephiroth.android.library.R;
import it.sephiroth.android.library.util.ApiHelper;
import it.sephiroth.android.library.util.ViewHelperFactory;
import it.sephiroth.android.library.util.ViewHelperFactory.ViewHelper;
import it.sephiroth.android.library.util.v11.MultiChoiceModeListener;
import it.sephiroth.android.library.util.v11.MultiChoiceModeWrapper;

import static android.widget.AbsListView.CHOICE_MODE_MULTIPLE;
import static android.widget.AbsListView.CHOICE_MODE_MULTIPLE_MODAL;
import static android.widget.AbsListView.CHOICE_MODE_NONE;
import static android.widget.AbsListView.CHOICE_MODE_SINGLE;

@TargetApi (Build.VERSION_CODES.HONEYCOMB)
public abstract class AbsHListView extends AdapterView<ListAdapter> implements ViewTreeObserver.OnGlobalLayoutListener,
                                                                               ViewTreeObserver.OnTouchModeChangeListener {
    private static final String TAG = "AbsListView";
    /**
     * Disables the transcript mode.
     *
     * @see #setTranscriptMode(int)
     */
    public static final int TRANSCRIPT_MODE_DISABLED = 0;
    /**
     * The list will automatically scroll to the bottom when a data set change notification is received and only if the last item is
     * already visible on screen.
     *
     * @see #setTranscriptMode(int)
     */
    public static final int TRANSCRIPT_MODE_NORMAL = 1;
    /**
     * The list will automatically scroll to the bottom, no matter what items are currently visible.
     *
     * @see #setTranscriptMode(int)
     */
    public static final int TRANSCRIPT_MODE_ALWAYS_SCROLL = 2;
    /**
     * Indicates that we are not in the middle of a touch gesture
     */
    public static final int TOUCH_MODE_REST = -1;
    /**
     * Indicates we just received the touch event and we are waiting to see if the it is a tap or a scroll gesture.
     */
    public static final int TOUCH_MODE_DOWN = 0;
    /**
     * Indicates the touch has been recognized as a tap and we are now waiting to see if the touch is a longpress
     */
    public static final int TOUCH_MODE_TAP = 1;
    /**
     * Indicates we have waited for everything we can wait for, but the user's finger is still down
     */
    public static final int TOUCH_MODE_DONE_WAITING = 2;
    /**
     * Indicates the touch gesture is a scroll
     */
    public static final int TOUCH_MODE_SCROLL = 3;
    /**
     * Indicates the view is in the process of being flung
     */
    public static final int TOUCH_MODE_FLING = 4;
    /**
     * Indicates the touch gesture is an overscroll - a scroll beyond the beginning or end.
     */
    public static final int TOUCH_MODE_OVERSCROLL = 5;
    /**
     * Indicates the view is being flung outside of normal content bounds and will spring back.
     */
    public static final int TOUCH_MODE_OVERFLING = 6;
    /**
     * Regular layout - usually an unsolicited layout from the view system
     */
    public static final int LAYOUT_NORMAL = 0;
    /**
     * Show the first item
     */
    public static final int LAYOUT_FORCE_LEFT = 1;
    /**
     * Force the selected item to be on somewhere on the screen
     */
    public static final int LAYOUT_SET_SELECTION = 2;
    /**
     * Show the last item
     */
    public static final int LAYOUT_FORCE_RIGHT = 3;
    /**
     * Make a mSelectedItem appear in a specific location and build the rest of the views from there. The top is specified by
     * mSpecificLeft.
     */
    public static final int LAYOUT_SPECIFIC = 4;
    /**
     * Layout to sync as a result of a data change. Restore mSyncPosition to have its top at mSpecificLeft
     */
    public static final int LAYOUT_SYNC = 5;
    /**
     * Layout as a result of using the navigation keys
     */
    public static final int LAYOUT_MOVE_SELECTION = 6;
    ViewHelper mViewHelper;
    /**
     * The thread that created this view.
     */
    private final Thread mOwnerThread;
    /**
     * Controls if/how the user may choose/check items in the list
     */
    protected int mChoiceMode = CHOICE_MODE_NONE;
    /**
     * Controls CHOICE_MODE_MULTIPLE_MODAL. null when inactive.
     *
     * @hide
     */
    public Object mChoiceActionMode;
    /**
     * Wrapper for the multiple choice mode callback; AbsListView needs to perform a few extra actions around what application code
     * does.
     */
    Object mMultiChoiceModeCallback;
    /**
     * Running count of how many items are currently checked
     */
    int mCheckedItemCount;
    /**
     * Running state of which positions are currently checked
     */
    protected SparseArrayCompat<Boolean> mCheckStates;
    /**
     * Running state of which IDs are currently checked. If there is a value for a given key, the checked state for that ID is true
     * and the value holds the last known position in the adapter for that id.
     */
    LongSparseArray<Integer> mCheckedIdStates;
    /**
     * Controls how the next layout will happen
     */
    protected int mLayoutMode = LAYOUT_NORMAL;
    /**
     * Should be used by subclasses to listen to changes in the dataset
     */
    protected AdapterDataSetObserver mDataSetObserver;
    /**
     * The adapter containing the data to be displayed by this view
     */
    protected ListAdapter mAdapter;
    /**
     * If mAdapter != null, whenever this is true the adapter has stable IDs.
     */
    boolean mAdapterHasStableIds;

    /**
     * This flag indicates the a full notify is required when the RemoteViewsAdapter connects
     */
    private boolean mDeferNotifyDataSetChanged = false;

    /**
     * Indicates whether the list selector should be drawn on top of the children or behind
     */
    boolean mDrawSelectorOnTop = false;

    /**
     * The drawable used to draw the selector
     */
    Drawable mSelector;

    /**
     * The current position of the selector in the list.
     */
    int mSelectorPosition = INVALID_POSITION;

    /**
     * Defines the selector's location and dimension at drawing time
     */
    protected Rect mSelectorRect = new Rect();

    /**
     * The data set used to store unused views that should be reused during the next layout
     * to avoid creating new ones
     */
    protected final RecycleBin mRecycler = new RecycleBin();

    /**
     * The selection's left padding
     */
    int mSelectionLeftPadding = 0;

    /**
     * The selection's top padding
     */
    int mSelectionTopPadding = 0;

    /**
     * The selection's right padding
     */
    int mSelectionRightPadding = 0;

    /**
     * The selection's bottom padding
     */
    int mSelectionBottomPadding = 0;

    /**
     * This view's padding
     */
    protected Rect mListPadding = new Rect();

    /**
     * Subclasses must retain their measure spec from onMeasure() into this member
     */
    protected int mHeightMeasureSpec = 0;

    /**
     * The top scroll indicator
     */
    View mScrollLeft;

    /**
     * The down scroll indicator
     */
    View mScrollRight;

    /**
     * When the view is scrolling, this flag is set to true to indicate subclasses that
     * the drawing cache was enabled on the children
     */
    protected boolean mCachingStarted;
    protected boolean mCachingActive;

    /**
     * The position of the view that received the down motion event
     */
    protected int mMotionPosition;

    /**
     * The offset to the top of the mMotionPosition view when the down motion event was received
     */
    int mMotionViewOriginalLeft;

    /**
     * The desired offset to the top of the mMotionPosition view after a scroll
     */
    int mMotionViewNewLeft;

    /**
     * The X value associated with the the down motion event
     */
    int mMotionX;

    /**
     * The Y value associated with the the down motion event
     */
    int mMotionY;

    /**
     * One of TOUCH_MODE_REST, TOUCH_MODE_DOWN, TOUCH_MODE_TAP, TOUCH_MODE_SCROLL, or
     * TOUCH_MODE_DONE_WAITING
     */
    protected int mTouchMode = TOUCH_MODE_REST;

    /**
     * Y value from on the previous motion event (if any)
     */
    int mLastX;

    /**
     * How far the finger moved before we started scrolling
     */
    int mMotionCorrection;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    /**
     * Handles one frame of a fling
     */
    private FlingRunnable mFlingRunnable;

    /**
     * Handles scrolling between positions within the list.
     */
    protected AbsPositionScroller mPositionScroller;

    /**
     * The offset in pixels form the top of the AdapterView to the top
     * of the currently selected view. Used to save and restore state.
     */
    protected int mSelectedLeft = 0;

    /**
     * Indicates whether the list is stacked from the bottom edge or
     * the top edge.
     */
    protected boolean mStackFromRight;

    /**
     * When set to true, the list automatically discards the children's
     * bitmap cache after scrolling.
     */
    boolean mScrollingCacheEnabled;

    /**
     * Whether or not to enable the fast scroll feature on this list
     */
    boolean mFastScrollEnabled;

    /**
     * Whether or not to always show the fast scroll feature on this list
     */
    boolean mFastScrollAlwaysVisible;

    /**
     * Optional callback to notify client when scroll position has changed
     */
    private OnScrollListener mOnScrollListener;

    /**
     * Keeps track of our accessory window
     */
    //PopupWindow mPopup;

    /**
     * Used with type filter window
     */
    //EditText mTextFilter;

    /**
     * Indicates whether to use pixels-based or position-based scrollbar
     * properties.
     */
    private boolean mSmoothScrollbarEnabled = true;

    /**
     * Indicates that this view supports filtering
     */
    private boolean mTextFilterEnabled;

    /**
     * Indicates that this view is currently displaying a filtered view of the data
     */
    private boolean mFiltered;

    /**
     * Rectangle used for hit testing children
     */
    private Rect mTouchFrame;

    /**
     * The position to resurrect the selected position to.
     */
    protected int mResurrectToPosition = INVALID_POSITION;

    private ContextMenuInfo mContextMenuInfo = null;

    /**
     * Maximum distance to record overscroll
     */
    protected int mOverscrollMax;

    /**
     * Content height divided by this is the overscroll limit.
     */
    protected static final int OVERSCROLL_LIMIT_DIVISOR = 3;

    /**
     * How many positions in either direction we will search to try to
     * find a checked item with a stable ID that moved position across
     * a data set change. If the item isn't found it will be unselected.
     */
    private static final int CHECK_POSITION_SEARCH_DISTANCE = 20;

    /**
     * Used to request a layout when we changed touch mode
     */
    private static final int TOUCH_MODE_UNKNOWN = -1;
    private static final int TOUCH_MODE_ON = 0;
    private static final int TOUCH_MODE_OFF = 1;

    private int mLastTouchMode = TOUCH_MODE_UNKNOWN;

    private static final boolean PROFILE_SCROLLING = false;
    private boolean mScrollProfilingStarted = false;

    private static final boolean PROFILE_FLINGING = false;
    private boolean mFlingProfilingStarted = false;

    /**
     * The StrictMode "critical time span" objects to catch animation
     * stutters.  Non-null when a time-sensitive animation is
     * in-flight.  Must call finish() on them when done animating.
     * These are no-ops on user builds.
     */
    //private StrictMode.Span mScrollStrictSpan = null;
    //private StrictMode.Span mFlingStrictSpan = null;

    /**
     * The last CheckForLongPress runnable we posted, if any
     */
    private CheckForLongPress mPendingCheckForLongPress;

    /**
     * The last CheckForTap runnable we posted, if any
     */
    private CheckForTap mPendingCheckForTap;

    /**
     * The last CheckForKeyLongPress runnable we posted, if any
     */
    private CheckForKeyLongPress mPendingCheckForKeyLongPress;

    /**
     * Acts upon click
     */
    private AbsHListView.PerformClick mPerformClick;

    /**
     * Delayed action for touch mode.
     */
    private Runnable mTouchModeReset;

    /**
     * This view is in transcript mode -- it shows the bottom of the list when the data
     * changes
     */
    private int mTranscriptMode;

    /**
     * Indicates that this list is always drawn on top of a solid, single-color, opaque
     * background
     */
    private int mCacheColorHint;

    /**
     * The select child's view (from the adapter's getView) is enabled.
     */
    private boolean mIsChildViewEnabled;

    /**
     * The last scroll state reported to clients through {@link OnScrollListener}.
     */
    private int mLastScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    /**
     * Helper object that renders and controls the fast scroll thumb.
     */
    //private FastScroller mFastScroll;

    /**
     * Temporary holder for fast scroller style until a FastScroller object
     * is created.
     */
    private int mFastScrollStyle;

    private boolean mGlobalLayoutListenerAddedFilter;

    private int mTouchSlop;
    private float mDensityScale;

    //private InputConnection mDefInputConnection;
    //private InputConnectionWrapper mPublicInputConnection;

    private Runnable mClearScrollingCache;
    protected Runnable mPositionScrollAfterLayout;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private float mVelocityScale = 1.0f;

    final boolean[] mIsScrap = new boolean[1];

    private final int[] mScrollOffset = new int[2];
    private final int[] mScrollConsumed = new int[2];

    // Used for offsetting MotionEvents that we feed to the VelocityTracker.
    // In the future it would be nice to be able to give this to the VelocityTracker
    // directly, or alternatively put a VT into absolute-positioning mode that only
    // reads the raw screen-coordinate x/y values.
    private int mNestedXOffset = 0;

    // True when the popup should be hidden because of a call to
    // dispatchDisplayHint()
    private boolean mPopupHidden;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    /**
     * Maximum distance to overscroll by during edge effects
     */
    int mOverscrollDistance;

    /**
     * Maximum distance to overfling during edge effects
     */
    int mOverflingDistance;

    // These two EdgeGlows are always set and used together.
    // Checking one for null is as good as checking both.

    /**
     * Tracks the state of the top edge glow.
     */
    private EdgeEffectCompat mEdgeGlowTop;

    /**
     * Tracks the state of the bottom edge glow.
     */
    private EdgeEffectCompat mEdgeGlowBottom;

    /**
     * An estimate of how many pixels are between the top of the list and
     * the top of the first position in the adapter, based on the last time
     * we saw it. Used to hint where to draw edge glows.
     */
    private int mFirstPositionDistanceGuess;

    /**
     * An estimate of how many pixels are between the bottom of the list and
     * the bottom of the last position in the adapter, based on the last time
     * we saw it. Used to hint where to draw edge glows.
     */
    private int mLastPositionDistanceGuess;

    /**
     * Used for determining when to cancel out of overscroll.
     */
    private int mDirection = 0;

    /**
     * Tracked on measurement in transcript mode. Makes sure that we can still pin to
     * the bottom correctly on resizes.
     */
    private boolean mForceTranscriptScroll;

    private int mGlowPaddingTop;
    private int mGlowPaddingBottom;

    /**
     * Used for interacting with list items from an accessibility service.
     */
    //private ListItemAccessibilityDelegate mAccessibilityDelegate;

    private int mLastAccessibilityScrollEventFromIndex;
    private int mLastAccessibilityScrollEventToIndex;

    /**
     * Track the item count from the last time we handled a data change.
     */
    private int mLastHandledItemCount;

    /**
     * Used for smooth scrolling at a consistent rate
     */
    static final Interpolator sLinearInterpolator = new LinearInterpolator();

    /**
     * The saved state that we will be restoring from when we next sync.
     * Kept here so that if we happen to be asked to save our state before
     * the sync happens, we can return this existing data rather than losing
     * it.
     */
    private SavedState mPendingSync;

    /**
     * Whether the view is in the process of detaching from its window.
     */
    private boolean mIsDetaching;
    protected boolean mAttachedToWindow;

    private float mHorizontalScrollFactor;

    /**
     * Interface definition for a callback to be invoked when the list or grid
     * has been scrolled.
     */
    public interface OnScrollListener {

        /**
         * The view is not scrolling. Note navigating the list using the trackball counts as
         * being in the idle state since these transitions are not animated.
         */
        public static int SCROLL_STATE_IDLE = 0;

        /**
         * The user is scrolling using touch, and their finger is still on the screen
         */
        public static int SCROLL_STATE_TOUCH_SCROLL = 1;

        /**
         * The user had previously been scrolling using touch and had performed a fling. The
         * animation is now coasting to a stop
         */
        public static int SCROLL_STATE_FLING = 2;

        /**
         * Callback method to be invoked while the list view or grid view is being scrolled. If the
         * view is being scrolled, this method will be called before the next frame of the scroll is
         * rendered. In particular, it will be called before any calls to
         * {@link Adapter#getView(int, View, ViewGroup)}.
         *
         * @param view The view whose scroll state is being reported
         *
         * @param scrollState The current scroll state. One of
         * {@link #SCROLL_STATE_TOUCH_SCROLL} or {@link #SCROLL_STATE_IDLE}.
         */
        public void onScrollStateChanged(AbsHListView view, int scrollState);

        /**
         * Callback method to be invoked when the list or grid has been scrolled. This will be
         * called after the scroll has completed
         * @param view The view whose scroll state is being reported
         * @param firstVisibleItem the index of the first visible cell (ignore if
         *        visibleItemCount == 0)
         * @param visibleItemCount the number of visible cells
         * @param totalItemCount the number of items in the list adaptor
         */
        public void onScroll(AbsHListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount);
    }

    /**
     * The top-level view of a list item can implement this interface to allow
     * itself to modify the bounds of the selection shown for that item.
     */
    public interface SelectionBoundsAdjuster {
        /**
         * Called to allow the list item to adjust the bounds shown for
         * its selection.
         *
         * @param bounds On call, this contains the bounds the list has
         * selected for the item (that is the bounds of the entire view).  The
         * values can be modified as desired.
         */
        public void adjustListItemSelectionBounds(Rect bounds);
    }

    public AbsHListView(Context context) {
        super(context);
        initAbsListView();

        mOwnerThread = Thread.currentThread();

        //setHorizontalScrollBarEnabled(true);
        //TypedArray a = context.obtainStyledAttributes(R.styleable.View);
        //initializeScrollbarsInternal(a);
        //a.recycle();
    }

    public AbsHListView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.hlv_absHListViewStyle);
    }

    public AbsHListView(Context context, AttributeSet defStyleAttr, int defStyleRes) {
        super(context, defStyleAttr, defStyleRes);

        initAbsListView();

        mOwnerThread = Thread.currentThread();

        TypedArray array = context.getTheme().obtainStyledAttributes(defStyleAttr, R.styleable.AbsHListView, defStyleRes, 0);

        Drawable listSelector = null;
        boolean drawSelectorOnTop = false;
        boolean stackFromRight = false;
        boolean scrollingCacheEnabled = true;
        int transcriptMode = TRANSCRIPT_MODE_DISABLED;
        int color = 0;
        boolean smoothScrollbar = true;
        int choiceMode = CHOICE_MODE_NONE;

        if (null != array) {
            listSelector = array.getDrawable(R.styleable.AbsHListView_android_listSelector);
            drawSelectorOnTop = array.getBoolean(R.styleable.AbsHListView_android_drawSelectorOnTop, false);
            stackFromRight = array.getBoolean(R.styleable.AbsHListView_hlv_stackFromRight, false);
            scrollingCacheEnabled = array.getBoolean(R.styleable.AbsHListView_android_scrollingCache, true);
            transcriptMode = array.getInt(R.styleable.AbsHListView_hlv_transcriptMode, TRANSCRIPT_MODE_DISABLED);
            color = array.getColor(R.styleable.AbsHListView_android_cacheColorHint, 0);
            smoothScrollbar = array.getBoolean(R.styleable.AbsHListView_android_smoothScrollbar, true);
            choiceMode = array.getInt(R.styleable.AbsHListView_android_choiceMode, CHOICE_MODE_NONE);
            array.recycle();

            if (LOG_ENABLED) {
                Log.d(TAG, "listSelector: " + listSelector);
                Log.d(TAG, "drawSelectorOnTop: " + drawSelectorOnTop);
                Log.d(TAG, "stackFromRight: " + stackFromRight);
                Log.d(TAG, "scrollingCacheEnabled: " + scrollingCacheEnabled);
                Log.d(TAG, "transcriptMode: " + transcriptMode);
                Log.d(TAG, "smoothScrollbar: " + smoothScrollbar);
                Log.d(TAG, "choiceMode: " + choiceMode);
            }
        }

        if (listSelector != null) {
            setSelector(listSelector);
        }

        mDrawSelectorOnTop = drawSelectorOnTop;
        setStackFromRight(stackFromRight);
        setScrollingCacheEnabled(scrollingCacheEnabled);
        setTranscriptMode(transcriptMode);
        setCacheColorHint(color);
        setSmoothScrollbarEnabled(smoothScrollbar);
        setChoiceMode(choiceMode);
    }

    private void initAbsListView() {
        // Setting focusable in touch mode will set the focusable property to true
        setClickable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);
        setAlwaysDrawnWithCacheEnabled(false);
        setScrollingCacheEnabled(true);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
        mViewHelper = ViewHelperFactory.create(this);
        mDensityScale = getContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public void setOverScrollMode(int mode) {
        if (mode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffectCompat(context);
                mEdgeGlowBottom = new EdgeEffectCompat(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
        super.setOverScrollMode(mode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        if (adapter != null) {
            mAdapterHasStableIds = mAdapter.hasStableIds();
            if (mChoiceMode != CHOICE_MODE_NONE && mAdapterHasStableIds &&
                mCheckedIdStates == null) {
                mCheckedIdStates = new LongSparseArray<Integer>();
            }
        }

        if (mCheckStates != null) {
            mCheckStates.clear();
        }

        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
    }

    /**
     * Returns the number of items currently selected. This will only be valid
     * if the choice mode is not {@link #CHOICE_MODE_NONE} (default).
     *
     * <p>To determine the specific items that are currently selected, use one of
     * the <code>getChecked*</code> methods.
     *
     * @return The number of items currently selected
     *
     * @see #getCheckedItemPosition()
     * @see #getCheckedItemPositions()
     * @see #getCheckedItemIds()
     */
    public int getCheckedItemCount() {
        return mCheckedItemCount;
    }

    /**
     * Returns the checked state of the specified position. The result is only
     * valid if the choice mode has been set to {@link #CHOICE_MODE_SINGLE}
     * or {@link #CHOICE_MODE_MULTIPLE}.
     *
     * @param position The item whose checked state to return
     * @return The item's checked state or <code>false</code> if choice mode
     *         is invalid
     *
     * @see #setChoiceMode(int)
     */
    public boolean isItemChecked(int position) {
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
            return mCheckStates.get(position, false);
        }

        return false;
    }

    /**
     * Returns the currently checked item. The result is only valid if the choice
     * mode has been set to {@link #CHOICE_MODE_SINGLE}.
     *
     * @return The position of the currently checked item or
     *         {@link #INVALID_POSITION} if nothing is selected
     *
     * @see #setChoiceMode(int)
     */
    public int getCheckedItemPosition() {
        if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
            return mCheckStates.keyAt(0);
        }

        return INVALID_POSITION;
    }

    /**
     * Returns the set of checked items in the list. The result is only valid if
     * the choice mode has not been set to {@link #CHOICE_MODE_NONE}.
     *
     * @return  A SparseBooleanArray which will return true for each call to
     *          get(int position) where position is a checked position in the
     *          list and false otherwise, or <code>null</code> if the choice
     *          mode is set to {@link #CHOICE_MODE_NONE}.
     */
    public SparseArrayCompat<Boolean> getCheckedItemPositions() {
        if (mChoiceMode != CHOICE_MODE_NONE) {
            return mCheckStates;
        }
        return null;
    }

    /**
     * Returns the set of checked items ids. The result is only valid if the
     * choice mode has not been set to {@link #CHOICE_MODE_NONE} and the adapter
     * has stable IDs. ({@link ListAdapter#hasStableIds()} == {@code true})
     *
     * @return A new array which contains the id of each checked item in the
     *         list.
     */
    public long[] getCheckedItemIds() {
        if (mChoiceMode == CHOICE_MODE_NONE || mCheckedIdStates == null || mAdapter == null) {
            return new long[0];
        }

        final LongSparseArray<Integer> idStates = mCheckedIdStates;
        final int count = idStates.size();
        final long[] ids = new long[count];

        for (int i = 0; i < count; i++) {
            ids[i] = idStates.keyAt(i);
        }

        return ids;
    }

    /**
     * Clear any choices previously set
     */
    public void clearChoices() {
        if (mCheckStates != null) {
            mCheckStates.clear();
        }
        if (mCheckedIdStates != null) {
            mCheckedIdStates.clear();
        }
        mCheckedItemCount = 0;
    }

    /**
     * Sets the checked state of the specified position. The is only valid if
     * the choice mode has been set to {@link #CHOICE_MODE_SINGLE} or
     * {@link #CHOICE_MODE_MULTIPLE}.
     *
     * @param position The item whose checked state is to be checked
     * @param value The new checked state for the item
     */
    public void setItemChecked(int position, boolean value) {
        if (mChoiceMode == CHOICE_MODE_NONE) {
            return;
        }

        // Start selection mode if needed. We don't need to if we're unchecking something.
        if (ApiHelper.AT_LEAST_11) {
            if (value && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
                if (mMultiChoiceModeCallback == null ||
                    !((MultiChoiceModeWrapper) mMultiChoiceModeCallback).hasWrappedCallback()) {
                    throw new IllegalStateException(
                        "AbsListView: attempted to start selection mode " +
                            "for CHOICE_MODE_MULTIPLE_MODAL but no choice mode callback was " +
                            "supplied. Call setMultiChoiceModeListener to set a callback.");
                }
                mChoiceActionMode = startActionMode((MultiChoiceModeWrapper) mMultiChoiceModeCallback);
            }
        }

        if (mChoiceMode == CHOICE_MODE_MULTIPLE
            || (ApiHelper.AT_LEAST_11 && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL)) {
            boolean oldValue = mCheckStates.get(position, false);
            mCheckStates.put(position, value);
            if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                if (value) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                } else {
                    mCheckedIdStates.delete(mAdapter.getItemId(position));
                }
            }
            if (oldValue != value) {
                if (value) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
            }
            if (mChoiceActionMode != null) {
                final long id = mAdapter.getItemId(position);
                ((MultiChoiceModeWrapper) mMultiChoiceModeCallback)
                    .onItemCheckedStateChanged((ActionMode) mChoiceActionMode, position, id, value);
            }
        } else {
            boolean updateIds = mCheckedIdStates != null && mAdapter.hasStableIds();
            // Clear all values if we're checking something, or unchecking the currently
            // selected item
            if (value || isItemChecked(position)) {
                mCheckStates.clear();
                if (updateIds) {
                    mCheckedIdStates.clear();
                }
            }
            // this may end up selecting the value we just cleared but this way
            // we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
            if (value) {
                mCheckStates.put(position, true);
                if (updateIds) {
                    mCheckedIdStates.put(mAdapter.getItemId(position), position);
                }
                mCheckedItemCount = 1;
            } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                mCheckedItemCount = 0;
            }
        }

        // Do not generate a data change while we are in the layout phase
        if (!mInLayout && !mBlockLayoutRequests) {
            mDataChanged = true;
            rememberSyncState();
            requestLayout();
        }
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        boolean handled = false;
        boolean dispatchItemClick = true;

        if (mChoiceMode != CHOICE_MODE_NONE) {
            handled = true;
            boolean checkedStateChanged = false;

            if (mChoiceMode == CHOICE_MODE_MULTIPLE
                || (ApiHelper.AT_LEAST_11 && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL
                && mChoiceActionMode != null)) {
                boolean checked = !mCheckStates.get(position, false);
                mCheckStates.put(position, checked);
                if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                    if (checked) {
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    } else {
                        mCheckedIdStates.delete(mAdapter.getItemId(position));
                    }
                }
                if (checked) {
                    mCheckedItemCount++;
                } else {
                    mCheckedItemCount--;
                }
                if (mChoiceActionMode != null) {
                    ((MultiChoiceModeWrapper) mMultiChoiceModeCallback)
                        .onItemCheckedStateChanged((ActionMode) mChoiceActionMode, position, id, checked);
                    dispatchItemClick = false;
                }
                checkedStateChanged = true;
            } else if (mChoiceMode == CHOICE_MODE_SINGLE) {
                boolean checked = !mCheckStates.get(position, false);
                if (checked) {
                    mCheckStates.clear();
                    mCheckStates.put(position, true);
                    if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
                        mCheckedIdStates.clear();
                        mCheckedIdStates.put(mAdapter.getItemId(position), position);
                    }
                    mCheckedItemCount = 1;
                } else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
                    mCheckedItemCount = 0;
                }
                checkedStateChanged = true;
            }

            if (checkedStateChanged) {
                updateOnScreenCheckedViews();
            }
        }

        if (dispatchItemClick) {
            handled |= super.performItemClick(view, position, id);
        }

        return handled;
    }

    /**
     * Perform a quick, in-place update of the checked or activated state
     * on all visible item views. This should only be called when a valid
     * choice mode is active.
     */
    private void updateOnScreenCheckedViews() {
        final int firstPos = mFirstPosition;
        final int count = getChildCount();
        final boolean useActivated = ApiHelper.AT_LEAST_11;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final int position = firstPos + i;

            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(mCheckStates.get(position, false));
            } else if (useActivated) {
                child.setActivated(mCheckStates.get(position, false));
            }
        }
    }

    /**
     * @see #setChoiceMode(int)
     *
     * @return The current choice mode
     */
    public int getChoiceMode() {
        return mChoiceMode;
    }

    /**
     * Defines the choice behavior for the List. By default, Lists do not have any choice behavior
     * ({@link #CHOICE_MODE_NONE}). By setting the choiceMode to {@link #CHOICE_MODE_SINGLE}, the
     * List allows up to one item to  be in a chosen state. By setting the choiceMode to
     * {@link #CHOICE_MODE_MULTIPLE}, the list allows any number of items to be chosen.
     *
     * @param choiceMode One of {@link #CHOICE_MODE_NONE}, {@link #CHOICE_MODE_SINGLE}, or
     * {@link #CHOICE_MODE_MULTIPLE}
     */
    @TargetApi (11)
    public void setChoiceMode(int choiceMode) {
        mChoiceMode = choiceMode;

        if (ApiHelper.AT_LEAST_11) {
            if (mChoiceActionMode != null) {
                ((ActionMode) mChoiceActionMode).finish();
                mChoiceActionMode = null;
            }
        }
        if (mChoiceMode != CHOICE_MODE_NONE) {
            if (mCheckStates == null) {
                mCheckStates = new SparseArrayCompat<Boolean>(0);
            }
            if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
                mCheckedIdStates = new LongSparseArray<Integer>(0);
            }
            // Modal multi-choice mode only has choices when the mode is active. Clear them.
            if (ApiHelper.AT_LEAST_11) {
                if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
                    clearChoices();
                    setLongClickable(true);
                }
            }
        }
    }

    /**
     * Set a {@link MultiChoiceModeListener} that will manage the lifecycle of the
     * selection {@link ActionMode}. Only used when the choice mode is set to
     * {@link #CHOICE_MODE_MULTIPLE_MODAL}.
     *
     * @param listener Listener that will manage the selection mode
     *
     * @see #setChoiceMode(int)
     */
    @TargetApi (11)
    public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
        if (ApiHelper.AT_LEAST_11) {
            if (mMultiChoiceModeCallback == null) {
                mMultiChoiceModeCallback = new MultiChoiceModeWrapper(this);
            }
            ((MultiChoiceModeWrapper) mMultiChoiceModeCallback).setWrapped(listener);
        } else {
            Log.e(TAG, "setMultiChoiceModeListener not supported for this version of Android");
        }
    }

    /**
     * @return true if all list content currently fits within the view boundaries
     */
    private boolean contentFits() {
        final int childCount = getChildCount();
        if (childCount == 0) return true;
        if (childCount != mItemCount) return false;

        return getChildAt(0).getTop() >= mListPadding.top &&
                getChildAt(childCount - 1).getBottom() <= getHeight() - mListPadding.bottom;
    }

    /**
     * Specifies whether fast scrolling is enabled or disabled.
     * <p>
     * When fast scrolling is enabled, the user can quickly scroll through lists
     * by dragging the fast scroll thumb.
     * <p>
     * If the adapter backing this list implements {@link SectionIndexer}, the
     * fast scroller will display section header previews as the user scrolls.
     * Additionally, the user will be able to quickly jump between sections by
     * tapping along the length of the scroll bar.
     *
     * @see SectionIndexer
     * @see #isFastScrollEnabled()
     * @param enabled true to enable fast scrolling, false otherwise
     */
//    public void setFastScrollEnabled(final boolean enabled) {
//        if (mFastScrollEnabled != enabled) {
//            mFastScrollEnabled = enabled;
//
//            if (isOwnerThread()) {
//                setFastScrollerEnabledUiThread(enabled);
//            } else {
//                post(new Runnable() {
//                    @Override
//                    public void run() {
//                        setFastScrollerEnabledUiThread(enabled);
//                    }
//                });
//            }
//        }
//    }

//    private void setFastScrollerEnabledUiThread(boolean enabled) {
//        if (mFastScroll != null) {
//            mFastScroll.setEnabled(enabled);
//        } else if (enabled) {
//            mFastScroll = new FastScroller(this, mFastScrollStyle);
//            mFastScroll.setEnabled(true);
//        }
//
//        resolvePadding();
//
//        if (mFastScroll != null) {
//            mFastScroll.updateLayout();
//        }
//    }

    /**
     * Specifies the style of the fast scroller decorations.
     *
     * @param styleResId style resource containing fast scroller properties
     * @see android.R.styleable#FastScroll
     */
//    public void setFastScrollStyle(int styleResId) {
//        if (mFastScroll == null) {
//            mFastScrollStyle = styleResId;
//        } else {
//            mFastScroll.setStyle(styleResId);
//        }
//    }

    /**
     * Set whether or not the fast scroller should always be shown in place of
     * the standard scroll bars. This will enable fast scrolling if it is not
     * already enabled.
     * <p>
     * Fast scrollers shown in this way will not fade out and will be a
     * permanent fixture within the list. This is best combined with an inset
     * scroll bar style to ensure the scroll bar does not overlap content.
     *
     * @param alwaysShow true if the fast scroller should always be displayed,
     *            false otherwise
     * @see #setScrollBarStyle(int)
     * @see #setFastScrollEnabled(boolean)
     */
//    public void setFastScrollAlwaysVisible(final boolean alwaysShow) {
//        if (mFastScrollAlwaysVisible != alwaysShow) {
//            if (alwaysShow && !mFastScrollEnabled) {
//                setFastScrollEnabled(true);
//            }
//
//            mFastScrollAlwaysVisible = alwaysShow;
//
//            if (isOwnerThread()) {
//                setFastScrollerAlwaysVisibleUiThread(alwaysShow);
//            } else {
//                post(new Runnable() {
//                    @Override
//                    public void run() {
//                        setFastScrollerAlwaysVisibleUiThread(alwaysShow);
//                    }
//                });
//            }
//        }
//    }

//    private void setFastScrollerAlwaysVisibleUiThread(boolean alwaysShow) {
//        if (mFastScroll != null) {
//            mFastScroll.setAlwaysShow(alwaysShow);
//        }
//    }

    /**
     * @return whether the current thread is the one that created the view
     */
    private boolean isOwnerThread() {
        return mOwnerThread == Thread.currentThread();
    }

    /**
     * Returns true if the fast scroller is set to always show on this view.
     *
     * @return true if the fast scroller will always show
     * @see #setFastScrollAlwaysVisible(boolean)
     */
//    public boolean isFastScrollAlwaysVisible() {
//        if (mFastScroll == null) {
//            return mFastScrollEnabled && mFastScrollAlwaysVisible;
//        } else {
//            return mFastScroll.isEnabled() && mFastScroll.isAlwaysShowEnabled();
//        }
//    }

    @Override
    public int getHorizontalScrollbarHeight() {
//        if (mFastScroll != null && mFastScroll.isEnabled()) {
//            return Math.max(super.getHorizontalScrollbarHeight(), mFastScroll.getHeight());
//        }
        return super.getHorizontalScrollbarHeight();
    }

    /**
     * Returns true if the fast scroller is enabled.
     *
     * @see #setFastScrollEnabled(boolean)
     * @return true if fast scroll is enabled, false otherwise
     */
//    @ViewDebug.ExportedProperty
//    public boolean isFastScrollEnabled() {
//        if (mFastScroll == null) {
//            return mFastScrollEnabled;
//        } else {
//            return mFastScroll.isEnabled();
//        }
//    }

//    @Override
//    public void setHorizontalScrollbarPosition(int position) {
//        super.setHorizontalScrollbarPosition(position);
//        if (mFastScroll != null) {
//            mFastScroll.setScrollbarPosition(position);
//        }
//    }

//    @Override
//    public void setScrollBarStyle(int style) {
//        super.setScrollBarStyle(style);
//        if (mFastScroll != null) {
//            mFastScroll.setScrollBarStyle(style);
//        }
//    }

    /**
     * If fast scroll is enabled, then don't draw the vertical scrollbar.
     * @hide
     */
//    protected boolean isHorizontalScrollBarHidden() {
//        return isFastScrollEnabled();
//    }

    /**
     * When smooth scrollbar is enabled, the position and size of the scrollbar thumb
     * is computed based on the number of visible pixels in the visible items. This
     * however assumes that all list items have the same height. If you use a list in
     * which items have different heights, the scrollbar will change appearance as the
     * user scrolls through the list. To avoid this issue, you need to disable this
     * property.
     *
     * When smooth scrollbar is disabled, the position and size of the scrollbar thumb
     * is based solely on the number of items in the adapter and the position of the
     * visible items inside the adapter. This provides a stable scrollbar as the user
     * navigates through a list of items with varying heights.
     *
     * @param enabled Whether or not to enable smooth scrollbar.
     *
     * @see #setSmoothScrollbarEnabled(boolean)
     * @attr ref android.R.styleable#AbsListView_smoothScrollbar
     */
    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    /**
     * Returns the current state of the fast scroll feature.
     *
     * @return True if smooth scrollbar is enabled is enabled, false otherwise.
     *
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    @ViewDebug.ExportedProperty
    public boolean isSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    /**
     * Set the listener that will receive notifications every time the list scrolls.
     *
     * @param l the scroll listener
     */
    public void setOnScrollListener(OnScrollListener l) {
        mOnScrollListener = l;
        invokeOnItemScrollListener();
    }

    /**
     * Notify our scroll listener (if there is one) of a change in scroll state
     */
    void invokeOnItemScrollListener() {
        //if (mFastScroll != null) {
        //    mFastScroll.onScroll(mFirstPosition, getChildCount(), mItemCount);
        //}
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(this, mFirstPosition, getChildCount(), mItemCount);
        }
        onScrollChanged(0, 0, 0, 0); // dummy values, View's implementation does not use these.
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // Since this class calls onScrollChanged even if the mFirstPosition and the
        // child count have not changed we will avoid sending duplicate accessibility
        // events.
        if (eventType == AccessibilityEventCompat.TYPE_VIEW_SCROLLED) {
            final int firstVisiblePosition = getFirstVisiblePosition();
            final int lastVisiblePosition = getLastVisiblePosition();
            if (mLastAccessibilityScrollEventFromIndex == firstVisiblePosition
                && mLastAccessibilityScrollEventToIndex == lastVisiblePosition) {
                return;
            } else {
                mLastAccessibilityScrollEventFromIndex = firstVisiblePosition;
                mLastAccessibilityScrollEventToIndex = lastVisiblePosition;
            }
        }
        super.sendAccessibilityEvent(eventType);
    }

    @TargetApi (14)
    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(AbsHListView.class.getName());
    }

    @TargetApi (Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @SuppressLint ("Override")
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(AbsHListView.class.getName());
        if (isEnabled()) {
            if (canScrollLeft()) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                info.setScrollable(true);
            }
            if (canScrollRight()) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                info.setScrollable(true);
            }
        }
    }

    int getSelectionModeForAccessibility() {
        final int choiceMode = getChoiceMode();
        switch (choiceMode) {
            case CHOICE_MODE_NONE:
                return AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_NONE;
            case CHOICE_MODE_SINGLE:
                return AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE;
            case CHOICE_MODE_MULTIPLE:
            case CHOICE_MODE_MULTIPLE_MODAL:
                return AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_MULTIPLE;
            default:
                return AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_NONE;
        }
    }

    @TargetApi (16)
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD: {
                if (isEnabled() && getLastVisiblePosition() < getCount() - 1) {
                    final int viewportWidth = getWidth() - mListPadding.left - mListPadding.right;
                    smoothScrollBy(viewportWidth, PositionScroller.SCROLL_DURATION);
                    return true;
                }
            } return false;
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD: {
                if (isEnabled() && mFirstPosition > 0) {
                    final int viewportWidth = getWidth() - mListPadding.left - mListPadding.right;
                    smoothScrollBy(-viewportWidth, PositionScroller.SCROLL_DURATION);
                    return true;
                }
            } return false;
        }
        return false;
    }

//    /** @hide */
//    @Override
//    public View findViewByAccessibilityIdTraversal(int accessibilityId) {
//        if (accessibilityId == getAccessibilityViewId()) {
//            return this;
//        }
//        // If the data changed the children are invalid since the data model changed.
//        // Hence, we pretend they do not exist. After a layout the children will sync
//        // with the model at which point we notify that the accessibility state changed,
//        // so a service will be able to re-fetch the views.
//        if (mDataChanged) {
//            return null;
//        }
//        return super.findViewByAccessibilityIdTraversal(accessibilityId);
//    }

    /**
     * Indicates whether the children's drawing cache is used during a scroll.
     * By default, the drawing cache is enabled but this will consume more memory.
     *
     * @return true if the scrolling cache is enabled, false otherwise
     *
     * @see #setScrollingCacheEnabled(boolean)
     * @see View#setDrawingCacheEnabled(boolean)
     */
    @ViewDebug.ExportedProperty
    public boolean isScrollingCacheEnabled() {
        return mScrollingCacheEnabled;
    }

    /**
     * Enables or disables the children's drawing cache during a scroll.
     * By default, the drawing cache is enabled but this will use more memory.
     *
     * When the scrolling cache is enabled, the caches are kept after the
     * first scrolling. You can manually clear the cache by calling
     * {@link android.view.ViewGroup#setChildrenDrawingCacheEnabled(boolean)}.
     *
     * @param enabled true to enable the scroll cache, false otherwise
     *
     * @see #isScrollingCacheEnabled()
     * @see View#setDrawingCacheEnabled(boolean)
     */
    public void setScrollingCacheEnabled(boolean enabled) {
        if (mScrollingCacheEnabled && !enabled) {
            clearScrollingCache();
        }
        mScrollingCacheEnabled = enabled;
    }

    /**
     * Enables or disables the type filter window. If enabled, typing when
     * this view has focus will filter the children to match the users input.
     * Note that the {@link Adapter} used by this view must implement the
     * {@link Filterable} interface.
     *
     * @param textFilterEnabled true to enable type filtering, false otherwise
     *
     * @see Filterable
     */
    public void setTextFilterEnabled(boolean textFilterEnabled) {
        mTextFilterEnabled = textFilterEnabled;
    }

    /**
     * Indicates whether type filtering is enabled for this view
     *
     * @return true if type filtering is enabled, false otherwise
     *
     * @see #setTextFilterEnabled(boolean)
     * @see Filterable
     */
    @ViewDebug.ExportedProperty
    public boolean isTextFilterEnabled() {
        return mTextFilterEnabled;
    }

    @Override
    public void getFocusedRect(Rect r) {
        View view = getSelectedView();
        if (view != null && view.getParent() == this) {
            // the focused rectangle of the selected view offset into the
            // coordinate space of this view.
            view.getFocusedRect(r);
            offsetDescendantRectToMyCoords(view, r);
        } else {
            // otherwise, just the norm
            super.getFocusedRect(r);
        }
    }

    @TargetApi (21)
    private void useDefaultSelector() {
        if (ApiHelper.AT_LEAST_21) {
            setSelector(getContext().getDrawable(android.R.drawable.list_selector_background));
        } else {
            setSelector(getResources().getDrawable(android.R.drawable.list_selector_background));
        }
    }

    /**
     * Indicates whether the content of this view is pinned to, or stacked from,
     * the bottom edge.
     *
     * @return true if the content is stacked from the bottom edge, false otherwise
     */
    public boolean isStackFromRight() {
        return mStackFromRight;
    }

    /**
     * When stack from bottom is set to true, the list fills its content starting from
     * the bottom of the view.
     *
     * @param stackFromRight true to pin the view's content to the bottom edge,
     *        false to pin the view's content to the top edge
     */
    public void setStackFromRight(boolean stackFromRight) {
        if (mStackFromRight != stackFromRight) {
            mStackFromRight = stackFromRight;
            requestLayoutIfNecessary();
        }
    }

    void requestLayoutIfNecessary() {
        if (getChildCount() > 0) {
            resetList();
            requestLayout();
            invalidate();
        }
    }

    static class SavedState extends BaseSavedState {
        long selectedId;
        long firstId;
        int viewLeft;
        int position;
        int width;
        String filter;
        boolean inActionMode;
        int checkedItemCount;
        SparseArrayCompat<Boolean> checkState;
        LongSparseArray<Integer> checkIdState;

        /**
         * Constructor called from {@link AbsHListView#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            selectedId = in.readLong();
            firstId = in.readLong();
            viewLeft = in.readInt();
            position = in.readInt();
            width = in.readInt();
            filter = in.readString();
            inActionMode = in.readByte() != 0;
            checkedItemCount = in.readInt();
            checkState = readSparseBooleanArray(in);
            checkIdState = readSparseLongArray(in);
        }

        private LongSparseArray<Integer> readSparseLongArray(Parcel in) {
            if (LOG_ENABLED) {
                Log.i(TAG, "readSparseLongArray");
            }
            final int N = in.readInt();
            if (N <= 0) {
                return null;
            }
            LongSparseArray<Integer> array = new LongSparseArray<Integer>(N);
            readSparseLongArrayInternal(array, in, N);
            return array;
        }

        private SparseArrayCompat<Boolean> readSparseBooleanArray(Parcel in) {
            if (LOG_ENABLED) {
                Log.i(TAG, "readSparseBooleanArray");
            }
            int N = in.readInt();
            if (N < 0) {
                return null;
            }
            SparseArrayCompat<Boolean> sa = new SparseArrayCompat<Boolean>(N);
            readSparseBooleanArrayInternal(sa, in, N);
            return sa;
        }

        private void readSparseLongArrayInternal(LongSparseArray<Integer> outVal, Parcel in, int N) {
            while (N > 0) {
                final long key = in.readLong();
                final int value = in.readInt();
                if (LOG_ENABLED) {
                    Log.i(TAG, "Unmarshalling key=" + key + " value=" + value);
                }
                outVal.put(key, value);
                N--;
            }
        }

        private void readSparseBooleanArrayInternal(SparseArrayCompat<Boolean> outVal, Parcel in, int N) {
            while (N > 0) {
                int key = in.readInt();
                boolean value = in.readByte() == 1;
                if (LOG_ENABLED) {
                    Log.i(TAG, "Unmarshalling key=" + key + " value=" + value);
                }
                outVal.append(key, value);
                N--;
            }
        }

        private void writeSparseLongArray(LongSparseArray<Integer> array, Parcel out) {
            if (LOG_ENABLED) {
                Log.i(TAG, "writeSparseLongArray");
            }
            final int N = array != null ? array.size() : 0;
            out.writeInt(N);
            for (int i = 0; i < N; i++) {
                out.writeLong(array.keyAt(i));
                out.writeInt(array.valueAt(i));
            }
        }

        private void writeSparseBooleanArray(SparseArrayCompat<Boolean> val, Parcel out) {
            if (LOG_ENABLED) {
                Log.i(TAG, "writeSparseBooleanArray");
            }
            if (val == null) {
                out.writeInt(-1);
                return;
            }
            int N = val.size();
            out.writeInt(N);
            int i = 0;
            while (i < N) {
                out.writeInt(val.keyAt(i));
                out.writeByte((byte) (val.valueAt(i) ? 1 : 0));
                i++;
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeLong(selectedId);
            out.writeLong(firstId);
            out.writeInt(viewLeft);
            out.writeInt(position);
            out.writeInt(width);
            out.writeString(filter);
            out.writeByte((byte) (inActionMode ? 1 : 0));
            out.writeInt(checkedItemCount);

            if (LOG_ENABLED) {
                Log.d(TAG, "writing checkState: " + checkState);
                Log.d(TAG, "writing checkIdState: " + checkIdState);
            }

            writeSparseBooleanArray(checkState, out);
            writeSparseLongArray(checkIdState, out);
        }

        @Override
        public String toString() {
            return "AbsListView.SavedState{"
                + Integer.toHexString(System.identityHashCode(this))
                + " selectedId=" + selectedId
                + " firstId=" + firstId
                + " viewLeft=" + viewLeft
                + " position=" + position
                + " width=" + width
                + " filter=" + filter
                + " checkState=" + checkState + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        /*
         * This doesn't really make sense as the place to dismiss the
         * popups, but there don't seem to be any other useful hooks
         * that happen early enough to keep from getting complaints
         * about having leaked the window.
		 */
        //dismissPopup();

        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        if (mPendingSync != null) {
            // Just keep what we last restored.
            ss.selectedId = mPendingSync.selectedId;
            ss.firstId = mPendingSync.firstId;
            ss.viewLeft = mPendingSync.viewLeft;
            ss.position = mPendingSync.position;
            ss.width = mPendingSync.width;
            ss.filter = mPendingSync.filter;
            ss.inActionMode = mPendingSync.inActionMode;
            ss.checkedItemCount = mPendingSync.checkedItemCount;
            ss.checkState = mPendingSync.checkState;
            ss.checkIdState = mPendingSync.checkIdState;
            return ss;
        }

        boolean haveChildren = getChildCount() > 0 && mItemCount > 0;
        long selectedId = getSelectedItemId();
        ss.selectedId = selectedId;
        ss.width = getWidth();

        if (selectedId >= 0) {
            // Remember the selection
            ss.viewLeft = mSelectedLeft;
            ss.position = getSelectedItemPosition();
            ss.firstId = INVALID_POSITION;
        } else {
            if (haveChildren && mFirstPosition > 0) {
                // Remember the position of the first child.
                // We only do this if we are not currently at the top of
                // the list, for two reasons:
                // (1) The list may be in the process of becoming empty, in
                // which case mItemCount may not be 0, but if we try to
                // ask for any information about position 0 we will crash.
                // (2) Being "at the top" seems like a special case, anyway,
                // and the user wouldn't expect to end up somewhere else when
                // they revisit the list even if its content has changed.
                View v = getChildAt(0);
                ss.viewLeft = v.getLeft();
                int firstPos = mFirstPosition;
                if (firstPos >= mItemCount) {
                    firstPos = mItemCount - 1;
                }
                ss.position = firstPos;
                ss.firstId = mAdapter.getItemId(firstPos);
            } else {
                ss.viewLeft = 0;
                ss.firstId = INVALID_POSITION;
                ss.position = 0;
            }
        }

        ss.filter = null;
        ss.inActionMode = android.os.Build.VERSION.SDK_INT >= 11 && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL
            && mChoiceActionMode != null;

        if (mCheckStates != null) {
            try {
                ss.checkState = mCheckStates.clone();
            } catch (NoSuchMethodError e) {
                e.printStackTrace();
                ss.checkState = new SparseArrayCompat<Boolean>();
            }
        }
        if (mCheckedIdStates != null) {
            final LongSparseArray<Integer> idState = new LongSparseArray<Integer>();
            final int count = mCheckedIdStates.size();
            for (int i = 0; i < count; i++) {
                idState.put(mCheckedIdStates.keyAt(i), mCheckedIdStates.valueAt(i));
            }
            ss.checkIdState = idState;
        }
        ss.checkedItemCount = mCheckedItemCount;

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (LOG_ENABLED) {
            Log.i(TAG, "onRestoreInstanceState");
        }
        SavedState ss = (SavedState) state;

        super.onRestoreInstanceState(ss.getSuperState());
        mDataChanged = true;

        mSyncWidth = ss.width;

        if (ss.selectedId >= 0) {
            mNeedSync = true;
            mPendingSync = ss;
            mSyncColId = ss.selectedId;
            mSyncPosition = ss.position;
            mSpecificLeft = ss.viewLeft;
            mSyncMode = SYNC_SELECTED_POSITION;
        } else if (ss.firstId >= 0) {
            setSelectedPositionInt(INVALID_POSITION);
            // Do this before setting mNeedSync since setNextSelectedPosition looks at mNeedSync
            setNextSelectedPositionInt(INVALID_POSITION);
            mSelectorPosition = INVALID_POSITION;
            mNeedSync = true;
            mPendingSync = ss;
            mSyncColId = ss.firstId;
            mSyncPosition = ss.position;
            mSpecificLeft = ss.viewLeft;
            mSyncMode = SYNC_FIRST_POSITION;
        }

        if (LOG_ENABLED) {
            Log.d(TAG, "checkState: " + ss.checkState);
            Log.d(TAG, "checkIdState: " + ss.checkIdState);
        }

        if (ss.checkState != null) {
            mCheckStates = ss.checkState;
        }

        if (ss.checkIdState != null) {
            mCheckedIdStates = ss.checkIdState;
        }

        mCheckedItemCount = ss.checkedItemCount;

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            if (ss.inActionMode && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mMultiChoiceModeCallback != null) {
                mChoiceActionMode = startActionMode((MultiChoiceModeWrapper) mMultiChoiceModeCallback);
            }
        }

        requestLayout();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && mSelectedPosition < 0 && !isInTouchMode()) {
            if (!mAttachedToWindow && mAdapter != null) {
                // Data may have changed while we were detached and it's valid
                // to change focus while detached. Refresh so we don't die.
                mDataChanged = true;
                mOldItemCount = mItemCount;
                mItemCount = mAdapter.getCount();
            }
            resurrectSelection();
        }
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests && !mInLayout) {
            super.requestLayout();
        }
    }

    /**
     * The list is empty. Clear everything out.
     */
    protected void resetList() {
        removeAllViewsInLayout();
        mFirstPosition = 0;
        mDataChanged = false;
        mPositionScrollAfterLayout = null;
        mNeedSync = false;
        mPendingSync = null;
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedColId = INVALID_COL_ID;
        setSelectedPositionInt(INVALID_POSITION);
        setNextSelectedPositionInt(INVALID_POSITION);
        mSelectedLeft = 0;
        mSelectorPosition = INVALID_POSITION;
        mSelectorRect.setEmpty();
        invalidate();
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        final int count = getChildCount();
        if (count > 0) {
            if (mSmoothScrollbarEnabled) {
                int extent = count * 100;

                View view = getChildAt(0);
                final int left = view.getLeft();
                int width = view.getWidth();
                if (width > 0) {
                    extent += (left * 100) / width;
                }

                view = getChildAt(count - 1);
                final int right = view.getRight();
                width = view.getWidth();
                if (width > 0) {
                    extent -= ((right - getWidth()) * 100) / width;
                }

                return extent;
            } else {
                return 1;
            }
        }
        return 0;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        final int firstPosition = mFirstPosition;
        final int childCount = getChildCount();
        if (firstPosition >= 0 && childCount > 0) {
            if (mSmoothScrollbarEnabled) {
                final View view = getChildAt(0);
                final int left = view.getLeft();
                int width = view.getWidth();
                if (width > 0) {
                    return Math.max(
                        firstPosition * 100 - (left * 100) / width
                            + (int) ((float) getScrollX() / getWidth() * mItemCount * 100), 0);
                }
            } else {
                int index;
                final int count = mItemCount;
                if (firstPosition == 0) {
                    index = 0;
                } else if (firstPosition + childCount == count) {
                    index = count;
                } else {
                    index = firstPosition + childCount / 2;
                }
                return (int) (firstPosition + childCount * (index / (float) count));
            }
        }
        return 0;
    }

    @Override
    protected int computeHorizontalScrollRange() {
        int result;
        if (mSmoothScrollbarEnabled) {
            result = Math.max(mItemCount * 100, 0);
            if (getScrollX() != 0) {
                // Compensate for overscroll
                result += Math.abs((int) ((float) getScrollX() / getWidth() * mItemCount * 100));
            }
        } else {
            result = mItemCount;
        }
        return result;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        final int count = getChildCount();
        final float fadeEdge = super.getLeftFadingEdgeStrength();
        if (count == 0) {
            return fadeEdge;
        } else {
            if (mFirstPosition > 0) {
                return 1.0f;
            }

            final int left = getChildAt(0).getLeft();
            final float fadeLength = (float) getHorizontalFadingEdgeLength();
            return left < getPaddingLeft() ? (float) -(left - getPaddingLeft()) / fadeLength : fadeEdge;
        }
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        final int count = getChildCount();
        final float fadeEdge = super.getRightFadingEdgeStrength();
        if (count == 0) {
            return fadeEdge;
        } else {
            if (mFirstPosition + count - 1 < mItemCount - 1) {
                return 1.0f;
            }

            final int right = getChildAt(count - 1).getRight();
            final int width = getWidth();
            final float fadeLength = (float) getHorizontalFadingEdgeLength();
            return right > width - getPaddingRight() ?
                (float) (right - width + getPaddingRight()) / fadeLength : fadeEdge;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mSelector == null) {
            useDefaultSelector();
        }
        final Rect listPadding = mListPadding;
        listPadding.left = mSelectionLeftPadding + getPaddingLeft();
        listPadding.top = mSelectionTopPadding + getPaddingTop();
        listPadding.right = mSelectionRightPadding + getPaddingRight();
        listPadding.bottom = mSelectionBottomPadding + getPaddingBottom();

        // Check if our previous measured size was at a point where we should scroll later.
        if (mTranscriptMode == TRANSCRIPT_MODE_NORMAL) {
            final int childCount = getChildCount();
            final int listRight = getWidth() - getPaddingRight();
            final View lastChild = getChildAt(childCount - 1);
            final int lastRight = lastChild != null ? lastChild.getRight() : listRight;
            mForceTranscriptScroll = mFirstPosition + childCount >= mLastHandledItemCount && lastRight <= listRight;
        }
    }

    /**
     * Subclasses should NOT override this method but {@link #layoutChildren()} instead.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInLayout = true;
        final int childCount = getChildCount();
        if (changed) {
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).forceLayout();
            }
            mRecycler.markChildrenDirty();
        }

        layoutChildren();
        mInLayout = false;

        mOverscrollMax = (r - l) / OVERSCROLL_LIMIT_DIVISOR;
    }

    /**
     * Subclasses must override this method to layout their children.
     */
    protected void layoutChildren() {}

    /**
     * @param focusedView view that holds accessibility focus
     * @return direct child that contains accessibility focus, or null if no
     * child contains accessibility focus
     */
    View getAccessibilityFocusedChild(View focusedView) {
        ViewParent viewParent = focusedView.getParent();
        while ((viewParent instanceof View) && (viewParent != this)) {
            focusedView = (View) viewParent;
            viewParent = viewParent.getParent();
        }

        if (!(viewParent instanceof View)) {
            return null;
        }

        return focusedView;
    }

    void updateScrollIndicators() {
        if (mScrollLeft != null) {
            mScrollLeft.setVisibility(canScrollLeft() ? View.VISIBLE : View.INVISIBLE);
        }

        if (mScrollRight != null) {
            mScrollRight.setVisibility(canScrollRight() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private boolean canScrollLeft() {
        boolean canScrollLeft;
        // 0th element is not visible
        canScrollLeft = mFirstPosition > 0;

        // ... Or top of 0th element is not visible
        if (!canScrollLeft) {
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                canScrollLeft = child.getLeft() < mListPadding.left;
            }
        }

        return canScrollLeft;
    }

    private boolean canScrollRight() {
        boolean canScrollRight;
        int count = getChildCount();

        // Last item is not visible
        canScrollRight = (mFirstPosition + count) < mItemCount;

        // ... Or bottom of the last element is not visible
        if (!canScrollRight && count > 0) {
            View child = getChildAt(count - 1);
            canScrollRight = child.getRight() > getRight() - mListPadding.right;
        }

        return canScrollRight;
    }

    @Override
    @ViewDebug.ExportedProperty
    public View getSelectedView() {
        if (mItemCount > 0 && mSelectedPosition >= 0) {
            return getChildAt(mSelectedPosition - mFirstPosition);
        } else {
            return null;
        }
    }

    /**
     * List padding is the maximum of the normal view's padding and the padding of the selector.
     *
     * @return The top list padding.
     * @see android.view.View#getPaddingTop()
     * @see #getSelector()
     */
    public int getListPaddingTop() {
        return mListPadding.top;
    }

    /**
     * List padding is the maximum of the normal view's padding and the padding of the selector.
     *
     * @return The bottom list padding.
     * @see android.view.View#getPaddingBottom()
     * @see #getSelector()
     */
    public int getListPaddingBottom() {
        return mListPadding.bottom;
    }

    /**
     * List padding is the maximum of the normal view's padding and the padding of the selector.
     *
     * @return The left list padding.
     * @see android.view.View#getPaddingLeft()
     * @see #getSelector()
     */
    public int getListPaddingLeft() {
        return mListPadding.left;
    }

    /**
     * List padding is the maximum of the normal view's padding and the padding of the selector.
     *
     * @return The right list padding.
     * @see android.view.View#getPaddingRight()
     * @see #getSelector()
     */
    public int getListPaddingRight() {
        return mListPadding.right;
    }

    /**
     * Get a view and have it show the data associated with the specified position. This is called when we have already discovered
     * that the view is not available for reuse in the recycle bin. The only choices left are converting an old view or making a new
     * one.
     *
     * @param position The position to display
     * @param isScrap  Array of at least 1 boolean, the first entry will become true if the returned view was taken from the
     *                 scrap heap,
     *                 false if otherwise.
     * @return A view displaying the data associated with the specified position
     */
    protected View obtainView(int position, boolean[] isScrap) {
        isScrap[0] = false;

        // Check whether we have a transient state view. Attempt to re-bind the
        // data and discard the view if we fail.
        final View transientView = mRecycler.getTransientStateView(position);
        if (transientView != null) {
            final LayoutParams params = (LayoutParams) transientView.getLayoutParams();
            // If the view type hasn't changed, attempt to re-bind the data.
            if (params.viewType == mAdapter.getItemViewType(position)) {
                final View updatedView = mAdapter.getView(position, transientView, this);

                // If we failed to re-bind the data, scrap the obtained view.
                if (updatedView != transientView) {
                    setItemViewLayoutParams(updatedView, position);
                    mRecycler.addScrapView(updatedView, position);
                }
            }
            // Scrap view implies temporary detachment.
            isScrap[0] = true;
            return transientView;
        }

        final View scrapView = mRecycler.getScrapView(position);
        final View child = mAdapter.getView(position, scrapView, this);

        if (scrapView != null) {
            if (child != scrapView) {
                // Failed to re-bind the data, return scrap to the heap.
                mRecycler.addScrapView(scrapView, position);
            } else {
                isScrap[0] = true;
                child.onFinishTemporaryDetach();
            }
        }

        if (mCacheColorHint != 0) {
            child.setDrawingCacheBackgroundColor(mCacheColorHint);
        }


        if (android.os.Build.VERSION.SDK_INT >= 16) {
            if (child.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                child.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        }

        setItemViewLayoutParams(child, position);
        return child;
    }

    private void setItemViewLayoutParams(View child, int position) {
        final ViewGroup.LayoutParams vlp = child.getLayoutParams();
        LayoutParams lp;
        if (vlp == null) {
            lp = (LayoutParams) generateDefaultLayoutParams();
        } else if (!checkLayoutParams(vlp)) {
            lp = (LayoutParams) generateLayoutParams(vlp);
        } else {
            lp = (LayoutParams) vlp;
        }

        if (mAdapterHasStableIds) {
            lp.itemId = mAdapter.getItemId(position);
        }
        lp.viewType = mAdapter.getItemViewType(position);
        child.setLayoutParams(lp);
    }

    /**
     * Positions the selector in a way that mimics touch.
     */
    @SuppressWarnings("unused")
    void positionSelectorLikeTouch(int position, View sel, float x, float y) {
        positionSelectorLikeFocus(position, sel);

        if (mSelector != null && position != INVALID_POSITION) {
            DrawableCompat.setHotspot(mSelector, x, y);
        }
    }

    /**
     * Positions the selector in a way that mimics keyboard focus.
     */
    void positionSelectorLikeFocus(int position, View sel) {
        // If we're changing position, update the visibility since the selector
        // is technically being detached from the previous selection.
        final Drawable selector = mSelector;
        final boolean manageState = selector != null && mSelectorPosition != position
            && position != INVALID_POSITION;
        if (manageState) {
            selector.setVisible(false, false);
        }

        positionSelector(position, sel);

        if (manageState) {
            final Rect bounds = mSelectorRect;
            final float x = bounds.exactCenterX();
            final float y = bounds.exactCenterY();
            selector.setVisible(getVisibility() == VISIBLE, false);
            DrawableCompat.setHotspot(selector, x, y);
        }
    }

    protected void positionSelector(int position, View sel) {
        if (position != INVALID_POSITION) {
            mSelectorPosition = position;
        }

        final Rect selectorRect = mSelectorRect;
        selectorRect.set(sel.getLeft(), sel.getTop(), sel.getRight(), sel.getBottom());
        if (sel instanceof SelectionBoundsAdjuster) {
            ((SelectionBoundsAdjuster) sel).adjustListItemSelectionBounds(selectorRect);
        }

        // Adjust for selection padding.
        selectorRect.left -= mSelectionLeftPadding;
        selectorRect.right += mSelectionRightPadding;
        selectorRect.top -= mSelectionTopPadding;
        selectorRect.bottom += mSelectionBottomPadding;

        // Update the selector drawable.
        final Drawable selector = mSelector;
        if (selector != null) {
            selector.setBounds(selectorRect);
        }

        final boolean isChildViewEnabled = mIsChildViewEnabled;
        if (sel.isEnabled() != isChildViewEnabled) {
            mIsChildViewEnabled = !isChildViewEnabled;
            if (getSelectedItemPosition() != INVALID_POSITION) {
                refreshDrawableState();
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int saveCount = 0;
        //        final boolean clipToPadding = (mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK;
        //        if (clipToPadding) {
        //            saveCount = canvas.save();
        //            final int scrollX = mScrollX;
        //            final int scrollY = mScrollY;
        //            canvas.clipRect(scrollX + mPaddingLeft, scrollY + mPaddingTop,
        //                scrollX + mRight - mLeft - mPaddingRight,
        //                scrollY + mBottom - mTop - mPaddingBottom);
        //            mGroupFlags &= ~CLIP_TO_PADDING_MASK;
        //        }

        final boolean drawSelectorOnTop = mDrawSelectorOnTop;
        if (!drawSelectorOnTop) {
            drawSelector(canvas);
        }

        super.dispatchDraw(canvas);

        if (drawSelectorOnTop) {
            drawSelector(canvas);
        }
        //
        //        if (clipToPadding) {
        //            canvas.restoreToCount(saveCount);
        //            mGroupFlags |= CLIP_TO_PADDING_MASK;
        //        }
    }

	/*
     * @Override protected boolean isPaddingOffsetRequired() { return ( mGroupFlags & CLIP_TO_PADDING_MASK ) !=
	 * CLIP_TO_PADDING_MASK;
	 * }
	 * 
	 * @Override protected int getLeftPaddingOffset() { return ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ? 0 :
	 * -mPaddingLeft; }
	 * 
	 * @Override protected int getTopPaddingOffset() { return ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ? 0 :
	 * -mPaddingTop; }
	 * 
	 * @Override protected int getRightPaddingOffset() { return ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ?
	  * 0 :
	 * mPaddingRight; }
	 * 
	 * @Override protected int getBottomPaddingOffset() { return ( mGroupFlags & CLIP_TO_PADDING_MASK ) == CLIP_TO_PADDING_MASK ?
	  * 0 :
	 * mPaddingBottom; }
	 */

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (getChildCount() > 0) {
            mDataChanged = true;
            rememberSyncState();
        }
    }

    /**
     * @return True if the current touch mode requires that we draw the selector in the pressed state.
     */
    boolean touchModeDrawsInPressedState() {
        // FIXME use isPressed for this
        switch (mTouchMode) {
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                return true;
            default:
                return false;
        }
    }

    /**
     * Indicates whether this view is in a state where the selector should be drawn. This will happen if we have focus but are
     * not in
     * touch mode, or we are in the middle of displaying the pressed state for an item.
     *
     * @return True if the selector should be shown
     */
    protected boolean shouldShowSelector() {
        return (hasFocus() && !isInTouchMode()) || touchModeDrawsInPressedState();
    }

    private void drawSelector(Canvas canvas) {
        if (!mSelectorRect.isEmpty()) {
            final Drawable selector = mSelector;
            selector.setBounds(mSelectorRect);
            selector.draw(canvas);
        }
    }

    /**
     * Controls whether the selection highlight drawable should be drawn on top of the item or behind it.
     *
     * @param onTop If true, the selector will be drawn on the item it is highlighting. The default is false.
     * @attr ref android.R.styleable#AbsListView_drawSelectorOnTop
     */
    public void setDrawSelectorOnTop(boolean onTop) {
        mDrawSelectorOnTop = onTop;
    }

    /**
     * Set a Drawable that should be used to highlight the currently selected item.
     *
     * @param resID A Drawable resource to use as the selection highlight.
     * @attr ref android.R.styleable#AbsListView_listSelector
     */
    @TargetApi (21)
    public void setSelector(int resID) {
        if (ApiHelper.AT_LEAST_21) {
            setSelector(getContext().getDrawable(resID));
        } else {
            setSelector(getResources().getDrawable(resID));
        }
    }

    public void setSelector(Drawable sel) {
        Log.i(TAG, "setSelector: " + sel);
        if (mSelector != null) {
            mSelector.setCallback(null);
            unscheduleDrawable(mSelector);
        }
        mSelector = sel;
        Rect padding = new Rect();
        sel.getPadding(padding);
        mSelectionLeftPadding = padding.left;
        mSelectionTopPadding = padding.top;
        mSelectionRightPadding = padding.right;
        mSelectionBottomPadding = padding.bottom;
        sel.setCallback(this);
        updateSelectorState();
    }

    /**
     * Returns the selector {@link android.graphics.drawable.Drawable} that is used to draw the selection in the list.
     *
     * @return the drawable used to display the selector
     */
    public Drawable getSelector() {
        return mSelector;
    }

    /**
     * Sets the selector state to "pressed" and posts a CheckForKeyLongPress to see if this is a long press.
     */
    protected void keyPressed() {
        if (!isEnabled() || !isClickable()) {
            return;
        }

        Drawable selector = mSelector;
        Rect selectorRect = mSelectorRect;
        if (selector != null && (isFocused() || touchModeDrawsInPressedState())
            && !selectorRect.isEmpty()) {

            final View v = getChildAt(mSelectedPosition - mFirstPosition);

            if (v != null) {
                if (v.hasFocusable()) {
                    return;
                }
                v.setPressed(true);
            }
            setPressed(true);

            final boolean longClickable = isLongClickable();
            Drawable d = selector.getCurrent();
            if (d != null && d instanceof TransitionDrawable) {
                if (longClickable) {
                    ((TransitionDrawable) d).startTransition(
                        ViewConfiguration.getLongPressTimeout());
                } else {
                    ((TransitionDrawable) d).resetTransition();
                }
            }
            if (longClickable && !mDataChanged) {
                if (mPendingCheckForKeyLongPress == null) {
                    mPendingCheckForKeyLongPress = new CheckForKeyLongPress();
                }
                mPendingCheckForKeyLongPress.rememberWindowAttachCount();
                postDelayed(mPendingCheckForKeyLongPress, ViewConfiguration.getLongPressTimeout());
            }
        }
    }

    public void setScrollIndicators(View left, View right) {
        mScrollLeft = left;
        mScrollRight = right;
    }

    public static final int[] STATESET_NOTHING = new int[]{0};

    void updateSelectorState() {
        if (mSelector != null) {
            if (shouldShowSelector()) {
                mSelector.setState(getDrawableState());
            } else {
                mSelector.setState(STATESET_NOTHING);
            }
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateSelectorState();
    }

    @SuppressLint ("Override")
    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // If the child view is enabled then do the default behavior.
        if (mIsChildViewEnabled) {
            // Common case
            return super.onCreateDrawableState(extraSpace);
        }

        // The selector uses this View's drawable state. The selected child view
        // is disabled, so we need to remove the enabled state from the drawable
        // states.
        final int enabledState = ENABLED_STATE_SET[0];

        // If we don't have any extra space, it will return one of the static state arrays,
        // and clearing the enabled state on those arrays is a bad thing! If we specify
        // we need extra space, it will create+copy into a new array that safely mutable.
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        int enabledPos = -1;
        for (int i = state.length - 1; i >= 0; i--) {
            if (state[i] == enabledState) {
                enabledPos = i;
                break;
            }
        }

        // Remove the enabled state
        if (enabledPos >= 0) {
            System.arraycopy(
                state, enabledPos + 1, state, enabledPos,
                state.length - enabledPos - 1);
        }

        return state;
    }

    @Override
    public boolean verifyDrawable(Drawable dr) {
        return mSelector == dr || super.verifyDrawable(dr);
    }

    @TargetApi (11)
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mSelector != null) {
            mSelector.jumpToCurrentState();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;

        final ViewTreeObserver treeObserver = getViewTreeObserver();
        treeObserver.addOnTouchModeChangeListener(this);

        if (mAdapter != null && mDataSetObserver == null) {
            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);

            // Data may have changed while we were detached. Refresh.
            mDataChanged = true;
            mOldItemCount = mItemCount;
            mItemCount = mAdapter.getCount();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttachedToWindow = false;
        mIsDetaching = true;

        // Dismiss the popup in case onSaveInstanceState() was not invoked

        // Detach any view left in the scrap heap
        mRecycler.clear();

        final ViewTreeObserver treeObserver = getViewTreeObserver();
        treeObserver.removeOnTouchModeChangeListener(this);

        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            mDataSetObserver = null;
        }

        if (mFlingRunnable != null) {
            removeCallbacks(mFlingRunnable);
        }

        if (mPositionScroller != null) {
            mPositionScroller.stop();
        }

        if (mClearScrollingCache != null) {
            removeCallbacks(mClearScrollingCache);
        }

        if (mPerformClick != null) {
            removeCallbacks(mPerformClick);
        }

        if (mTouchModeReset != null) {
            removeCallbacks(mTouchModeReset);
            mTouchModeReset = null;
        }
        mIsDetaching = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        final int touchMode = isInTouchMode() ? TOUCH_MODE_ON : TOUCH_MODE_OFF;

        if (!hasWindowFocus) {
            setChildrenDrawingCacheEnabled(false);
            if (mFlingRunnable != null) {
                removeCallbacks(mFlingRunnable);
                // let the fling runnable report it's new state which
                // should be idle
                mFlingRunnable.endFling();
                if (mPositionScroller != null) {
                    mPositionScroller.stop();
                }
                if (getScrollX() != 0) {
                    mViewHelper.setScrollX(0);
                    finishGlows();
                    invalidate();
                }
            }
            // Always hide the type filter

            if (touchMode == TOUCH_MODE_OFF) {
                // Remember the last selected element
                mResurrectToPosition = mSelectedPosition;
            }
        } else {
            // If we changed touch mode since the last time we had focus
            if (touchMode != mLastTouchMode && mLastTouchMode != TOUCH_MODE_UNKNOWN) {
                // If we come back in trackball mode, we bring the selection back
                if (touchMode == TOUCH_MODE_OFF) {
                    // This will trigger a layout
                    resurrectSelection();

                    // If we come back in touch mode, then we want to hide the selector
                } else {
                    hideSelector();
                    mLayoutMode = LAYOUT_NORMAL;
                    layoutChildren();
                }
            }
        }

        mLastTouchMode = touchMode;
    }

    /**
     * Creates the ContextMenuInfo returned from {@link #getContextMenuInfo()}. This methods knows the view, position and ID of the
     * item that received the long press.
     *
     * @param view     The view that received the long press.
     * @param position The position of the item that received the long press.
     * @param id       The ID of the item that received the long press.
     * @return The extra information that should be returned by {@link #getContextMenuInfo()}.
     */
    ContextMenuInfo createContextMenuInfo(View view, int position, long id) {
        return new AdapterContextMenuInfo(view, position, id);
    }

    /**
     * A base class for Runnables that will check that their view is still attached to the original window as when the Runnable was
     * created.
     */
    private class WindowRunnnable {
        private int mOriginalAttachCount;

        public void rememberWindowAttachCount() {
            mOriginalAttachCount = getWindowAttachCount();
        }

        public boolean sameWindow() {
            return hasWindowFocus() && getWindowAttachCount() == mOriginalAttachCount;
        }
    }

    private class PerformClick extends WindowRunnnable implements Runnable {
        int mClickMotionPosition;

        @Override
        public void run() {
            // The data has changed since we posted this action in the event queue,
            // bail out before bad things happen
            if (mDataChanged) {
                return;
            }

            final ListAdapter adapter = mAdapter;
            final int motionPosition = mClickMotionPosition;
            if (adapter != null && mItemCount > 0 &&
                motionPosition != INVALID_POSITION &&
                motionPosition < adapter.getCount() && sameWindow()) {
                final View view = getChildAt(motionPosition - mFirstPosition);
                // If there is no view, something bad happened (the view scrolled off the
                // screen, etc.) and we should cancel the click
                if (view != null) {
                    performItemClick(view, motionPosition, adapter.getItemId(motionPosition));
                }
            }
        }
    }

    private class CheckForLongPress extends WindowRunnnable implements Runnable {
        @Override
        public void run() {
            final int motionPosition = mMotionPosition;
            final View child = getChildAt(motionPosition - mFirstPosition);
            if (child != null) {
                final int longPressPosition = mMotionPosition;
                final long longPressId = mAdapter.getItemId(mMotionPosition);

                boolean handled = false;
                if (sameWindow() && !mDataChanged) {
                    handled = performLongPress(child, longPressPosition, longPressId);
                }
                if (handled) {
                    mTouchMode = TOUCH_MODE_REST;
                    setPressed(false);
                    child.setPressed(false);
                } else {
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                }
            }
        }
    }

    private class CheckForKeyLongPress extends WindowRunnnable implements Runnable {
        @Override
        public void run() {
            if (isPressed() && mSelectedPosition >= 0) {
                int index = mSelectedPosition - mFirstPosition;
                View v = getChildAt(index);

                if (!mDataChanged) {
                    boolean handled = false;
                    if (sameWindow()) {
                        handled = performLongPress(v, mSelectedPosition, mSelectedColId);
                    }
                    if (handled) {
                        setPressed(false);
                        v.setPressed(false);
                    }
                } else {
                    setPressed(false);
                    if (v != null) {
                        v.setPressed(false);
                    }
                }
            }
        }
    }

    boolean performLongPress(final View child, final int longPressPosition, final long longPressId) {

        // CHOICE_MODE_MULTIPLE_MODAL takes over long press.
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
                if (mChoiceActionMode == null &&
                    (mChoiceActionMode = startActionMode((MultiChoiceModeWrapper) mMultiChoiceModeCallback)) != null) {
                    setItemChecked(longPressPosition, true);
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                return true;
            }
        }

        boolean handled = false;
        if (mOnItemLongClickListener != null) {
            handled = mOnItemLongClickListener.onItemLongClick(
                AbsHListView.this, child,
                longPressPosition, longPressId);
        }
        if (!handled) {
            mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
            handled = super.showContextMenuForChild(AbsHListView.this);
        }
        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        return handled;
    }

    @Override
    protected ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    public boolean showContextMenu(float x, float y, int metaState) {
        final int position = pointToPosition((int) x, (int) y);
        if (position != INVALID_POSITION) {
            final long id = mAdapter.getItemId(position);
            View child = getChildAt(position - mFirstPosition);
            if (child != null) {
                mContextMenuInfo = createContextMenuInfo(child, position, id);
                return super.showContextMenuForChild(AbsHListView.this);
            }
        }
        return showContextMenu(x, y, metaState);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        final int longPressPosition = getPositionForView(originalView);
        if (longPressPosition >= 0) {
            final long longPressId = mAdapter.getItemId(longPressPosition);
            boolean handled = false;

            if (mOnItemLongClickListener != null) {
                handled = mOnItemLongClickListener.onItemLongClick(
                    AbsHListView.this, originalView,
                    longPressPosition, longPressId);
            }
            if (!handled) {
                mContextMenuInfo = createContextMenuInfo(
                    getChildAt(longPressPosition - mFirstPosition),
                    longPressPosition, longPressId);
                handled = super.showContextMenuForChild(originalView);
            }

            return handled;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!isEnabled()) {
                    return true;
                }
                if (isClickable() && isPressed() &&
                    mSelectedPosition >= 0 && mAdapter != null &&
                    mSelectedPosition < mAdapter.getCount()) {

                    final View view = getChildAt(mSelectedPosition - mFirstPosition);
                    if (view != null) {
                        performItemClick(view, mSelectedPosition, mSelectedColId);
                        view.setPressed(false);
                    }
                    setPressed(false);
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // Don't dispatch setPressed to our children. We call setPressed on ourselves to
        // get the selector in the right state, but we don't want to press each child.
    }

    /**
     * Maps a point to a position in the list.
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     * @return The position of the item which contains the specified point, or {@link #INVALID_POSITION} if the point does not
     * intersect an item.
     */
    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }

        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Maps a point to a the colId of the item which intersects that point.
     *
     * @param x X in local coordinate
     * @param y Y in local coordinate
     * @return The colId of the item which contains the specified point, or {@link #INVALID_COL_ID} if the point does not intersect
     * an item.
     */
    public long pointToColId(int x, int y) {
        int position = pointToPosition(x, y);
        if (position >= 0) {
            return mAdapter.getItemId(position);
        }
        return INVALID_COL_ID;
    }

    private final class CheckForTap implements Runnable {
        float x;
        float y;

        @Override
        public void run() {
            if (mTouchMode == TOUCH_MODE_DOWN) {
                mTouchMode = TOUCH_MODE_TAP;
                final View child = getChildAt(mMotionPosition - mFirstPosition);
                if (child != null && !child.hasFocusable()) {
                    mLayoutMode = LAYOUT_NORMAL;

                    if (!mDataChanged) {
                        child.setPressed(true);
                        setPressed(true);
                        layoutChildren();
                        positionSelector(mMotionPosition, child);
                        refreshDrawableState();

                        final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
                        final boolean longClickable = isLongClickable();

                        if (mSelector != null) {
                            final Drawable d = mSelector.getCurrent();
                            if (d != null && d instanceof TransitionDrawable) {
                                if (longClickable) {
                                    ((TransitionDrawable) d).startTransition(longPressTimeout);
                                } else {
                                    ((TransitionDrawable) d).resetTransition();
                                }
                            }
                            DrawableCompat.setHotspot(mSelector, x, y);
                        }

                        if (longClickable) {
                            if (mPendingCheckForLongPress == null) {
                                mPendingCheckForLongPress = new CheckForLongPress();
                            }
                            mPendingCheckForLongPress.rememberWindowAttachCount();
                            postDelayed(mPendingCheckForLongPress, longPressTimeout);
                        } else {
                            mTouchMode = TOUCH_MODE_DONE_WAITING;
                        }
                    } else {
                        mTouchMode = TOUCH_MODE_DONE_WAITING;
                    }
                }
            }
        }
    }

    private boolean startScrollIfNeeded(int x, int y, MotionEvent vtev) {
        // Check if we have moved far enough that it looks more like a
        // scroll than a tap
        final int deltaX = x - mMotionX;
        final int distance = Math.abs(deltaX);
        final boolean overscroll = getScrollX() != 0;
        if ((overscroll || distance > mTouchSlop) &&
            (mViewHelper.getNestedScrollAxes() & SCROLL_AXIS_HORIZONTAL) == 0) {
            createScrollingCache();
            if (overscroll) {
                mTouchMode = TOUCH_MODE_OVERSCROLL;
                mMotionCorrection = 0;
            } else {
                mTouchMode = TOUCH_MODE_SCROLL;
                mMotionCorrection = deltaX > 0 ? mTouchSlop : -mTouchSlop;
            }
            final Handler handler = getHandler();
            // Handler should not be null unless the AbsListView is not attached to a
            // window, which would make it very hard to scroll it... but the monkeys
            // say it's possible.
            if (handler != null) {
                handler.removeCallbacks(mPendingCheckForLongPress);
            }
            setPressed(false);
            View motionView = getChildAt(mMotionPosition - mFirstPosition);
            if (motionView != null) {
                motionView.setPressed(false);
            }
            reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
            // Time to start stealing events! Once we've stolen them, don't let anyone
            // steal from us
            final ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
            scrollIfNeeded(x, y, vtev);
            return true;
        }

        return false;
    }

    @TargetApi (21)
    private void scrollIfNeeded(int x, int y, MotionEvent vtev) {
        int rawDeltaX = x - mMotionX;

        int scrollOffsetCorrection = 0;
        int scrollConsumedCorrection = 0;
        if (mLastX == Integer.MIN_VALUE) {
            rawDeltaX -= mMotionCorrection;
        }

        if (ApiHelper.AT_LEAST_21 && dispatchNestedPreScroll(
            0, mLastX != Integer.MIN_VALUE ? mLastX - x : -rawDeltaX,
            mScrollConsumed, mScrollOffset)) {
            rawDeltaX += mScrollConsumed[0];
            scrollOffsetCorrection = -mScrollOffset[0];
            scrollConsumedCorrection = mScrollConsumed[0];
            if (vtev != null) {
                vtev.offsetLocation(mScrollOffset[0], 0);
                mNestedXOffset += mScrollOffset[0];
            }
        }

        final int deltaX = rawDeltaX;
        int incrementalDeltaX =
            mLastX != Integer.MIN_VALUE ? x - mLastX + scrollConsumedCorrection : deltaX;
        int lastXCorrection = 0;

        if (mTouchMode == TOUCH_MODE_SCROLL) {

            if (x != mLastX) {
                // We may be here after stopping a fling and continuing to scroll.
                // If so, we haven't disallowed intercepting touch events yet.
                // Make sure that we do so in case we're in a parent that can intercept.
                if (Math.abs(rawDeltaX) > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                final int motionIndex;
                if (mMotionPosition >= 0) {
                    motionIndex = mMotionPosition - mFirstPosition;
                } else {
                    // If we don't have a motion position that we can reliably track,
                    // pick something in the middle to make a best guess at things below.
                    motionIndex = getChildCount() / 2;
                }

                int motionViewPrevLeft = 0;
                View motionView = this.getChildAt(motionIndex);
                if (motionView != null) {
                    motionViewPrevLeft = motionView.getLeft();
                }

                // No need to do all this work if we're not going to move anyway
                boolean atEdge = false;
                if (incrementalDeltaX != 0) {
                    atEdge = trackMotionScroll(deltaX, incrementalDeltaX);
                }

                // Check to see if we have bumped into the scroll limit
                motionView = this.getChildAt(motionIndex);
                if (motionView != null) {
                    // Check if the top of the motion view is where it is
                    // supposed to be
                    final int motionViewRealLeft = motionView.getLeft();
                    if (atEdge) {
                        // Apply overscroll

                        int overscroll = -incrementalDeltaX - (motionViewRealLeft - motionViewPrevLeft);
                        if (ApiHelper.AT_LEAST_21 && dispatchNestedScroll(
                            overscroll - incrementalDeltaX, 0, overscroll, 0,
                            mScrollOffset)) {
                            lastXCorrection -= mScrollOffset[0];
                            if (vtev != null) {
                                vtev.offsetLocation(mScrollOffset[0], 0);
                                mNestedXOffset += mScrollOffset[0];
                            }
                        } else {
                            final boolean atOverscrollEdge = overScrollBy(
                                overscroll, 0,
                                getScrollX(), 0, 0, 0, mOverscrollDistance, 0, true);

                            if (atOverscrollEdge && mVelocityTracker != null) {
                                // Don't allow overfling if we're at the edge
                                mVelocityTracker.clear();
                            }

                            final int overscrollMode = getOverScrollMode();
                            if (overscrollMode == OVER_SCROLL_ALWAYS ||
                                (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS &&
                                    !contentFits())) {
                                if (!atOverscrollEdge) {
                                    mDirection = 0; // Reset when entering overscroll.
                                    mTouchMode = TOUCH_MODE_OVERSCROLL;
                                }
                                if (incrementalDeltaX > 0) {
                                    mEdgeGlowTop.onPull((float) -overscroll / getWidth());
                                    if (!mEdgeGlowBottom.isFinished()) {
                                        mEdgeGlowBottom.onRelease();
                                    }
                                    //invalidate(0, 0, getWidth(), mEdgeGlowTop.getMaxHeight() + getPaddingTop());
                                    invalidate();
                                } else if (incrementalDeltaX < 0) {
                                    mEdgeGlowBottom.onPull((float) overscroll / getWidth());
                                    if (!mEdgeGlowTop.isFinished()) {
                                        mEdgeGlowTop.onRelease();
                                    }
                                    invalidate();
                                    //invalidate(0, getHeight() - getPaddingBottom() -mEdgeGlowBottom.getMaxHeight(), getWidth(),
                                    // getHeight());
                                }
                            }
                        }
                    }
                    mMotionX = x + lastXCorrection + scrollOffsetCorrection;
                }
                mLastX = x + lastXCorrection + scrollOffsetCorrection;
            }
        } else if (mTouchMode == TOUCH_MODE_OVERSCROLL) {
            if (x != mLastX) {
                final int oldScroll = getScrollX();
                final int newScroll = oldScroll - incrementalDeltaX;
                int newDirection = x > mLastX ? 1 : -1;

                if (mDirection == 0) {
                    mDirection = newDirection;
                }

                int overScrollDistance = -incrementalDeltaX;
                if ((newScroll < 0 && oldScroll >= 0) || (newScroll > 0 && oldScroll <= 0)) {
                    overScrollDistance = -oldScroll;
                    incrementalDeltaX += overScrollDistance;
                } else {
                    incrementalDeltaX = 0;
                }

                if (overScrollDistance != 0) {
                    overScrollBy(overScrollDistance, 0, getScrollX(), 0, 0, 0, mOverscrollDistance, 0, true);
                    final int overscrollMode = getOverScrollMode();
                    if (overscrollMode == OVER_SCROLL_ALWAYS || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS
                        && !contentFits())) {
                        if (rawDeltaX > 0) {
                            mEdgeGlowTop.onPull((float) overScrollDistance / getWidth());
                            if (!mEdgeGlowBottom.isFinished()) {
                                mEdgeGlowBottom.onRelease();
                            }
                            invalidate();
                        } else if (rawDeltaX < 0) {
                            mEdgeGlowBottom.onPull((float) overScrollDistance / getWidth());
                            if (!mEdgeGlowTop.isFinished()) {
                                mEdgeGlowTop.onRelease();
                            }
                            invalidate();
                        }
                    }
                }

                if (incrementalDeltaX != 0) {
                    // Coming back to 'real' list scrolling
                    if (getScrollX() != 0) {
                        mViewHelper.setScrollX(0);
                        invalidateParentIfNeeded();
                    }

                    trackMotionScroll(incrementalDeltaX, incrementalDeltaX);

                    mTouchMode = TOUCH_MODE_SCROLL;

                    // We did not scroll the full amount. Treat this essentially like the
                    // start of a new touch scroll
                    final int motionPosition = findClosestMotionCol(x);

                    mMotionCorrection = 0;
                    View motionView = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalLeft = motionView != null ? motionView.getLeft() : 0;
                    mMotionX = x + scrollOffsetCorrection;
                    mMotionPosition = motionPosition;
                }
                mLastX = x + lastXCorrection + scrollOffsetCorrection;
                mDirection = newDirection;
            }
        }
    }

    @TargetApi (11)
    protected void invalidateParentIfNeeded() {
        if (mViewHelper.isHardwareAccelerated() && getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
        if (isInTouchMode) {
            // Get rid of the selection when we enter touch mode
            hideSelector();
            // Layout, but only if we already have done so previously.
            // (Otherwise may clobber a LAYOUT_SYNC layout that was requested to restore
            // state.)
            if (getWidth() > 0 && getChildCount() > 0) {
                // We do not lose focus initiating a touch (since AbsListView is focusable in
                // touch mode). Force an initial layout to get rid of the selection.
                layoutChildren();
            }
            updateSelectorState();
        } else {
            int touchMode = mTouchMode;
            if (touchMode == TOUCH_MODE_OVERSCROLL || touchMode == TOUCH_MODE_OVERFLING) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.endFling();
                }
                if (mPositionScroller != null) {
                    mPositionScroller.stop();
                }

                if (getScrollX() != 0) {
                    mViewHelper.setScrollX(0);
                    finishGlows();
                    invalidate();
                }
            }
        }
    }

    /**
     * Performs button-related actions during a touch down event.
     *
     * @param event The event.
     * @return True if the down was consumed.
     */
    @TargetApi (14)
    protected boolean performButtonActionOnTouchDown(MotionEvent event) {
        if (android.os.Build.VERSION.SDK_INT >= 14) {
            if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                if (showContextMenu(event.getX(), event.getY(), event.getMetaState())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressLint ("Override")
    @TargetApi (21)
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            return isClickable() || isLongClickable();
        }

        if (mPositionScroller != null) {
            mPositionScroller.stop();
        }

        if (mIsDetaching || !mAttachedToWindow) {
            // Something isn't right.
            // Since we rely on being attached to get data set change notifications,
            // don't risk doing anything where we might try to resync and find things
            // in a bogus state.
            return false;
        }

        if (ApiHelper.AT_LEAST_21) {
            startNestedScroll(SCROLL_AXIS_HORIZONTAL);
        }
        initVelocityTrackerIfNotExists();
        final MotionEvent vtev = MotionEvent.obtain(ev);

        final int actionMasked = ev.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedXOffset = 0;
        }
        vtev.offsetLocation(mNestedXOffset, 0);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                onTouchDown(ev);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                onTouchMove(ev, vtev);
                break;
            }

            case MotionEvent.ACTION_UP: {
                onTouchUp(ev);
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                onTouchCancel();
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                final int x = mMotionX;
                final int y = mMotionY;
                final int motionPosition = pointToPosition(x, y);
                if (motionPosition >= 0) {
                    // Remember where the motion event started
                    final View child = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalLeft = child.getLeft();
                    mMotionPosition = motionPosition;
                }
                mLastX = x;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                // New pointers take over dragging duties
                final int index = ev.getActionIndex();
                final int id = ev.getPointerId(index);
                final int x = (int) ev.getX(index);
                final int y = (int) ev.getY(index);
                mMotionCorrection = 0;
                mActivePointerId = id;
                mMotionX = x;
                mMotionY = y;
                final int motionPosition = pointToPosition(x, y);
                if (motionPosition >= 0) {
                    // Remember where the motion event started
                    final View v = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalLeft = v.getLeft();
                    mMotionPosition = motionPosition;
                }
                mLastX = x;
                break;
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();
        return true;
    }

    private void onTouchDown(MotionEvent ev) {
        mActivePointerId = ev.getPointerId(0);

        if (mTouchMode == TOUCH_MODE_OVERFLING) {
            // Stopped the fling. It is a scroll.
            mFlingRunnable.endFling();
            if (mPositionScroller != null) {
                mPositionScroller.stop();
            }
            mTouchMode = TOUCH_MODE_OVERSCROLL;
            mMotionX = (int) ev.getX();
            mMotionY = (int) ev.getY();
            mLastX = mMotionX;
            mMotionCorrection = 0;
            mDirection = 0;
        } else {
            final int x = (int) ev.getX();
            final int y = (int) ev.getY();
            int motionPosition = pointToPosition(x, y);

            if (!mDataChanged) {
                if (mTouchMode == TOUCH_MODE_FLING) {
                    // Stopped a fling. It is a scroll.
                    createScrollingCache();
                    mTouchMode = TOUCH_MODE_SCROLL;
                    mMotionCorrection = 0;
                    motionPosition = findMotionCol(x);
                    mFlingRunnable.flywheelTouch();
                } else if ((motionPosition >= 0) && getAdapter().isEnabled(motionPosition)) {
                    // User clicked on an actual view (and was not stopping a
                    // fling). It might be a click or a scroll. Assume it is a
                    // click until proven otherwise.
                    mTouchMode = TOUCH_MODE_DOWN;

                    // FIXME Debounce
                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }

                    mPendingCheckForTap.x = ev.getX();
                    mPendingCheckForTap.y = ev.getY();
                    postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                }
            }

            if (motionPosition >= 0) {
                // Remember where the motion event started
                final View v = getChildAt(motionPosition - mFirstPosition);
                mMotionViewOriginalLeft = v.getLeft();
            }

            mMotionX = x;
            mMotionY = y;
            mMotionPosition = motionPosition;
            mLastX = Integer.MIN_VALUE;
        }

        if (mTouchMode == TOUCH_MODE_DOWN && mMotionPosition != INVALID_POSITION
            && performButtonActionOnTouchDown(ev)) {
            removeCallbacks(mPendingCheckForTap);
        }
    }

    private void onTouchMove(MotionEvent ev, MotionEvent vtev) {
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) {
            pointerIndex = 0;
            mActivePointerId = ev.getPointerId(pointerIndex);
        }

        if (mDataChanged) {
            // Re-sync everything if data has been changed
            // since the scroll operation can query the adapter.
            layoutChildren();
        }

        final int x = (int) ev.getX(pointerIndex);

        switch (mTouchMode) {
            case TOUCH_MODE_DOWN:
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                // Check if we have moved far enough that it looks more like a
                // scroll than a tap. If so, we'll enter scrolling mode.
                if (startScrollIfNeeded(x, (int) ev.getY(pointerIndex), vtev)) {
                    break;
                }
                // Otherwise, check containment within list bounds. If we're
                // outside bounds, cancel any active presses.
                final float y = ev.getY(pointerIndex);
                if (!mViewHelper.pointInView(x, y, mTouchSlop)) {
                    setPressed(false);
                    final View motionView = getChildAt(mMotionPosition - mFirstPosition);
                    if (motionView != null) {
                        motionView.setPressed(false);
                    }
                    removeCallbacks(
                        mTouchMode == TOUCH_MODE_DOWN ?
                            mPendingCheckForTap : mPendingCheckForLongPress);
                    mTouchMode = TOUCH_MODE_DONE_WAITING;
                    updateSelectorState();
                }
                break;
            case TOUCH_MODE_SCROLL:
            case TOUCH_MODE_OVERSCROLL:
                scrollIfNeeded(x, (int) ev.getY(pointerIndex), vtev);
                break;
        }
    }

    @TargetApi (21)
    private void onTouchUp(MotionEvent ev) {
        switch (mTouchMode) {
            case TOUCH_MODE_DOWN:
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                final int motionPosition = mMotionPosition;
                final View child = getChildAt(motionPosition - mFirstPosition);
                if (child != null) {
                    if (mTouchMode != TOUCH_MODE_DOWN) {
                        child.setPressed(false);
                    }

                    final float y = ev.getY();
                    final boolean inList = y > mListPadding.top && y < getHeight() - mListPadding.bottom;
                    if (inList && !child.hasFocusable()) {
                        if (mPerformClick == null) {
                            mPerformClick = new PerformClick();
                        }

                        final AbsHListView.PerformClick performClick = mPerformClick;
                        performClick.mClickMotionPosition = motionPosition;
                        performClick.rememberWindowAttachCount();

                        mResurrectToPosition = motionPosition;

                        if (mTouchMode == TOUCH_MODE_DOWN || mTouchMode == TOUCH_MODE_TAP) {
                            removeCallbacks(
                                mTouchMode == TOUCH_MODE_DOWN ?
                                    mPendingCheckForTap : mPendingCheckForLongPress);
                            mLayoutMode = LAYOUT_NORMAL;
                            if (!mDataChanged && mAdapter.isEnabled(motionPosition)) {
                                mTouchMode = TOUCH_MODE_TAP;
                                setSelectedPositionInt(mMotionPosition);
                                layoutChildren();
                                child.setPressed(true);
                                positionSelector(mMotionPosition, child);
                                setPressed(true);
                                if (mSelector != null) {
                                    Drawable d = mSelector.getCurrent();
                                    if (d != null && d instanceof TransitionDrawable) {
                                        ((TransitionDrawable) d).resetTransition();
                                    }
                                    DrawableCompat.setHotspot(mSelector, ev.getX(), y);
                                }
                                if (mTouchModeReset != null) {
                                    removeCallbacks(mTouchModeReset);
                                }
                                mTouchModeReset = new Runnable() {
                                    @Override
                                    public void run() {
                                        mTouchModeReset = null;
                                        mTouchMode = TOUCH_MODE_REST;
                                        child.setPressed(false);
                                        setPressed(false);
                                        if (!mDataChanged && !mIsDetaching && mAttachedToWindow) {
                                            performClick.run();
                                        }
                                    }
                                };
                                postDelayed(
                                    mTouchModeReset,
                                    ViewConfiguration.getPressedStateDuration());
                            } else {
                                mTouchMode = TOUCH_MODE_REST;
                                updateSelectorState();
                            }
                            return;
                        } else if (!mDataChanged && mAdapter.isEnabled(motionPosition)) {
                            performClick.run();
                        }
                    }
                }
                mTouchMode = TOUCH_MODE_REST;
                updateSelectorState();
                break;
            case TOUCH_MODE_SCROLL:
                final int childCount = getChildCount();
                if (childCount > 0) {
                    final int firstChildLeft = getChildAt(0).getLeft();
                    final int lastChildRight = getChildAt(childCount - 1).getRight();
                    final int contentLeft = mListPadding.left;
                    final int contentRight = getWidth() - mListPadding.right;
                    if (mFirstPosition == 0 && firstChildLeft >= contentLeft &&
                        mFirstPosition + childCount < mItemCount &&
                        lastChildRight <= getWidth() - contentRight) {
                        mTouchMode = TOUCH_MODE_REST;
                        reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                    } else {
                        final VelocityTracker velocityTracker = mVelocityTracker;
                        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                        final int initialVelocity = (int)
                            (velocityTracker.getXVelocity(mActivePointerId) * mVelocityScale);
                        // Fling if we have enough velocity and we aren't at a boundary.
                        // Since we can potentially overfling more than we can overscroll, don't
                        // allow the weird behavior where you can scroll to a boundary then
                        // fling further.
                        boolean flingVelocity = Math.abs(initialVelocity) > mMinimumVelocity;
                        if (flingVelocity &&
                            !((mFirstPosition == 0 &&
                                firstChildLeft == contentLeft - mOverscrollDistance) ||
                                (mFirstPosition + childCount == mItemCount &&
                                    lastChildRight == contentRight + mOverscrollDistance))) {
                            if (!ApiHelper.AT_LEAST_21 || !dispatchNestedPreFling(-initialVelocity, 0)) {
                                if (mFlingRunnable == null) {
                                    mFlingRunnable = new FlingRunnable();
                                }
                                reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                                mFlingRunnable.start(-initialVelocity);
                                if (ApiHelper.AT_LEAST_21) {
                                    dispatchNestedFling(-initialVelocity, 0, true);
                                }
                            } else {
                                mTouchMode = TOUCH_MODE_REST;
                                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                            }
                        } else {
                            mTouchMode = TOUCH_MODE_REST;
                            reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                            if (mFlingRunnable != null) {
                                mFlingRunnable.endFling();
                            }
                            if (mPositionScroller != null) {
                                mPositionScroller.stop();
                            }

                            if (ApiHelper.AT_LEAST_21 && flingVelocity && !dispatchNestedPreFling(-initialVelocity, 0)) {
                                dispatchNestedFling(-initialVelocity, 0, false);
                            }
                        }
                    }
                } else {
                    mTouchMode = TOUCH_MODE_REST;
                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                break;

            case TOUCH_MODE_OVERSCROLL:
                if (mFlingRunnable == null) {
                    mFlingRunnable = new FlingRunnable();
                }
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                final int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);

                reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                if (Math.abs(initialVelocity) > mMinimumVelocity) {
                    mFlingRunnable.startOverfling(-initialVelocity);
                } else {
                    mFlingRunnable.startSpringback();
                }

                break;
        }

        setPressed(false);

        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }

        // Need to redraw since we probably aren't drawing the selector anymore
        invalidate();
        removeCallbacks(mPendingCheckForLongPress);
        recycleVelocityTracker();
        mActivePointerId = INVALID_POINTER;
    }

    private void onTouchCancel() {
        switch (mTouchMode) {
            case TOUCH_MODE_OVERSCROLL:
                if (mFlingRunnable == null) {
                    mFlingRunnable = new FlingRunnable();
                }
                mFlingRunnable.startSpringback();
                break;

            case TOUCH_MODE_OVERFLING:
                // Do nothing - let it play out.
                break;

            default:
                mTouchMode = TOUCH_MODE_REST;
                setPressed(false);
                final View motionView = this.getChildAt(mMotionPosition - mFirstPosition);
                if (motionView != null) {
                    motionView.setPressed(false);
                }
                clearScrollingCache();
                removeCallbacks(mPendingCheckForLongPress);
                recycleVelocityTracker();
        }

        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }
        mActivePointerId = INVALID_POINTER;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (getScrollX() != scrollX) {
            onScrollChanged(scrollX, scrollY, getScrollX(), getScrollY());
            mViewHelper.setScrollX(scrollX);
            invalidateParentIfNeeded();

            awakenScrollBars();
        }
    }

    @TargetApi (Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    if (mTouchMode == TOUCH_MODE_REST) {
                        final float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                        if (hscroll != 0) {
                            final int delta = (int) (hscroll * getHorizontalScrollFactor());
                            if (!trackMotionScroll(delta, delta)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    /**
     * Initiate a fling with the given velocity.
     * <p/>
     * <p>Applications can use this method to manually initiate a fling as if the user
     * initiated it via touch interaction.</p>
     *
     * @param velocityX Horizontal velocity in pixels per second. Note that this is velocity of
     *                  content, not velocity of a touch that initiated the fling.
     */
    public void fling(int velocityX) {
        if (mFlingRunnable == null) {
            mFlingRunnable = new FlingRunnable();
        }
        reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
        mFlingRunnable.start(velocityX);
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return ((nestedScrollAxes & SCROLL_AXIS_HORIZONTAL) != 0);
    }

    @Override
    @TargetApi (21)
    public void onNestedScrollAccepted(View child, View target, int axes) {
        if (ApiHelper.AT_LEAST_21) {
            super.onNestedScrollAccepted(child, target, axes);
            startNestedScroll(SCROLL_AXIS_HORIZONTAL);
        }
    }

    @TargetApi (21)
    @Override
    public void onNestedScroll(
        View target, int dxConsumed, int dyConsumed,
        int dxUnconsumed, int dyUnconsumed) {
        final int motionIndex = getChildCount() / 2;
        final View motionView = getChildAt(motionIndex);
        final int oldLeft = motionView != null ? motionView.getLeft() : 0;
        if (motionView == null || trackMotionScroll(-dyUnconsumed, -dyUnconsumed)) {
            int mxUnconsumed = dxUnconsumed;
            int mxConsumed = 0;
            if (motionView != null) {
                mxConsumed = motionView.getLeft() - oldLeft;
                mxUnconsumed -= mxConsumed;
            }

            // TODO: verify this
            if (ApiHelper.AT_LEAST_21) {
                dispatchNestedScroll(mxConsumed, 0, mxUnconsumed, 0, null);
            }
        }
    }

    @TargetApi (21)
    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        final int childCount = getChildCount();
        if (!consumed && childCount > 0 && canScrollList((int) velocityX) &&
            Math.abs(velocityX) > mMinimumVelocity) {
            reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
            if (mFlingRunnable == null) {
                mFlingRunnable = new FlingRunnable();
            }
            if (!dispatchNestedPreFling(velocityX, 0)) {
                mFlingRunnable.start((int) velocityX);
            }
            return true;
        }
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    /**
     * Gets a scale factor that determines the distance the view should scroll vertically in response to
     * {@link MotionEvent#ACTION_SCROLL}.
     *
     * @return The vertical scroll scale factor.
     */
    protected float getHorizontalScrollFactor() {
        if (mHorizontalScrollFactor == 0) {

            TypedValue outValue = new TypedValue();
            boolean success = getContext().getTheme().resolveAttribute(R.attr.hlv_listPreferredItemWidth, outValue, true);

            if (success) {
                mHorizontalScrollFactor = outValue.getDimension(getContext().getResources().getDisplayMetrics());
            } else {
                throw new IllegalStateException("Expected theme to define hlv_listPreferredItemWidth.");
            }
        }
        return mHorizontalScrollFactor;
    }

    /**
     * TODO: to be implemented
     */
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowTop != null) {
            final int scrollX = getScrollX();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                final int topPadding = mListPadding.top + mGlowPaddingTop;
                final int bottomPadding = mListPadding.bottom + mGlowPaddingBottom;
                final int height = getHeight() - topPadding - bottomPadding;
                // final int width = getWidth();

                int edgeX = Math.min(0, scrollX + mFirstPositionDistanceGuess);
                // canvas.translate(edgeX, topPadding);
                // mEdgeGlowTop.setSize(height, height);

                canvas.rotate(-90);
                canvas.translate(-getHeight() + topPadding, edgeX);
                mEdgeGlowTop.setSize(height, height);

                if (mEdgeGlowTop.draw(canvas)) {
                    //mEdgeGlowTop.setPosition( edgeX, topPadding );
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                final int topPadding = mListPadding.left + mGlowPaddingTop;
                final int bottomPadding = mListPadding.right + mGlowPaddingBottom;
                final int height = getHeight() - topPadding - bottomPadding;
                final int width = getWidth();

                int edgeX = Math.max(width, scrollX + mLastPositionDistanceGuess);
                canvas.rotate(90);
                canvas.translate(-topPadding, -edgeX);

                mEdgeGlowBottom.setSize(height, height);

                if (mEdgeGlowBottom.draw(canvas)) {
                    // Account for the rotation
                    // mEdgeGlowBottom.setPosition( edgeX + width, edgeY );
                    invalidate();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    @SuppressWarnings("unused")
    public void setOverScrollEffectPadding(int topPadding, int bottomPadding) {
        mGlowPaddingTop = topPadding;
        mGlowPaddingBottom = bottomPadding;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @TargetApi (21)
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int actionMasked = ev.getActionMasked();
        View v;

        if (mPositionScroller != null) {
            mPositionScroller.stop();
        }

        if (mIsDetaching || !mAttachedToWindow) {
            // Something isn't right.
            // Since we rely on being attached to get data set change notifications,
            // don't risk doing anything where we might try to resync and find things
            // in a bogus state.
            return false;
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                int touchMode = mTouchMode;
                if (touchMode == TOUCH_MODE_OVERFLING || touchMode == TOUCH_MODE_OVERSCROLL) {
                    mMotionCorrection = 0;
                    return true;
                }

                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);

                int motionPosition = findMotionCol(x);
                if (touchMode != TOUCH_MODE_FLING && motionPosition >= 0) {
                    // User clicked on an actual view (and was not stopping a fling).
                    // Remember where the motion event started
                    v = getChildAt(motionPosition - mFirstPosition);
                    mMotionViewOriginalLeft = v.getLeft();
                    mMotionX = x;
                    mMotionY = y;
                    mMotionPosition = motionPosition;
                    mTouchMode = TOUCH_MODE_DOWN;
                    clearScrollingCache();
                }
                mLastX = Integer.MIN_VALUE;
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                mNestedXOffset = 0;

                if (ApiHelper.AT_LEAST_21) {
                    startNestedScroll(SCROLL_AXIS_HORIZONTAL);
                }
                if (touchMode == TOUCH_MODE_FLING) {
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                switch (mTouchMode) {
                    case TOUCH_MODE_DOWN:
                        int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        if (pointerIndex == -1) {
                            pointerIndex = 0;
                            mActivePointerId = ev.getPointerId(pointerIndex);
                        }
                        final int x = (int) ev.getX(pointerIndex);
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                        if (startScrollIfNeeded(x, (int) ev.getY(pointerIndex), null)) {
                            return true;
                        }
                        break;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mTouchMode = TOUCH_MODE_REST;
                mActivePointerId = INVALID_POINTER;
                recycleVelocityTracker();
                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                if (ApiHelper.AT_LEAST_21) {
                    stopNestedScroll();
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                break;
            }
        }

        return false;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
            MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mMotionX = (int) ev.getX(newPointerIndex);
            mMotionY = (int) ev.getY(newPointerIndex);
            mMotionCorrection = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTouchables(ArrayList<View> views) {
        final int count = getChildCount();
        final int firstPosition = mFirstPosition;
        final ListAdapter adapter = mAdapter;

        if (adapter == null) {
            return;
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (adapter.isEnabled(firstPosition + i)) {
                views.add(child);
            }
            child.addTouchables(views);
        }
    }

    /**
     * Fires an "on scroll state changed" event to the registered {@link it.sephiroth.android.library.widget.AbsHListView
     * .OnScrollListener}, if any.
     * The state change is fired only if the specified state is different from the previously known state.
     *
     * @param newState The new scroll state.
     */
    void reportScrollStateChange(int newState) {
        if (newState != mLastScrollState) {
            if (mOnScrollListener != null) {
                mLastScrollState = newState;
                mOnScrollListener.onScrollStateChanged(this, newState);
            }
        }
    }

    /**
     * Responsible for fling behavior. Use {@link #start(int)} to initiate a fling. Each frame of the fling is handled in
     * {@link #run()}. A FlingRunnable will keep re-posting itself until the fling is done.
     */
    private class FlingRunnable implements Runnable {
        /**
         * Tracks the decay of a fling scroll
         */
        private final it.sephiroth.android.library.widget.OverScroller mScroller;
        /**
         * Y value reported by mScroller on the previous fling
         */
        private int mLastFlingX;
        private final Runnable mCheckFlywheel = new Runnable() {
            @Override
            public void run() {
                final int activeId = mActivePointerId;
                final VelocityTracker vt = mVelocityTracker;
                final OverScroller scroller = mScroller;
                if (vt == null || activeId == INVALID_POINTER) {
                    return;
                }

                vt.computeCurrentVelocity(1000, mMaximumVelocity);
                final float xvel = -vt.getXVelocity(activeId);

                if (Math.abs(xvel) >= mMinimumVelocity && scroller.isScrollingInDirection(xvel, 0)) {
                    // Keep the fling alive a little longer
                    postDelayed(this, FLYWHEEL_TIMEOUT);
                } else {
                    endFling();
                    mTouchMode = TOUCH_MODE_SCROLL;
                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                }
            }
        };
        private static final int FLYWHEEL_TIMEOUT = 40; // milliseconds

        FlingRunnable() {
            mScroller = new OverScroller(getContext());
        }

        void start(int initialVelocity) {
            int initialX = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingX = initialX;
            mScroller.setInterpolator(null);
            mScroller.fling(initialX, 0, initialVelocity, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            mTouchMode = TOUCH_MODE_FLING;
            mViewHelper.postOnAnimation(this);
        }

        void startSpringback() {
            if (mScroller.springBack(getScrollX(), 0, 0, 0, 0, 0)) {
                mTouchMode = TOUCH_MODE_OVERFLING;
                invalidate();
                mViewHelper.postOnAnimation(this);
            } else {
                mTouchMode = TOUCH_MODE_REST;
                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
            }
        }

        void startOverfling(int initialVelocity) {
            mScroller.setInterpolator(null);
            mScroller.fling(getScrollX(), 0, initialVelocity, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, getWidth(), 0);
            mTouchMode = TOUCH_MODE_OVERFLING;
            invalidate();
            mViewHelper.postOnAnimation(this);
        }

        void edgeReached(int delta) {
            mScroller.notifyHorizontalEdgeReached(getScrollX(), 0, mOverflingDistance);
            final int overscrollMode = getOverScrollMode();
            if (overscrollMode == OVER_SCROLL_ALWAYS || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && !contentFits())) {
                mTouchMode = TOUCH_MODE_OVERFLING;
                final int vel = (int) mScroller.getCurrVelocity();
                if (delta > 0) {
                    mEdgeGlowTop.onAbsorb(vel);
                } else {
                    mEdgeGlowBottom.onAbsorb(vel);
                }
            } else {
                mTouchMode = TOUCH_MODE_REST;
                if (mPositionScroller != null) {
                    mPositionScroller.stop();
                }
            }
            invalidate();
            mViewHelper.postOnAnimation(this);
        }

        void startScroll(int distance, int duration, boolean linear) {
            int initialX = distance < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingX = initialX;
            mScroller.setInterpolator(linear ? sLinearInterpolator : null);
            mScroller.startScroll(initialX, 0, distance, 0, duration);
            mTouchMode = TOUCH_MODE_FLING;
            mViewHelper.postOnAnimation(this);
        }

        void endFling() {
            mTouchMode = TOUCH_MODE_REST;

            removeCallbacks(this);
            removeCallbacks(mCheckFlywheel);

            reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
            clearScrollingCache();
            mScroller.abortAnimation();
        }

        void flywheelTouch() {
            postDelayed(mCheckFlywheel, FLYWHEEL_TIMEOUT);
        }

        @Override
        public void run() {
            switch (mTouchMode) {
                default:
                    endFling();
                    return;

                case TOUCH_MODE_SCROLL:
                    if (mScroller.isFinished()) {
                        return;
                    }
                    // Fall through
                case TOUCH_MODE_FLING: {
                    if (mDataChanged) {
                        layoutChildren();
                    }

                    if (mItemCount == 0 || getChildCount() == 0) {
                        endFling();
                        return;
                    }

                    final OverScroller scroller = mScroller;
                    boolean more = scroller.computeScrollOffset();
                    final int x = scroller.getCurrX();

                    // Flip sign to convert finger direction to list items direction
                    // (e.g. finger moving down means list is moving towards the top)
                    int delta = mLastFlingX - x;

                    // Pretend that each frame of a fling scroll is a touch scroll
                    if (delta > 0) {
                        // List is moving towards the top. Use first view as mMotionPosition
                        mMotionPosition = mFirstPosition;
                        final View firstView = getChildAt(0);
                        mMotionViewOriginalLeft = firstView.getLeft();

                        // Don't fling more than 1 screen
                        delta = Math.min(getWidth() - getPaddingRight() - getPaddingLeft() - 1, delta);
                    } else {
                        // List is moving towards the bottom. Use last view as mMotionPosition
                        int offsetToLast = getChildCount() - 1;
                        mMotionPosition = mFirstPosition + offsetToLast;

                        final View lastView = getChildAt(offsetToLast);
                        mMotionViewOriginalLeft = lastView.getLeft();

                        // Don't fling more than 1 screen
                        delta = Math.max(-(getWidth() - getPaddingRight() - getPaddingLeft() - 1), delta);
                    }

                    // Check to see if we have bumped into the scroll limit
                    View motionView = getChildAt(mMotionPosition - mFirstPosition);
                    int oldLeft = 0;
                    if (motionView != null) {
                        oldLeft = motionView.getLeft();
                    }

                    // Don't stop just because delta is zero (it could have been rounded)
                    final boolean atEdge = trackMotionScroll(delta, delta);
                    final boolean atEnd = atEdge && (delta != 0);
                    if (atEnd) {
                        if (motionView != null) {
                            // Tweak the scroll for how far we overshot
                            int overshoot = -(delta - (motionView.getLeft() - oldLeft));
                            overScrollBy(overshoot, 0, getScrollX(), 0, 0, 0, mOverflingDistance, 0, false);
                        }
                        if (more) {
                            edgeReached(delta);
                        }
                        break;
                    }

                    if (more && !atEnd) {
                        if (atEdge) {
                            invalidate();
                        }
                        mLastFlingX = x;
                        mViewHelper.postOnAnimation(this);
                    } else {
                        endFling();

                    }
                    break;
                }

                case TOUCH_MODE_OVERFLING: {
                    final OverScroller scroller = mScroller;
                    if (scroller.computeScrollOffset()) {
                        final int scrollX = getScrollX();
                        final int currX = scroller.getCurrX();
                        final int deltaX = currX - scrollX;
                        if (overScrollBy(deltaX, 0, scrollX, 0, 0, 0, mOverflingDistance, 0, false)) {
                            final boolean crossRight = scrollX <= 0 && currX > 0;
                            final boolean crossLeft = scrollX >= 0 && currX < 0;
                            if (crossRight || crossLeft) {
                                int velocity = (int) scroller.getCurrVelocity();
                                if (crossLeft) {
                                    velocity = -velocity;
                                }

                                // Don't flywheel from this; we're just continuing things.
                                scroller.abortAnimation();
                                start(velocity);
                            } else {
                                startSpringback();
                            }
                        } else {
                            invalidate();
                            mViewHelper.postOnAnimation(this);
                        }
                    } else {
                        endFling();
                    }
                    break;
                }
            }
        }
    }

    /**
     * The amount of friction applied to flings. The default value is {@link ViewConfiguration#getScrollFriction}.
     *
     * @return A scalar dimensionless value representing the coefficient of friction.
     */
    @SuppressWarnings("unused")
    public void setFriction(float friction) {
        if (mFlingRunnable == null) {
            mFlingRunnable = new FlingRunnable();
        }
        mFlingRunnable.mScroller.setFriction(friction);
    }

    /**
     * Sets a scale factor for the fling velocity. The initial scale factor is 1.0.
     *
     * @param scale The scale factor to multiply the velocity by.
     */
    @SuppressWarnings("unused")
    public void setVelocityScale(float scale) {
        mVelocityScale = scale;
    }

    /**
     * Override this for better control over position scrolling.
     */
    AbsPositionScroller createPositionScroller() {
        return new PositionScroller();
    }

    /**
     * Smoothly scroll to the specified adapter position. The view will scroll such that the indicated position is displayed.
     *
     * @param position Scroll to this adapter position.
     */
    public void smoothScrollToPosition(int position) {
        if (mPositionScroller == null) {
            mPositionScroller = createPositionScroller();
        }
        mPositionScroller.start(position);
    }

    /**
     * Smoothly scroll to the specified adapter position. The view will scroll such that the indicated position is displayed
     * <code>offset</code> pixels from the top edge of the view. If this is impossible, (e.g. the offset would scroll the first or
     * last item beyond the boundaries of the list) it will get as close as possible. The scroll will take <code>duration</code>
     * milliseconds to complete.
     *
     * @param position Position to scroll to
     * @param offset   Desired distance in pixels of <code>position</code> from the top of the view when scrolling is finished
     * @param duration Number of milliseconds to use for the scroll
     */
    @SuppressWarnings("unused")
    public void smoothScrollToPositionFromLeft(int position, int offset, int duration) {
        if (mPositionScroller == null) {
            mPositionScroller = createPositionScroller();
        }
        mPositionScroller.startWithOffset(position, offset, duration);
    }

    /**
     * Smoothly scroll to the specified adapter position. The view will scroll such that the indicated position is displayed
     * <code>offset</code> pixels from the top edge of the view. If this is impossible, (e.g. the offset would scroll the first or
     * last item beyond the boundaries of the list) it will get as close as possible.
     *
     * @param position Position to scroll to
     * @param offset   Desired distance in pixels of <code>position</code> from the top of the view when scrolling is finished
     */
    @SuppressWarnings("unused")
    public void smoothScrollToPositionFromLeft(int position, int offset) {
        if (mPositionScroller == null) {
            mPositionScroller = createPositionScroller();
        }
        mPositionScroller.startWithOffset(position, offset, offset);
    }

    /**
     * Smoothly scroll to the specified adapter position. The view will scroll such that the indicated position is displayed, but it
     * will stop early if scrolling further would scroll boundPosition out of view.
     *
     * @param position      Scroll to this adapter position.
     * @param boundPosition Do not scroll if it would move this adapter position out of view.
     */
    public void smoothScrollToPosition(int position, int boundPosition) {
        if (mPositionScroller == null) {
            mPositionScroller = createPositionScroller();
        }
        mPositionScroller.start(position, boundPosition);
    }

    /**
     * Smoothly scroll by distance pixels over duration milliseconds.
     *
     * @param distance Distance to scroll in pixels.
     * @param duration Duration of the scroll animation in milliseconds.
     */
    public void smoothScrollBy(int distance, int duration) {
        smoothScrollBy(distance, duration, false);
    }

    public void smoothScrollBy(int distance, int duration, boolean linear) {
        if (mFlingRunnable == null) {
            mFlingRunnable = new FlingRunnable();
        }

        // No sense starting to scroll if we're not going anywhere
        final int firstPos = mFirstPosition;
        final int childCount = getChildCount();
        final int lastPos = firstPos + childCount;
        final int leftLimit = getPaddingLeft();
        final int rightLimit = getWidth() - getPaddingRight();

        if (distance == 0 || mItemCount == 0 || childCount == 0 ||
            (firstPos == 0 && getChildAt(0).getLeft() == leftLimit && distance < 0) ||
            (lastPos == mItemCount &&
                getChildAt(childCount - 1).getRight() == rightLimit && distance > 0)) {
            mFlingRunnable.endFling();
            if (mPositionScroller != null) {
                mPositionScroller.stop();
            }
        } else {
            reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
            mFlingRunnable.startScroll(distance, duration, linear);
        }
    }

    /**
     * Allows RemoteViews to scroll relatively to a position.
     */
    protected void smoothScrollByOffset(int position) {
        int index = -1;
        if (position < 0) {
            index = getFirstVisiblePosition();
        } else if (position > 0) {
            index = getLastVisiblePosition();
        }

        if (index > -1) {
            View child = getChildAt(index - getFirstVisiblePosition());
            if (child != null) {
                Rect visibleRect = new Rect();
                if (child.getGlobalVisibleRect(visibleRect)) {
                    // the child is partially visible
                    int childRectArea = child.getWidth() * child.getHeight();
                    int visibleRectArea = visibleRect.width() * visibleRect.height();
                    float visibleArea = (visibleRectArea / (float) childRectArea);
                    final float visibleThreshold = 0.75f;
                    if ((position < 0) && (visibleArea < visibleThreshold)) {
                        // the top index is not perceivably visible so offset
                        // to account for showing that top index as well
                        ++index;
                    } else if ((position > 0) && (visibleArea < visibleThreshold)) {
                        // the bottom index is not perceivably visible so offset
                        // to account for showing that bottom index as well
                        --index;
                    }
                }
                smoothScrollToPosition(Math.max(0, Math.min(getCount(), index + position)));
            }
        }
    }

    private void createScrollingCache() {
        if (mScrollingCacheEnabled && !mCachingStarted && !mViewHelper.isHardwareAccelerated()) {
            setChildrenDrawnWithCacheEnabled(true);
            setChildrenDrawingCacheEnabled(true);
            mCachingStarted = mCachingActive = true;
        }
    }

    private void clearScrollingCache() {
        if (!mViewHelper.isHardwareAccelerated()) {
            if (mClearScrollingCache == null) {
                mClearScrollingCache = new Runnable() {
                    @Override
                    public void run() {
                        if (mCachingStarted) {
                            mCachingStarted = mCachingActive = false;
                            setChildrenDrawnWithCacheEnabled(false);
                            if ((getPersistentDrawingCache() & PERSISTENT_SCROLLING_CACHE) == 0) {
                                setChildrenDrawingCacheEnabled(false);
                            }
                            if (!isAlwaysDrawnWithCacheEnabled()) {
                                invalidate();
                            }
                        }
                    }
                };
            }
            post(mClearScrollingCache);
        }
    }

    /**
     * Scrolls the list items within the view by a specified number of pixels.
     *
     * @param x the amount of pixels to scroll by vertically
     * @see #canScrollList(int)
     */
    @SuppressWarnings("unused")
    public void scrollListBy(int x) {
        trackMotionScroll(-x, -x);
    }

    /**
     * Check if the items in the list can be scrolled in a certain direction.
     *
     * @param direction Negative to check scrolling up, positive to check
     *                  scrolling down.
     * @return true if the list can be scrolled in the specified direction,
     * false otherwise.
     * @see #scrollListBy(int)
     */
    public boolean canScrollList(int direction) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return false;
        }

        final int firstPosition = mFirstPosition;
        final Rect listPadding = mListPadding;
        if (direction > 0) {
            final int lastRight = getChildAt(childCount - 1).getRight();
            final int lastPosition = firstPosition + childCount;
            return lastPosition < mItemCount || lastRight > getWidth() - listPadding.right;
        } else {
            final int firstLeft = getChildAt(0).getLeft();
            return firstPosition > 0 || firstLeft < listPadding.left;
        }
    }

    /**
     * Track a motion scroll
     *
     * @param deltaX            Amount to offset mMotionView. This is the accumulated delta since the motion began. Positive
     *                          numbers mean the user's
     *                          finger is moving down the screen.
     * @param incrementalDeltaX Change in deltaY from the previous event.
     * @return true if we're already at the beginning/end of the list and have nothing to do.
     */
    boolean trackMotionScroll(int deltaX, int incrementalDeltaX) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return true;
        }

        final int firstLeft = getChildAt(0).getLeft();
        final int lastRight = getChildAt(childCount - 1).getRight();

        final Rect listPadding = mListPadding;

        // "effective padding" In this case is the amount of padding that affects
        // how much space should not be filled by items. If we don't clip to padding
        // there is no effective padding.
        int effectivePaddingLeft = 0;
        int effectivePaddingRight = 0;

        // FIXME account for grid vertical spacing too?
        final int spaceBefore = effectivePaddingLeft - firstLeft;
        final int end = getWidth() - effectivePaddingRight;
        final int spaceAfter = lastRight - end;

        final int width = getWidth() - getPaddingRight() - getPaddingLeft();
        if (deltaX < 0) {
            deltaX = Math.max(-(width - 1), deltaX);
        } else {
            deltaX = Math.min(width - 1, deltaX);
        }

        if (incrementalDeltaX < 0) {
            incrementalDeltaX = Math.max(-(width - 1), incrementalDeltaX);
        } else {
            incrementalDeltaX = Math.min(width - 1, incrementalDeltaX);
        }

        final int firstPosition = mFirstPosition;

        // Update our guesses for where the first and last views are
        if (firstPosition == 0) {
            mFirstPositionDistanceGuess = firstLeft - listPadding.left;
        } else {
            mFirstPositionDistanceGuess += incrementalDeltaX;
        }
        if (firstPosition + childCount == mItemCount) {
            mLastPositionDistanceGuess = lastRight + listPadding.right;
        } else {
            mLastPositionDistanceGuess += incrementalDeltaX;
        }

        final boolean cannotScrollRight = (firstPosition == 0 && firstLeft >= listPadding.left && incrementalDeltaX >= 0);
        final boolean cannotScrollLeft =
            (firstPosition + childCount == mItemCount && lastRight <= getWidth() - listPadding.right && incrementalDeltaX <= 0);

        if (cannotScrollRight || cannotScrollLeft) {
            return incrementalDeltaX != 0;
        }

        final boolean down = incrementalDeltaX < 0;

        final boolean inTouchMode = isInTouchMode();
        if (inTouchMode) {
            hideSelector();
        }

        final int headerViewsCount = getHeaderViewsCount();
        final int footerViewsStart = mItemCount - getFooterViewsCount();

        int start = 0;
        int count = 0;

        if (down) {
            int top = -incrementalDeltaX;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                if (child.getRight() >= top) {
                    break;
                } else {
                    count++;
                    int position = firstPosition + i;
                    if (position >= headerViewsCount && position < footerViewsStart) {
                        mRecycler.addScrapView(child, position);
                    }
                }
            }
        } else {
            int bottom = getWidth() - incrementalDeltaX;

            for (int i = childCount - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                if (child.getLeft() <= bottom) {
                    break;
                } else {
                    start = i;
                    count++;
                    int position = firstPosition + i;
                    if (position >= headerViewsCount && position < footerViewsStart) {
                        mRecycler.addScrapView(child, position);
                    }
                }
            }
        }

        mMotionViewNewLeft = mMotionViewOriginalLeft + deltaX;

        mBlockLayoutRequests = true;

        if (count > 0) {
            detachViewsFromParent(start, count);
            mRecycler.removeSkippedScrap();
        }

        // invalidate before moving the children to avoid unnecessary invalidate
        // calls to bubble up from the children all the way to the top
        if (!awakenScrollBars()) {
            invalidate();
        }

        offsetChildrenLeftAndRight(incrementalDeltaX);

        if (down) {
            mFirstPosition += count;
        }

        final int absIncrementalDeltaX = Math.abs(incrementalDeltaX);
        if (spaceBefore < absIncrementalDeltaX || spaceAfter < absIncrementalDeltaX) {
            fillGap(down);
        }

        if (!inTouchMode && mSelectedPosition != INVALID_POSITION) {
            final int childIndex = mSelectedPosition - mFirstPosition;
            if (childIndex >= 0 && childIndex < getChildCount()) {
                positionSelector(mSelectedPosition, getChildAt(childIndex));
            }
        } else if (mSelectorPosition != INVALID_POSITION) {
            final int childIndex = mSelectorPosition - mFirstPosition;
            if (childIndex >= 0 && childIndex < getChildCount()) {
                positionSelector(INVALID_POSITION, getChildAt(childIndex));
            }
        } else {
            mSelectorRect.setEmpty();
        }

        mBlockLayoutRequests = false;

        invokeOnItemScrollListener();

        return false;
    }

    /**
     * Why the hell Google guys set public methods hidden??? fuck you
     */
    public void offsetChildrenLeftAndRight(int offset) {
        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            final View v = getChildAt(i);
            v.offsetLeftAndRight(offset);
        }
    }

    /**
     * Returns the number of header views in the list. Header views are special views
     * at the top of the list that should not be recycled during a layout.
     *
     * @return The number of header views, 0 in the default implementation.
     */
    int getHeaderViewsCount() {
        return 0;
    }

    /**
     * Returns the number of footer views in the list. Footer views are special views
     * at the bottom of the list that should not be recycled during a layout.
     *
     * @return The number of footer views, 0 in the default implementation.
     */
    int getFooterViewsCount() {
        return 0;
    }

    /**
     * Fills the gap left open by a touch-scroll. During a touch scroll, children that remain on screen are shifted and the other
     * ones are discarded. The role of this method is to fill the gap thus created by performing a partial layout in the empty
     * space.
     *
     * @param after true if the scroll is going after, false if it is going up
     */
    protected abstract void fillGap(boolean after);

    protected void hideSelector() {
        if (mSelectedPosition != INVALID_POSITION) {
            if (mLayoutMode != LAYOUT_SPECIFIC) {
                mResurrectToPosition = mSelectedPosition;
            }
            if (mNextSelectedPosition >= 0 && mNextSelectedPosition != mSelectedPosition) {
                mResurrectToPosition = mNextSelectedPosition;
            }
            setSelectedPositionInt(INVALID_POSITION);
            setNextSelectedPositionInt(INVALID_POSITION);
            mSelectedLeft = 0;
        }
    }

    /**
     * @return A position to select. First we try mSelectedPosition. If that has been clobbered by entering touch mode, we then try
     * mResurrectToPosition. Values are pinned to the range of items available in the adapter
     */
    protected int reconcileSelectedPosition() {
        int position = mSelectedPosition;
        if (position < 0) {
            position = mResurrectToPosition;
        }
        position = Math.max(0, position);
        position = Math.min(position, mItemCount - 1);
        return position;
    }

    /**
     * Find the col closest to x. This col will be used as the motion col when scrolling
     *
     * @param x Where the user touched
     * @return The position of the first (or only) item in the col containing x
     */
    protected abstract int findMotionCol(int x);

    /**
     * Find the col closest to x. This col will be used as the motion col when scrolling.
     *
     * @param x Where the user touched
     * @return The position of the first (or only) item in the col closest to x
     */
    protected int findClosestMotionCol(int x) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return INVALID_POSITION;
        }

        final int motionCol = findMotionCol(x);
        return motionCol != INVALID_POSITION ? motionCol : mFirstPosition + childCount - 1;
    }

    /**
     * Causes all the views to be rebuilt and redrawn.
     */
    @SuppressWarnings("unused")
    public void invalidateViews() {
        mDataChanged = true;
        rememberSyncState();
        requestLayout();
        invalidate();
    }

    /**
     * If there is a selection returns false. Otherwise resurrects the selection and returns true if resurrected.
     */
    protected boolean resurrectSelectionIfNeeded() {
        if (mSelectedPosition < 0 && resurrectSelection()) {
            updateSelectorState();
            return true;
        }
        return false;
    }

    /**
     * Makes the item at the supplied position selected.
     *
     * @param position the position of the new selection
     */
    public abstract void setSelectionInt(int position);

    /**
     * Attempt to bring the selection back if the user is switching from touch to trackball mode
     *
     * @return Whether selection was set to something.
     */
    boolean resurrectSelection() {
        final int childCount = getChildCount();

        if (childCount <= 0) {
            return false;
        }

        int selectedLeft = 0;
        int selectedPos;
        int childrenLeft = mListPadding.left;
        int childrenRight = getRight() - getLeft() - mListPadding.right;
        final int firstPosition = mFirstPosition;
        final int toPosition = mResurrectToPosition;
        boolean down = true;

        if (toPosition >= firstPosition && toPosition < firstPosition + childCount) {
            selectedPos = toPosition;

            final View selected = getChildAt(selectedPos - mFirstPosition);
            selectedLeft = selected.getLeft();
            int selectedRight = selected.getRight();

            // We are scrolled, don't get in the fade
            if (selectedLeft < childrenLeft) {
                selectedLeft = childrenLeft + getHorizontalFadingEdgeLength();
            } else if (selectedRight > childrenRight) {
                selectedLeft = childrenRight - selected.getMeasuredWidth() - getHorizontalFadingEdgeLength();
            }
        } else {
            if (toPosition < firstPosition) {
                // Default to selecting whatever is first
                selectedPos = firstPosition;
                for (int i = 0; i < childCount; i++) {
                    final View v = getChildAt(i);
                    final int left = v.getLeft();

                    if (i == 0) {
                        // Remember the position of the first item
                        selectedLeft = left;
                        // See if we are scrolled at all
                        if (firstPosition > 0 || left < childrenLeft) {
                            // If we are scrolled, don't select anything that is
                            // in the fade region
                            childrenLeft += getHorizontalFadingEdgeLength();
                        }
                    }
                    if (left >= childrenLeft) {
                        // Found a view whose top is fully visisble
                        selectedPos = firstPosition + i;
                        selectedLeft = left;
                        break;
                    }
                }
            } else {
                final int itemCount = mItemCount;
                down = false;
                selectedPos = firstPosition + childCount - 1;

                for (int i = childCount - 1; i >= 0; i--) {
                    final View v = getChildAt(i);
                    final int left = v.getLeft();
                    final int right = v.getRight();

                    if (i == childCount - 1) {
                        selectedLeft = left;
                        if (firstPosition + childCount < itemCount || right > childrenRight) {
                            childrenRight -= getHorizontalFadingEdgeLength();
                        }
                    }

                    if (right <= childrenRight) {
                        selectedPos = firstPosition + i;
                        selectedLeft = left;
                        break;
                    }
                }
            }
        }

        mResurrectToPosition = INVALID_POSITION;
        removeCallbacks(mFlingRunnable);
        if (mPositionScroller != null) {
            mPositionScroller.stop();
        }
        mTouchMode = TOUCH_MODE_REST;
        clearScrollingCache();
        mSpecificLeft = selectedLeft;
        selectedPos = lookForSelectablePosition(selectedPos, down);
        if (selectedPos >= firstPosition && selectedPos <= getLastVisiblePosition()) {
            mLayoutMode = LAYOUT_SPECIFIC;
            updateSelectorState();
            setSelectionInt(selectedPos);
            invokeOnItemScrollListener();
        } else {
            selectedPos = INVALID_POSITION;
        }
        reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);

        return selectedPos >= 0;
    }

    void confirmCheckedPositionsById() {
        if (LOG_ENABLED) {
            Log.i(TAG, "confirmCheckedPositionsById");
        }
        // Clear out the positional check states, we'll rebuild it below from IDs.
        mCheckStates.clear();

        boolean checkedCountChanged = false;
        for (int checkedIndex = 0; checkedIndex < mCheckedIdStates.size(); checkedIndex++) {
            final long id = mCheckedIdStates.keyAt(checkedIndex);
            final int lastPos = mCheckedIdStates.valueAt(checkedIndex);

            final long lastPosId = mAdapter.getItemId(lastPos);
            if (id != lastPosId) {
                // Look around to see if the ID is nearby. If not, uncheck it.
                final int start = Math.max(0, lastPos - CHECK_POSITION_SEARCH_DISTANCE);
                final int end = Math.min(lastPos + CHECK_POSITION_SEARCH_DISTANCE, mItemCount);
                boolean found = false;
                for (int searchPos = start; searchPos < end; searchPos++) {
                    final long searchId = mAdapter.getItemId(searchPos);
                    if (id == searchId) {
                        found = true;
                        mCheckStates.put(searchPos, true);
                        mCheckedIdStates.setValueAt(checkedIndex, searchPos);
                        break;
                    }
                }

                if (!found) {
                    mCheckedIdStates.delete(id);
                    checkedIndex--;
                    mCheckedItemCount--;
                    checkedCountChanged = true;

                    if (android.os.Build.VERSION.SDK_INT > 11) {
                        if (mChoiceActionMode != null && mMultiChoiceModeCallback != null) {
                            ((MultiChoiceModeWrapper) mMultiChoiceModeCallback)
                                .onItemCheckedStateChanged((ActionMode) mChoiceActionMode, lastPos, id, false);
                        }
                    }
                }
            } else {
                mCheckStates.put(lastPos, true);
            }
        }

        if (checkedCountChanged && mChoiceActionMode != null && android.os.Build.VERSION.SDK_INT > 11) {
            ((ActionMode) mChoiceActionMode).invalidate();
        }
    }

    @Override
    protected void handleDataChanged() {
        if (LOG_ENABLED) {
            Log.i(TAG, "handleDataChanged");
        }
        int count = mItemCount;
        int lastHandledItemCount = mLastHandledItemCount;
        mLastHandledItemCount = mItemCount;

        if (mChoiceMode != CHOICE_MODE_NONE && mAdapter != null && mAdapter.hasStableIds()) {
            confirmCheckedPositionsById();
        }

        // TODO: In the future we can recycle these views based on stable ID instead.
        mRecycler.clearTransientStateViews();

        if (count > 0) {
            int newPos;
            int selectablePos;

            // Find the col we are supposed to sync to
            if (mNeedSync) {
                // Update this first, since setNextSelectedPositionInt inspects it
                mNeedSync = false;
                mPendingSync = null;

                if (mTranscriptMode == TRANSCRIPT_MODE_ALWAYS_SCROLL) {
                    mLayoutMode = LAYOUT_FORCE_RIGHT;
                    return;
                } else if (mTranscriptMode == TRANSCRIPT_MODE_NORMAL) {
                    if (mForceTranscriptScroll) {
                        mForceTranscriptScroll = false;
                        mLayoutMode = LAYOUT_FORCE_RIGHT;
                        return;
                    }
                    final int childCount = getChildCount();
                    final int listRight = getWidth() - getPaddingRight();
                    final View lastChild = getChildAt(childCount - 1);
                    final int lastRight = lastChild != null ? lastChild.getBottom() : listRight;
                    if (mFirstPosition + childCount >= lastHandledItemCount &&
                        lastRight <= listRight) {
                        mLayoutMode = LAYOUT_FORCE_RIGHT;
                        return;
                    }
                    // Something new came in and we didn't scroll; give the user a clue that
                    // there's something new.
                    awakenScrollBars();
                }

                switch (mSyncMode) {
                    case SYNC_SELECTED_POSITION:
                        if (isInTouchMode()) {
                            // We saved our state when not in touch mode. (We know this because
                            // mSyncMode is SYNC_SELECTED_POSITION.) Now we are trying to
                            // restore in touch mode. Just leave mSyncPosition as it is (possibly
                            // adjusting if the available range changed) and return.
                            mLayoutMode = LAYOUT_SYNC;
                            mSyncPosition = Math.min(Math.max(0, mSyncPosition), count - 1);

                            return;
                        } else {
                            // See if we can find a position in the new data with the same
                            // id as the old selection. This will change mSyncPosition.
                            newPos = findSyncPosition();
                            if (newPos >= 0) {
                                // Found it. Now verify that new selection is still selectable
                                selectablePos = lookForSelectablePosition(newPos, true);
                                if (selectablePos == newPos) {
                                    // Same col id is selected
                                    mSyncPosition = newPos;

                                    if (mSyncWidth == getWidth()) {
                                        // If we are at the same height as when we saved state, try
                                        // to restore the scroll position too.
                                        mLayoutMode = LAYOUT_SYNC;
                                    } else {
                                        // We are not the same height as when the selection was saved, so
                                        // don't try to restore the exact position
                                        mLayoutMode = LAYOUT_SET_SELECTION;
                                    }

                                    // Restore selection
                                    setNextSelectedPositionInt(newPos);
                                    return;
                                }
                            }
                        }
                        break;
                    case SYNC_FIRST_POSITION:
                        // Leave mSyncPosition as it is -- just pin to available range
                        mLayoutMode = LAYOUT_SYNC;
                        mSyncPosition = Math.min(Math.max(0, mSyncPosition), count - 1);

                        return;
                }
            }

            if (!isInTouchMode()) {
                // We couldn't find matching data -- try to use the same position
                newPos = getSelectedItemPosition();

                // Pin position to the available range
                if (newPos >= count) {
                    newPos = count - 1;
                }
                if (newPos < 0) {
                    newPos = 0;
                }

                // Make sure we select something selectable -- first look down
                selectablePos = lookForSelectablePosition(newPos, true);

                if (selectablePos >= 0) {
                    setNextSelectedPositionInt(selectablePos);
                    return;
                } else {
                    // Looking down didn't work -- try looking up
                    selectablePos = lookForSelectablePosition(newPos, false);
                    if (selectablePos >= 0) {
                        setNextSelectedPositionInt(selectablePos);
                        return;
                    }
                }
            } else {

                // We already know where we want to resurrect the selection
                if (mResurrectToPosition >= 0) {
                    return;
                }
            }

        }

        // Nothing is selected. Give up and reset everything.
        mLayoutMode = mStackFromRight ? LAYOUT_FORCE_RIGHT : LAYOUT_FORCE_LEFT;
        mSelectedPosition = INVALID_POSITION;
        mSelectedColId = INVALID_COL_ID;
        mNextSelectedPosition = INVALID_POSITION;
        mNextSelectedColId = INVALID_COL_ID;
        mNeedSync = false;
        mPendingSync = null;
        mSelectorPosition = INVALID_POSITION;
        checkSelectionChanged();
    }

    //    @Override
    //    protected void onDisplayHint(int hint) {
    //        super.onDisplayHint(hint);
    //        switch (hint) {
    //            case INVISIBLE:
    //                if (mPopup != null && mPopup.isShowing()) {
    //                    dismissPopup();
    //                }
    //                break;
    //            case VISIBLE:
    //                if (mFiltered && mPopup != null && !mPopup.isShowing()) {
    //                    showPopup();
    //                }
    //                break;
    //        }
    //        mPopupHidden = hint == INVISIBLE;
    //    }

    //    /**
    //     * Removes the filter window
    //     */
    //    private void dismissPopup() {
    //        if (mPopup != null) {
    //            mPopup.dismiss();
    //        }
    //    }

    //    /**
    //     * Shows the filter window
    //     */
    //    private void showPopup() {
    //        // Make sure we have a window before showing the popup
    //        if (getWindowVisibility() == View.VISIBLE) {
    //            createTextFilter(true);
    //            positionPopup();
    //            // Make sure we get focus if we are showing the popup
    //            checkFocus();
    //        }
    //    }

    //    private void positionPopup() {
    //        int screenHeight = getResources().getDisplayMetrics().heightPixels;
    //        final int[] xy = new int[2];
    //        getLocationOnScreen(xy);
    //        // TODO: The 20 below should come from the theme
    //        // TODO: And the gravity should be defined in the theme as well
    //        final int bottomGap = screenHeight - xy[1] - getHeight() + (int) (mDensityScale * 20);
    //        if (!mPopup.isShowing()) {
    //            mPopup.showAtLocation(this, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
    //                xy[0], bottomGap);
    //        } else {
    //            mPopup.update(xy[0], bottomGap, -1, -1);
    //        }
    //    }

    /**
     * What is the distance between the source and destination rectangles given the direction of focus navigation between them? The
     * direction basically helps figure out more quickly what is self evident by the relationship between the rects...
     *
     * @param source    the source rectangle
     * @param dest      the destination rectangle
     * @param direction the direction
     * @return the distance between the rectangles
     */
    public static int getDistance(Rect source, Rect dest, int direction) {
        int sX, sY; // source x, y
        int dX, dY; // dest x, y
        switch (direction) {
            case View.FOCUS_RIGHT:
                sX = source.right;
                sY = source.top + source.height() / 2;
                dX = dest.left;
                dY = dest.top + dest.height() / 2;
                break;
            case View.FOCUS_DOWN:
                sX = source.left + source.width() / 2;
                sY = source.bottom;
                dX = dest.left + dest.width() / 2;
                dY = dest.top;
                break;
            case View.FOCUS_LEFT:
                sX = source.left;
                sY = source.top + source.height() / 2;
                dX = dest.right;
                dY = dest.top + dest.height() / 2;
                break;
            case View.FOCUS_UP:
                sX = source.left + source.width() / 2;
                sY = source.top;
                dX = dest.left + dest.width() / 2;
                dY = dest.bottom;
                break;
            case View.FOCUS_FORWARD:
            case View.FOCUS_BACKWARD:
                sX = source.right + source.width() / 2;
                sY = source.top + source.height() / 2;
                dX = dest.left + dest.width() / 2;
                dY = dest.top + dest.height() / 2;
                break;
            default:
                throw new IllegalArgumentException(
                    "direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT, "
                        + "FOCUS_FORWARD, FOCUS_BACKWARD}.");
        }
        int deltaX = dX - sX;
        int deltaY = dY - sY;
        return deltaY * deltaY + deltaX * deltaX;
    }

    //    @Override
    //    protected boolean isInFilterMode() {
    //        return mFiltered;
    //    }

    //    /**
    //     * Sends a key to the text filter window
    //     *
    //     * @param keyCode The keycode for the event
    //     * @param event The actual key event
    //     *
    //     * @return True if the text filter handled the event, false otherwise.
    //     */
    //    boolean sendToTextFilter(int keyCode, int count, KeyEvent event) {
    //        if (!acceptFilter()) {
    //            return false;
    //        }
    //
    //        boolean handled = false;
    //        boolean okToSend = true;
    //        switch (keyCode) {
    //            case KeyEvent.KEYCODE_DPAD_UP:
    //            case KeyEvent.KEYCODE_DPAD_DOWN:
    //            case KeyEvent.KEYCODE_DPAD_LEFT:
    //            case KeyEvent.KEYCODE_DPAD_RIGHT:
    //            case KeyEvent.KEYCODE_DPAD_CENTER:
    //            case KeyEvent.KEYCODE_ENTER:
    //                okToSend = false;
    //                break;
    //            case KeyEvent.KEYCODE_BACK:
    //                if (mFiltered && mPopup != null && mPopup.isShowing()) {
    //                    if (event.getAction() == KeyEvent.ACTION_DOWN
    //                        && event.getRepeatCount() == 0) {
    //                        KeyEvent.DispatcherState state = getKeyDispatcherState();
    //                        if (state != null) {
    //                            state.startTracking(event, this);
    //                        }
    //                        handled = true;
    //                    } else if (event.getAction() == KeyEvent.ACTION_UP
    //                        && event.isTracking() && !event.isCanceled()) {
    //                        handled = true;
    //                        mTextFilter.setText("");
    //                    }
    //                }
    //                okToSend = false;
    //                break;
    //            case KeyEvent.KEYCODE_SPACE:
    //                // Only send spaces once we are filtered
    //                okToSend = mFiltered;
    //                break;
    //        }
    //
    //        if (okToSend) {
    //            createTextFilter(true);
    //
    //            KeyEvent forwardEvent = event;
    //            if (forwardEvent.getRepeatCount() > 0) {
    //                forwardEvent = KeyEvent.changeTimeRepeat(event, event.getEventTime(), 0);
    //            }
    //
    //            int action = event.getAction();
    //            switch (action) {
    //                case KeyEvent.ACTION_DOWN:
    //                    handled = mTextFilter.onKeyDown(keyCode, forwardEvent);
    //                    break;
    //
    //                case KeyEvent.ACTION_UP:
    //                    handled = mTextFilter.onKeyUp(keyCode, forwardEvent);
    //                    break;
    //
    //                case KeyEvent.ACTION_MULTIPLE:
    //                    handled = mTextFilter.onKeyMultiple(keyCode, count, event);
    //                    break;
    //            }
    //        }
    //        return handled;
    //    }

    /**
     * Return an InputConnection for editing of the filter text.
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        //        if (isTextFilterEnabled()) {
        //            if (mPublicInputConnection == null) {
        //                mDefInputConnection = new BaseInputConnection(this, false);
        //                mPublicInputConnection = new InputConnectionWrapper(outAttrs);
        //            }
        //            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_FILTER;
        //            outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE;
        //            return mPublicInputConnection;
        //        }
        return null;
    }

    //    private class InputConnectionWrapper implements InputConnection {
    //        private final EditorInfo mOutAttrs;
    //        private InputConnection mTarget;
    //
    //        public InputConnectionWrapper(EditorInfo outAttrs) {
    //            mOutAttrs = outAttrs;
    //        }
    //
    //        private InputConnection getTarget() {
    //            if (mTarget == null) {
    //                mTarget = getTextFilterInput().onCreateInputConnection(mOutAttrs);
    //            }
    //            return mTarget;
    //        }
    //
    //        @Override
    //        public boolean reportFullscreenMode(boolean enabled) {
    //            // Use our own input connection, since it is
    //            // the "real" one the IME is talking with.
    //            return mDefInputConnection.reportFullscreenMode(enabled);
    //        }
    //
    //        @Override
    //        public boolean performEditorAction(int editorAction) {
    //            // The editor is off in its own window; we need to be
    //            // the one that does this.
    //            if (editorAction == EditorInfo.IME_ACTION_DONE) {
    //                InputMethodManager imm = (InputMethodManager)
    //                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    //                if (imm != null) {
    //                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
    //                }
    //                return true;
    //            }
    //            return false;
    //        }
    //
    //        @Override
    //        public boolean sendKeyEvent(KeyEvent event) {
    //            // Use our own input connection, since the filter
    //            // text view may not be shown in a window so has
    //            // no ViewAncestor to dispatch events with.
    //            return mDefInputConnection.sendKeyEvent(event);
    //        }
    //
    //        @Override
    //        public CharSequence getTextBeforeCursor(int n, int flags) {
    //            if (mTarget == null) {
    //                return "";
    //            }
    //            return mTarget.getTextBeforeCursor(n, flags);
    //        }
    //
    //        @Override
    //        public CharSequence getTextAfterCursor(int n, int flags) {
    //            if (mTarget == null) {
    //                return "";
    //            }
    //            return mTarget.getTextAfterCursor(n, flags);
    //        }
    //
    //        @Override
    //        public CharSequence getSelectedText(int flags) {
    //            if (mTarget == null) {
    //                return "";
    //            }
    //            return mTarget.getSelectedText(flags);
    //        }
    //
    //        @Override
    //        public int getCursorCapsMode(int reqModes) {
    //            if (mTarget == null) {
    //                return InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    //            }
    //            return mTarget.getCursorCapsMode(reqModes);
    //        }
    //
    //        @Override
    //        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
    //            return getTarget().getExtractedText(request, flags);
    //        }
    //
    //        @Override
    //        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
    //            return getTarget().deleteSurroundingText(beforeLength, afterLength);
    //        }
    //
    //        @Override
    //        public boolean setComposingText(CharSequence text, int newCursorPosition) {
    //            return getTarget().setComposingText(text, newCursorPosition);
    //        }
    //
    //        @Override
    //        public boolean setComposingRegion(int start, int end) {
    //            return getTarget().setComposingRegion(start, end);
    //        }
    //
    //        @Override
    //        public boolean finishComposingText() {
    //            return mTarget == null || mTarget.finishComposingText();
    //        }
    //
    //        @Override
    //        public boolean commitText(CharSequence text, int newCursorPosition) {
    //            return getTarget().commitText(text, newCursorPosition);
    //        }
    //
    //        @Override
    //        public boolean commitCompletion(CompletionInfo text) {
    //            return getTarget().commitCompletion(text);
    //        }
    //
    //        @Override
    //        public boolean commitCorrection(CorrectionInfo correctionInfo) {
    //            return getTarget().commitCorrection(correctionInfo);
    //        }
    //
    //        @Override
    //        public boolean setSelection(int start, int end) {
    //            return getTarget().setSelection(start, end);
    //        }
    //
    //        @Override
    //        public boolean performContextMenuAction(int id) {
    //            return getTarget().performContextMenuAction(id);
    //        }
    //
    //        @Override
    //        public boolean beginBatchEdit() {
    //            return getTarget().beginBatchEdit();
    //        }
    //
    //        @Override
    //        public boolean endBatchEdit() {
    //            return getTarget().endBatchEdit();
    //        }
    //
    //        @Override
    //        public boolean clearMetaKeyStates(int states) {
    //            return getTarget().clearMetaKeyStates(states);
    //        }
    //
    //        @Override
    //        public boolean performPrivateCommand(String action, Bundle data) {
    //            return getTarget().performPrivateCommand(action, data);
    //        }
    //
    //        @Override
    //        public boolean requestCursorUpdates(int cursorUpdateMode) {
    //            return getTarget().requestCursorUpdates(cursorUpdateMode);
    //        }
    //    }

    /**
     * For filtering we proxy an input connection to an internal text editor,
     * and this allows the proxying to happen.
     */
    @Override
    public boolean checkInputConnectionProxy(View view) {
        return false;
    }

    //    /**
    //     * Creates the window for the text filter and populates it with an EditText field;
    //     *
    //     * @param animateEntrance true if the window should appear with an animation
    //     */
    //    private void createTextFilter(boolean animateEntrance) {
    //        if (mPopup == null) {
    //            PopupWindow p = new PopupWindow(getContext());
    //            p.setFocusable(false);
    //            p.setTouchable(false);
    //            p.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    //            p.setContentView(getTextFilterInput());
    //            p.setWidth(LayoutParams.WRAP_CONTENT);
    //            p.setHeight(LayoutParams.WRAP_CONTENT);
    //            p.setBackgroundDrawable(null);
    //            mPopup = p;
    //            getViewTreeObserver().addOnGlobalLayoutListener(this);
    //            mGlobalLayoutListenerAddedFilter = true;
    //        }
    //        if (animateEntrance) {
    //            mPopup.setAnimationStyle(com.android.internal.R.style.Animation_TypingFilter);
    //        } else {
    //            mPopup.setAnimationStyle(com.android.internal.R.style.Animation_TypingFilterRestore);
    //        }
    //    }

    //    private EditText getTextFilterInput() {
    //        if (mTextFilter == null) {
    //            final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
    //            mTextFilter = (EditText) layoutInflater.inflate(
    //                com.android.internal.R.layout.typing_filter, null);
    //            // For some reason setting this as the "real" input type changes
    //            // the text view in some way that it doesn't work, and I don't
    //            // want to figure out why this is.
    //            mTextFilter.setRawInputType(
    //                EditorInfo.TYPE_CLASS_TEXT
    //                    | EditorInfo.TYPE_TEXT_VARIATION_FILTER);
    //            mTextFilter.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    //            mTextFilter.addTextChangedListener(this);
    //        }
    //        return mTextFilter;
    //    }

    /**
     * Clear the text filter.
     */
    @SuppressWarnings("unused")
    public void clearTextFilter() {
        //        if (mFiltered) {
        //            getTextFilterInput().setText("");
        //            mFiltered = false;
        //            if (mPopup != null && mPopup.isShowing()) {
        //                dismissPopup();
        //            }
        //        }
    }

    /**
     * Returns if the ListView currently has a text filter.
     */
    @SuppressWarnings("unused")
    public boolean hasTextFilter() {
        return false;
    }

    //    @Override
    //    public void onGlobalLayout() {
    //        if (isShown()) {
    //            // Show the popup if we are filtered
    //            if (mFiltered && mPopup != null && !mPopup.isShowing() && !mPopupHidden) {
    //                showPopup();
    //            }
    //        } else {
    //            // Hide the popup when we are no longer visible
    //            if (mPopup != null && mPopup.isShowing()) {
    //                dismissPopup();
    //            }
    //        }
    //
    //    }

    //    /**
    //     * For our text watcher that is associated with the text filter.  Does
    //     * nothing.
    //     */
    //    @Override
    //    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    //    }

    //    /**
    //     * For our text watcher that is associated with the text filter. Performs
    //     * the actual filtering as the text changes, and takes care of hiding and
    //     * showing the popup displaying the currently entered filter text.
    //     */
    //    @Override
    //    public void onTextChanged(CharSequence s, int start, int before, int count) {
    //        if (isTextFilterEnabled()) {
    //            createTextFilter(true);
    //            int length = s.length();
    //            boolean showing = mPopup.isShowing();
    //            if (!showing && length > 0) {
    //                // Show the filter popup if necessary
    //                showPopup();
    //                mFiltered = true;
    //            } else if (showing && length == 0) {
    //                // Remove the filter popup if the user has cleared all text
    //                dismissPopup();
    //                mFiltered = false;
    //            }
    //            if (mAdapter instanceof Filterable) {
    //                Filter f = ((Filterable) mAdapter).getFilter();
    //                // Filter should not be null when we reach this part
    //                if (f != null) {
    //                    f.filter(s, this);
    //                } else {
    //                    throw new IllegalStateException(
    //                        "You cannot call onTextChanged with a non "
    //                            + "filterable adapter");
    //                }
    //            }
    //        }
    //    }

    //    /**
    //     * For our text watcher that is associated with the text filter.  Does
    //     * nothing.
    //     */
    //    @Override
    //    public void afterTextChanged(Editable s) {
    //    }

    //    @Override
    //    public void onFilterComplete(int count) {
    //        if (mSelectedPosition < 0 && count > 0) {
    //            mResurrectToPosition = INVALID_POSITION;
    //            resurrectSelection();
    //        }
    //    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new AbsHListView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT, 0);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new AbsHListView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof AbsHListView.LayoutParams;
    }

    /**
     * Puts the list or grid into transcript mode. In this mode the list or grid will always scroll to the bottom to show new items.
     *
     * @param mode the transcript mode to set
     * @see #TRANSCRIPT_MODE_DISABLED
     * @see #TRANSCRIPT_MODE_NORMAL
     * @see #TRANSCRIPT_MODE_ALWAYS_SCROLL
     */
    public void setTranscriptMode(int mode) {
        mTranscriptMode = mode;
    }

    /**
     * Returns the current transcript mode.
     *
     * @return {@link #TRANSCRIPT_MODE_DISABLED}, {@link #TRANSCRIPT_MODE_NORMAL} or {@link #TRANSCRIPT_MODE_ALWAYS_SCROLL}
     */
    @SuppressWarnings("unused")
    public int getTranscriptMode() {
        return mTranscriptMode;
    }

    @Override
    public int getSolidColor() {
        return mCacheColorHint;
    }

    /**
     * When set to a non-zero value, the cache color hint indicates that this list is always drawn on top of a solid, single-color,
     * opaque background.
     * <p/>
     * Zero means that what's behind this object is translucent (non solid) or is not made of a single color. This hint will not
     * affect any existing background drawable set on this view ( typically set via {@link #setBackgroundDrawable(Drawable)}).
     *
     * @param color The background color
     */
    public void setCacheColorHint(int color) {
        if (color != mCacheColorHint) {
            mCacheColorHint = color;
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                getChildAt(i).setDrawingCacheBackgroundColor(color);
            }
            mRecycler.setCacheColorHint(color);
        }
    }

    /**
     * When set to a non-zero value, the cache color hint indicates that this list is always drawn on top of a solid, single-color,
     * opaque background
     *
     * @return The cache color hint
     */
    @ViewDebug.ExportedProperty (category = "drawing")
    public int getCacheColorHint() {
        return mCacheColorHint;
    }

    /**
     * Move all views (excluding headers and footers) held by this AbsListView into the supplied List. This includes views displayed
     * on the screen as well as views stored in AbsListView's internal view recycler.
     *
     * @param views A list into which to put the reclaimed views
     */
    @SuppressLint ("NewApi")
    @SuppressWarnings("unused")
    public void reclaimViews(List<View> views) {
        int childCount = getChildCount();
        RecyclerListener listener = mRecycler.mRecyclerListener;

        // Reclaim views on screen
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            AbsHListView.LayoutParams lp = (AbsHListView.LayoutParams) child.getLayoutParams();
            // Don't reclaim header or footer views, or views that should be ignored
            if (lp != null && mRecycler.shouldRecycleViewType(lp.viewType)) {
                views.add(child);

                if (ApiHelper.AT_LEAST_14) {
                    child.setAccessibilityDelegate(null);
                }

                if (listener != null) {
                    // Pretend they went through the scrap heap
                    listener.onMovedToScrapHeap(child);
                }
            }
        }
        mRecycler.reclaimScrapViews(views);
        removeAllViewsInLayout();
    }

    private void finishGlows() {
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.finish();
            mEdgeGlowBottom.finish();
        }
    }

    //    /**
    //     * Sets up this AbsListView to use a remote views adapter which connects to a RemoteViewsService
    //     * through the specified intent.
    //     *
    //     * @param intent the intent used to identify the RemoteViewsService for the adapter to connect to.
    //     */
    //    public void setRemoteViewsAdapter(Intent intent) {
    //        // Ensure that we don't already have a RemoteViewsAdapter that is bound to an existing
    //        // service handling the specified intent.
    //        if (mRemoteAdapter != null) {
    //            Intent.FilterComparison fcNew = new Intent.FilterComparison(intent);
    //            Intent.FilterComparison fcOld = new Intent.FilterComparison(
    //                mRemoteAdapter.getRemoteViewsServiceIntent());
    //            if (fcNew.equals(fcOld)) {
    //                return;
    //            }
    //        }
    //        mDeferNotifyDataSetChanged = false;
    //        // Otherwise, create a new RemoteViewsAdapter for binding
    //        mRemoteAdapter = new RemoteViewsAdapter(getContext(), intent, this);
    //        if (mRemoteAdapter.isDataReady()) {
    //            setAdapter(mRemoteAdapter);
    //        }
    //    }

    //    /**
    //     * Sets up the onClickHandler to be used by the RemoteViewsAdapter when inflating RemoteViews
    //     *
    //     * @param handler The OnClickHandler to use when inflating RemoteViews.
    //     * @hide
    //     */
    //    public void setRemoteViewsOnClickHandler(OnClickHandler handler) {
    //        // Ensure that we don't already have a RemoteViewsAdapter that is bound to an existing
    //        // service handling the specified intent.
    //        if (mRemoteAdapter != null) {
    //            mRemoteAdapter.setRemoteViewsOnClickHandler(handler);
    //        }
    //    }

    /**
     * This defers a notifyDataSetChanged on the pending RemoteViewsAdapter if it has not
     * connected yet.
     */
    @SuppressWarnings("unused")
    public void deferNotifyDataSetChanged() {
        mDeferNotifyDataSetChanged = true;
    }

    //    /**
    //     * Called back when the adapter connects to the RemoteViewsService.
    //     */
    //    @Override
    //    public boolean onRemoteAdapterConnected() {
    //        if (mRemoteAdapter != mAdapter) {
    //            setAdapter(mRemoteAdapter);
    //            if (mDeferNotifyDataSetChanged) {
    //                mRemoteAdapter.notifyDataSetChanged();
    //                mDeferNotifyDataSetChanged = false;
    //            }
    //            return false;
    //        } else if (mRemoteAdapter != null) {
    //            mRemoteAdapter.superNotifyDataSetChanged();
    //            return true;
    //        }
    //        return false;
    //    }

    /**
     * Called back when the adapter disconnects from the RemoteViewsService.
     */
    public void onRemoteAdapterDisconnected() {
        // If the remote adapter disconnects, we keep it around
        // since the currently displayed items are still cached.
        // Further, we want the service to eventually reconnect
        // when necessary, as triggered by this view requesting
        // items from the Adapter.
    }

    /**
     * Hints the RemoteViewsAdapter, if it exists, about which views are currently
     * being displayed by the AbsListView.
     */
    void setVisibleRangeHint(int start, int end) {
        //        if (mRemoteAdapter != null) {
        //            mRemoteAdapter.setVisibleRangeHint(start, end);
        //        }
    }

    /**
     * Sets the recycler listener to be notified whenever a View is set aside in the recycler for later reuse. This listener can be
     * used to free resources associated to the View.
     *
     * @param listener The recycler listener to be notified of views set aside in the recycler.
     * @see it.sephiroth.android.library.widget.AbsHListView.RecycleBin
     * @see it.sephiroth.android.library.widget.AbsHListView.RecyclerListener
     */
    public void setRecyclerListener(RecyclerListener listener) {
        mRecycler.mRecyclerListener = listener;
    }

    public class AdapterDataSetObserver extends AdapterView<ListAdapter>.AdapterDataSetObserver {
        @Override
        public void onChanged() {
            if (LOG_ENABLED) {
                Log.i(TAG, "AdapterDataSetObserver::onChanged");
            }
            super.onChanged();
        }

        @Override
        public void onInvalidated() {
            if (LOG_ENABLED) {
                Log.i(TAG, "AdapterDataSetObserver::onInvalidated");
            }
            super.onInvalidated();
        }
    }

    /**
     * AbsListView extends LayoutParams to provide a place to hold the view type.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * View type for this view, as returned by {@link android.widget.Adapter#getItemViewType(int) }
         */
        public int viewType;
        /**
         * When this boolean is set, the view has been added to the AbsListView at least once. It is used to know whether
         * headers/footers have already been added to the list view and whether they should be treated as recycled views or not.
         */
        public boolean recycledHeaderFooter;
        /**
         * When an AbsListView is measured with an AT_MOST measure spec, it needs to obtain children views to measure itself. When
         * doing so, the children are not attached to the window, but put in the recycler which assumes they've been attached
         * before.
         * Setting this flag will force the reused view to be attached to the window rather than just attached to the parent.
         */
        public boolean forceAdd;
        /**
         * The position the view was removed from when pulled out of the scrap heap.
         */
        public int scrappedFromPosition;
        /**
         * The ID the view represents
         */
        public long itemId = -1;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        @SuppressWarnings("unused")
        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int viewType) {
            super(w, h);
            this.viewType = viewType;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    /**
     * A RecyclerListener is used to receive a notification whenever a View is placed inside the RecycleBin's scrap heap. This
     * listener is used to free resources associated to Views placed in the RecycleBin.
     *
     * @see AbsHListView.RecycleBin
     * @see AbsHListView#setRecyclerListener(it.sephiroth.android.library.widget.AbsHListView.RecyclerListener)
     */
    public static interface RecyclerListener {
        /**
         * Indicates that the specified View was moved into the recycler's scrap heap. The view is not displayed on screen any more
         * and any expensive resource associated with the view should be discarded.
         *
         * @param view
         */
        void onMovedToScrapHeap(View view);
    }

    /**
     * The RecycleBin facilitates reuse of views across layouts. The RecycleBin has two levels of
     * storage: ActiveViews and ScrapViews. ActiveViews are those views which were onscreen at the
     * start of a layout. By construction, they are displaying current information. At the end of
     * layout, all views in ActiveViews are demoted to ScrapViews. ScrapViews are old views that
     * could potentially be used by the adapter to avoid allocating views unnecessarily.
     *
     * @see android.widget.AbsListView#setRecyclerListener(android.widget.AbsListView.RecyclerListener)
     * @see android.widget.AbsListView.RecyclerListener
     */
    class RecycleBin {
        private RecyclerListener mRecyclerListener;
        /**
         * The position of the first view stored in mActiveViews.
         */
        private int mFirstActivePosition;
        /**
         * Views that were on screen at the start of layout. This array is populated at the start of
         * layout, and at the end of layout all view in mActiveViews are moved to mScrapViews.
         * Views in mActiveViews represent a contiguous range of Views, with position of the first
         * view store in mFirstActivePosition.
         */
        private View[] mActiveViews = new View[0];
        /**
         * Unsorted views that can be used by the adapter as a convert view.
         */
        private ArrayList<View>[] mScrapViews;
        private int mViewTypeCount;
        private ArrayList<View> mCurrentScrap;
        private ArrayList<View> mSkippedScrap;
        private SparseArrayCompat<View> mTransientStateViews;
        private LongSparseArray<View> mTransientStateViewsById;

        public void setViewTypeCount(int viewTypeCount) {
            if (viewTypeCount < 1) {
                throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
            }
            //noinspection unchecked
            ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
            for (int i = 0; i < viewTypeCount; i++) {
                scrapViews[i] = new ArrayList<View>();
            }
            mViewTypeCount = viewTypeCount;
            mCurrentScrap = scrapViews[0];
            mScrapViews = scrapViews;
        }

        public void markChildrenDirty() {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                final int scrapCount = scrap.size();
                for (int i = 0; i < scrapCount; i++) {
                    scrap.get(i).forceLayout();
                }
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    final int scrapCount = scrap.size();
                    for (int j = 0; j < scrapCount; j++) {
                        scrap.get(j).forceLayout();
                    }
                }
            }
            if (mTransientStateViews != null) {
                final int count = mTransientStateViews.size();
                for (int i = 0; i < count; i++) {
                    mTransientStateViews.valueAt(i).forceLayout();
                }
            }
            if (mTransientStateViewsById != null) {
                final int count = mTransientStateViewsById.size();
                for (int i = 0; i < count; i++) {
                    mTransientStateViewsById.valueAt(i).forceLayout();
                }
            }
        }

        public boolean shouldRecycleViewType(int viewType) {
            return viewType >= 0;
        }

        /**
         * Clears the scrap heap.
         */
        void clear() {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                clearScrap(scrap);
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    clearScrap(scrap);
                }
            }

            clearTransientStateViews();
        }

        /**
         * Fill ActiveViews with all of the children of the AbsListView.
         *
         * @param childCount          The minimum number of views mActiveViews should hold
         * @param firstActivePosition The position of the first view that will be stored in
         *                            mActiveViews
         */
        void fillActiveViews(int childCount, int firstActivePosition) {
            if (mActiveViews.length < childCount) {
                mActiveViews = new View[childCount];
            }
            mFirstActivePosition = firstActivePosition;

            //noinspection MismatchedReadAndWriteOfArray
            final View[] activeViews = mActiveViews;
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                AbsHListView.LayoutParams lp = (AbsHListView.LayoutParams) child.getLayoutParams();
                // Don't put header or footer views into the scrap heap
                if (lp != null && lp.viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                    // Note:  We do place AdapterView.ITEM_VIEW_TYPE_IGNORE in active views.
                    //        However, we will NOT place them into scrap views.
                    activeViews[i] = child;
                }
            }
        }

        /**
         * Get the view corresponding to the specified position. The view will be removed from
         * mActiveViews if it is found.
         *
         * @param position The position to look up in mActiveViews
         * @return The view if it is found, null otherwise
         */
        View getActiveView(int position) {
            int index = position - mFirstActivePosition;
            final View[] activeViews = mActiveViews;
            if (index >= 0 && index < activeViews.length) {
                final View match = activeViews[index];
                activeViews[index] = null;
                return match;
            }
            return null;
        }

        View getTransientStateView(int position) {
            if (mAdapter != null && mAdapterHasStableIds && mTransientStateViewsById != null) {
                long id = mAdapter.getItemId(position);
                View result = mTransientStateViewsById.get(id);
                mTransientStateViewsById.remove(id);
                return result;
            }
            if (mTransientStateViews != null) {
                final int index = mTransientStateViews.indexOfKey(position);
                if (index >= 0) {
                    View result = mTransientStateViews.valueAt(index);
                    mTransientStateViews.removeAt(index);
                    return result;
                }
            }
            return null;
        }

        /**
         * Dumps and fully detaches any currently saved views with transient
         * state.
         */
        void clearTransientStateViews() {
            final SparseArrayCompat<View> viewsByPos = mTransientStateViews;
            if (viewsByPos != null) {
                final int N = viewsByPos.size();
                for (int i = 0; i < N; i++) {
                    removeDetachedView(viewsByPos.valueAt(i), false);
                }
                viewsByPos.clear();
            }

            final LongSparseArray<View> viewsById = mTransientStateViewsById;
            if (viewsById != null) {
                final int N = viewsById.size();
                for (int i = 0; i < N; i++) {
                    removeDetachedView(viewsById.valueAt(i), false);
                }
                viewsById.clear();
            }
        }

        /**
         * @return A view from the ScrapViews collection. These are unordered.
         */
        View getScrapView(int position) {
            if (mViewTypeCount == 1) {
                return retrieveFromScrap(mCurrentScrap, position);
            } else {
                final int whichScrap = mAdapter.getItemViewType(position);
                if (whichScrap >= 0 && whichScrap < mScrapViews.length) {
                    return retrieveFromScrap(mScrapViews[whichScrap], position);
                }
            }
            return null;
        }

        /**
         * Puts a view into the list of scrap views.
         * <p/>
         * If the list data hasn't changed or the adapter has stable IDs, views
         * with transient state will be preserved for later retrieval.
         *
         * @param scrap    The view to add
         * @param position The view's position within its parent
         */
        void addScrapView(View scrap, int position) {
            final AbsHListView.LayoutParams lp = (AbsHListView.LayoutParams) scrap.getLayoutParams();
            if (lp == null) {
                return;
            }

            lp.scrappedFromPosition = position;

            // Remove but don't scrap header or footer views, or views that
            // should otherwise not be recycled.
            final int viewType = lp.viewType;
            if (!shouldRecycleViewType(viewType)) {
                return;
            }

            scrap.onStartTemporaryDetach();

            // The the accessibility state of the view may change while temporary
            // detached and we do not allow detached views to fire accessibility
            // events. So we are announcing that the subtree changed giving a chance
            // to clients holding on to a view in this subtree to refresh it.
            //            notifyViewAccessibilityStateChangedIfNeeded(
            //                AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);

            // Don't scrap views that have transient state.
            final boolean scrapHasTransientState = ViewCompat.hasTransientState(scrap);
            if (scrapHasTransientState) {
                if (mAdapter != null && mAdapterHasStableIds) {
                    // If the adapter has stable IDs, we can reuse the view for
                    // the same data.
                    if (mTransientStateViewsById == null) {
                        mTransientStateViewsById = new LongSparseArray<View>();
                    }
                    mTransientStateViewsById.put(lp.itemId, scrap);
                } else if (!mDataChanged) {
                    // If the data hasn't changed, we can reuse the views at
                    // their old positions.
                    if (mTransientStateViews == null) {
                        mTransientStateViews = new SparseArrayCompat<View>();
                    }
                    mTransientStateViews.put(position, scrap);
                } else {
                    // Otherwise, we'll have to remove the view and start over.
                    if (mSkippedScrap == null) {
                        mSkippedScrap = new ArrayList<View>();
                    }
                    mSkippedScrap.add(scrap);
                }
            } else {
                if (mViewTypeCount == 1) {
                    mCurrentScrap.add(scrap);
                } else {
                    mScrapViews[viewType].add(scrap);
                }

                if (mRecyclerListener != null) {
                    mRecyclerListener.onMovedToScrapHeap(scrap);
                }
            }
        }

        /**
         * Finish the removal of any views that skipped the scrap heap.
         */
        void removeSkippedScrap() {
            if (mSkippedScrap == null) {
                return;
            }
            final int count = mSkippedScrap.size();
            for (int i = 0; i < count; i++) {
                removeDetachedView(mSkippedScrap.get(i), false);
            }
            mSkippedScrap.clear();
        }

        /**
         * Move all views remaining in mActiveViews to mScrapViews.
         */
        void scrapActiveViews() {
            final View[] activeViews = mActiveViews;
            final boolean hasListener = mRecyclerListener != null;
            final boolean multipleScraps = mViewTypeCount > 1;

            ArrayList<View> scrapViews = mCurrentScrap;
            final int count = activeViews.length;
            for (int i = count - 1; i >= 0; i--) {
                final View victim = activeViews[i];
                if (victim != null) {
                    final AbsHListView.LayoutParams lp
                        = (AbsHListView.LayoutParams) victim.getLayoutParams();
                    final int whichScrap = lp.viewType;

                    activeViews[i] = null;

                    if (ViewCompat.hasTransientState(victim)) {
                        // Store views with transient state for later use.
                        victim.onStartTemporaryDetach();

                        if (mAdapter != null && mAdapterHasStableIds) {
                            if (mTransientStateViewsById == null) {
                                mTransientStateViewsById = new LongSparseArray<View>();
                            }
                            long id = mAdapter.getItemId(mFirstActivePosition + i);
                            mTransientStateViewsById.put(id, victim);
                        } else if (!mDataChanged) {
                            if (mTransientStateViews == null) {
                                mTransientStateViews = new SparseArrayCompat<View>();
                            }
                            mTransientStateViews.put(mFirstActivePosition + i, victim);
                        } else if (whichScrap != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                            // The data has changed, we can't keep this view.
                            removeDetachedView(victim, false);
                        }
                    } else if (!shouldRecycleViewType(whichScrap)) {
                        // Discard non-recyclable views except headers/footers.
                        if (whichScrap != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
                            removeDetachedView(victim, false);
                        }
                    } else {
                        // Store everything else on the appropriate scrap heap.
                        if (multipleScraps) {
                            scrapViews = mScrapViews[whichScrap];
                        }

                        victim.onStartTemporaryDetach();
                        lp.scrappedFromPosition = mFirstActivePosition + i;
                        scrapViews.add(victim);

                        if (hasListener) {
                            mRecyclerListener.onMovedToScrapHeap(victim);
                        }
                    }
                }
            }

            pruneScrapViews();
        }

        /**
         * Makes sure that the size of mScrapViews does not exceed the size of
         * mActiveViews, which can happen if an adapter does not recycle its
         * views. Removes cached transient state views that no longer have
         * transient state.
         */
        private void pruneScrapViews() {
            final int maxViews = mActiveViews.length;
            final int viewTypeCount = mViewTypeCount;
            final ArrayList<View>[] scrapViews = mScrapViews;
            for (int i = 0; i < viewTypeCount; ++i) {
                final ArrayList<View> scrapPile = scrapViews[i];
                int size = scrapPile.size();
                final int extras = size - maxViews;
                size--;
                for (int j = 0; j < extras; j++) {
                    removeDetachedView(scrapPile.remove(size--), false);
                }
            }

            final SparseArrayCompat<View> transViewsByPos = mTransientStateViews;
            if (transViewsByPos != null) {
                for (int i = 0; i < transViewsByPos.size(); i++) {
                    final View v = transViewsByPos.valueAt(i);
                    if (!ViewCompat.hasTransientState(v)) {
                        removeDetachedView(v, false);
                        transViewsByPos.removeAt(i);
                        i--;
                    }
                }
            }

            final LongSparseArray<View> transViewsById = mTransientStateViewsById;
            if (transViewsById != null) {
                for (int i = 0; i < transViewsById.size(); i++) {
                    final View v = transViewsById.valueAt(i);
                    if (!ViewCompat.hasTransientState(v)) {
                        removeDetachedView(v, false);
                        transViewsById.removeAt(i);
                        i--;
                    }
                }
            }
        }

        /**
         * Puts all views in the scrap heap into the supplied list.
         */
        void reclaimScrapViews(List<View> views) {
            if (mViewTypeCount == 1) {
                views.addAll(mCurrentScrap);
            } else {
                final int viewTypeCount = mViewTypeCount;
                final ArrayList<View>[] scrapViews = mScrapViews;
                for (int i = 0; i < viewTypeCount; ++i) {
                    final ArrayList<View> scrapPile = scrapViews[i];
                    views.addAll(scrapPile);
                }
            }
        }

        /**
         * Updates the cache color hint of all known views.
         *
         * @param color The new cache color hint.
         */
        void setCacheColorHint(int color) {
            if (mViewTypeCount == 1) {
                final ArrayList<View> scrap = mCurrentScrap;
                final int scrapCount = scrap.size();
                for (int i = 0; i < scrapCount; i++) {
                    scrap.get(i).setDrawingCacheBackgroundColor(color);
                }
            } else {
                final int typeCount = mViewTypeCount;
                for (int i = 0; i < typeCount; i++) {
                    final ArrayList<View> scrap = mScrapViews[i];
                    final int scrapCount = scrap.size();
                    for (int j = 0; j < scrapCount; j++) {
                        scrap.get(j).setDrawingCacheBackgroundColor(color);
                    }
                }
            }
            // Just in case this is called during a layout pass
            final View[] activeViews = mActiveViews;
            for (final View victim : activeViews) {
                if (victim != null) {
                    victim.setDrawingCacheBackgroundColor(color);
                }
            }
        }

        private View retrieveFromScrap(ArrayList<View> scrapViews, int position) {
            final int size = scrapViews.size();
            if (size > 0) {
                // See if we still have a view for this position or ID.
                for (int i = 0; i < size; i++) {
                    final View view = scrapViews.get(i);
                    final AbsHListView.LayoutParams params =
                        (AbsHListView.LayoutParams) view.getLayoutParams();

                    if (mAdapterHasStableIds) {
                        final long id = mAdapter.getItemId(position);
                        if (id == params.itemId) {
                            return scrapViews.remove(i);
                        }
                    } else if (params.scrappedFromPosition == position) {
                        final View scrap = scrapViews.remove(i);
                        clearAccessibilityFromScrap(scrap);
                        return scrap;
                    }
                }
                final View scrap = scrapViews.remove(size - 1);
                clearAccessibilityFromScrap(scrap);
                return scrap;
            } else {
                return null;
            }
        }

        private void clearScrap(final ArrayList<View> scrap) {
            final int scrapCount = scrap.size();
            for (int j = 0; j < scrapCount; j++) {
                removeDetachedView(scrap.remove(scrapCount - 1 - j), false);
            }
        }

        @TargetApi (14)
        private void clearAccessibilityFromScrap(View view) {
            if (ApiHelper.AT_LEAST_14) {
                view.setAccessibilityDelegate(null);
            }
        }

        @TargetApi (14)
        private void removeDetachedView(View child, boolean animate) {
            if (ApiHelper.AT_LEAST_14) {
                child.setAccessibilityDelegate(null);
            }
            AbsHListView.this.removeDetachedView(child, animate);
        }
    }

    /**
     * Returns the width of the view for the specified position.
     *
     * @param position the item position
     * @return view width in pixels
     */
    int getWidthForPosition(int position) {
        final int firstVisiblePosition = getFirstVisiblePosition();
        final int childCount = getChildCount();
        final int index = position - firstVisiblePosition;
        if (index >= 0 && index < childCount) {
            // Position is on-screen, use existing view.
            final View view = getChildAt(index);
            return view.getWidth();
        } else {
            // Position is off-screen, obtain & recycle view.
            final View view = obtainView(position, mIsScrap);
            view.measure(MeasureSpec.UNSPECIFIED, mHeightMeasureSpec);
            final int width = view.getMeasuredWidth();
            mRecycler.addScrapView(view, position);
            return width;
        }
    }

    /**
     * Sets the selected item and positions the selection x pixels from the top edge
     * of the ListView. (If in touch mode, the item will not be selected but it will
     * still be positioned appropriately.)
     *
     * @param position Index (starting at 0) of the data item to be selected.
     * @param x        The distance from the left edge of the HListView (plus padding) that the
     *                 item will be positioned.
     */
    @SuppressWarnings ("unused")
    public void setSelectionFromLeft(int position, int x) {
        if (mAdapter == null) {
            return;
        }

        if (!isInTouchMode()) {
            position = lookForSelectablePosition(position, true);
            if (position >= 0) {
                setNextSelectedPositionInt(position);
            }
        } else {
            mResurrectToPosition = position;
        }

        if (position >= 0) {
            mLayoutMode = LAYOUT_SPECIFIC;
            mSpecificLeft = mListPadding.left + x;

            if (mNeedSync) {
                mSyncPosition = position;
                mSyncColId = mAdapter.getItemId(position);
            }

            if (mPositionScroller != null) {
                mPositionScroller.stop();
            }
            requestLayout();
        }
    }

    /**
     * Abstract positon scroller used to handle smooth scrolling.
     */
    static abstract class AbsPositionScroller {
        public abstract void start(int position);

        public abstract void start(int position, int boundPosition);

        @SuppressWarnings ("unused")
        public abstract void startWithOffset(int position, int offset);

        public abstract void startWithOffset(int position, int offset, int duration);

        public abstract void stop();
    }

    /**
     * Default position scroller that simulates a fling.
     */
    class PositionScroller extends AbsPositionScroller implements Runnable {
        private static final int SCROLL_DURATION = 200;
        private static final int MOVE_DOWN_POS = 1;
        private static final int MOVE_UP_POS = 2;
        private static final int MOVE_DOWN_BOUND = 3;
        private static final int MOVE_UP_BOUND = 4;
        private static final int MOVE_OFFSET = 5;
        private int mMode;
        private int mTargetPos;
        private int mBoundPos;
        private int mLastSeenPos;
        private int mScrollDuration;
        private final int mExtraScroll;
        private int mOffsetFromLeft;

        PositionScroller() {
            mExtraScroll = ViewConfiguration.get(getContext()).getScaledFadingEdgeLength();
        }

        @Override
        public void start(final int position) {
            stop();

            if (mDataChanged) {
                // Wait until we're back in a stable state to try this.
                mPositionScrollAfterLayout = new Runnable() {
                    @Override
                    public void run() {
                        start(position);
                    }
                };
                return;
            }

            final int childCount = getChildCount();
            if (childCount == 0) {
                // Can't scroll without children.
                return;
            }

            final int firstPos = mFirstPosition;
            final int lastPos = firstPos + childCount - 1;

            int viewTravelCount;
            int clampedPosition = Math.max(0, Math.min(getCount() - 1, position));
            if (clampedPosition < firstPos) {
                viewTravelCount = firstPos - clampedPosition + 1;
                mMode = MOVE_UP_POS;
            } else if (clampedPosition > lastPos) {
                viewTravelCount = clampedPosition - lastPos + 1;
                mMode = MOVE_DOWN_POS;
            } else {
                scrollToVisible(clampedPosition, INVALID_POSITION, SCROLL_DURATION);
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = clampedPosition;
            mBoundPos = INVALID_POSITION;
            mLastSeenPos = INVALID_POSITION;

            postOnAnimation(this);
        }

        @Override
        public void start(final int position, final int boundPosition) {
            stop();

            if (boundPosition == INVALID_POSITION) {
                start(position);
                return;
            }

            if (mDataChanged) {
                // Wait until we're back in a stable state to try this.
                mPositionScrollAfterLayout = new Runnable() {
                    @Override
                    public void run() {
                        start(position, boundPosition);
                    }
                };
                return;
            }

            final int childCount = getChildCount();
            if (childCount == 0) {
                // Can't scroll without children.
                return;
            }

            final int firstPos = mFirstPosition;
            final int lastPos = firstPos + childCount - 1;

            int viewTravelCount;
            int clampedPosition = Math.max(0, Math.min(getCount() - 1, position));
            if (clampedPosition < firstPos) {
                final int boundPosFromLast = lastPos - boundPosition;
                if (boundPosFromLast < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = firstPos - clampedPosition + 1;
                final int boundTravel = boundPosFromLast - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_UP_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_UP_POS;
                }
            } else if (clampedPosition > lastPos) {
                final int boundPosFromFirst = boundPosition - firstPos;
                if (boundPosFromFirst < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = clampedPosition - lastPos + 1;
                final int boundTravel = boundPosFromFirst - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_DOWN_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_DOWN_POS;
                }
            } else {
                scrollToVisible(clampedPosition, boundPosition, SCROLL_DURATION);
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = clampedPosition;
            mBoundPos = boundPosition;
            mLastSeenPos = INVALID_POSITION;

            postOnAnimation(this);
        }

        @Override
        public void startWithOffset(int position, int offset) {
            startWithOffset(position, offset, SCROLL_DURATION);
        }

        @Override
        public void startWithOffset(final int position, int offset, final int duration) {
            stop();

            if (mDataChanged) {
                // Wait until we're back in a stable state to try this.
                final int postOffset = offset;
                mPositionScrollAfterLayout = new Runnable() {
                    @Override
                    public void run() {
                        startWithOffset(position, postOffset, duration);
                    }
                };
                return;
            }

            final int childCount = getChildCount();
            if (childCount == 0) {
                // Can't scroll without children.
                return;
            }

            offset += getPaddingTop();

            mTargetPos = Math.max(0, Math.min(getCount() - 1, position));
            mOffsetFromLeft = offset;
            mBoundPos = INVALID_POSITION;
            mLastSeenPos = INVALID_POSITION;
            mMode = MOVE_OFFSET;

            final int firstPos = mFirstPosition;
            final int lastPos = firstPos + childCount - 1;

            int viewTravelCount;
            if (mTargetPos < firstPos) {
                viewTravelCount = firstPos - mTargetPos;
            } else if (mTargetPos > lastPos) {
                viewTravelCount = mTargetPos - lastPos;
            } else {
                // On-screen, just scroll.
                final int targetLeft = getChildAt(mTargetPos - firstPos).getLeft();
                smoothScrollBy(targetLeft - offset, duration, true);
                return;
            }

            // Estimate how many screens we should travel
            final float screenTravelCount = (float) viewTravelCount / childCount;
            mScrollDuration = screenTravelCount < 1 ?
                duration : (int) (duration / screenTravelCount);
            mLastSeenPos = INVALID_POSITION;

            postOnAnimation(this);
        }

        /**
         * Scroll such that targetPos is in the visible padded region without scrolling
         * boundPos out of view. Assumes targetPos is onscreen.
         */
        private void scrollToVisible(int targetPos, int boundPos, int duration) {
            final int firstPos = mFirstPosition;
            final int childCount = getChildCount();
            final int lastPos = firstPos + childCount - 1;
            final int paddedLeft = mListPadding.left;
            final int paddedRight = getWidth() - mListPadding.right;

            if (targetPos < firstPos || targetPos > lastPos) {
                Log.w(
                    TAG, "scrollToVisible called with targetPos " + targetPos +
                        " not visible [" + firstPos + ", " + lastPos + "]");
            }
            if (boundPos < firstPos || boundPos > lastPos) {
                // boundPos doesn't matter, it's already offscreen.
                boundPos = INVALID_POSITION;
            }

            final View targetChild = getChildAt(targetPos - firstPos);
            final int targetLeft = targetChild.getLeft();
            final int targetRight = targetChild.getRight();
            int scrollBy = 0;

            if (targetRight > paddedRight) {
                scrollBy = targetRight - paddedRight;
            }
            if (targetLeft < paddedLeft) {
                scrollBy = targetLeft - paddedLeft;
            }

            if (scrollBy == 0) {
                return;
            }

            if (boundPos >= 0) {
                final View boundChild = getChildAt(boundPos - firstPos);
                final int boundLeft = boundChild.getLeft();
                final int boundRight = boundChild.getRight();
                final int absScroll = Math.abs(scrollBy);

                if (scrollBy < 0 && boundRight + absScroll > paddedRight) {
                    // Don't scroll the bound view off the bottom of the screen.
                    scrollBy = Math.max(0, boundRight - paddedRight);
                } else if (scrollBy > 0 && boundLeft - absScroll < paddedLeft) {
                    // Don't scroll the bound view off the top of the screen.
                    scrollBy = Math.min(0, boundLeft - paddedLeft);
                }
            }

            smoothScrollBy(scrollBy, duration);
        }

        @Override
        public void stop() {
            removeCallbacks(this);
        }

        @Override
        public void run() {
            final int listWidth = getWidth();
            final int firstPos = mFirstPosition;

            switch (mMode) {
                case MOVE_DOWN_POS: {
                    final int lastViewIndex = getChildCount() - 1;
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastViewIndex < 0) {
                        return;
                    }

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        postOnAnimation(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewWidth = lastView.getWidth();
                    final int lastViewLeft = lastView.getLeft();
                    final int lastViewPixelsShowing = listWidth - lastViewLeft;
                    final int extraScroll = lastPos < mItemCount - 1 ?
                        Math.max(mListPadding.right, mExtraScroll) : mListPadding.right;

                    final int scrollBy = lastViewWidth - lastViewPixelsShowing + extraScroll;
                    smoothScrollBy(scrollBy, mScrollDuration, true);

                    mLastSeenPos = lastPos;
                    if (lastPos < mTargetPos) {
                        postOnAnimation(this);
                    }
                    break;
                }

                case MOVE_DOWN_BOUND: {
                    final int nextViewIndex = 1;
                    final int childCount = getChildCount();

                    if (firstPos == mBoundPos || childCount <= nextViewIndex
                        || firstPos + childCount >= mItemCount) {
                        return;
                    }
                    final int nextPos = firstPos + nextViewIndex;

                    if (nextPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        postOnAnimation(this);
                        return;
                    }

                    final View nextView = getChildAt(nextViewIndex);
                    final int nextViewWidth = nextView.getWidth();
                    final int nextViewLeft = nextView.getLeft();
                    final int extraScroll = Math.max(mListPadding.right, mExtraScroll);
                    if (nextPos < mBoundPos) {
                        smoothScrollBy(
                            Math.max(0, nextViewWidth + nextViewLeft - extraScroll),
                            mScrollDuration, true);

                        mLastSeenPos = nextPos;

                        postOnAnimation(this);
                    } else {
                        if (nextViewLeft > extraScroll) {
                            smoothScrollBy(nextViewLeft - extraScroll, mScrollDuration, true);
                        }
                    }
                    break;
                }

                case MOVE_UP_POS: {
                    if (firstPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        postOnAnimation(this);
                        return;
                    }

                    final View firstView = getChildAt(0);
                    if (firstView == null) {
                        return;
                    }
                    final int firstViewLeft = firstView.getLeft();
                    final int extraScroll = firstPos > 0 ?
                        Math.max(mExtraScroll, mListPadding.left) : mListPadding.left;

                    smoothScrollBy(firstViewLeft - extraScroll, mScrollDuration, true);

                    mLastSeenPos = firstPos;

                    if (firstPos > mTargetPos) {
                        postOnAnimation(this);
                    }
                    break;
                }

                case MOVE_UP_BOUND: {
                    final int lastViewIndex = getChildCount() - 2;
                    if (lastViewIndex < 0) {
                        return;
                    }
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        postOnAnimation(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewWidth = lastView.getWidth();
                    final int lastViewLeft = lastView.getLeft();
                    final int lastViewPixelsShowing = listWidth - lastViewLeft;
                    final int extraScroll = Math.max(mListPadding.left, mExtraScroll);
                    mLastSeenPos = lastPos;
                    if (lastPos > mBoundPos) {
                        smoothScrollBy(-(lastViewPixelsShowing - extraScroll), mScrollDuration, true);
                        postOnAnimation(this);
                    } else {
                        final int bottom = listWidth - extraScroll;
                        final int lastViewBottom = lastViewLeft + lastViewWidth;
                        if (bottom > lastViewBottom) {
                            smoothScrollBy(-(bottom - lastViewBottom), mScrollDuration, true);
                        }
                    }
                    break;
                }

                case MOVE_OFFSET: {
                    if (mLastSeenPos == firstPos) {
                        // No new views, let things keep going.
                        postOnAnimation(this);
                        return;
                    }

                    mLastSeenPos = firstPos;

                    final int childCount = getChildCount();
                    final int position = mTargetPos;
                    final int lastPos = firstPos + childCount - 1;

                    int viewTravelCount = 0;
                    if (position < firstPos) {
                        viewTravelCount = firstPos - position + 1;
                    } else if (position > lastPos) {
                        viewTravelCount = position - lastPos;
                    }

                    // Estimate how many screens we should travel
                    final float screenTravelCount = (float) viewTravelCount / childCount;

                    final float modifier = Math.min(Math.abs(screenTravelCount), 1.f);
                    if (position < firstPos) {
                        final int distance = (int) (-getWidth() * modifier);
                        final int duration = (int) (mScrollDuration * modifier);
                        smoothScrollBy(distance, duration, true);
                        postOnAnimation(this);
                    } else if (position > lastPos) {
                        final int distance = (int) (getWidth() * modifier);
                        final int duration = (int) (mScrollDuration * modifier);
                        smoothScrollBy(distance, duration, true);
                        postOnAnimation(this);
                    } else {
                        // On-screen, just scroll.
                        final int targetLeft = getChildAt(position - firstPos).getLeft();
                        final int distance = targetLeft - mOffsetFromLeft;
                        final int duration = (int) (mScrollDuration *
                            ((float) Math.abs(distance) / getWidth()));
                        smoothScrollBy(distance, duration, true);
                    }
                    break;
                }

                default:
                    break;
            }
        }
    }
}
