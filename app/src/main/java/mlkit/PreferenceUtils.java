package mlkit;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.gms.common.images.Size;
import com.google.common.base.Preconditions;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import hku.cs.comp3330.section1a2024.group19.gymmygo.R;
import mlkit.CameraSource.SizePair;

/** Utility class to retrieve shared preferences related to pose detection. */
public class PreferenceUtils {

  private static final int POSE_DETECTOR_PERFORMANCE_MODE_FAST = 1;

  @Nullable
  public static SizePair getCameraPreviewSizePair(Context context, int cameraId) {
    Preconditions.checkArgument(
            cameraId == CameraSource.CAMERA_FACING_BACK
                    || cameraId == CameraSource.CAMERA_FACING_FRONT);
    String previewSizePrefKey;
    String pictureSizePrefKey;
    if (cameraId == CameraSource.CAMERA_FACING_BACK) {
      previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size);
      pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size);
    } else {
      previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size);
      pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size);
    }

    try {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      return new SizePair(
              Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
              Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)));
    } catch (Exception e) {
      return null;
    }
  }

  public static PoseDetectorOptionsBase getPoseDetectorOptionsForLivePreview(Context context) {
    int performanceMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_pose_detection_performance_mode,
            POSE_DETECTOR_PERFORMANCE_MODE_FAST);
    boolean preferGPU = preferGPUForPoseDetection(context);
    if (performanceMode == POSE_DETECTOR_PERFORMANCE_MODE_FAST) {
      PoseDetectorOptions.Builder builder =
              new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    } else {
      AccuratePoseDetectorOptions.Builder builder =
              new AccuratePoseDetectorOptions.Builder()
                      .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    }
  }

  public static PoseDetectorOptionsBase getPoseDetectorOptionsForStillImage(Context context) {
    int performanceMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_still_image_pose_detection_performance_mode,
            POSE_DETECTOR_PERFORMANCE_MODE_FAST);
    boolean preferGPU = preferGPUForPoseDetection(context);
    if (performanceMode == POSE_DETECTOR_PERFORMANCE_MODE_FAST) {
      PoseDetectorOptions.Builder builder =
              new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    } else {
      AccuratePoseDetectorOptions.Builder builder =
              new AccuratePoseDetectorOptions.Builder()
                      .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    }
  }

  public static boolean preferGPUForPoseDetection(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_prefer_gpu);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldShowPoseDetectionInFrameLikelihoodLivePreview(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_live_preview_pose_detector_show_in_frame_likelihood);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldShowPoseDetectionInFrameLikelihoodStillImage(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_still_image_pose_detector_show_in_frame_likelihood);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionVisualizeZ(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_visualize_z);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionRescaleZForVisualization(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_rescale_z);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionRunClassification(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_run_classification);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  /**
   * Mode type preference is backed by {@link android.preference.ListPreference} which only supports
   * storing its entry value as string type, so we need to retrieve as string and then convert to
   * integer.
   */
  private static int getModeTypePreferenceValue(Context context, @StringRes int prefKeyResId, int defaultValue) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(prefKeyResId);
    return Integer.parseInt(sharedPreferences.getString(prefKey, String.valueOf(defaultValue)));
  }

  public static boolean isCameraLiveViewportEnabled(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  private PreferenceUtils() {}
}

