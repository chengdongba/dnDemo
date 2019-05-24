package com.dqchen.imagehelp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import java.io.IOException;
import java.io.InputStream;

/**
 * 自定义长图显示view
 */
public class LongImageView extends View implements GestureDetector.OnGestureListener, View.OnTouchListener {

    private GestureDetector mDetector;
    private Scroller mScroller;
    private Rect mRect;
    private BitmapFactory.Options mOptions;
    private int mImageWidth;
    private int mImageHeight;
    private BitmapRegionDecoder mDecoder;
    private int mViewWidth;
    private int mViewHeight;
    private float mScale;
    private Bitmap bitmap;

    public LongImageView(Context context) {
        this(context, null, 0);
    }

    public LongImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * 构造方法中完成一些初始化的操作
     *
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    public LongImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //指定加载区域
        mRect = new Rect();
        //滑动事件
        mScroller = new Scroller(context);
        //手势
        mDetector = new GestureDetector(context, this);
        //设置内存的复用
        mOptions = new BitmapFactory.Options();
        //设置触摸事件
        setOnTouchListener(this);
    }

    /**
     * 用户输入一张图片
     *
     * @param is
     */
    public void setImage(InputStream is) {
        mOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, mOptions);
        //获取图片的信息
        mImageWidth = mOptions.outWidth;
        mImageHeight = mOptions.outHeight;
        //可复用
        mOptions.inMutable = true;
        //参数
        mOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        mOptions.inJustDecodeBounds = false;
        //初始化一个区域解码器
        try {
            mDecoder = BitmapRegionDecoder.newInstance(is, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //调用layout
        requestLayout();
    }

    /**
     * 测量
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //获取view的信息
        mViewWidth = getMeasuredWidth();
        mViewHeight = getMeasuredHeight();
        //根据图片的宽高和view的信息,设置缩放比例
        mScale = mViewWidth / (float) mImageWidth;
        //确定要加载图片的区域
        mRect.left = 0;
        mRect.top = 0;
        mRect.right = mImageWidth;
        mRect.bottom = (int) (mImageHeight / mScale);
    }

    /**
     * 绘制图片
     *
     * @param canvas
     */
    Matrix mMatrix = new Matrix();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //拿到区域解码器解码一张图片
        if (null == mDecoder) {
            return;//说明用户没有设置图片
        }
        mOptions.inBitmap = bitmap;//复用图片
        bitmap = mDecoder.decodeRegion(mRect, mOptions);
        //matrix设置图片的缩放比
        mMatrix.setScale(mScale, mScale);
        //绘制
        canvas.drawBitmap(bitmap, mMatrix, null);
    }

    /**
     * 按下时,如果还在滑动,强制停止滑动
     *
     * @param e
     * @return
     */
    @Override
    public boolean onDown(MotionEvent e) {
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        return true;//返回true,继续接收后继事件
    }

    /**
     * 滑动事件
     *
     * @param e1        按下
     * @param e2        滑动
     * @param distanceX 滑动的x
     * @param distanceY 滑动的y
     * @return
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //上下滑动时,处理显示的区域
        mRect.offset(0, (int) distanceY);
        //处理滑动到顶端的情况
        if (mRect.top < 0) {
            mRect.top = 0;
            mRect.bottom = (int) (mViewHeight / mScale);
        }
        if (mRect.bottom > mImageHeight) {
            mRect.bottom = mImageHeight;
            mRect.top = mImageHeight - (int) (mViewHeight / mScale);
        }
        invalidate();
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //触摸事件交由手势处理
        return mDetector.onTouchEvent(event);
    }

    /**
     * 处理惯性问题
     *
     * @param e1
     * @param e2
     * @param velocityX
     * @param velocityY
     * @return
     */
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mScroller.fling(
                0, mRect.top,
                0, (int) -velocityY,
                0, 0,
                0, mImageHeight-(int) (mViewHeight / mScale)
        );
        return false;
    }

    /**
     * 使用上面计算的结果
     */
    @Override
    public void computeScroll() {
        if (mScroller.isFinished()) {
            return;
        }
        if (mScroller.computeScrollOffset()) {
            mRect.top = mScroller.getCurrY();
            mRect.bottom = mRect.top + (int) (mViewHeight / mScale);
            invalidate();
        }
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }
}
