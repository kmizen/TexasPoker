package com.example.pokeradvisor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "PokerAdvisor";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private CameraBridgeViewBase cameraView;
    private Mat mat;
    private int cameraId = 0; // 0 for rear camera, 1 for front camera

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate called");
        setContentView(R.layout.activity_main);

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed");
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_LONG).show();
            return;
        } else {
            Log.i(TAG, "OpenCV initialized successfully");
        }

        cameraView = findViewById(R.id.camera_view);
        if (cameraView == null) {
            Log.e(TAG, "cameraView is null - check activity_main.xml layout");
            Toast.makeText(this, "Camera view not found in layout", Toast.LENGTH_LONG).show();
            return;
        }

        cameraView.setCvCameraViewListener(this);
        cameraView.setCameraIndex(cameraId); // Explicitly set camera index (0 = rear, 1 = front)
        Log.i(TAG, "Camera index set to: " + cameraId);

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Requesting camera permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            Log.i(TAG, "Camera permission already granted");
            initializeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult called, requestCode: " + requestCode);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission granted");
                initializeCamera();
            } else {
                Log.w(TAG, "Camera permission denied");
                Toast.makeText(this, "Camera permission is required to use this app",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeCamera() {
        Log.i(TAG, "initializeCamera called");
        if (cameraView != null) {
            cameraView.enableView();
            Log.i(TAG, "cameraView.enableView called");
        } else {
            Log.e(TAG, "cameraView is null in initializeCamera");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume called");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            if (cameraView != null) {
                cameraView.enableView();
                Log.i(TAG, "cameraView.enableView called in onResume");
            } else {
                Log.e(TAG, "cameraView is null in onResume");
            }
        } else {
            Log.w(TAG, "Camera permission not granted in onResume");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause called");
        if (cameraView != null) {
            cameraView.disableView();
            Log.i(TAG, "cameraView.disableView called in onPause");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
        if (cameraView != null) {
            cameraView.disableView();
            Log.i(TAG, "cameraView.disableView called in onDestroy");
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted called, width: " + width + ", height: " + height);
        mat = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped called");
        if (mat != null) {
            mat.release();
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.i(TAG, "onCameraFrame called");
        mat = inputFrame.rgba();
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.threshold(gray, gray, 100, 255, Imgproc.THRESH_BINARY);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.size() > 0) {
            Log.i(TAG, "Found " + contours.size() + " contours");
            Imgproc.drawContours(mat, contours, -1, new Scalar(0, 255, 0), 5);
        } else {
            Log.i(TAG, "No contours found");
        }

        gray.release();
        hierarchy.release();
        return mat;
    }
}