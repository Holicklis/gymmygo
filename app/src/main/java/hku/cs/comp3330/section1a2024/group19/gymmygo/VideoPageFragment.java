package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.*;
import android.widget.Button;
import android.widget.Toast;
import android.hardware.camera2.params.StreamConfigurationMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;


import com.google.mlkit.vision.pose.*;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.google.mlkit.vision.common.InputImage;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VideoPageFragment extends Fragment {

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private TextureView textureView;
    private Button recordButton;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private Size videoSize;
    private String cameraId;

    private PoseDetector poseDetector;
    Display displayOverlay;

    private ActivityResultLauncher<String[]> permissionLauncher;
    ArrayList<Pose> poseArrayList = new ArrayList<>();
    ArrayList<Bitmap> bitmapArrayList = new ArrayList<>();
    ArrayList<Bitmap> bitmap4DisplayArrayList = new ArrayList<>();
    Canvas canvas;
    Paint mPaint = new Paint();



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize pose detector
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setStrokeWidth(10);

        // Initialize the permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    boolean shouldShowRationale = false;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        String permission = entry.getKey();
                        Boolean granted = entry.getValue();
                        allGranted &= granted;
                        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                            shouldShowRationale = true;
                        }
                    }
                    if (allGranted) {
                        startCamera();
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_page, container, false);

        textureView = view.findViewById(R.id.texture_view);
        recordButton = view.findViewById(R.id.btn_record);
        displayOverlay = view.findViewById(R.id.displayOverlay);

        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecordingVideo();
                recordButton.setText("Record");
            } else {
                startRecordingVideo();
                recordButton.setText("Stop");
            }
        });

        textureView.setSurfaceTextureListener(textureListener);

        return view;
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                permissionLauncher.launch(REQUIRED_PERMISSIONS);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            // Handle texture size change if needed
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            // Analyze frame for pose detection
            analyzeFrame();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                permissionLauncher.launch(REQUIRED_PERMISSIONS);
            }
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        CameraManager manager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // Skip front-facing cameras
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size[] availableSizes = map.getOutputSizes(SurfaceTexture.class);
                videoSize = chooseVideoSize(availableSizes);

                this.cameraId = cameraId;
                break;
            }

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            if (isRecording) {
                try {
                    startRecordingSession();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                startPreviewSession();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void startPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            final CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(
                    Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(previewBuilder.build(), captureCallback, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getContext(), "Failed to start camera preview.", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecordingSession() throws IOException {
        setupMediaRecorder();

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        assert surfaceTexture != null;
        surfaceTexture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        Surface recorderSurface = mediaRecorder.getSurface();

        try {
            final CaptureRequest.Builder recordingBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordingBuilder.addTarget(previewSurface);
            recordingBuilder.addTarget(recorderSurface);

            List<Surface> surfaces = Arrays.asList(previewSurface, recorderSurface);

            cameraDevice.createCaptureSession(
                    surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureSession.setRepeatingRequest(recordingBuilder.build(), captureCallback, null);
                                mediaRecorder.start();
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getContext(), "Failed to start recording session.", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() throws IOException {
        mediaRecorder = new MediaRecorder();

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(getVideoFilePath());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mediaRecorder.prepare();
    }

    private String getVideoFilePath() {
        final File dir = requireActivity().getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/")) + System.currentTimeMillis() + ".mp4";
    }

    private void startRecordingVideo() {
        if (cameraDevice == null || !textureView.isAvailable() || videoSize == null) {
            return;
        }
        isRecording = true;
        startCamera();
    }

    private void stopRecordingVideo() {
        isRecording = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        startCamera();
    }

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        // Implement capture callback methods if needed
    };

    private void processImage(InputImage image) {
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    // Save the pose into the poseArrayList
                    poseArrayList.add(pose);

                    // Get the bitmap from the InputImage
                    Bitmap bitmap = image.getBitmapInternal();
                    if (bitmap != null) {
                        // Save the bitmap into bitmapArrayList
                        bitmapArrayList.add(bitmap);
                    }

                    // Now that we have the pose and bitmap, we can draw the pose
                    drawPoseOnBitmap(pose, bitmap);
                })
                .addOnFailureListener(Throwable::printStackTrace);
    }

    private void drawPoseOnBitmap(Pose pose, Bitmap bitmap) {
        // Create a mutable copy of the bitmap to draw on
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        // Set up paint for drawing
        Paint paint = new Paint();
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(8f);
        paint.setStyle(Paint.Style.FILL);

        // Extract landmarks from the pose
        List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
        for (PoseLandmark landmark : landmarks) {
            float x = landmark.getPosition().x;
            float y = landmark.getPosition().y;
            canvas.drawCircle(x, y, 10f, paint);
        }

        // Update your Display class with the new bitmap
        requireActivity().runOnUiThread(() -> {
            // Pass the bitmap to the Display class
            displayOverlay.setBitmap(mutableBitmap);
        });
    }

    private int getRotationDegrees() {
        int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private void analyzeFrame() {
        // Capture frame from TextureView and process it
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap != null) {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            processImage(image);
        }
    }

    private Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 16 / 9 && size.getWidth() <= 1920) {
                return size;
            }
        }
        return choices[0];
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permissions Required")
                .setMessage("Please enable permissions in app settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
