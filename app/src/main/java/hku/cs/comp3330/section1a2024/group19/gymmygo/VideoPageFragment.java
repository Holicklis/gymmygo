package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Recorder;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.camera.video.Recording;

public class VideoPageFragment extends Fragment {

    private static final String TAG = "VideoPageFragment";

    private PreviewView previewView;
    private SurfaceView overlayView;
    private VideoView videoView;
    private Button btnRecord, btnStop, btnSave;
    private boolean isRecording = false;
    private Uri videoUri;

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    private ExecutorService cameraExecutor;

    // Pose Detector
    private PoseDetector poseDetector;

    // CameraX
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis imageAnalysis;

    // VideoCapture
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;

    public VideoPageFragment() {
        // Required empty public constructor
    }

    public static VideoPageFragment newInstance() {
        return new VideoPageFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize CameraX executor
        cameraExecutor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "Camera executor initialized");

        // Initialize Pose Detector with STREAM_MODE
        PoseDetectorOptions options =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                        .build();
        poseDetector = PoseDetection.getClient(options);
        Log.d(TAG, "Pose detector initialized with STREAM_MODE");
        Toast.makeText(getContext(), "Pose detector initialized", Toast.LENGTH_SHORT).show();

        // Initialize the permissions launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        Log.d(TAG, "All permissions granted");
                        // Camera setup will be initiated in onViewCreated()
                    } else {
                        Log.w(TAG, "Permissions not granted");
                        Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_LONG).show();
                        // Optionally, disable functionality or close the fragment
                    }
                }
        );

        // Request permissions if not already granted
        if (!hasPermissions()) {
            Log.d(TAG, "Requesting permissions");
            requestPermissions();
        } else {
            Log.d(TAG, "All permissions already granted");
            // Camera setup will be initiated in onViewCreated()
        }
    }

    private boolean hasPermissions() {
        boolean hasCamera = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasAudio = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean hasWriteStorage = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadStorage = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Permissions - CAMERA: " + hasCamera + ", RECORD_AUDIO: " + hasAudio +
                ", WRITE_EXTERNAL_STORAGE: " + hasWriteStorage + ", READ_EXTERNAL_STORAGE: " + hasReadStorage);

        return hasCamera && hasAudio && hasWriteStorage && hasReadStorage;
    }

    private void requestPermissions() {
        Log.d(TAG, "Launching permission request");
        requestPermissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            Log.d(TAG, "Camera executor shut down");
        }
        if (poseDetector != null) {
            poseDetector.close();
            Log.d(TAG, "Pose detector closed");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View rootView = inflater.inflate(R.layout.fragment_video_page, container, false);
        Log.d(TAG, "Fragment view created");

        // Initialize views
        previewView = rootView.findViewById(R.id.previewView);
        overlayView = rootView.findViewById(R.id.overlayView);
        videoView = rootView.findViewById(R.id.video_view);
        btnRecord = rootView.findViewById(R.id.btn_record);
        btnStop = rootView.findViewById(R.id.btn_stop);
        btnSave = rootView.findViewById(R.id.btn_save);

        Log.d(TAG, "UI components initialized");

        // Configure overlayView for transparency and Z-order
        overlayView.setZOrderOnTop(true);
        overlayView.getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        Log.d(TAG, "OverlayView configured for transparency and Z-order");

        // Initialize VideoCapture Use Case
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD)) // Changed to Quality.HIGH for compatibility
                .build();

        videoCapture = VideoCapture.withOutput(recorder);
        Log.d(TAG, "VideoCapture initialized with Quality.HIGH");

        // Set up buttons
        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                Log.d(TAG, "Record button clicked");
                startRecording();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (isRecording) {
                Log.d(TAG, "Stop button clicked");
                stopRecording();
            }
        });

        btnSave.setOnClickListener(v -> {
            if (videoUri != null) {
                Log.d(TAG, "Save button clicked");
                saveVideo();
            } else {
                Log.w(TAG, "Save button clicked but videoUri is null");
                Toast.makeText(getActivity(), "No video to save", Toast.LENGTH_SHORT).show();
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "View created. Initiating camera setup if permissions are granted.");
        if (hasPermissions()) {
            startCamera();
        }
    }

    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try{
                cameraProvider = cameraProviderFuture.get();
                Log.d(TAG, "CameraProvider obtained");

                bindCameraUseCases();

            } catch (ExecutionException | InterruptedException e){
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(getContext(), "Error starting camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    private void bindCameraUseCases() {
        Log.d(TAG, "Binding camera use cases");

        // Preview
        preview = new Preview.Builder()
                .build();
        Log.d(TAG, "Preview use case built");

        // Select back camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        Log.d(TAG, "CameraSelector built");

        // ImageAnalysis for Pose Detection
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        Log.d(TAG, "ImageAnalysis use case built");

        // Set analyzer for continuous frame processing
        imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);
        Log.d(TAG, "ImageAnalysis analyzer set");

        // Unbind all use cases before rebinding
        cameraProvider.unbindAll();
        Log.d(TAG, "All use cases unbound");

        // Bind use cases to lifecycle
        try {
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis, videoCapture);
            Log.d(TAG, "Camera use cases (Preview, ImageAnalysis, VideoCapture) bound to lifecycle");

            // Connect the preview use case to the PreviewView
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            Log.d(TAG, "Preview set to PreviewView");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(getContext(), "Failed to bind camera use cases: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
//    private void bindCameraUseCases(){
//        Log.d(TAG, "Binding camera use cases");
//
//        // Preview
//        preview = new Preview.Builder()
//                .build();
//        Log.d(TAG, "Preview use case built");
//
//        // Select back camera
//        CameraSelector cameraSelector = new CameraSelector.Builder()
//                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                .build();
//        Log.d(TAG, "CameraSelector built");
//
//        // ImageAnalysis for Pose Detection
//        imageAnalysis = new ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build();
//        Log.d(TAG, "ImageAnalysis use case built");
//
//        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
//            processImageProxy(imageProxy);
//        });
//        Log.d(TAG, "ImageAnalysis analyzer set");
//
//        // Unbind all use cases before rebinding
//        cameraProvider.unbindAll();
//        Log.d(TAG, "All use cases unbound");
//
//        // Bind use cases to lifecycle
//        try {
//            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis, videoCapture);
//            Log.d(TAG, "Camera use cases (Preview, ImageAnalysis, VideoCapture) bound to lifecycle");
//
//            // Connect the preview use case to the PreviewView
//            preview.setSurfaceProvider(previewView.getSurfaceProvider());
//            Log.d(TAG, "Preview set to PreviewView");
//        } catch (Exception e) {
//            Log.e(TAG, "Use case binding failed", e);
//            Toast.makeText(getContext(), "Failed to bind camera use cases: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
//    }
@OptIn(markerClass = ExperimentalGetImage.class)
private void processImageProxy(ImageProxy imageProxy) {
    @androidx.camera.core.ExperimentalGetImage
    android.media.Image mediaImage = imageProxy.getImage();
    if (mediaImage != null) {
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        // Process the image with pose detection
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    // Draw pose landmarks on overlay
                    drawPose(pose);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Pose detection failed", e);
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close(); // Close the proxy when done
                    Log.d(TAG, "ImageProxy closed");
                });
    } else {
        Log.w(TAG, "mediaImage is null");
        imageProxy.close();
    }
}

    private void drawPose(Pose pose) {
        if (overlayView == null) {
            Log.w(TAG, "overlayView is null");
            return;
        }

        Canvas canvas = overlayView.getHolder().lockCanvas();
        if (canvas == null) {
            Log.w(TAG, "Canvas is null");
            return;
        }

        try {
            // Clear the canvas
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

            Paint paint = new Paint();
            paint.setColor(Color.GREEN);
            paint.setStrokeWidth(8);
            paint.setStyle(Paint.Style.STROKE);

            // Scale landmarks for drawing
            float scaleX = (float) overlayView.getWidth() / (float) previewView.getWidth();
            float scaleY = (float) overlayView.getHeight() / (float) previewView.getHeight();

            // Draw each landmark
            for (PoseLandmark landmark : pose.getAllPoseLandmarks()) {
                if (landmark == null) continue;
                float x = landmark.getPosition().x * scaleX;
                float y = landmark.getPosition().y * scaleY;
                canvas.drawCircle(x, y, 8, paint);
            }

            // Draw skeleton connections (as in previous code)
        } finally {
            overlayView.getHolder().unlockCanvasAndPost(canvas);
        }
    }
//    @OptIn(markerClass = ExperimentalGetImage.class)
//    private void processImageProxy(ImageProxy imageProxy){
//        @androidx.camera.core.ExperimentalGetImage
//        android.media.Image mediaImage = imageProxy.getImage();
//        if (mediaImage != null){
//            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
//
//            poseDetector.process(image)
//                    .addOnSuccessListener(pose -> {
//                        drawPose(pose);
//                    })
//                    .addOnFailureListener(e -> {
//                        Log.e(TAG, "Pose detection failed", e);
//                    })
//                    .addOnCompleteListener(task -> {
//                        imageProxy.close();
//                        Log.d(TAG, "ImageProxy closed");
//                    });
//        } else {
//            Log.w(TAG, "mediaImage is null");
//            imageProxy.close();
//        }
//    }
//
//    private void drawPose(Pose pose){
//        if (overlayView == null){
//            Log.w(TAG, "overlayView is null");
//            return;
//        }
//
//        Canvas canvas = overlayView.getHolder().lockCanvas();
//        if (canvas == null){
//            Log.w(TAG, "Canvas is null");
//            return;
//        }
//
//        try {
//            // Clear the canvas
//            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
//            Log.d(TAG, "Canvas cleared");
//
//            Paint paint = new Paint();
//            paint.setColor(Color.GREEN);
//            paint.setStrokeWidth(8);
//            paint.setStyle(Paint.Style.STROKE);
//
//            // Calculate scaling factors
//            float scaleX = (float) overlayView.getWidth() / (float) previewView.getWidth();
//            float scaleY = (float) overlayView.getHeight() / (float) previewView.getHeight();
//
//            // Draw landmarks with scaling
//            for (PoseLandmark landmark : pose.getAllPoseLandmarks()){
//                if (landmark == null){
//                    continue;
//                }
//                float x = landmark.getPosition().x * scaleX;
//                float y = landmark.getPosition().y * scaleY;
//                canvas.drawCircle(x, y, 8, paint);
//
//                // Log landmark positions
//                Log.d(TAG, "Landmark: " + landmark.getLandmarkType() + " Position: (" + x + ", " + y + ")");
//            }
//
//            // Define pairs of landmarks to connect for drawing the skeleton
//            int[][] skeletonPairs = {
//                    {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER},
//                    {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW},
//                    {PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST},
//                    {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW},
//                    {PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST},
//                    {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP},
//                    {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP},
//                    {PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP},
//                    {PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE},
//                    {PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE},
//                    {PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE},
//                    {PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE},
//                    {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_HIP},
//                    {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_HIP}
//            };
//
//            // Draw skeleton lines with scaling
//            for (int[] pair : skeletonPairs){
//                PoseLandmark first = pose.getPoseLandmark(pair[0]);
//                PoseLandmark second = pose.getPoseLandmark(pair[1]);
//                if (first != null && second != null){
//                    float startX = first.getPosition().x * scaleX;
//                    float startY = first.getPosition().y * scaleY;
//                    float endX = second.getPosition().x * scaleX;
//                    float endY = second.getPosition().y * scaleY;
//
//                    canvas.drawLine(startX, startY, endX, endY, paint);
//
//                    // Log skeleton line drawing
//                    Log.d(TAG, "Drawing line between " + first.getLandmarkType() + " and " + second.getLandmarkType());
//                }
//            }
//
//            Log.d(TAG, "Pose drawn successfully");
//        } finally {
//            // Ensure the canvas is always unlocked and posted
//            overlayView.getHolder().unlockCanvasAndPost(canvas);
//            Log.d(TAG, "Canvas unlocked and posted");
//        }
//    }

    private void startRecording(){
        if (isRecording) {
            Log.w(TAG, "Attempted to start recording while already recording");
            Toast.makeText(getActivity(), "Already recording", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting recording process");

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "video_" + System.currentTimeMillis() + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GymmyGoVideos");

        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        Log.d(TAG, "Prepared MediaStoreOutputOptions: " + outputOptions.toString());

        // Start recording
        recording = videoCapture.getOutput()
                .prepareRecording(requireContext(), outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(requireContext()), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        Log.d(TAG, "Recording started");
                        getActivity().runOnUiThread(() -> {
                            btnRecord.setVisibility(View.GONE);
                            btnStop.setVisibility(View.VISIBLE);
                            btnSave.setVisibility(View.GONE);
                            Toast.makeText(getActivity(), "Recording started", Toast.LENGTH_SHORT).show();
                        });
                        isRecording = true;
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) videoRecordEvent;
                        if (!finalize.hasError()) {
                            videoUri = finalize.getOutputResults().getOutputUri();
                            Log.d(TAG, "Recording completed: " + videoUri);

                            getActivity().runOnUiThread(() -> {
                                btnStop.setVisibility(View.GONE);
                                btnSave.setVisibility(View.VISIBLE);
                                btnRecord.setVisibility(View.VISIBLE);
                                Toast.makeText(getActivity(), "Recording stopped", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            // Handle error
                            Log.e(TAG, "Recording failed: " + finalize.getError());
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getActivity(), "Recording failed", Toast.LENGTH_SHORT).show();
                            });
                        }
                        isRecording = false;
                        recording = null;
                    }
                });

        Log.d(TAG, "Recording has been initiated");
    }

    private void stopRecording(){
        if (!isRecording) {
            Log.w(TAG, "Attempted to stop recording while not recording");
            Toast.makeText(getActivity(), "Not recording", Toast.LENGTH_SHORT).show();
            return;
        }
        if (recording != null) {
            Log.d(TAG, "Stopping recording");
            recording.stop();
            // `isRecording` will be set to false in the Finalize callback
        }
    }

    private void saveVideo(){
        if (videoUri == null){
            Log.w(TAG, "No video URI found to save");
            Toast.makeText(getActivity(), "No video to save", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Saving video to VideoView: " + videoUri.toString());

        // Play the video in VideoView
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(videoUri);
        videoView.start();

        // Hide VideoView after playing
        videoView.setOnCompletionListener(mp -> {
            videoView.setVisibility(View.GONE); // Hide the VideoView after playback
            previewView.setVisibility(View.VISIBLE); // Show the camera preview again
            Log.d(TAG, "Video playback completed, returning to camera preview");
        });

        Toast.makeText(getActivity(), "Video saved and playing", Toast.LENGTH_SHORT).show();

        btnSave.setVisibility(View.GONE);
        btnRecord.setVisibility(View.VISIBLE);
    }


}
