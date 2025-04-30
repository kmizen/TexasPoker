package com.example.pokeradvisor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "PokerAdvisor";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final long FRAME_PROCESS_INTERVAL_MS = 2000; // Process every 2 seconds

    private JavaCamera2View cameraView;
    private Mat rgbaMat;
    private long lastProcessedTime = 0;

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

        if (!OpenCVLoader.initLocal()) {
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted in onResume");
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
        rgbaMat = inputFrame.rgba();

        // Throttle frame processing to once every 2 seconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessedTime < FRAME_PROCESS_INTERVAL_MS) {
            // Add the green overlay even on skipped frames to maintain visual consistency
            Mat overlay = new Mat(rgbaMat.size(), rgbaMat.type(), new Scalar(0, 255, 0, 100));
            Core.addWeighted(rgbaMat, 0.8, overlay, 0.2, 0.0, rgbaMat);
            overlay.release();
            return rgbaMat;
        }
        lastProcessedTime = currentTime;

        Log.d(TAG, "Processing frame: " + rgbaMat.cols() + "x" + rgbaMat.rows());

        // Convert to HSV for color-based segmentation
        Mat hsvMat = new Mat();
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGB2HSV);
        Log.d(TAG, "Converted to HSV: " + hsvMat.cols() + "x" + hsvMat.rows());

        // Create a mask for white regions (cards have a white background)
        Mat mask = new Mat();
        // White in HSV: Hue doesn't matter, low saturation, high value
        Scalar lowerWhite = new Scalar(0, 0, 200); // Low saturation, high value
        Scalar upperWhite = new Scalar(179, 50, 255); // Allow some variation
        Core.inRange(hsvMat, lowerWhite, upperWhite, mask);
        Log.d(TAG, "White mask created: " + mask.cols() + "x" + mask.rows());

        // Apply morphological operations to clean up the mask
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel); // Remove noise
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel); // Close small gaps
        Log.d(TAG, "Morphological operations applied");

        // Find contours on the mask
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Log.d(TAG, "Contours found: " + contours.size());

        // List to store identified cards
        List<String> identifiedCards = new ArrayList<>();

        // Analyze each contour for card identification
        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint contour = contours.get(i);
            double area = Imgproc.contourArea(contour);
            // Filter out small contours (likely noise)
            if (area < 500) { // Lowered threshold to capture smaller cards
                continue;
            }

            // Get the bounding rectangle for the contour
            Rect boundingRect = Imgproc.boundingRect(contour);

            // Filter by aspect ratio (cards are typically 2.5:3.5, so aspect ratio ~0.714)
            double aspectRatio = (double) boundingRect.width / boundingRect.height;
            if (aspectRatio < 0.4 || aspectRatio > 1.0) { // Relaxed range to allow for overlapping cards
                continue;
            }

            // Ensure the ROI is within the frame bounds
            if (boundingRect.x < 0 || boundingRect.y < 0 ||
                    boundingRect.x + boundingRect.width > rgbaMat.cols() ||
                    boundingRect.y + boundingRect.height > rgbaMat.rows()) {
                continue;
            }

            // Extract the ROI (region of interest) for the card
            Mat roi = rgbaMat.submat(boundingRect);

            // Identify the suit by analyzing the color in the ROI
            String suit = identifySuit(roi);
            // Identify the rank by analyzing the top-left corner of the ROI
            String rank = identifyRank(roi);

            // Combine rank and suit to form the card identifier (e.g., "Kh")
            if (suit != null && rank != null) {
                String card = rank + suit;
                identifiedCards.add(card);
                Log.d(TAG, "Identified card: " + card);
            }

            // Draw the contour on the frame in green
            Imgproc.drawContours(rgbaMat, contours, i, new Scalar(0, 255, 0), 2);

            // Release the ROI Mat
            roi.release();
        }

        // Create a string of identified cards (e.g., "Kh, As, 7d")
        String displayText = identifiedCards.isEmpty() ? "No cards identified" : String.join(", ", identifiedCards);

        // Draw the text on the frame
        Imgproc.putText(
                rgbaMat,
                displayText,
                new Point(50, 50), // Position at top-left with some padding
                Imgproc.FONT_HERSHEY_SIMPLEX,
                1.0, // Font scale
                new Scalar(255, 255, 255), // White text
                2 // Thickness
        );

        // Add the green overlay after processing to avoid interference
        Mat overlay = new Mat(rgbaMat.size(), rgbaMat.type(), new Scalar(0, 255, 0, 100));
        Core.addWeighted(rgbaMat, 0.8, overlay, 0.2, 0.0, rgbaMat);
        Log.d(TAG, "Green overlay applied");

        // Release temporary Mats
        hsvMat.release();
        mask.release();
        hierarchy.release();
        overlay.release();
        kernel.release();

        return rgbaMat;
    }

    // Helper method to identify the suit based on template matching
    private String identifySuit(Mat roi) {
        try {
            // Convert ROI to grayscale
            Mat grayRoi = new Mat();
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_RGB2GRAY);

            String[] suits = {"h", "s", "d", "c"};
            String bestSuit = null;
            double bestScore = -1;

            for (String suit : suits) {
                Mat template = loadTemplateFromAssets("suit_" + suit + ".png");
                if (template == null) continue;

                // Ensure template is grayscale
                Mat grayTemplate = new Mat();
                if (template.channels() == 3) {
                    Imgproc.cvtColor(template, grayTemplate, Imgproc.COLOR_RGB2GRAY);
                } else {
                    grayTemplate = template;
                }

                Mat result = new Mat();
                Imgproc.matchTemplate(grayRoi, grayTemplate, result, Imgproc.TM_CCOEFF_NORMED);
                double score = Core.minMaxLoc(result).maxVal;

                if (score > bestScore && score > 0.7) {
                    bestScore = score;
                    bestSuit = suit;
                }

                template.release();
                result.release();
                if (grayTemplate != template) {
                    grayTemplate.release();
                }
            }

            grayRoi.release();
            return bestSuit;
        } catch (Exception e) {
            Log.e(TAG, "Exception in identifySuit", e);
            return null;
        }
    }

    // Helper method to identify the rank using template matching
    private String identifyRank(Mat roi) {
        Mat grayRoi = new Mat();
        Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_RGB2GRAY);
        int cornerSize = Math.min(roi.rows(), roi.cols()) / 4;
        if (cornerSize <= 0) {
            grayRoi.release();
            return null;
        }
        Rect cornerRect = new Rect(0, 0, cornerSize, cornerSize);
        if (cornerRect.width > roi.cols() || cornerRect.height > roi.rows()) {
            grayRoi.release();
            return null;
        }
        Mat corner = grayRoi.submat(cornerRect);

        String[] ranks = {"A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};
        String bestRank = null;
        double bestScore = -1;

        for (String rank : ranks) {
            Mat template = loadTemplateFromAssets("rank_" + rank + ".png");
            if (template == null) continue;

            Mat result = new Mat();
            Imgproc.matchTemplate(corner, template, result, Imgproc.TM_CCOEFF_NORMED);
            double score = Core.minMaxLoc(result).maxVal;

            if (score > bestScore && score > 0.7) {
                bestScore = score;
                bestRank = rank;
            }

            template.release();
            result.release();
        }

        corner.release();
        grayRoi.release();
        return bestRank;
    }

    // Helper method to load template images from assets
    private Mat loadTemplateFromAssets(String filename) {
        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open(filename);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            Mat template = new Mat();
            Utils.bitmapToMat(bitmap, template);
            Imgproc.cvtColor(template, template, Imgproc.COLOR_BGR2GRAY);
            return template;
        } catch (IOException e) {
            Log.e(TAG, "Failed to load template: " + filename, e);
            return null;
        }
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
                finish();
            }
        }
    }
}