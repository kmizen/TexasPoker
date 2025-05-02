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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "PokerAdvisor";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final long FRAME_PROCESS_INTERVAL_MS = 2000; // Process every 2 seconds
    private static final long UPDATE_INTERVAL = 1000; // Update text every 1 second (in milliseconds)

    private JavaCamera2View cameraView;
    private Mat rgbaMat;
    private long lastProcessedTime = 0;
    private String lastDisplayedText = ""; // Track the last displayed text
    private long lastUpdateTime = 0; // Track the last time the text was updated

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

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessedTime < FRAME_PROCESS_INTERVAL_MS) {
            Mat overlay = new Mat(rgbaMat.size(), rgbaMat.type(), new Scalar(0, 255, 0, 100));
            Core.addWeighted(rgbaMat, 0.8, overlay, 0.2, 0.0, rgbaMat);
            Imgproc.putText(rgbaMat, lastDisplayedText, new Point(50, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 255), 2);
            overlay.release();
            return rgbaMat;
        }
        lastProcessedTime = currentTime;

        Bitmap bitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbaMat, bitmap);

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    StringBuilder resultText = new StringBuilder();
                    for (Text.TextBlock block : text.getTextBlocks()) {
                        String blockText = block.getText().trim();
                        Log.d(TAG, "OCR Block Text: " + blockText);
                        if (isCardText(blockText)) {
                            resultText.append(blockText).append(", ");
                        }
                    }
                    synchronized (this) {
                        lastDisplayedText = resultText.length() > 0 ? resultText.substring(0, resultText.length() - 2) : "No cards identified";
                        lastUpdateTime = System.currentTimeMillis();
                    }
                    bitmap.recycle(); // Recycle after OCR completes
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR Failed: " + e.getMessage());
                    synchronized (this) {
                        lastDisplayedText = "OCR Error";
                        lastUpdateTime = System.currentTimeMillis();
                    }
                    bitmap.recycle(); // Recycle after OCR completes
                });

        Mat overlay = new Mat(rgbaMat.size(), rgbaMat.type(), new Scalar(0, 255, 0, 100));
        Core.addWeighted(rgbaMat, 0.8, overlay, 0.2, 0.0, rgbaMat);
        Imgproc.putText(rgbaMat, lastDisplayedText, new Point(50, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 255), 2);
        overlay.release();

        return rgbaMat;
    }

    // Helper method to validate card text (e.g., "Kh", "10s")
    private boolean isCardText(String text) {
        String pattern = "^[AKQJ10][hscd]$|^[2-9][hscd]$";
        return text.matches(pattern);
    }

    // Helper method to identify the suit based on template matching
    private String identifySuit(Mat roi) {
        // Convert ROI to grayscale
        Mat grayRoi = new Mat();
        Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_RGB2GRAY);
        Log.d(TAG, "Original grayRoi size: " + grayRoi.cols() + "x" + grayRoi.rows());

        String[] suits = {"h", "s", "d", "c"};
        String bestSuit = null;
        double bestScore = -1;

        for (String suit : suits) {
            Mat template = loadTemplateFromAssets("suit_" + suit + ".png");
            if (template == null) {
                Log.w(TAG, "Failed to load template: suit_" + suit + ".png");
                continue;
            }

            // Ensure template is grayscale
            Mat grayTemplate = new Mat();
            if (template.channels() == 3) {
                Imgproc.cvtColor(template, grayTemplate, Imgproc.COLOR_RGB2GRAY);
            } else {
                grayTemplate = template;
            }
            Log.d(TAG, "Template suit_" + suit + " size: " + grayTemplate.cols() + "x" + grayTemplate.rows());

            // Ensure grayRoi is at least as large as grayTemplate
            if (grayTemplate.cols() > grayRoi.cols() || grayTemplate.rows() > grayRoi.rows()) {
                int newWidth = Math.max(grayRoi.cols(), grayTemplate.cols());
                int newHeight = Math.max(grayRoi.rows(), grayTemplate.rows());
                Mat resizedRoi = new Mat();
                Imgproc.resize(grayRoi, resizedRoi, new Size(newWidth, newHeight));
                grayRoi.release();
                grayRoi = resizedRoi;
                Log.d(TAG, "Resized grayRoi to: " + grayRoi.cols() + "x" + grayRoi.rows());
            }

            Mat result = new Mat();
            Imgproc.matchTemplate(grayRoi, grayTemplate, result, Imgproc.TM_CCOEFF_NORMED);
            double score = Core.minMaxLoc(result).maxVal;
            Log.d(TAG, "Template matching score for suit_" + suit + ": " + score);

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
        if (cornerRect.width > grayRoi.cols() || cornerRect.height > grayRoi.rows()) {
            grayRoi.release();
            return null;
        }
        Mat corner = grayRoi.submat(cornerRect);
        Log.d(TAG, "Extracted corner for rank: " + corner.cols() + "x" + corner.rows());

        String[] ranks = {"A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};
        String bestRank = null;
        double bestScore = -1;

        for (String rank : ranks) {
            Mat template = loadTemplateFromAssets("rank_" + rank + ".png");
            if (template == null) {
                Log.w(TAG, "Failed to load template: rank_" + rank + ".png");
                continue;
            }

            Log.d(TAG, "Loaded template for rank_" + rank + ": " + template.cols() + "x" + template.rows());

            // Ensure corner is at least as large as template
            if (template.cols() > corner.cols() || template.rows() > corner.rows()) {
                int newWidth = Math.max(corner.cols(), template.cols());
                int newHeight = Math.max(corner.rows(), template.rows());
                Mat resizedCorner = new Mat();
                Imgproc.resize(corner, resizedCorner, new Size(newWidth, newHeight));
                corner.release();
                corner = resizedCorner;
                Log.d(TAG, "Resized corner for rank_" + rank + " to: " + corner.cols() + "x" + corner.rows());
            }

            Mat result = new Mat();
            Imgproc.matchTemplate(corner, template, result, Imgproc.TM_CCOEFF_NORMED);
            double score = Core.minMaxLoc(result).maxVal;
            Log.d(TAG, "Template matching score for rank_" + rank + ": " + score);

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
            if (bitmap == null) return null;
            Mat template = new Mat();
            Utils.bitmapToMat(bitmap, template);
            Imgproc.cvtColor(template, template, Imgproc.COLOR_BGR2GRAY);
            return template;
        } catch (IOException e) {
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