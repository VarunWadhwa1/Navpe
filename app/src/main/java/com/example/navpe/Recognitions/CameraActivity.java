package com.example.navpe.Recognitions;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;


import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.navpe.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.nio.ByteBuffer;
import java.util.Objects;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener, Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private static final Logger LOGGER = new Logger();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private final byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    private static final String KEY_USE_FACING = "use_facing";
    private Integer useFacing = null;
    protected Integer getCameraFacing() {
    return useFacing;
  }
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);

        Intent intent = getIntent();
        useFacing = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_FRONT);
        //    useFacing = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_BACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.tfe_od_activity_camera);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
//        FloatingActionButton btnSwitchCam = findViewById(R.id.fab_switchcam);

//        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
//        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                //                int width = bottomSheetLayout.getMeasuredWidth();
//                int height = gestureLayout.getMeasuredHeight();
//                sheetBehavior.setPeekHeight(height);
//                }
//            });
//        sheetBehavior.setHideable(false);
//
//        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
//            @Override
//            public void onStateChanged(@NonNull View bottomSheet, int newState) {
//                switch (newState) {
//                    case BottomSheetBehavior.STATE_HIDDEN:
//                    case BottomSheetBehavior.STATE_DRAGGING:
//                        break;
//                    case BottomSheetBehavior.STATE_EXPANDED:
//                        bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
//                        break;
//                    case BottomSheetBehavior.STATE_COLLAPSED:
//                    case BottomSheetBehavior.STATE_SETTLING:
//                        bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
//                        break;
//                }
//            }
//            @Override
//            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
//        });

//        apiSwitchCompat.setOnCheckedChangeListener(this);
//        btnSwitchCam.setOnClickListener(v -> onSwitchCamClick());
    }
//    private void onSwitchCamClick() {
//        switchCamera();
//    }
//    public void switchCamera() {
//        Intent intent = getIntent();
//        if (useFacing == CameraCharacteristics.LENS_FACING_FRONT) {
//            useFacing = CameraCharacteristics.LENS_FACING_BACK;
//        }else {
//            useFacing = CameraCharacteristics.LENS_FACING_FRONT;
//        }
//        intent.putExtra(KEY_USE_FACING, useFacing);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//        restartWith(intent);
//    }
//    private void restartWith(Intent intent) {
//        finish();
//        overridePendingTransition(0, 0);
//        startActivity(intent);
//        overridePendingTransition(0, 0);
//    }
    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }
    protected int getLuminanceStride() {
    return yRowStride;
  }
    protected byte[] getLuminance() {
    return yuvBytes[0];
  }
    /** Callback for android.hardware.Camera API */
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }
        try {
              // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                //rgbBytes = new int[previewWidth * previewHeight];
                //onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
                rgbBytes = new int[previewWidth * previewHeight];
                int rotation = 90;
                if (useFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                    rotation = 270;
                }
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), rotation);
            }
        }catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }
        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter = () -> ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);

        postInferenceCallback = () -> {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
        };
        processImage();
    }
    /** Callback for Camera2 API */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }
            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter = () -> ImageUtils.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2],
                      previewWidth, previewHeight, yRowStride, uvRowStride, uvPixelStride, rgbBytes);
                postInferenceCallback = () -> {
                image.close();
                isProcessingFrame = false;
            };
            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }
    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }
    @Override
    public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
    }

    @Override
    public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
    }

    @Override
    public void onRequestPermissionsResult(
          final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
    }

    private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
    }

    private boolean hasPermission() {
        return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
        }
        requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
          CameraCharacteristics characteristics) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return false;
    }
    // deviceLevel is not LEGACY, can use numerical sort
        return android.hardware.camera2.CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL <= deviceLevel;
    }
    private String chooseCamera() {

        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (useFacing != null && facing != null && !facing.equals(useFacing)){
                    continue;
                }
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) || isHardwareLevelSupported(characteristics);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }
        return null;
    }
    protected void setFragment() {
        String cameraId = chooseCamera();

        Fragment fragment;
        if (useCamera2API){
            CameraConnectionFragment camera2Fragment = CameraConnectionFragment.newInstance((size, rotation) -> {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                CameraActivity.this.onPreviewSizeChosen(size, rotation);
                            }, this, getLayoutId(), getDesiredPreviewFrameSize());
            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;

        } else {
            int facing = (useFacing == CameraCharacteristics.LENS_FACING_BACK) ?
                          Camera.CameraInfo.CAMERA_FACING_BACK :
                          Camera.CameraInfo.CAMERA_FACING_FRONT;
            fragment = new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize(), facing);
        }
        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }
    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
        final ByteBuffer buffer = planes[i].getBuffer();
        if (yuvBytes[i] == null) {
            LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
            yuvBytes[i] = new byte[buffer.capacity()];
        }
            buffer.get(yuvBytes[i]);
        }
    }
    public boolean isDebug() {
      return false;
  }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

//    @SuppressLint("SetTextI18n")
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {}
//        setUseNNAPI(isChecked);
//        if (isChecked) apiSwitchCompat.setText("NNAPI");
//        else apiSwitchCompat.setText("TFLITE");
//    }

    @Override
    public void onClick(View v) {}
//        if (v.getId() == R.id.plus) {
//            String threads = threadsTextView.getText().toString().trim();
//            int numThreads = Integer.parseInt(threads);
//            if (numThreads >= 9) return;
//            numThreads++;
//            threadsTextView.setText(String.valueOf(numThreads));
//            setNumThreads(numThreads);
//        }else if (v.getId() == R.id.minus) {
//            String threads = threadsTextView.getText().toString().trim();
//            int numThreads = Integer.parseInt(threads);
//            if (numThreads == 1) {
//            return;
//            }
//            numThreads--;
//            threadsTextView.setText(String.valueOf(numThreads));
//            setNumThreads(numThreads);
//        }
//    }
//  protected void showFrameInfo(String frameInfo) {
//    frameValueTextView.setText(frameInfo);
//  }
//  protected void showCropInfo(String cropInfo) {
//    cropValueTextView.setText(cropInfo);
//  }
//  protected void showInference(String inferenceTime) {
//        inferenceTimeTextView.setText(inferenceTime);
//  }
  protected abstract void processImage();
  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);
  protected abstract int getLayoutId();
  protected abstract Size getDesiredPreviewFrameSize();
  protected abstract void setNumThreads(int numThreads);
  protected abstract void setUseNNAPI(boolean isChecked);
}
