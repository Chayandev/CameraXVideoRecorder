package com.example.cameraxvideorecorder

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.lifecycle.ViewModel
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
//interface UseCaseExecutionListener {
//    fun onUseCaseExecutedSuccessfully()
//}

class CameraViewModel : ViewModel() {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    var cameraLensFacing = CameraSelector.LENS_FACING_BACK
    var rotationDegrees = 0f

    private val _isVideoCapturing=MutableLiveData(false)
    var isVideoCapturing :LiveData<Boolean> =_isVideoCapturing

    private var cameraExecutorJob: Job? = null
    private val cameraScope = CoroutineScope(Dispatchers.Default)

    private val _videoCaptureButtonEnabled = MutableLiveData<Boolean>(true)
    val videoCaptureButtonEnabled: LiveData<Boolean> = _videoCaptureButtonEnabled

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

//    private var useCaseExecutionListener: UseCaseExecutionListener? = null
//    fun setUseCaseExecutionListener(listener: UseCaseExecutionListener) {
//        useCaseExecutionListener = listener
//    }
    fun startCamera(
        viewSurfaceProvider: Preview.SurfaceProvider,
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases(viewSurfaceProvider, lifecycleOwner)
        }, ContextCompat.getMainExecutor(context))

        // Initialize the cameraExecutor
        cameraExecutorJob = cameraScope.launch {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    fun bindCameraUserCases(
        viewSurfaceProvider: Preview.SurfaceProvider,
        lifecycleOwner: LifecycleOwner
    ) {
        // Preview
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(viewSurfaceProvider)
        }

        // Recorder
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraLensFacing)
            .build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
            // Notify the listener that the use case has been executed successfully
            //useCaseExecutionListener?.onUseCaseExecutedSuccessfully()
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    fun captureVideo(context: Context) {
        _isVideoCapturing.value=true
        val videoCapture = this.videoCapture ?: return
        _videoCaptureButtonEnabled.value = false

//        val curRecording = recording
//        if (curRecording != null) {
//            curRecording.stop()
//            recording = null
//            return
//        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        viewModelScope.launch(Dispatchers.IO) {
            recording = videoCapture.output
                .prepareRecording(context, mediaStoreOutputOptions)
                .apply {
                    if (PermissionChecker.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) ==
                        PermissionChecker.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            _videoCaptureButtonEnabled.postValue(true)
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                val msg =
                                    "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                                _toastMessage.postValue(msg)
                                Log.d(TAG, msg)
                            } else {
                                recording?.close()
                                recording = null
                                val errorMsg = "Video capture ends with error: ${recordEvent.error}"
                                _toastMessage.postValue(errorMsg)
                                Log.e(TAG, errorMsg)
                            }
                            _isVideoCapturing.value=false
                            _videoCaptureButtonEnabled.postValue(true)
                        }
                    }
                }
        }
    }


    fun releaseCamera() {
        cameraProvider.unbindAll()
    }

    fun updateCameraTorch(torchState: Boolean) {
        camera.cameraControl.enableTorch(torchState)
    }

    fun destroyCameraExecutor() {
        cameraExecutorJob?.cancel()
        cameraExecutor.shutdown()
    }

    fun updateRotationDegree(rotation: Float) {
        rotationDegrees += rotation
    }

    companion object {
        private const val TAG = "CameraViewModel"
        private const val FILENAME_FORMAT = "yyyy-MM-dd HH-mm-ss-SSS"
    }
}
