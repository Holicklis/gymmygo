package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.List;

public class Display extends View {

    private Rect srcRect;
    private Rect disRect;

    private Bitmap bitmap;
    private Pose pose;
    private Paint paint;

    public Display(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(8f);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        disRect = new Rect(0, 0, getWidth(), getHeight());

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, srcRect, disRect, null);

            // Draw pose landmarks
            if (pose != null) {
                List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
                if (landmarks != null && !landmarks.isEmpty()) {
                    float scaleX = (float) disRect.width() / srcRect.width();
                    float scaleY = (float) disRect.height() / srcRect.height();

                    for (PoseLandmark landmark : landmarks) {
                        float x = landmark.getPosition().x * scaleX;
                        float y = landmark.getPosition().y * scaleY;
                        canvas.drawCircle(x, y, 10f, paint);
                    }
                }
            }
        }
    }
}

