package com.example.pokeradvisor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PokerAdvisor";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: Activity created");

        surfaceView = findViewById(R.id.surface_view);
        surfaceHolder = surfaceView.getHolder();
        Log.d(TAG, "onCreate: SurfaceView and SurfaceHolder initialized");

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "SurfaceHolder.Callback: surfaceCreated");
                if (checkCameraPermission()) {
                    Log.d(TAG, "SurfaceHolder.Callback: Camera permission granted, opening camera");
                    openCamera();
                    } else {
                    Log.w(TAG, "SurfaceHolder.Callback: Camera permission not granted");
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "SurfaceHolder.Callback: surfaceChanged - format: " + format + ", width: " + width + ", height: " + height);
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "SurfaceHolder.Callback: surfaceDestroyed");
                closeCamera();
            }
        });
    }

    private boolean checkCameraPermission() {
        Log.d(TAG, "checkCameraPermission: Checking camera permission");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "checkCameraPermission: Camera permission not granted, requesting");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return false;
        }
        Log.d(TAG, "checkCameraPermission: Camera permission already granted");
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: Request code: " + requestCode);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Camera permission granted, opening camera");
                openCamera();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Camera permission denied");
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startBackgroundThread() {
        Log.d(TAG, "startBackgroundThread: Starting background thread");
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.d(TAG, "startBackgroundThread: Background thread started");
    }

    private void stopBackgroundThread() {
        Log.d(TAG, "stopBackgroundThread: Stopping background thread");
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
                Log.d(TAG, "stopBackgroundThread: Background thread stopped");
            } catch (InterruptedException e) {
                Log.e(TAG, "stopBackgroundThread: Error stopping background thread", e);
            }
        } else {
            Log.w(TAG, "stopBackgroundThread: Background thread was null");
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera: Attempting to open camera");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            Log.d(TAG, "openCamera: Available camera IDs: " + Arrays.toString(cameraIdList));
            String cameraId = cameraIdList[0]; // Use first camera
            Log.d(TAG, "openCamera: Selected camera ID: " + cameraId);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "openCamera: Camera permission not granted");
                return;
            }
            startBackgroundThread();
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                    Log.d(TAG, "CameraDevice.StateCallback: Camera opened successfully");
                    cameraDevice = device;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice device) {
                    Log.w(TAG, "CameraDevice.StateCallback: Camera disconnected");
                    device.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice device, int error) {
                    Log.e(TAG, "CameraDevice.StateCallback: Camera error: " + error);
                    device.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: Cannot access camera", e);
        }
    }

    @SuppressWarnings("deprecation") // Suppress deprecation warning for API 24 compatibility
    private void createCameraPreviewSession() {
        Log.d(TAG, "createCameraPreviewSession: Creating camera preview session");
        try {
            Surface surface = surfaceHolder.getSurface();
            List<Surface> surfaces = Arrays.asList(surface);
            Log.d(TAG, "createCameraPreviewSession: Surface obtained from SurfaceHolder");

            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "CameraCaptureSession.StateCallback: Session configured");
                    if (cameraDevice == null) {
                        Log.e(TAG, "CameraCaptureSession.StateCallback: CameraDevice is null");
                        return;
                    }
                    captureSession = session;
                    try {
                        CaptureRequest.Builder previewRequestBuilder =
                                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewRequestBuilder.addTarget(surface);
                        Log.d(TAG, "CameraCaptureSession.StateCallback: Preview request builder created and target added");
                        session.setRepeatingRequest(previewRequestBuilder.build(),
                                null, backgroundHandler);
                        Log.d(TAG, "CameraCaptureSession.StateCallback: Repeating request set");
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "CameraCaptureSession.StateCallback: Error setting up preview", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "CameraCaptureSession.StateCallback: Session configuration failed");
                }
            };

            // Use the deprecated method for API 24 compatibility
            cameraDevice.createCaptureSession(surfaces, stateCallback, backgroundHandler);
            Log.d(TAG, "createCameraPreviewSession: Capture session creation requested");

        } catch (CameraAccessException e) {
            Log.e(TAG, "createCameraPreviewSession: Error creating capture session", e);
        }
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera: Closing camera");
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
            Log.d(TAG, "closeCamera: Capture session closed");
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
            Log.d(TAG, "closeCamera: Camera device closed");
        }
        stopBackgroundThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed");
        if (surfaceHolder.getSurface().isValid() && checkCameraPermission()) {
            Log.d(TAG, "onResume: Surface is valid and permission granted, opening camera");
            openCamera();
        } else {
            Log.w(TAG, "onResume: Surface invalid or permission not granted");
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: Activity paused");
        closeCamera();
        super.onPause();
    }
}