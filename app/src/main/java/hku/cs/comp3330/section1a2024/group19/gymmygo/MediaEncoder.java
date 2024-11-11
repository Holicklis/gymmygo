package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaEncoder {

    private static final String TAG = "MediaEncoder";
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30; // 30 fps
    private static final int I_FRAME_INTERVAL = 1; // 1 second between I-frames
    private static final int BIT_RATE = 10_000_000; // 10 Mbps

    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private int trackIndex = -1;
    private boolean muxerStarted = false;
    private boolean hasFrames = false;

    /**
     * Constructor to initialize the MediaEncoder with the specified output path.
     *
     * @param outputPath The file path where the encoded video will be saved.
     */
    public MediaEncoder(String outputPath) {
        try {
            // Instantiate MediaCodecList to retrieve codec information
            MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo codecInfo = null;

            // Iterate through available codecs to find a suitable encoder
            for (MediaCodecInfo info : mediaCodecList.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(MIME_TYPE)) {
                        codecInfo = info;
                        break;
                    }
                }
                if (codecInfo != null) break;
            }

            if (codecInfo == null) {
                Log.e(TAG, "No encoder found for MIME type: " + MIME_TYPE);
                return;
            }

            // Find a supported color format (YUV420SemiPlanar)
            int colorFormat = -1;
            for (int format : codecInfo.getCapabilitiesForType(MIME_TYPE).colorFormats) {
                if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    colorFormat = format;
                    break;
                }
            }

            if (colorFormat == -1) {
                Log.e(TAG, "Required color format COLOR_FormatYUV420SemiPlanar not supported");
                return;
            }

            // Create and configure the MediaFormat
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 1280, 720);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

            // Initialize MediaCodec
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            Log.d(TAG, "MediaCodec started with format: " + format);

            // Initialize MediaMuxer
            mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Log.d(TAG, "MediaMuxer initialized with output path: " + outputPath);

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize MediaEncoder", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during MediaEncoder initialization", e);
        }
    }

    /**
     * Encodes a YUV420 byte array frame into the video.
     *
     * @param yuvFrame The YUV420 byte array representing a single frame.
     */
    public void encodeFrame(byte[] yuvFrame) {
        if (mediaCodec == null || mediaMuxer == null) {
            Log.e(TAG, "MediaCodec or MediaMuxer not initialized");
            return;
        }

        try {
            // Feed data to MediaCodec
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(yuvFrame);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuvFrame.length, System.nanoTime() / 1000, 0);
                    hasFrames = true;
                    Log.d(TAG, "Frame encoded and queued");
                }
            } else {
                Log.w(TAG, "No available input buffer for encoding");
            }

            // Retrieve encoded data
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                if (outputBuffer == null) {
                    Log.e(TAG, "Output buffer is null");
                    break;
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // Codec config data, ignore
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    if (!muxerStarted) {
                        MediaFormat outputFormat = mediaCodec.getOutputFormat(outputBufferIndex);
                        trackIndex = mediaMuxer.addTrack(outputFormat);
                        mediaMuxer.start();
                        muxerStarted = true;
                        Log.d(TAG, "MediaMuxer started");
                    }

                    // Adjust the ByteBuffer position and limit
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    Log.d(TAG, "Sample data written to MediaMuxer");
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during frame encoding", e);
        }
    }

    /**
     * Signals the end of input stream and finalizes the video file.
     */
    public void finish() {
        if (mediaCodec != null) {
            try {
                if (hasFrames) {
                    mediaCodec.signalEndOfInputStream();
                    Log.d(TAG, "End of input stream signaled");
                } else {
                    Log.w(TAG, "No frames encoded. Skipping signalEndOfInputStream()");
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                boolean outputDone = false;

                while (!outputDone) {
                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // No output available yet
                        break;
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) {
                            throw new RuntimeException("Format changed twice");
                        }
                        MediaFormat newFormat = mediaCodec.getOutputFormat(outputBufferIndex);
                        trackIndex = mediaMuxer.addTrack(newFormat);
                        mediaMuxer.start();
                        muxerStarted = true;
                        Log.d(TAG, "MediaMuxer started");
                    } else if (outputBufferIndex < 0) {
                        // Ignore unexpected outputBufferIndex
                    } else {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer == null) {
                            throw new RuntimeException("Output buffer is null");
                        }

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // Codec config data, ignore
                            bufferInfo.size = 0;
                        }

                        if (bufferInfo.size != 0) {
                            if (!muxerStarted) {
                                MediaFormat outputFormat = mediaCodec.getOutputFormat(outputBufferIndex);
                                trackIndex = mediaMuxer.addTrack(outputFormat);
                                mediaMuxer.start();
                                muxerStarted = true;
                                Log.d(TAG, "MediaMuxer started");
                            }

                            // Adjust the ByteBuffer position and limit
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                            mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                            Log.d(TAG, "Sample data written to MediaMuxer during finish");
                        }

                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true;
                            Log.d(TAG, "End of stream reached");
                        }
                    }
                }

                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;

                if (mediaMuxer != null) {
                    if (muxerStarted) {
                        mediaMuxer.stop();
                        Log.d(TAG, "MediaMuxer stopped");
                    }
                    mediaMuxer.release();
                    mediaMuxer = null;
                }

                Log.d(TAG, "MediaEncoder finished and released");
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException in MediaEncoder.finish()", e);
                // Proceed to release resources even if signalEndOfInputStream fails
                try {
                    mediaCodec.stop();
                } catch (Exception ex) {
                    Log.e(TAG, "Error stopping MediaCodec", ex);
                }
                mediaCodec.release();
                mediaCodec = null;

                if (mediaMuxer != null) {
                    try {
                        if (muxerStarted) {
                            mediaMuxer.stop();
                            Log.d(TAG, "MediaMuxer stopped after exception");
                        }
                        mediaMuxer.release();
                        mediaMuxer = null;
                    } catch (Exception ex) {
                        Log.e(TAG, "Error stopping MediaMuxer", ex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in MediaEncoder.finish()", e);
                // Ensure resources are released
                try {
                    mediaCodec.stop();
                } catch (Exception ex) {
                    Log.e(TAG, "Error stopping MediaCodec", ex);
                }
                mediaCodec.release();
                mediaCodec = null;

                if (mediaMuxer != null) {
                    try {
                        if (muxerStarted) {
                            mediaMuxer.stop();
                            Log.d(TAG, "MediaMuxer stopped after exception");
                        }
                        mediaMuxer.release();
                        mediaMuxer = null;
                    } catch (Exception ex) {
                        Log.e(TAG, "Error stopping MediaMuxer", ex);
                    }
                }
            }
        }
    }

    /**
     * Checks whether any frames have been encoded.
     *
     * @return true if at least one frame has been encoded; false otherwise.
     */
    public boolean hasFrames() {
        return hasFrames;
    }
}
