package it.sephiroth.android.library.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;

import java.util.ArrayList;

/**
 * This is a HorizontalListView trying to mimic DynamicListView from Google DevByte.
 * Only this works horizontally.
 * <p/>
 * Created by robertwang on 9/11/14.
 */

@TargetApi(14)
public class DraggableHorizontalListView extends HListView {

    // =====================================================================================

    private final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 15;
    private final int MOVE_DURATION = 150;
    private final int LINE_THICKNESS = 15;

    private int mLastEventX = -1;

    private int mDownY = -1;
    private int mDownX = -1;

    private int mTotalOffset = 0;

    // Is the selected cell being dragged around?
    private boolean mCellIsMobile = false;

    // Is the ListView scrolling while cell being dragged?
    private boolean mIsMobileScrolling = false;

    private int mSmoothScrollAmountAtEdge = 0;

    private final int INVALID_ID = -1;

    // The id of the item is one the left side of the target cell
    private long mLeftItemId = INVALID_ID;

    // The id of the item is one the right side of the target cell
    private long mRightItemId = INVALID_ID;

    // The id of the target cell
    private long mMobileItemId = INVALID_ID;

    // A drawable for containing the screen shot of target cell
    private BitmapDrawable mHoverCell;
    private Rect mHoverCellCurrentBounds;
    private Rect mHoverCellOriginalBounds;

    private final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;

    private boolean mIsWaitingForScrollFinish = false;

    // 3 types of states: FLING, IDLE, TOUCH_SCROLL
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private ArrayList<String> mDataList;
    private static final String TAG = DraggableHorizontalListView.class.getSimpleName();

    // =====================================================================================

    public DraggableHorizontalListView(Context context) {
        super(context);
        init(context);
    }

    public DraggableHorizontalListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DraggableHorizontalListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void setList(ArrayList<String> dataList) {
        mDataList = dataList;
    }

    public void init(Context context) {
        setOnItemLongClickListener(mOnItemLongClickListener);
        setOnScrollListener(mScrollListener);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE / metrics.density);
    }

    private AdapterView.OnItemLongClickListener mOnItemLongClickListener =
            new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                    mTotalOffset = 0;

                    int position = pointToPosition(mDownX, mDownY);
                    int itemNum = position - getFirstVisiblePosition();
                    Log.d(TAG, "position: " + position + " ,itemNum: " + itemNum);

                    View selectedView = getChildAt(itemNum);
                    mMobileItemId = getAdapter().getItemId(position);
                    Log.d(TAG, "mMobileItemId: " + mMobileItemId);

                    mHoverCell = getAndAddHoverView(selectedView);
                    selectedView.setVisibility(INVISIBLE);

                    mCellIsMobile = true;

                    updateNeighborViewsForID(mMobileItemId);

                    return true;
                }
            };

    /**
     * Creates the hover cell with the appropriate bitmap and of appropriate
     * size. The hover cell's BitmapDrawable is drawn on top of the bitmap every
     * single time an invalidate call is made.
     */
    private BitmapDrawable getAndAddHoverView(View v) {

        int w = v.getWidth();
        int h = v.getHeight();
        int top = v.getTop();
        int left = v.getLeft();

        Bitmap b = getBitmapWithBorder(v);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), b);

        mHoverCellOriginalBounds = new Rect(left, top, left + w, top + h);
        mHoverCellCurrentBounds = new Rect(mHoverCellOriginalBounds);

        drawable.setBounds(mHoverCellCurrentBounds);

        return drawable;
    }

    /**
     * Draws a black border over the screenshot of the view passed in.
     */
    private Bitmap getBitmapWithBorder(View v) {
        Bitmap bitmap = getBitmapFromView(v);
        Canvas can = new Canvas(bitmap);

        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(LINE_THICKNESS);
        paint.setColor(Color.BLACK);

        can.drawBitmap(bitmap, 0, 0, null);
        can.drawRect(rect, paint);

        return bitmap;
    }

    /**
     * Returns a bitmap showing a screenshot of the view passed in.
     */
    private Bitmap getBitmapFromView(View v) {
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }

    /**
     * Stores a reference to the views above and below the item currently
     * corresponding to the hover cell. It is important to note that if this
     * item is either at the top or bottom of the list, mAboveItemId or mBelowItemId
     * may be invalid.
     */
    private void updateNeighborViewsForID(long itemID) {
        int position = getPositionForID(itemID);
        BaseAdapter adapter = ((BaseAdapter) getAdapter());
        mLeftItemId = adapter.getItemId(position - 1);
        mRightItemId = adapter.getItemId(position + 1);
    }

    /**
     * Retrieves the view in the list corresponding to itemID
     */
    public View getViewForID(long itemID) {
        int firstVisiblePosition = getFirstVisiblePosition();
        BaseAdapter adapter = ((BaseAdapter) getAdapter());
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int position = firstVisiblePosition + i;
            long id = adapter.getItemId(position);
            if (id == itemID) {
                return v;
            }
        }
        return null;
    }

    /**
     * Retrieves the position in the list corresponding to itemID
     */
    public int getPositionForID(long itemID) {
        View v = getViewForID(itemID);
        if (v == null) {
            return -1;
        } else {
            return getPositionForView(v);
        }
    }


    /**
     * dispatchDraw gets invoked when all the child views are about to be drawn.
     * By overriding this method, the hover cell (BitmapDrawable) can be drawn
     * over the listview's items whenever the listview is redrawn.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHoverCell != null) {
            mHoverCell.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mDownX = (int) event.getX();
                mDownY = (int) event.getY();
                mActivePointerId = event.getPointerId(0);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER_ID) {
                    break;
                }

                int pointerIndex = event.findPointerIndex(mActivePointerId);

                mLastEventX = (int) event.getX(pointerIndex);

                int deltaX = mLastEventX - mDownX;

                //int deltaY = mLastEventY - mDownY;

                if (mCellIsMobile) {
                    mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left + deltaX + mTotalOffset,
                            mHoverCellOriginalBounds.top);
                    mHoverCell.setBounds(mHoverCellCurrentBounds);
                    invalidate();

                    handleCellSwitch();

                    mIsMobileScrolling = false;
                    handleMobileCellScroll();

                    return false;
                }
                break;
            case MotionEvent.ACTION_UP:
                touchEventsEnded();
                break;
            case MotionEvent.ACTION_CANCEL:
                touchEventsCancelled();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                /* If a multitouch event took place and the original touch dictating
                 * the movement of the hover cell has ended, then the dragging event
                 * ends and the hover cell is animated to its corresponding position
                 * in the listview. */
                pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    touchEventsEnded();
                }
                break;
            default:
                break;
        }


        return super.onTouchEvent(event);
    }

    /**
     * This method determines whether the hover cell has been shifted far enough
     * to invoke a cell swap. If so, then the respective cell swap candidate is
     * determined and the data set is changed. Upon posting a notification of the
     * data set change, a layout is invoked to place the cells in the right place.
     * Using a ViewTreeObserver and a corresponding OnPreDrawListener, we can
     * offset the cell being swapped to where it previously was and then animate it to
     * its new position.
     */
    private void handleCellSwitch() {
        final int deltaX = mLastEventX - mDownX;
        int deltaXTotal = mHoverCellOriginalBounds.left + mTotalOffset + deltaX;

        View rightView = getViewForID(mRightItemId);
        View mobileView = getViewForID(mMobileItemId);
        View leftView = getViewForID(mLeftItemId);

        boolean isRight = (rightView != null) && (deltaXTotal > rightView.getLeft());
        boolean isLeft = (leftView != null) && (deltaXTotal < leftView.getLeft());

        if (isRight || isLeft) {

            final long switchItemID = isRight ? mRightItemId : mLeftItemId;
            View switchView = isRight ? rightView : leftView;
            final int originalItem = getPositionForView(mobileView);

            if (switchView == null) {
                updateNeighborViewsForID(mMobileItemId);
                return;
            }

            swapElements(mDataList, originalItem, getPositionForView(switchView));

            ((BaseAdapter) getAdapter()).notifyDataSetChanged();

            mDownX = mLastEventX;

            final int switchViewStartLeft = switchView.getLeft();

            mobileView.setVisibility(View.VISIBLE);
            switchView.setVisibility(View.INVISIBLE);

            updateNeighborViewsForID(mMobileItemId);

            final ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    observer.removeOnPreDrawListener(this);

                    View switchView = getViewForID(switchItemID);

                    mTotalOffset += deltaX;

                    int switchViewNewLeft = switchView.getLeft();
                    int delta = switchViewStartLeft - switchViewNewLeft;

                    switchView.setTranslationX(delta);

                    ObjectAnimator animator = ObjectAnimator.ofFloat(switchView,
                            View.TRANSLATION_X, 0);
                    animator.setDuration(MOVE_DURATION);
                    animator.start();

                    return true;
                }
            });
        }
    }

    private void swapElements(ArrayList<String> arrayList, int indexOne, int indexTwo) {
        Log.d(TAG, "Swapping " + indexOne + " & " + indexTwo);
        Object temp = arrayList.get(indexOne);
        arrayList.set(indexOne, arrayList.get(indexTwo));
        arrayList.set(indexTwo, temp.toString());
    }


    /**
     * Resets all the appropriate fields to a default state while also animating
     * the hover cell back to its correct location.
     */
    private void touchEventsEnded() {

        Log.d(TAG, "touchEventEnded()");

        final View mobileView = getViewForID(mMobileItemId);

        if (mCellIsMobile || mIsWaitingForScrollFinish) {
            mCellIsMobile = false;
            mIsWaitingForScrollFinish = false;
            mIsMobileScrolling = false;
            mActivePointerId = INVALID_POINTER_ID;

            // If the autoscroller has not completed scrolling, we need to wait for it to
            // finish in order to determine the final location of where the hover cell
            // should be animated to.
            if (mScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mIsWaitingForScrollFinish = true;
                return;
            }

            Log.d(TAG, "mHoverCellOriginalBounds.left: " + mHoverCellOriginalBounds.left);
            Log.d(TAG, "mobileView.getTop(): " + mobileView.getTop());
            Log.d(TAG, "mobileView.getLeft(): " + mobileView.getLeft());

//            mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left, mobileView.getTop());
            mHoverCellCurrentBounds.offsetTo(mobileView.getLeft(), mobileView.getTop());

            ObjectAnimator hoverViewAnimator = ObjectAnimator.ofObject(mHoverCell, "bounds",
                    sBoundEvaluator, mHoverCellCurrentBounds);
            hoverViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    invalidate();
                }
            });
            hoverViewAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mLeftItemId = INVALID_ID;
                    mMobileItemId = INVALID_ID;
                    mRightItemId = INVALID_ID;
                    mobileView.setVisibility(VISIBLE);
                    mHoverCell = null;
                    setEnabled(true);
                    invalidate();
                }
            });
            hoverViewAnimator.start();
        } else {
            touchEventsCancelled();
        }
    }

    /**
     * Resets all the appropriate fields to a default state.
     */
    private void touchEventsCancelled() {
        View mobileView = getViewForID(mMobileItemId);
        if (mCellIsMobile) {
            mLeftItemId = INVALID_ID;
            mMobileItemId = INVALID_ID;
            mRightItemId = INVALID_ID;
            mobileView.setVisibility(VISIBLE);
            mHoverCell = null;
            invalidate();
        }
        mCellIsMobile = false;
        mIsMobileScrolling = false;
        mActivePointerId = INVALID_POINTER_ID;
    }

    /**
     * This TypeEvaluator is used to animate the BitmapDrawable back to its
     * final location when the user lifts his finger by modifying the
     * BitmapDrawable's bounds.
     */
    private final static TypeEvaluator<Rect> sBoundEvaluator = new TypeEvaluator<Rect>() {
        public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
            return new Rect(interpolate(startValue.left, endValue.left, fraction),
                    interpolate(startValue.top, endValue.top, fraction),
                    interpolate(startValue.right, endValue.right, fraction),
                    interpolate(startValue.bottom, endValue.bottom, fraction));
        }

        public int interpolate(int start, int end, float fraction) {
            return (int) (start + fraction * (end - start));
        }
    };


    /**
     * Determines whether this listview is in a scrolling state invoked
     * by the fact that the hover cell is out of the bounds of the listview;
     */
    private void handleMobileCellScroll() {
        mIsMobileScrolling = handleMobileCellScroll(mHoverCellCurrentBounds);
    }

    /**
     * This method is in charge of determining if the hover cell is above
     * or below the bounds of the listview. If so, the listview does an appropriate
     * upward or downward smooth scroll so as to reveal new items.
     */
    public boolean handleMobileCellScroll(Rect r) {
        int offset = computeVerticalScrollOffset();

        int width = getWidth();
        //
//        int height = getHeight();
        int extent = computeVerticalScrollExtent();
        int range = computeVerticalScrollRange();
        //
//        int hoverViewTop = r.top;
//        int hoverHeight = r.height();
        //
        int hoverViewLeft = r.left;
        int hoverWidth = r.width();


//        if (hoverViewTop <= 0 && offset > 0) {
//            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
//            return true;
//        }
//
//        if (hoverViewTop + hoverHeight >= height && (offset + extent) < range) {
//            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
//            return true;
//        }


        if (hoverViewLeft <= 0 && offset > 0) {
            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        if (hoverViewLeft + hoverWidth >= width && (offset + extent) < range) {
            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
            return true;
        }


        return false;
    }


    /**
     * This scroll listener is added to the listview in order to handle cell swapping
     * when the cell is either at the left or right edge of the listview. If the hover
     * cell is at either edge of the listview, the listview will begin scrolling. As
     * scrolling takes place, the listview continuously checks if new cells became visible
     * and determines whether they are potential candidates for a cell swap.
     */
    private OnScrollListener mScrollListener = new OnScrollListener() {

        private int mPreviousFirstVisibleItem = -1;
        private int mPreviousVisibleItemCount = -1;
        private int mCurrentFirstVisibleItem;
        private int mCurrentVisibleItemCount;
        private int mCurrentScrollState;


        @Override
        public void onScrollStateChanged(AbsHListView view, int scrollState) {
            mCurrentScrollState = scrollState;
            mScrollState = scrollState;
            isScrollCompleted();
        }

        @Override
        public void onScroll(AbsHListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            mCurrentFirstVisibleItem = firstVisibleItem;
            mCurrentVisibleItemCount = visibleItemCount;

            mPreviousFirstVisibleItem = (mPreviousFirstVisibleItem == -1) ? mCurrentFirstVisibleItem
                    : mPreviousFirstVisibleItem;
            mPreviousVisibleItemCount = (mPreviousVisibleItemCount == -1) ? mCurrentVisibleItemCount
                    : mPreviousVisibleItemCount;

            checkAndHandleFirstVisibleCellChange();
            checkAndHandleLastVisibleCellChange();

            mPreviousFirstVisibleItem = mCurrentFirstVisibleItem;
            mPreviousVisibleItemCount = mCurrentVisibleItemCount;
        }


        /**
         * This method is in charge of invoking 1 of 2 actions. Firstly, if the listview
         * is in a state of scrolling invoked by the hover cell being outside the bounds
         * of the listview, then this scrolling event is continued. Secondly, if the hover
         * cell has already been released, this invokes the animation for the hover cell
         * to return to its correct position after the listview has entered an idle scroll
         * state.
         */
        private void isScrollCompleted() {
            if (mCurrentVisibleItemCount > 0 && mCurrentScrollState == SCROLL_STATE_IDLE) {
                if (mCellIsMobile && mIsMobileScrolling) {
                    handleMobileCellScroll();
                } else if (mIsWaitingForScrollFinish) {
                    touchEventsEnded();
                }
            }
        }

        /**
         * Determines if the listview scrolled up enough to reveal a new cell at the
         * top of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleFirstVisibleCellChange() {
            if (mCurrentFirstVisibleItem != mPreviousFirstVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForID(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }

        /**
         * Determines if the listview scrolled down enough to reveal a new cell at the
         * bottom of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleLastVisibleCellChange() {
            int currentLastVisibleItem = mCurrentFirstVisibleItem + mCurrentVisibleItemCount;
            int previousLastVisibleItem = mPreviousFirstVisibleItem + mPreviousVisibleItemCount;
            if (currentLastVisibleItem != previousLastVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForID(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }
    };


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        //Log.d(TAG, "@ KeyEvent handled by me!" + event.toString());

        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return super.onKeyDown(event.getKeyCode(), event);

            case KeyEvent.ACTION_UP:
                return super.onKeyUp(event.getKeyCode(), event);

            case KeyEvent.ACTION_MULTIPLE:
                return super.onKeyMultiple(event.getKeyCode(), 1, event);

            default: // shouldn't happen
                return false;
        }
    }
}
