package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class BitmapUtils {

    private static final String TAG = "BitmapUtils";

    /**
     * Converts YUV420 (NV21) Image to a mutable Bitmap.
     *
     * @param image The YUV420 image.
     * @return Mutable Bitmap representation of the image, or null if conversion fails.
     */
    public static Bitmap yuv420ToBitmap(android.media.Image image) {
        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean success = yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
            if (!success) {
                Log.e(TAG, "Failed to compress YUV image to JPEG");
                return null;
            }
            byte[] imageBytes = out.toByteArray();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true; // Make the bitmap mutable
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
            if (bitmap == null) {
                Log.e(TAG, "BitmapFactory failed to decode byte array");
                return null;
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV420 to Bitmap", e);
            return null;
        }
    }

    /**
     * Converts a Bitmap to YUV420 (NV21) byte array.
     *
     * @param bitmap The Bitmap to convert.
     * @param width  Bitmap width.
     * @param height Bitmap height.
     * @return YUV420 byte array, or null if conversion fails.
     */
    public static byte[] bitmapToYuv420(Bitmap bitmap, int width, int height) {
        try {
            // Validate dimensions
            if (width > bitmap.getWidth() || height > bitmap.getHeight()) {
                Log.e(TAG, "Requested width and/or height exceed bitmap dimensions");
                return null;
            }

            int frameSize = width * height;
            byte[] yuv = new byte[frameSize * 3 / 2];

            int[] argb = new int[width * height];
            bitmap.getPixels(argb, 0, width, 0, 0, width, height);

            // Y plane
            int yIndex = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = argb[y * width + x];
                    int r = (color >> 16) & 0xff;
                    int g = (color >> 8) & 0xff;
                    int b = color & 0xff;

                    // Calculate Y component
                    yuv[yIndex++] = (byte) ((66 * r + 129 * g + 25 * b + 128) >> 8);
                }
            }

            // U and V planes (interleaved as NV12)
            int uvIndex = frameSize;
            for (int y = 0; y < height; y += 2) {
                for (int x = 0; x < width; x += 2) {
                    int sumR = 0, sumG = 0, sumB = 0;
                    int count = 0;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            if (y + dy < height && x + dx < width) {
                                int color = argb[(y + dy) * width + (x + dx)];
                                sumR += (color >> 16) & 0xff;
                                sumG += (color >> 8) & 0xff;
                                sumB += color & 0xff;
                                count++;
                            }
                        }
                    }
                    int avgR = sumR / count;
                    int avgG = sumG / count;
                    int avgB = sumB / count;

                    // Calculate U and V components
                    byte u = (byte) ((-38 * avgR - 74 * avgG + 112 * avgB + 128) >> 8);
                    byte v = (byte) ((112 * avgR - 94 * avgG - 18 * avgB + 128) >> 8);

                    yuv[uvIndex++] = u;
                    yuv[uvIndex++] = v;
                }
            }

            return yuv;
        } catch (Exception e) {
            Log.e(TAG, "Error converting Bitmap to YUV420", e);
            return null;
        }
    }
}
