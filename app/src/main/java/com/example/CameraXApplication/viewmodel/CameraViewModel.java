package com.example.CameraXApplication.viewmodel;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraViewModel extends ViewModel {
    private VideoCapture<Recorder> videoCapture = null;
    private Recording recording = null;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraSelector cameraSelector;
    private int cameraLensFacing = CameraSelector.LENS_FACING_BACK;
    private float rotationDegrees = 0f;

    private final MutableLiveData<Boolean> _isVideoCapturing = new MutableLiveData<>(false);
    public LiveData<Boolean> isVideoCapturing = _isVideoCapturing;

    private final MutableLiveData<Boolean> _videoCaptureButtonEnabled = new MutableLiveData<>(true);
    public LiveData<Boolean> videoCaptureButtonEnabled = _videoCaptureButtonEnabled;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public LiveData<String> toastMessage = _toastMessage;

    private final MutableLiveData<Long> _recordingTime = new MutableLiveData<>();
    public LiveData<Long> recordingTime = _recordingTime;

    private final MutableLiveData<Quality> _resolutionQuality = new MutableLiveData<>(Quality.SD);
    public LiveData<Quality> resolutionQuality = _resolutionQuality;

    private UseCaseExecutionListener useCaseExecutionListener;

    public void setUseCaseExecutionListener(UseCaseExecutionListener listener) {
        useCaseExecutionListener = listener;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public void startCamera(Preview.SurfaceProvider viewSurfaceProvider, Context context, LifecycleOwner lifecycleOwner) {
        ProcessCameraProvider.getInstance(context).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get();
                _resolutionQuality.setValue(Objects.requireNonNull(getSupportedResolutionQuality()).get(0));
                bindCameraUseCases(viewSurfaceProvider, lifecycleOwner);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));

        // Initialize the cameraExecutor
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @ExperimentalCamera2Interop
    public void bindCameraUseCases(Preview.SurfaceProvider viewSurfaceProvider, LifecycleOwner lifecycleOwner) {
        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewSurfaceProvider);
        // Recorder
        QualitySelector qualitySelector = QualitySelector.from(Objects.requireNonNull(resolutionQuality.getValue()));
        Recorder recorder = new Recorder.Builder().setQualitySelector(qualitySelector).build();

        videoCapture = VideoCapture.withOutput(recorder);

        cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraLensFacing).build();

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture);

            // Notify the listener that the use case has been executed successfully
            if (useCaseExecutionListener != null) {
                useCaseExecutionListener.onUseCaseExecutedSuccessfully();
            }
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    public void captureVideo(Context context) {
        _isVideoCapturing.setValue(true);
        VideoCapture<Recorder> videoCapture = this.videoCapture;
        if (videoCapture == null) return;
        _videoCaptureButtonEnabled.setValue(false);

        Recording curRecording = recording;
        if (curRecording != null) {
            curRecording.stop();
            stopRecording();
            recording = null;
            return;
        }
        startRecording();
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video");
        }

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(context.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        new Thread(() -> {
            recording = videoCapture.getOutput().prepareRecording(context, mediaStoreOutputOptions).withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context), recordEvent -> {
                        if (recordEvent instanceof VideoRecordEvent.Start) {
                            _videoCaptureButtonEnabled.postValue(true);
                        } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                            if (!finalizeEvent.hasError()) {
                                String msg = "Video capture succeeded: " + finalizeEvent.getOutputResults().getOutputUri();
                                _toastMessage.postValue(msg);
                                Log.d(TAG, msg);
                            } else {
                                if (recording != null) {
                                    recording.close();
                                    recording = null;
                                }
                                String errorMsg = "Video capture ends with error: " + finalizeEvent.getError();
                                _toastMessage.postValue(errorMsg);
                                Log.e(TAG, errorMsg);
                            }
                            _isVideoCapturing.postValue(false);
                            _videoCaptureButtonEnabled.postValue(true);
                        }
                    });
        }).start();

    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public void checkSupportedResolutionQuality(Quality quality) {
        List<Quality> supportedQualities = getSupportedResolutionQuality();
        if (supportedQualities != null && supportedQualities.contains(quality)) {
            _resolutionQuality.setValue(quality);
        } else {
            _toastMessage.postValue("This Quality Doesn't support by this device");
        }

    }


    @ExperimentalCamera2Interop
    private List<Quality> getSupportedResolutionQuality() {
        if (cameraProvider == null) {
            return null; // Return null if cameraProvider is not initialized
        }

        List<CameraInfo> cameraInfos = cameraProvider.getAvailableCameraInfos();
        if (cameraInfos == null) {
            return null; // Return null if cameraInfos is null
        }

        for (CameraInfo info : cameraInfos) {
            Camera2CameraInfo camera2Info = Camera2CameraInfo.from(info);
            Integer lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
            if (lensFacing != null && lensFacing == cameraLensFacing) {
                return QualitySelector.getSupportedQualities(info);
            }
        }
        return null; // Return null if no supported qualities found
    }


    public void updateCameraTorch(boolean torchState) {
        camera.getCameraControl().enableTorch(torchState);
    }

    public void destroyCameraExecutor() {
        if(cameraExecutor!=null) {
            cameraExecutor.shutdown();
        }
    }

    public float getRotationDegrees() {
        return rotationDegrees;
    }

    public void updateRotationDegree(float rotation) {
        rotationDegrees += rotation;
    }

    public Camera getCamera() {
        return camera;
    }

    public void zoomEffect(float value) {
        camera.getCameraControl().setLinearZoom(value / 100f);
    }

    public void setCameraLensFacing(Integer lensFacing) {
        cameraLensFacing = lensFacing;
    }

    public Integer getCameraLensFacing() {
        return cameraLensFacing;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            long currentTime = SystemClock.elapsedRealtime() - recordingStartTime;
            _recordingTime.setValue(currentTime);
            handler.postDelayed(this, 1000);
        }
    };

    private long recordingStartTime = 0;

    private void startRecording() {
        recordingStartTime = SystemClock.elapsedRealtime();
        handler.post(updateTimer);
    }

    private void stopRecording() {
        handler.removeCallbacks(updateTimer);
    }

    public void pauseHandler(Context context) {
        if (recording != null) {
            recording.stop();
            captureVideo(context);
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public void setDefaultResolution() {
        _resolutionQuality.setValue(Objects.requireNonNull(getSupportedResolutionQuality()).get(0));
    }

    private static final String TAG = "CameraViewModel";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd HH-mm-ss-SSS";

}
