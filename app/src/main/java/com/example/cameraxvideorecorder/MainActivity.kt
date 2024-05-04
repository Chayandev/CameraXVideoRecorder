package com.example.cameraxvideorecorder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.video.Quality
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cameraxvideorecorder.databinding.ActivityMainBinding
import com.example.cameraxvideorecorder.databinding.QualityItemLayoutBinding
import com.example.cameraxvideorecorder.viewmodel.CameraViewModel
import com.example.cameraxvideorecorder.viewmodel.UseCaseExecutionListener
import java.util.concurrent.TimeUnit

class MainActivity(private val viewModel: CameraViewModel = CameraViewModel()) :
    AppCompatActivity(), UseCaseExecutionListener {
    private lateinit var viewBinding: ActivityMainBinding


    // Define required permissions
    private val REQUIRED_PERMISSIONS =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    // Request code for camera permission
    private val REQUEST_CODE_PERMISSIONS = 10

    private var selectedQualityItem: QualityItemLayoutBinding? = null

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
        viewModel.setUseCaseExecutionListener(this)
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
                setTheDefaultUi()
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
            viewModel.setDefaultResolution()
        }
        viewBinding.videoCaptureButton.setOnClickListener {
            viewModel.captureVideo(this)
        }

        if (viewBinding.qualitySelectorLL.visibility == View.VISIBLE) {
            setTheDefaultUi()
        }

        // Set click listeners for each item
        viewBinding.quality480PLL.qualityItemLl.setOnClickListener {
            onItemClicked(
                viewBinding.quality480PLL,
                Quality.SD
            )
        }
        viewBinding.quality720PLL.qualityItemLl.setOnClickListener {
            onItemClicked(
                viewBinding.quality720PLL,
                Quality.HD
            )
        }
        viewBinding.quality1080PLL.qualityItemLl.setOnClickListener {
            onItemClicked(
                viewBinding.quality1080PLL,
                Quality.FHD
            )
        }
        viewBinding.quality2160PLL.qualityItemLl.setOnClickListener {
            onItemClicked(
                viewBinding.quality2160PLL,
                Quality.UHD
            )
        }

        //Observe isVideoCapturing LiveData to change the Button UI
        viewModel.isVideoCapturing.observe(this) { isCapturing ->
            if (isCapturing) {
                viewBinding.qualitySelectorLL.visibility = View.GONE
                viewBinding.zoomSlider.visibility = View.VISIBLE
                viewBinding.zoomSlider.addOnChangeListener { slider, value, fromUser ->
//                    Toast.makeText(this, value.toString(), Toast.LENGTH_SHORT).show()
                    viewModel.zoomEffect(value)
                }
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape_with_big_stroke)
                viewBinding.cameraFaceChangeButton.visibility = View.GONE
                viewBinding.timerIcon.visibility = View.GONE
                viewBinding.timerViewLL.visibility = View.VISIBLE
            } else {
                viewBinding.qualitySelectorLL.visibility = View.VISIBLE
                viewBinding.zoomSlider.value = 0f
                viewBinding.zoomSlider.visibility = View.GONE
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape)
                viewBinding.cameraFaceChangeButton.visibility = View.VISIBLE
                viewBinding.timerIcon.visibility = View.VISIBLE
                viewBinding.timerViewLL.visibility = View.GONE
            }
        }
        // Observe videoCaptureButtonEnabled LiveData to enable/disable the video capture button
        viewModel.videoCaptureButtonEnabled.observe(this) { enabled ->
            viewBinding.videoCaptureButton.isEnabled = enabled
        }

        // Observe toastMessage LiveData to show Toast messages
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.recordingTime.observe(this) { time ->
            updateRecordingTime(time)
        }

        viewModel.resolutionQuality.observe(this) { quality ->
            setUpResolutionOnChange(quality)
        }

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

    private fun setUpTapToFocus() {
        viewBinding.viewFinder.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = viewBinding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()

                val x = event.x
                val y = event.y

                val focusCircle = RectF(x - 80, y - 80, x + 80, y + 80)

                viewBinding.focusSquareView.focusSquare = focusCircle
                viewBinding.focusSquareView.invalidate()

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

    override fun onPause() {
        super.onPause()
        viewModel.pauseHandler(this)
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

    private fun setTheDefaultUi() {
        //setDefaultFlash
        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
        //setupUI
        selectedQualityItem = viewBinding.quality480PLL
        viewBinding.quality480PLL.qualityItemLl.setBackgroundResource(R.drawable.rounded_corner_yellow_shape)
        viewBinding.quality480PLL.pixelText.apply {
            setText(R.string._480p)
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        viewBinding.quality480PLL.subDetail.apply {
            setText(R.string.sd)
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        viewBinding.quality720PLL.qualityItemLl.background = null
        viewBinding.quality720PLL.pixelText.apply {
            setText(R.string._720p)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        viewBinding.quality720PLL.subDetail.apply {
            setText(R.string.HD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        viewBinding.quality1080PLL.qualityItemLl.background = null
        viewBinding.quality1080PLL.pixelText.apply {
            setText(R.string._1080p)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        viewBinding.quality1080PLL.subDetail.apply {
            setText(R.string.FHD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        viewBinding.quality2160PLL.qualityItemLl.background = null
        viewBinding.quality2160PLL.pixelText.apply {
            setText(R.string._2160p)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        viewBinding.quality2160PLL.subDetail.apply {
            setText(R.string.UHD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }

    }

    private fun onItemClicked(item: QualityItemLayoutBinding, quality: Quality) {
        Log.d("clickedQuality", quality.toString())
        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off)
        viewModel.checkSupportedResolutionQuality(quality)
    }

    private fun setUpResolutionOnChange(quality: Quality) {
        val item = when (quality) {
            Quality.SD -> {
                viewBinding.quality480PLL
            }

            Quality.HD -> {
                viewBinding.quality720PLL
            }

            Quality.FHD -> {
                viewBinding.quality1080PLL
            }

            else -> {
                viewBinding.quality2160PLL
            }
        }
        // Reset the background of the previously selected item (if any)
        selectedQualityItem?.qualityItemLl?.background = null
        selectedQualityItem?.pixelText?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        selectedQualityItem?.subDetail?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
        // Update the background of the clicked item
        selectedQualityItem = item
        item.qualityItemLl.setBackgroundResource(R.drawable.rounded_corner_yellow_shape)
        item.pixelText.apply {
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        item.subDetail.apply {
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        viewModel.bindCameraUserCases(viewBinding.viewFinder.surfaceProvider, this)
    }

    private fun updateRecordingTime(time: Long) {
        val timeString = time.toFormattedTime()
        viewBinding.timerView.text = timeString
    }

    private fun Long.toFormattedTime(): String {
        val seconds = ((this / 1000) % 60).toInt()
        val minutes = ((this / (1000 * 60)) % 60).toInt()
        val hours = ((this / (1000 * 60 * 60)) % 24).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Call these functions from UI events like button clicks
//    private fun startRecording() {
//        viewModel.startRecording()
//    }
//
//    private fun stopRecording() {
//        viewModel.stopRecording()
//    }
    override fun onUseCaseExecutedSuccessfully() {
        setUpTapToFocus()
    }

}

