package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.List;

public class PoseOverlayView extends View {

    private Paint landmarkPaint;
    private Paint skeletonPaint;
    private Pose currentPose;

    // Reference to PreviewView to get scaling factors
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    public PoseOverlayView(Context context) {
        super(context);
        init();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PoseOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paint for landmarks
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.GREEN);
        landmarkPaint.setStrokeWidth(8f);
        landmarkPaint.setStyle(Paint.Style.FILL);

        // Initialize paint for skeleton connections
        skeletonPaint = new Paint();
        skeletonPaint.setColor(Color.RED);
        skeletonPaint.setStrokeWidth(4f);
        skeletonPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Updates the current pose and triggers a redraw of the view.
     *
     * @param pose The detected pose to be rendered.
     */
    public void setPose(Pose pose) {
        this.currentPose = pose;
        invalidate(); // Triggers onDraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentPose == null) {
            return;
        }

        List<PoseLandmark> landmarks = currentPose.getAllPoseLandmarks();

        // Calculate scaling factors based on view size and image size
        // Assuming image size is 1280x720 as set in ImageAnalysis
        scaleX = (float) getWidth() / 1280f;
        scaleY = (float) getHeight() / 720f;

        // Draw landmarks
        for (PoseLandmark landmark : landmarks) {
            if (landmark == null) continue;
            float x = landmark.getPosition().x * scaleX;
            float y = landmark.getPosition().y * scaleY;
            canvas.drawCircle(x, y, 8f, landmarkPaint);
        }

        // Draw skeleton connections
        drawSkeleton(canvas, currentPose);
    }

    /**
     * Draws lines between specific pose landmarks to represent the skeleton.
     *
     * @param canvas The canvas on which to draw.
     * @param pose   The pose containing landmarks.
     */
    private void drawSkeleton(Canvas canvas, Pose pose) {
        // Define pairs of landmarks to connect
        int[][] skeletonPairs = {
                {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER},
                {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW},
                {PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST},
                {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW},
                {PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST},
                {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP},
                {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP},
                {PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP},
                {PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE},
                {PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE},
                {PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE},
                {PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE},
                {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_HIP},
                {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_HIP}
        };

        for (int[] pair : skeletonPairs) {
            PoseLandmark first = pose.getPoseLandmark(pair[0]);
            PoseLandmark second = pose.getPoseLandmark(pair[1]);
            if (first != null && second != null) {
                float startX = first.getPosition().x * scaleX;
                float startY = first.getPosition().y * scaleY;
                float endX = second.getPosition().x * scaleX;
                float endY = second.getPosition().y * scaleY;
                canvas.drawLine(startX, startY, endX, endY, skeletonPaint);
            }
        }
    }
}
