package com.example.writingboard.view.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.Iterator;
import java.util.List;

public class WritingBoard extends View {


    private Paint paint; //画笔
    private Paint eraserRectPaint; //板擦画笔
    private Paint selectPaint; //圈选画笔
    private Paint markPaint; //标记画笔
    private Path path;
    private WritingStep curStep; //当前笔迹信息
    private Canvas canvas;
    private Bitmap bitmap;
    private Paint bitmapPaint;
    private float startX, startY;
    private WritingState writingState;
    private boolean isEraser = false;
    private boolean isSelect = false;
    private boolean isMulti = false;

    private EraserType eraserType = EraserType.TYPE_ALL;
    private Paint eraserPaint;

    private Path maskPath;
    private Paint maskPaint;
    private PointF selectStart;
    private PointF selectEnd;
    private Path rectArea;
    private Path intersectPath;

    private MultiWriting multiWriting;


    private Path selectRectPath; //圈选框的path
    private RectF selectRect; //圈选的外接矩形，方便判断触点

    private float curScaleFactor = 1;

    //标记被圈选的笔迹是否在操作中
    private boolean isOp = false;

    private boolean isRoam = false; //标记是否处于漫游功能

    private ScaleGestureDetector detector; //控制缩放

    public WritingBoard(Context context) {
        this(context, null);
    }

    public WritingBoard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WritingBoard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * 画笔初始化
     */
    private void init(Context context) {
        bitmapPaint = new Paint(Paint.DITHER_FLAG);
        writingState = WritingState.getInstance();
        path = new Path();
        update();
        //初始化板擦
        eraserRectPaint = new Paint();
        eraserRectPaint.setColor(Color.BLACK);
        eraserRectPaint.setAntiAlias(true);
        eraserRectPaint.setDither(true);
        eraserRectPaint.setStyle(Paint.Style.FILL);

        eraserPaint = new Paint();
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        eraserPaint.setStyle(Paint.Style.FILL);

        //初始化圈选画笔
        selectPaint = new Paint();
        selectPaint.setColor(Color.RED);
        selectPaint.setStrokeWidth(4);
        selectPaint.setAntiAlias(true);
        selectPaint.setDither(true);
        selectPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
        selectPaint.setStyle(Paint.Style.STROKE);

        //初始化标记画笔
        markPaint = new Paint();
        markPaint.setColor(Color.RED);
        markPaint.setStrokeWidth(10);
        markPaint.setAntiAlias(true);
        markPaint.setDither(true);
        markPaint.setStrokeJoin(Paint.Join.ROUND);
        markPaint.setStrokeCap(Paint.Cap.ROUND);
        markPaint.setStyle(Paint.Style.STROKE);

        multiWriting = new MultiWriting(); //初始化多点书写

        maskPaint = new Paint();
        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setColor(0x22000000);

        rectArea = new Path();
        intersectPath = new Path();


        //初始化detector
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                curScaleFactor = detector.getScaleFactor();
                if (Math.abs(detector.getPreviousSpan() - detector.getCurrentSpan()) < 5) {
                    curScaleFactor = 1;
                }
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {

            }
        });
    }

    public Paint getPaint() {
        return paint;
    }

    /**
     * 更新画笔
     */
    public void update() {
        paint = new Paint();
        paint.setColor(writingState.getPenColor());
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(writingState.getPenSize());
        paint.setStyle(Paint.Style.STROKE);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        detector.onTouchEvent(event);
        if (isMulti) {
            multiWriting.onTouchEvent(event, canvas, paint);
        } else {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (isSelect) {
                        clearCanvas();
                        repaintAll();
                        selectStart = new PointF(event.getX(), event.getY());
                        selectEnd = new PointF(selectStart.x, selectStart.y);
                        rectArea.reset();
                    }
                case MotionEvent.ACTION_POINTER_DOWN:
                    start(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isSelect) {
                        selectEnd.x = event.getX();
                        selectEnd.y = event.getY();
                    }
                    move(event);
                    break;
                case MotionEvent.ACTION_UP:
                    if (isSelect) {
                        selectEnd.x = event.getX();
                        selectEnd.y = event.getY();
                    }
                case MotionEvent.ACTION_POINTER_UP:
                    end(event);
                    break;
            }
        }
        invalidate();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (BitmapStep step : writingState.getBitmapSteps()) {
            canvas.drawBitmap(step.getBitmap(), step.getMatrix(), null);
        }
        canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
        if (isMulti) {
            multiWriting.draw(canvas, paint);
        } else {
            if (isEraser) {
                canvas.drawRect(startX - 50, startY - 50, startX + 50, startY + 50, eraserRectPaint);
            } else {
                if (path != null && !isRoam) {
                    if (!isSelect)
                        canvas.drawPath(path, paint);
                    else {

                        //canvas.drawPath(path, selectPaint);
                        rectArea.reset();
                        rectArea.addRect(selectStart.x, selectStart.y, selectEnd.x, selectEnd.y,
                                Path.Direction.CW);
                        intersectPath.reset();
                        intersectPath.addPath(maskPath);
                        intersectPath.op(rectArea, Path.Op.DIFFERENCE);
                        canvas.drawPath(intersectPath, maskPaint);
                        canvas.drawRect(selectStart.x, selectStart.y,
                                selectEnd.x, selectEnd.y, selectPaint);
                        //canvas.drawRect(selectStart.x, selectStart.y, selectEnd.x, selectEnd.y, selectPaint);
                    }
                }

            }
        }

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        maskPath = new Path();
        RectF maskRect = new RectF(0, 0, w, h);
        maskPath.addRect(maskRect, Path.Direction.CW);
    }

    private void start(MotionEvent event) {

        float x = event.getX();
        float y = event.getY();
        if (event.getSize() > 0.03 && !isSelect) {
            isEraser = true;
        }
        if (!isEraser) {

            if (isSelect) {
                if (selectRect != null && selectRect.contains(x, y)) {
                    isOp = true;
                } else {
                    isOp = false;
                    selectRect = new RectF();
                    selectRectPath = new Path();
                }
            } else {
                Log.d("手指按下的数量", event.getPointerCount() + "个");
                //如果不在圈选状态下，二指以上可触发漫游功能。
                if (event.getPointerCount() > 1) {
                    isRoam = true;
                }
            }

            if (!isOp) {
                path = new Path();
                path.moveTo(x, y);
                if (!isSelect) {
                    curStep = new WritingStep(path, paint);
                }
            }
        }

        startX = x;
        startY = y;
    }

    private void move(MotionEvent event) {

        float x = event.getX(0);
        float y = event.getY(0);
        if (isEraser) {
            switch (eraserType) {
                case TYPE_ALL:
                    eraseALine(x, y);
                    break;
                case TYPE_PART:
                    canvas.drawRect(x - 50, y - 50, x + 50, y + 50, eraserPaint);
                    erasePath(x, y);
                    break;
            }
        } else {
            if (Math.abs(x - startX) >= 4 || Math.abs(y - startY) >= 4) {
                if (isOp) moveSelectPath(x - startX, y - startY);
                else if (isRoam) {
                    transformCanvas(x - startX, y - startY);
                    curStep = null;
                }
                if (path != null) {
                    path.quadTo(startX, startY, (x + startX) / 2, (y + startY) / 2);
                }

            }
        }
        startX = x;
        startY = y;
    }

    /**
     * 移动和缩放画布
     *
     * @param dx 变换后的y坐标
     * @param dy 变换后的x坐标
     */
    private void transformCanvas(float dx, float dy) {
        clearCanvas();
        Log.d("curScaleFactor", + curScaleFactor + "");

        for (WritingStep step : writingState.getSteps()) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(dx, dy);
            matrix.postScale(curScaleFactor, curScaleFactor, detector.getFocusX(),
                    detector.getFocusY());
            step.updatePoints();
            step.getPath().transform(matrix);
        }

        for (BitmapStep step : writingState.getBitmapSteps()) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(dx, dy);
            matrix.postScale(curScaleFactor,
                    curScaleFactor, detector.getFocusX(), detector.getFocusY());
            step.getMatrix().postTranslate(dx, dy);
            step.getMatrix().postScale(curScaleFactor,
                    curScaleFactor, detector.getFocusX(), detector.getFocusY());
            step.getPath().transform(matrix);
        }
        repaintAll();
    }

    private void moveSelectPath(float dx, float dy) {
        clearCanvas();

        //笔迹转换
        for (WritingStep step : writingState.getSelectedSteps()) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(dx, dy);
            matrix.postScale(curScaleFactor, curScaleFactor, detector.getFocusX(),
                    detector.getFocusY());

            step.getPath().transform(matrix);
            markPaint.setStrokeWidth(step.getPaint().getStrokeWidth() + 10);
            canvas.drawPath(step.getPath(), markPaint);
        }

        //图片转换
        for (BitmapStep step : writingState.getSelectBitmap()) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(dx, dy);
            matrix.postScale(curScaleFactor, curScaleFactor, detector.getFocusX(),
                    detector.getFocusY());
            step.getPath().transform(matrix);

            step.getMatrix().postTranslate(dx, dy);
            step.getMatrix().postScale(curScaleFactor, curScaleFactor, detector.getFocusX(),
                    detector.getFocusY());

            markPaint.setStrokeWidth(10);
            canvas.drawPath(step.getPath(), markPaint);
        }


        //绘制圈选的最小外接矩形
        if (selectRectPath != null) {
            selectRect = new RectF();
            Matrix m = new Matrix();
            m.postTranslate(dx, dy);
            m.postScale(curScaleFactor, curScaleFactor, detector.getFocusX(),
                    detector.getFocusY());
            selectRectPath.transform(m);
            selectRectPath.computeBounds(selectRect, true);
            expandRect(selectRect);
            canvas.drawRect(selectRect, selectPaint);
        }

        repaintAll();
    }

    private void end(MotionEvent event) {
        if (!isEraser) {
            Log.d("选择和操作", isSelect ? "选择了" : "没选择");
            if (isSelect) {
                Log.d("选择和操作", isOp ? "操作中" : "未操作");
                if (event.getPointerId(event.getActionIndex()) == 0) {
                    if (!isOp) {
                        selectStep();
                    } else {
                        isOp = false;
                    }
                }
                writingState.updateSteps();
            } else {
                if (isRoam) {
                    isRoam = false;
                } else {
                    if (path != null && curStep != null) {
                        canvas.drawPath(path, paint);
                        curStep.setPaint(paint);
                        curStep.setPath(path);
                        writingState.getSteps().add(curStep);
                        curStep = null;
                    }
                }
            }
            path = null;
        } else {
            splitLine();
            isEraser = false;
        }

        initFlags();
    }

    /**
     * 初始化标志
     */
    private void initFlags() {
        curScaleFactor = 1;
    }

    /**
     * 擦除一条线
     *
     * @param x 触点x
     * @param y 触点y
     */
    private void eraseALine(float x, float y) {
        Iterator<WritingStep> it = writingState.getSteps().iterator();
        while (it.hasNext()) {
            WritingStep step = it.next();
            if (step.inArea(x, y)) {
                it.remove();
                writingState.getDeleteSteps().add(step);
                clearCanvas();
                repaintAll();
            }
        }
    }

    /**
     * 部分擦除
     * @param x 手指位置
     * @param y 手指位置
     */
    private void erasePath(float x, float y) {
        Iterator<WritingStep> it = writingState.getSteps().iterator();
        RectF rect = new RectF(x - 60, y - 60, x + 60, y + 60);
        while (it.hasNext()) {
            WritingStep step = it.next();
            for (PathPoint p : step.getPoints()) {
                if (rect.contains(p.x, p.y)) {
                    p.isRemove = true;
                    if (!writingState.getSplitSteps().contains(step)) {
                        writingState.getSplitSteps().add(step);
                    }
                }
            }
        }
    }

    /**
     * 分割笔迹
     */
    private void splitLine(){
        for (WritingStep step : writingState.getSplitSteps()) {
            boolean yes = writingState.getSteps().remove(step);
            Log.d("移除成功了吗", yes ? "Yes" : "No");
            PathMeasure measure = new PathMeasure(step.getPath(), false);
            List<PathPoint> points = step.getPoints();
            int ps = 0;
            int pe = 0;
            while (pe < points.size()) {
                if ((points.get(pe).isRemove || pe == points.size() - 1) && pe - ps > 2) {
                    Path temp = new Path();
                    measure.getSegment(ps * WritingStep.DENSITY,
                            pe * WritingStep.DENSITY, temp, true);
                    WritingStep newStep = new WritingStep(temp, step.getPaint());
                    if (newStep.updatePoints())
                        writingState.getSteps().add(newStep);
                    ps = pe;
                    while (ps < points.size()) {
                        if (!points.get(ps).isRemove) {
                            break;
                        }
                        ps++;
                    }
                    pe = ps;
                }
                pe++;
            }
        }
        writingState.getSplitSteps().clear();
        clearCanvas();
        repaintAll();
    }

    /**
     * 圈选笔迹
     */
    private void selectStep() {

        Path selectPath = new Path();

        clearCanvas();
        writingState.getSelectedSteps().clear();
        writingState.getSelectBitmap().clear();

        for (WritingStep step : writingState.getSteps()) {
            Path temp = new Path();
            temp.addPath(rectArea);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                temp.op(step.getPath(), Path.Op.INTERSECT);
                if (!temp.isEmpty()) {
                    selectPath.addPath(step.getPath());
                    writingState.getSelectedSteps().add(step);
                    markPaint.setStrokeWidth(step.getPaint().getStrokeWidth() + 10);
                    canvas.drawPath(step.getPath(), markPaint);
                }
            }
        }

        for (BitmapStep step : writingState.getBitmapSteps()) {
            Path temp = new Path();
            temp.addPath(rectArea);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                temp.op(step.getPath(), Path.Op.INTERSECT);
                if (!temp.isEmpty()) {
                    selectPath.addPath(step.getPath());
                    writingState.getSelectBitmap().add(step);
                    markPaint.setStrokeWidth(10);
                    canvas.drawPath(step.getPath(), markPaint);
                }
            }
        }

        if (!selectPath.isEmpty()) {
            //计算圈选path总边界
            selectRect = new RectF();
            selectPath.computeBounds(selectRect, true);
            selectRectPath = new Path();
            selectRectPath.addRect(selectRect, Path.Direction.CCW);
            expandRect(selectRect);
            canvas.drawRect(selectRect, selectPaint);
        }
        repaintAll();
    }

    private void expandRect(RectF r) {
        int m = 0;
        for (WritingStep step: writingState.getSelectedSteps()){
            if (m < step.getPaint().getStrokeWidth()) {
                m = (int) step.getPaint().getStrokeWidth();
            }
        }

        m += 10;
        r.left -= m;
        r.right += m;
        r.top -= m;
        r.bottom += m;
    }

    /**
     * 清除画布
     */
    private void clearCanvas() {
        Paint p = new Paint();
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPaint(p);
    }

    /**
     * 重绘所有笔迹
     */
    private void repaintAll() {
        Log.d("现存的笔迹数量", writingState.getSteps().size() + "");
        for (WritingStep step : writingState.getSteps()) {
            canvas.drawPath(step.getPath(), step.getPaint());
        }
        invalidate();
    }

    /**
     * 判断是否在圈选状态
     *
     * @return select
     */
    public boolean isSelect() {
        return isSelect;
    }

    public void setSelect(boolean select) {
        isSelect = select;
    }

    /**
     * 退出圈选状态
     */
    public void exitSelect() {
        clearCanvas();
        repaintAll();
        selectRect = null;
        selectRectPath = null;
    }

    /**
     * 添加图片
     * @param bitmap 图片
     */
    public void addBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            writingState.getBitmapSteps().add(new BitmapStep(bitmap, new Matrix()));
        }
        clearCanvas();
        repaintAll();
    }

    public boolean isMulti() {
        return isMulti;
    }

    public void setMulti(boolean multi) {
        isMulti = multi;
    }

    public enum EraserType{
        TYPE_ALL,
        TYPE_PART
    }

    public EraserType getEraserType() {
        return eraserType;
    }

    public void setEraserType(EraserType eraserType) {
        this.eraserType = eraserType;
    }
}

