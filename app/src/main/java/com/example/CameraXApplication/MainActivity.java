package com.example.CameraXApplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.TorchState;
import androidx.camera.video.Quality;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.CameraXApplication.databinding.ActivityMainBinding;
import com.example.CameraXApplication.databinding.QualityItemLayoutBinding;
import com.example.CameraXApplication.viewmodel.CameraViewModel;
import com.example.CameraXApplication.viewmodel.UseCaseExecutionListener;



import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements UseCaseExecutionListener {
    private CameraViewModel viewModel;
    private ActivityMainBinding viewBinding;
    private QualityItemLayoutBinding selectedQualityItem;

    private final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private final int REQUEST_CODE_PERMISSIONS = 10;

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new CameraViewModel();
        viewModel.setUseCaseExecutionListener(this);

       checkSelfPermission();
        // user-interaction
        viewBinding.flashButton.setOnClickListener(v -> setupFlash());

        viewBinding.cameraFaceChangeButton.setOnClickListener(v -> {
            viewModel.setCameraLensFacing(viewModel.getCameraLensFacing() == CameraSelector.LENS_FACING_FRONT ?
                    CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT);
            if (viewModel.getRotationDegrees() == 0f) {
                viewModel.updateRotationDegree(180f);
                viewBinding.flashButton.setVisibility(View.INVISIBLE);
                setTheDefaultUi();
            } else {
                viewModel.updateRotationDegree(-180f);
                viewBinding.flashButton.setVisibility(View.VISIBLE);
                viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off);
            }
            viewBinding.flipCameraIcon.animate().rotation(viewModel.getRotationDegrees()).start();
            viewModel.bindCameraUseCases(viewBinding.viewFinder.getSurfaceProvider(), this);
            viewModel.setDefaultResolution();
        });

        viewBinding.videoCaptureButton.setOnClickListener(v -> viewModel.captureVideo(this));

        if (viewBinding.qualitySelectorLL.getVisibility() == View.VISIBLE) {
            setTheDefaultUi();
        }

        viewBinding.quality480PLL.qualityItemLl.setOnClickListener(v -> onItemClicked(viewBinding.quality480PLL, Quality.SD));
        viewBinding.quality720PLL.qualityItemLl.setOnClickListener(v -> onItemClicked(viewBinding.quality720PLL, Quality.HD));
        viewBinding.quality1080PLL.qualityItemLl.setOnClickListener(v -> onItemClicked(viewBinding.quality1080PLL, Quality.FHD));
        viewBinding.quality2160PLL.qualityItemLl.setOnClickListener(v -> onItemClicked(viewBinding.quality2160PLL, Quality.UHD));


    }

    private void checkSelfPermission() {
        if (allPermissionsGranted()) {
            viewModel.startCamera(viewBinding.viewFinder.getSurfaceProvider(), this, this);
            executeCameraRelatedFunctionality();
        } else {
            // If any permission is denied, request permissions or redirect to settings
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private void executeCameraRelatedFunctionality() {
        viewModel.isVideoCapturing.observe(this, isCapturing -> {
            if (isCapturing) {
                viewBinding.qualitySelectorLL.setVisibility(View.GONE);
                viewBinding.zoomSlider.setVisibility(View.VISIBLE);
                viewBinding.zoomSlider.addOnChangeListener((slider, value, fromUser) -> viewModel.zoomEffect(value));
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape_with_big_stroke);
                viewBinding.cameraFaceChangeButton.setVisibility(View.GONE);
                viewBinding.timerIcon.setVisibility(View.GONE);
                viewBinding.timerViewLL.setVisibility(View.VISIBLE);
            } else {
                viewBinding.qualitySelectorLL.setVisibility(View.VISIBLE);
                viewBinding.zoomSlider.setValue(0f);
                viewBinding.zoomSlider.setVisibility(View.GONE);
                viewBinding.videoCaptureButton.setBackgroundResource(R.drawable.rounded_shape);
                viewBinding.cameraFaceChangeButton.setVisibility(View.VISIBLE);
                viewBinding.timerIcon.setVisibility(View.VISIBLE);
                viewBinding.timerViewLL.setVisibility(View.GONE);
            }
        });
        viewModel.videoCaptureButtonEnabled.observe(this, enabled -> viewBinding.videoCaptureButton.setEnabled(enabled));

        viewModel.toastMessage.observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.recordingTime.observe(this, this::updateRecordingTime);
        viewModel.resolutionQuality.observe(this, this::setUpResolutionOnChange);
    }

    private void setupFlash() {
        Camera camera = viewModel.getCamera();
        if (camera != null) {
            CameraInfo cameraInfo = camera.getCameraInfo();
            if (cameraInfo.hasFlashUnit()) {
                Integer torchState = cameraInfo.getTorchState().getValue();
                if (torchState != null) {
                    if (torchState == TorchState.OFF) {
                        viewModel.updateCameraTorch(true);
                        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_on);
                    } else {
                        viewModel.updateCameraTorch(false);
                        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off);
                    }
                }
            } else {
                Toast.makeText(
                        this, "Flash is not available!",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void onItemClicked(QualityItemLayoutBinding item, Quality quality) {
        Log.d("clickedQuality", quality.toString());
        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off);
        viewModel.checkSupportedResolutionQuality(quality);
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void setUpResolutionOnChange(Quality quality) {
        QualityItemLayoutBinding item;
        if (quality.equals(Quality.SD)) {
            item = viewBinding.quality480PLL;
        } else if (quality.equals(Quality.HD)) {
            item = viewBinding.quality720PLL;
        } else if (quality.equals(Quality.FHD)) {
            item = viewBinding.quality1080PLL;
        } else if (quality.equals(Quality.UHD)) {
            item = viewBinding.quality2160PLL;
        } else {
            throw new IllegalArgumentException("Unexpected quality: " + quality);
        }

        if (selectedQualityItem != null) {
            selectedQualityItem.qualityItemLl.setBackground(null);
            selectedQualityItem.pixelText.setTextColor(ContextCompat.getColor(this, R.color.white));
            selectedQualityItem.subDetail.setTextColor(ContextCompat.getColor(this, R.color.white));
        }

        selectedQualityItem = item;
        item.qualityItemLl.setBackgroundResource(R.drawable.rounded_corner_yellow_shape);
        item.pixelText.setTextColor(ContextCompat.getColor(this, R.color.black));
        item.subDetail.setTextColor(ContextCompat.getColor(this, R.color.black));

        viewModel.bindCameraUseCases(viewBinding.viewFinder.getSurfaceProvider(), this);
    }

    private void setTheDefaultUi() {
        viewBinding.flashButton.setImageResource(R.drawable.ic_flash_off);

        selectedQualityItem = viewBinding.quality480PLL;
        selectedQualityItem.qualityItemLl.setBackgroundResource(R.drawable.rounded_corner_yellow_shape);
        selectedQualityItem.pixelText.setText(R.string._480p);
        selectedQualityItem.pixelText.setTextColor(ContextCompat.getColor(this, R.color.black));
        selectedQualityItem.subDetail.setText(R.string.sd);
        selectedQualityItem.subDetail.setTextColor(ContextCompat.getColor(this, R.color.black));

        viewBinding.quality720PLL.qualityItemLl.setBackground(null);
        viewBinding.quality720PLL.pixelText.setText(R.string._720p);
        viewBinding.quality720PLL.pixelText.setTextColor(ContextCompat.getColor(this, R.color.white));
        viewBinding.quality720PLL.subDetail.setText(R.string.HD);
        viewBinding.quality720PLL.subDetail.setTextColor(ContextCompat.getColor(this, R.color.white));

        viewBinding.quality1080PLL.qualityItemLl.setBackground(null);
        viewBinding.quality1080PLL.pixelText.setText(R.string._1080p);
        viewBinding.quality1080PLL.pixelText.setTextColor(ContextCompat.getColor(this, R.color.white));
        viewBinding.quality1080PLL.subDetail.setText(R.string.FHD);
        viewBinding.quality1080PLL.subDetail.setTextColor(ContextCompat.getColor(this, R.color.white));

        viewBinding.quality2160PLL.qualityItemLl.setBackground(null);
        viewBinding.quality2160PLL.pixelText.setText(R.string._2160p);
        viewBinding.quality2160PLL.pixelText.setTextColor(ContextCompat.getColor(this, R.color.white));
        viewBinding.quality2160PLL.subDetail.setText(R.string.UHD);
        viewBinding.quality2160PLL.subDetail.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void updateRecordingTime(Long time) {
        String timeString = toFormattedTime(time);
        viewBinding.timerView.setText(timeString);
    }

    private String toFormattedTime(Long time) {
        int seconds = (int) ((time / 1000) % 60);
        int minutes = (int) ((time / (1000 * 60)) % 60);
        int hours = (int) ((time / (1000 * 60 * 60)) % 24);

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private void setUpTapToFocus() {
        viewBinding.viewFinder.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                FocusMeteringAction action = new FocusMeteringAction.Builder(
                        viewBinding.viewFinder.getMeteringPointFactory().createPoint(event.getX(), event.getY()),
                        FocusMeteringAction.FLAG_AF
                ).setAutoCancelDuration(2, TimeUnit.SECONDS).build();

                float x = event.getX();
                float y = event.getY();
                RectF focusSquare = new RectF(x - 80, y - 80, x + 80, y + 80);

                 viewBinding.focusSquareView.focusSquare=focusSquare;
                viewBinding.focusSquareView.invalidate();

                viewModel.getCamera().getCameraControl().startFocusAndMetering(action);

                view.performClick();
            }
            return true;
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewModel.startCamera(viewBinding.viewFinder.getSurfaceProvider(), this, this);
                executeCameraRelatedFunctionality();
            } else {
                // If any permission is denied, redirect the user to settings
                Toast.makeText(
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show();
                requestPermissionsFromSettings();
                finish();
            }
        }
    }

    private void requestPermissionsFromSettings() {
        // Redirect the user to the app settings screen to grant permissions
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.pauseHandler(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.destroyCameraExecutor();
    }

    @Override
    public void onUseCaseExecutedSuccessfully() {
        setUpTapToFocus();
    }
}