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
import org.opencv.android.JavaCamera2View;
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

    private JavaCamera2View cameraView;
    private Mat rgbaMat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate called");

        cameraView = findViewById(R.id.camera_view);
        if (cameraView == null) {
            Log.e(TAG, "Camera view not found in layout");
            Toast.makeText(this, "Camera view not found in layout", Toast.LENGTH_LONG).show();
            return;
        }

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed");
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_LONG).show();
            return;
        }
        Log.i(TAG, "OpenCV initialized successfully");

        cameraView.setCvCameraViewListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume called");

        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted in onResume");
            // Request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            Log.i(TAG, "Camera permission already granted");
            initializeCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause called");
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    private void initializeCamera() {
        Log.i(TAG, "initializeCamera called");
        if (cameraView != null) {
            cameraView.setCameraPermissionGranted();
            cameraView.enableView();
            Log.i(TAG, "cameraView.enableView called");
        } else {
            Log.e(TAG, "Camera view is null in initializeCamera");
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted called, width: " + width + ", height: " + height);
        rgbaMat = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped called");
        if (rgbaMat != null) {
            rgbaMat.release();
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.i(TAG, "onCameraFrame called");
        rgbaMat = inputFrame.rgba();

        // Convert to grayscale
        Mat grayMat = new Mat();
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // Apply edge detection
        Mat edges = new Mat();
        Imgproc.Canny(grayMat, edges, 50, 150);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Draw contours on the original frame
        for (int i = 0; i < contours.size(); i++) {
            Imgproc.drawContours(rgbaMat, contours, i, new Scalar(0, 255, 0), 2);
        }

        // Release temporary Mats
        grayMat.release();
        edges.release();
        hierarchy.release();

        return rgbaMat;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission granted by user");
                initializeCamera();
            } else {
                Log.w(TAG, "Camera permission denied by user");
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show();
                finish(); // Close the app if permission is denied
            }
        }
    }
}