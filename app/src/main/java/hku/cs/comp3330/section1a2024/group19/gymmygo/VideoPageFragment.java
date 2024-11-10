package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
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
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPageFragment extends Fragment {

    private static final String TAG = "VideoPageFragment";

    private PreviewView previewView;
    private PoseOverlayView poseOverlayView;
    private VideoView videoView;
    private Button btnRecord, btnStop, btnSave;
    private boolean isRecording = false;

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

    // Frame Throttling
    private long lastAnalysisTimestamp = 0;
    private static final long ANALYSIS_INTERVAL_MS = 200; // Process every 200ms

    // Media Encoder
    private MediaEncoder mediaEncoder;

    // Video Output Path
    private String videoOutputPath;

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
                        // Initiate camera setup
                        startCamera();
                    } else {
                        Log.w(TAG, "Permissions not granted");
                        Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_LONG).show();
                        // Disable camera-related functionalities
                        disableCameraFunctions();
                    }
                }
        );

        // Request permissions if not already granted
        if (!hasPermissions()) {
            Log.d(TAG, "Requesting permissions");
            requestPermissions();
        } else {
            Log.d(TAG, "All permissions already granted");
            // Initiate camera setup
            startCamera();
        }
    }

    private boolean hasPermissions() {
        boolean hasCamera = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasAudio = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Permissions - CAMERA: " + hasCamera + ", RECORD_AUDIO: " + hasAudio);

        return hasCamera && hasAudio;
    }

    private void requestPermissions() {
        Log.d(TAG, "Launching permission request");
        requestPermissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        });
    }

    private void disableCameraFunctions() {
        if (getView() != null) {
            btnRecord = getView().findViewById(R.id.btn_record);
            btnStop = getView().findViewById(R.id.btn_stop);
            btnSave = getView().findViewById(R.id.btn_save);
            btnRecord.setEnabled(false);
            btnStop.setEnabled(false);
            btnSave.setEnabled(false);
        }
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
        if (mediaEncoder != null) {
            mediaEncoder.finish();
            Log.d(TAG, "MediaEncoder finished");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View rootView = inflater.inflate(R.layout.fragment_video_page, container, false);
        Log.d(TAG, "Fragment view created");

        // Initialize views
        previewView = rootView.findViewById(R.id.previewView);
        poseOverlayView = rootView.findViewById(R.id.poseOverlayView);
        videoView = rootView.findViewById(R.id.video_view);
        btnRecord = rootView.findViewById(R.id.btn_record);
        btnStop = rootView.findViewById(R.id.btn_stop);
        btnSave = rootView.findViewById(R.id.btn_save);

        Log.d(TAG, "UI components initialized");

        // Initialize VideoCapture Use Case with adjusted QualitySelector
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();

        videoCapture = VideoCapture.withOutput(recorder);
        Log.d(TAG, "VideoCapture initialized with Quality.HD");

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
            if (videoOutputPath != null) {
                Log.d(TAG, "Save button clicked");
                playVideo();
            } else {
                Log.w(TAG, "Save button clicked but videoOutputPath is null");
                Toast.makeText(getActivity(), "No video to save", Toast.LENGTH_SHORT).show();
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "View created. Camera setup initiated if permissions are granted.");
        // Camera setup is already initiated in onCreate based on permissions
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

        // Preview Use Case with SD Resolution
        preview = new Preview.Builder()
                .setTargetResolution(new android.util.Size(1280, 720)) // Set to SD resolution
                .build();
        Log.d(TAG, "Preview use case built with resolution 1280x720");

        // Select back camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        Log.d(TAG, "CameraSelector built");

        // ImageAnalysis Use Case with SD Resolution
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new android.util.Size(1280, 720)) // Match Preview resolution
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        Log.d(TAG, "ImageAnalysis use case built with resolution 1280x720");

        // Set analyzer for continuous frame processing with throttling
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

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp - lastAnalysisTimestamp < ANALYSIS_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        lastAnalysisTimestamp = currentTimestamp;

        @androidx.camera.core.ExperimentalGetImage
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // Process the image with pose detection
            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        // Pass the detected pose to the overlay view
                        poseOverlayView.setPose(pose);

                        // If recording, encode the frame with overlay
                        if (isRecording && mediaEncoder != null) {
                            // Convert ImageProxy to Bitmap
                            Bitmap bitmap = BitmapUtils.yuv420ToBitmap(mediaImage);
                            if (bitmap == null) {
                                Log.e(TAG, "Bitmap conversion failed");
                                return;
                            }

                            // Draw pose on Bitmap
                            Canvas canvas = new Canvas(bitmap);
                            poseOverlayView.draw(canvas);

                            // Convert the Bitmap back to YUV420 byte array
                            int bitmapWidth = bitmap.getWidth();
                            int bitmapHeight = bitmap.getHeight();
                            Log.d(TAG, "Bitmap dimensions: width=" + bitmapWidth + ", height=" + bitmapHeight);

                            byte[] yuvData = BitmapUtils.bitmapToYuv420(bitmap, bitmapWidth, bitmapHeight);
                            if (yuvData == null) {
                                Log.e(TAG, "Failed to convert Bitmap to YUV420");
                                return;
                            }

                            // Encode the YUV420 frame into video
                            mediaEncoder.encodeFrame(yuvData);
                        }
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

    private void startRecording(){
        if (isRecording) {
            Log.w(TAG, "Attempted to start recording while already recording");
            Toast.makeText(getActivity(), "Already recording", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting recording process");

        // Prepare output directory
        String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                + "/GymmyGoVideos";
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            boolean dirCreated = directory.mkdirs();
            if (!dirCreated) {
                Log.e(TAG, "Failed to create directory: " + directoryPath);
                Toast.makeText(getActivity(), "Failed to create directory for videos", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Prepare output path
        videoOutputPath = directoryPath + "/video_" + System.currentTimeMillis() + ".mp4";

        mediaEncoder = new MediaEncoder(videoOutputPath);

        isRecording = true;
        Log.d(TAG, "Recording has been initiated");

        Toast.makeText(getActivity(), "Recording started", Toast.LENGTH_SHORT).show();

        // Update button visibility
        btnRecord.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
    }

    private void stopRecording(){
        if (!isRecording) {
            Log.w(TAG, "Attempted to stop recording while not recording");
            Toast.makeText(getActivity(), "Not recording", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaEncoder != null) {
            mediaEncoder.finish();
            mediaEncoder = null;
            Log.d(TAG, "Recording stopped and encoder finished");

            isRecording = false;

            Toast.makeText(getActivity(), "Recording stopped", Toast.LENGTH_SHORT).show();

            // Update button visibility
            btnStop.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        }
    }

    private void playVideo(){
        if (videoOutputPath == null){
            Log.w(TAG, "No video path found to play");
            Toast.makeText(getActivity(), "No video to play", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Playing video: " + videoOutputPath);

        // Play the video in VideoView
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoPath(videoOutputPath);
        videoView.start();

        // Hide VideoView after playing
        videoView.setOnCompletionListener(mp -> {
            videoView.setVisibility(View.GONE); // Hide the VideoView after playback
            previewView.setVisibility(View.VISIBLE); // Show the camera preview again
            Log.d(TAG, "Video playback completed, returning to camera preview");
        });

        Toast.makeText(getActivity(), "Video saved and playing", Toast.LENGTH_SHORT).show();

        // Update button visibility
        btnSave.setVisibility(View.GONE);
        btnRecord.setVisibility(View.VISIBLE);
    }
}
