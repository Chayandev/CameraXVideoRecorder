package com.example.caremaxvideorecorder

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.caremaxvideorecorder.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    // Define required permissions
    private val REQUIRED_PERMISSIONS =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    // Request code for camera permission
    private val REQUEST_CODE_PERMISSIONS = 10

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    private var cameraLensFacing = CameraSelector.LENS_FACING_BACK
    private var rotationDegrees = 0f

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        //user-interaction
        viewBinding.flashButton.setOnClickListener {
            setupFlash(camera)
        }
        viewBinding.cameraFaceChangeButton.setOnClickListener {
            cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            if (rotationDegrees == 0f) {
                rotationDegrees += 180f
                viewBinding.flashButton.visibility = View.INVISIBLE
            } else {
                rotationDegrees -= 180f
                viewBinding.flashButton.visibility = View.VISIBLE
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
            }
            viewBinding.flipCameraIcon.animate().rotation(rotationDegrees).start()
            bindCameraUserCases(cameraProvider)
        }
        viewBinding.videoCaptureButton.setOnClickListener {

        }
    }

    private fun setupFlash(cam: Camera) {
        Log.d("torchState", cam.cameraInfo.torchState.value.toString())
        if (cam.cameraInfo.hasFlashUnit()) {
            if (cam.cameraInfo.torchState.value == 0) {
                cam.cameraControl.enableTorch(true)
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_on)
            } else {
                cam.cameraControl.enableTorch(false)
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
            }
        } else {
            Toast.makeText(
                this, "Flash is not available!",
                Toast.LENGTH_SHORT
            ).show()
            //viewBinding.flashButton.isEnabled = false
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

    }

    private fun bindCameraUserCases(camProvider: ProcessCameraProvider) {
        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }
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
            camProvider.unbindAll()

            // Bind use cases to camera
            camera = camProvider.bindToLifecycle(
                this, cameraSelector, preview, videoCapture
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}

