package com.example.cameraxvideorecorder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.example.cameraxvideorecorder.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import java.util.concurrent.TimeUnit

class MainActivity(private val viewModel: CameraViewModel = CameraViewModel()) :
    AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding


    // Define required permissions
    private val REQUIRED_PERMISSIONS =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    // Request code for camera permission
    private val REQUEST_CODE_PERMISSIONS = 10


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
        //    viewModel.setUseCaseExecutionListener(this)
        // Request camera permissions
        if (allPermissionsGranted()) {
            viewModel.startCamera(viewBinding.viewFinder.surfaceProvider, this, this)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        //user-interaction
        viewBinding.flashButton.setOnClickListener {
            setupFlash()
        }
        viewBinding.cameraFaceChangeButton.setOnClickListener {
            viewModel.cameraLensFacing =
                if (viewModel.cameraLensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            if (viewModel.rotationDegrees == 0f) {
                viewModel.updateRotationDegree(180f)
                viewBinding.flashButton.visibility = View.INVISIBLE
            } else {
                viewModel.updateRotationDegree(-180f)
                viewBinding.flashButton.visibility = View.VISIBLE
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
            }
            viewBinding.flipCameraIcon.animate().rotation(viewModel.rotationDegrees).start()
            viewModel.bindCameraUserCases(
                viewBinding.viewFinder.surfaceProvider,
                this
            )
        }
        viewBinding.videoCaptureButton.setOnClickListener {
            viewModel.captureVideo(this)
        }


        //Observe isVideoCapturing LiveData to change the Button UI
        viewModel.isVideoCapturing.observe(this, Observer { isCapturing ->
            if (isCapturing) {
                viewBinding.zoomSlider.visibility = View.VISIBLE
                viewBinding.zoomSlider.addOnChangeListener { slider, value, fromUser ->
//                    Toast.makeText(this, value.toString(), Toast.LENGTH_SHORT).show()
                    viewModel.camera.cameraControl.setLinearZoom(value / 100.toFloat())
                }
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape_with_big_stroke)
                viewBinding.cameraFaceChangeButton.visibility = View.GONE
            } else {
                viewBinding.zoomSlider.visibility = View.GONE
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape)
                viewBinding.cameraFaceChangeButton.visibility = View.VISIBLE
            }
        })
        // Observe videoCaptureButtonEnabled LiveData to enable/disable the video capture button
        viewModel.videoCaptureButtonEnabled.observe(this, Observer { enabled ->
            viewBinding.videoCaptureButton.isEnabled = enabled
        })

        // Observe toastMessage LiveData to show Toast messages
        viewModel.toastMessage.observe(this, Observer { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupFlash() {
        Log.d("torchState", viewModel.camera.cameraInfo.torchState.value.toString())
        if (viewModel.camera.cameraInfo.hasFlashUnit()) {
            if (viewModel.camera.cameraInfo.torchState.value == 0) {
                viewModel.updateCameraTorch(true)
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_on)
            } else {
                viewModel.updateCameraTorch(false)
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
            }
        } else {
            Toast.makeText(
                this, "Flash is not available!",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    private fun setUpZoomTapToFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = viewModel.camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                viewModel.camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this, listener)
        viewBinding.viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = viewBinding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()
//                val x = event.x
//                val y = event.y
//
//                val focusCircle = RectF(x-50,y-50, x+50,y+50)
//
//                mainBinding.focusCircleView.focusCircle = focusCircle
//                mainBinding.focusCircleView.invalidate()
                viewModel.camera.cameraControl.startFocusAndMetering(action)

                view.performClick()
            }
            true
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.destroyCameraExecutor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewModel.startCamera(viewBinding.viewFinder.surfaceProvider, this, this)
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

//    override fun onUseCaseExecutedSuccessfully() {
//        setUpZoomTapToFocus()
//    }

}

