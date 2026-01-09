
package com.biometrics.fragment

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import com.biometrics.HandLandmarkerHelper
import com.biometrics.MainViewModel
import com.biometrics.OverlayView
import com.biometrics.R


import com.biometrics.databinding.BsdkFragmentCameraSdkBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

import android.util.Base64
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import com.biometrics.BiometricsCompletedDialogFragment

import java.io.ByteArrayOutputStream
import java.io.File
import android.Manifest
import java.nio.ByteBuffer

import androidx.activity.OnBackPressedCallback

import com.biometrics.model.BiometricsResult
import com.biometrics.utils.FingerprintExtractor
import com.biometrics.viewmodel.BiometricsSharedViewModel
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener, ConfirmationDialogFragment.ConfirmationListener {

    companion object {
        private const val TAG = "RMST Biomterics"
    }

    private val fingerprintExtractor = FingerprintExtractor()
    private var _fragmentCameraBinding: BsdkFragmentCameraSdkBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val sharedViewModel: BiometricsSharedViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var latestHandLandmarkerResult: HandLandmarkerResult? = null
    private var isFocusing = false
    private var lastFocusTime = 0L
    private var isProcessingCapture = false

    // Store current ROIs from OverlayView for direct capture (in preview coordinates!)
    private var currentFingerROIs = mutableMapOf<FingerprintExtractor.FingerType, android.graphics.RectF>()


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(requireContext(), "Camera permission granted", Toast.LENGTH_SHORT).show()
                // The view is already created, so just set up the camera
                // We can't call onViewCreated again as it will lead to an infinite loop.
                setupCameraAndExecutor()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
                // Optionally, navigate away or show an error message
            }
        }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Start the HandLandmarkerHelper again when users come back
        // to the foreground.
        if (this::backgroundExecutor.isInitialized) {
            backgroundExecutor.execute {
                if (this::handLandmarkerHelper.isInitialized && handLandmarkerHelper.isClose()) {
                    handLandmarkerHelper.setupHandLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::handLandmarkerHelper.isInitialized) {
            viewModel.setMaxHands(handLandmarkerHelper.maxNumHands)
            viewModel.setMinHandDetectionConfidence(handLandmarkerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(handLandmarkerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(handLandmarkerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(handLandmarkerHelper.currentDelegate)

            // Close the HandLandmarkerHelper and release resources
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        if (this::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
            backgroundExecutor.awaitTermination(
                Long.MAX_VALUE, TimeUnit.NANOSECONDS
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            BsdkFragmentCameraSdkBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RESTRUCTURED LOGIC
        if (PermissionsFragment.hasPermissions(requireContext())) {
            // If we have permission, set up everything.
            setupCameraAndExecutor()
        } else {
            // Otherwise, launch the permission request.
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dialog = ConfirmationDialogFragment()
                dialog.show(childFragmentManager, ConfirmationDialogFragment.TAG)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

    }

    override fun onConfirmation(confirmed: Boolean) {
        if (confirmed) {
            sharedViewModel.postResult(BiometricsResult.Cancelled)
        }
    }




    private fun setupCameraAndExecutor() {
        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()

            // Set up tap-to-focus
            fragmentCameraBinding.viewFinder.setOnTouchListener { _, event ->
                val factory = fragmentCameraBinding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                true
            }
        }

        // Create the HandLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                maxNumHands = viewModel.currentMaxHands,
                currentDelegate = viewModel.currentDelegate,
                handLandmarkerHelperListener = this
            )
        }

        fragmentCameraBinding.captureButton.setOnClickListener {
            takePhoto()
        }

        // REMOVED: Old capture listener (was bypassing quality checks)
        // fragmentCameraBinding.overlay.setCaptureListener(this)

        // NEW: Quality-based capture trigger (using screenshot, not camera frame!)
        fragmentCameraBinding.overlay.onReadyToCapture = {
            // All quality checks passed - capture screenshot NOW!
            Log.d(TAG, "✓ Quality-based capture triggered - capturing preview screenshot via PixelCopy")
            if (!isProcessingCapture) {
                isProcessingCapture = true

                // Capture screenshot of preview surface using PixelCopy (async callback)
                capturePreviewScreenshot { screenshotBitmap ->
                    if (screenshotBitmap != null && latestHandLandmarkerResult != null) {
                        // Process screenshot in background
                        lifecycleScope.launch(Dispatchers.IO) {
                            captureAndProcessFrame(screenshotBitmap, latestHandLandmarkerResult!!)
                        }
                    } else {
                        Log.e(TAG, "Failed to capture screenshot or no landmarks available")
                        isProcessingCapture = false
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Screenshot capture failed. Try again.", Toast.LENGTH_SHORT).show()
                            fragmentCameraBinding.overlay.resetCapture()
                        }
                    }
                }
            }
        }

        // Initialize progress bar
        fragmentCameraBinding.progressBar.visibility = View.GONE

        // Hide capture button
        fragmentCameraBinding.captureButton.visibility = View.GONE
    }
//    private fun initBottomSheetControls() {
//        // init bottom sheet settings
//        fragmentCameraBinding.bottomSheetLayout.maxHandsValue.text =
//            viewModel.currentMaxHands.toString()
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence
//            )
//
//        // When clicked, lower hand detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
//            if (handLandmarkerHelper.minHandDetectionConfidence >= 0.2) {
//                handLandmarkerHelper.minHandDetectionConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise hand detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
//            if (handLandmarkerHelper.minHandDetectionConfidence <= 0.8) {
//                handLandmarkerHelper.minHandDetectionConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower hand tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
//            if (handLandmarkerHelper.minHandTrackingConfidence >= 0.2) {
//                handLandmarkerHelper.minHandTrackingConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise hand tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
//            if (handLandmarkerHelper.minHandTrackingConfidence <= 0.8) {
//                handLandmarkerHelper.minHandTrackingConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower hand presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
//            if (handLandmarkerHelper.minHandPresenceConfidence >= 0.2) {
//                handLandmarkerHelper.minHandPresenceConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise hand presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
//            if (handLandmarkerHelper.minHandPresenceConfidence <= 0.8) {
//                handLandmarkerHelper.minHandPresenceConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, reduce the number of hands that can be detected at a
//        // time
//        fragmentCameraBinding.bottomSheetLayout.maxHandsMinus.setOnClickListener {
//            if (handLandmarkerHelper.maxNumHands > 1) {
//                handLandmarkerHelper.maxNumHands--
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, increase the number of hands that can be detected
//        // at a time
//        fragmentCameraBinding.bottomSheetLayout.maxHandsPlus.setOnClickListener {
//            if (handLandmarkerHelper.maxNumHands < 2) {
//                handLandmarkerHelper.maxNumHands++
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, change the underlying hardware used for inference.
//        // Current options are CPU and GPU
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//            viewModel.currentDelegate, false
//        )
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
//            object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(
//                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
//                ) {
//                    try {
//                        handLandmarkerHelper.currentDelegate = p2
//                        updateControlsUi()
//                    } catch(e: UninitializedPropertyAccessException) {
//                        Log.e(TAG, "HandLandmarkerHelper has not been initialized yet.")
//                    }
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//                    /* no op */
//                }
//            }
//    }

    // Update the values displayed in the bottom sheet. Reset Handlandmarker
    // helper.
//    private fun updateControlsUi() {
//        fragmentCameraBinding.bottomSheetLayout.maxHandsValue.text =
//            handLandmarkerHelper.maxNumHands.toString()
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                handLandmarkerHelper.minHandDetectionConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                handLandmarkerHelper.minHandTrackingConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                handLandmarkerHelper.minHandPresenceConfidence
//            )
//
//        // Needs to be cleared instead of reinitialized because the GPU
//        // delegate needs to be initialized on the thread using it when applicable
//        backgroundExecutor.execute {
//            handLandmarkerHelper.clearHandLandmarker()
//            handLandmarkerHelper.setupHandLandmarker()
//        }
//        fragmentCameraBinding.overlay.clear()
//    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageCapture = ImageCapture.Builder().build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // CRITICAL: Use TextureView instead of SurfaceView for PreviewView
            // This allows PixelCopy to capture the camera preview content
            // SurfaceView renders on separate layer and appears black in screenshots
            fragmentCameraBinding.viewFinder.implementationMode =
                androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE  // Uses TextureView

            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer, imageCapture
            )
            camera?.cameraControl?.enableTorch(true)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectHand(imageProxy: ImageProxy) {
        // OLD LOGIC REMOVED: No longer capturing from ImageProxy
        // Now using preview screenshot capture (triggered by overlay.onReadyToCapture)

        // Run hand detection on full camera frame (needed for MediaPipe)
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )

        // Screenshot capture is now handled by overlay.onReadyToCapture callback
        // which takes screenshot of preview surface (zoomed view)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after hand have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: HandLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {

                // Store latest result for capture
                latestHandLandmarkerResult = resultBundle.results.first()

//                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
//                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Store current ROIs for direct capture (these are the green boxes!)
                currentFingerROIs = fragmentCameraBinding.overlay.getFingerprintROIs().toMutableMap()

                // Trigger auto-focus on hand detection
                if (resultBundle.results.first().landmarks().isNotEmpty()) {
                    if (!isFocusing && (System.currentTimeMillis() - lastFocusTime > 1000)) {
                        isFocusing = true
                        val landmarks = resultBundle.results.first().landmarks().first()
                        val centerX = landmarks.map { it.x() }.average().toFloat()
                        val centerY = landmarks.map { it.y() }.average().toFloat()

                        val viewWidth = fragmentCameraBinding.viewFinder.width
                        val viewHeight = fragmentCameraBinding.viewFinder.height

                        val point = fragmentCameraBinding.viewFinder.meteringPointFactory.createPoint(centerX * viewWidth, centerY * viewHeight)
                        val action = FocusMeteringAction.Builder(point).build()
                        val future = camera?.cameraControl?.startFocusAndMetering(action)
                        future?.addListener({
                            isFocusing = false
                            lastFocusTime = System.currentTimeMillis()
                        }, ContextCompat.getMainExecutor(requireContext()))
                    }
                }

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == HandLandmarkerHelper.GPU_ERROR) {
//                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//                    HandLandmarkerHelper.DELEGATE_CPU, false
//                )
            }
        }
    }

    /**
     * NEW: Capture screenshot of preview surface using PixelCopy (what user sees on screen)
     * This captures the ZOOMED view, not the full camera frame!
     * Uses PixelCopy API because PreviewView uses SurfaceView which can't be captured with draw()
     *
     * CLEAN CAPTURE: Temporarily hides overlay (green boxes, labels) for clean fingerprint images
     * IMPORTANT: Must wait for the view to re-render after hiding overlay before capturing!
     */
    private fun capturePreviewScreenshot(callback: (Bitmap?) -> Unit) {
        try {
            val viewFinder = fragmentCameraBinding.viewFinder
            val overlay = fragmentCameraBinding.overlay

            // HIDE overlay before capture (no green boxes, labels, etc. in screenshot!)
            val originalVisibility = overlay.visibility
            overlay.visibility = View.INVISIBLE
            Log.d(TAG, "🙈 Overlay hidden for clean capture")

            // CRITICAL: Wait for the view hierarchy to actually render without the overlay
            // Using postDelayed to ensure at least one frame is drawn without the overlay
            viewFinder.postDelayed({
                try {
                    // Create bitmap matching preview dimensions
                    val bitmap = Bitmap.createBitmap(
                        viewFinder.width,
                        viewFinder.height,
                        Bitmap.Config.ARGB_8888
                    )

                    // Use PixelCopy to capture SurfaceView content
                    val locationInWindow = IntArray(2)
                    viewFinder.getLocationInWindow(locationInWindow)

                    val rect = android.graphics.Rect(
                        locationInWindow[0],
                        locationInWindow[1],
                        locationInWindow[0] + viewFinder.width,
                        locationInWindow[1] + viewFinder.height
                    )

                    // PixelCopy from window surface
                    activity?.window?.let { window ->
                        android.view.PixelCopy.request(
                            window,
                            rect,
                            bitmap,
                            { copyResult ->
                                // RESTORE overlay visibility immediately after capture
                                overlay.visibility = originalVisibility
                                Log.d(TAG, "👁️ Overlay restored")

                                if (copyResult == android.view.PixelCopy.SUCCESS) {
                                    Log.d(TAG, "📸 Clean screenshot captured: ${bitmap.width}x${bitmap.height}px (no UI overlay!)")
                                    callback(bitmap)
                                } else {
                                    Log.e(TAG, "PixelCopy failed with result: $copyResult")
                                    callback(null)
                                }
                            },
                            android.os.Handler(android.os.Looper.getMainLooper())
                        )
                    } ?: run {
                        // Restore overlay even if window not available
                        overlay.visibility = originalVisibility
                        Log.e(TAG, "Window not available for PixelCopy")
                        callback(null)
                    }
                } catch (e: Exception) {
                    // Restore overlay even on exception
                    overlay.visibility = originalVisibility
                    Log.e(TAG, "Failed during PixelCopy: ${e.message}", e)
                    callback(null)
                }
            }, 50) // Wait 50ms (~3 frames at 60fps) to ensure overlay is hidden in render
        } catch (e: Exception) {
            // Restore overlay even on exception
            fragmentCameraBinding.overlay.visibility = View.VISIBLE
            Log.e(TAG, "Failed to capture preview screenshot: ${e.message}", e)
            callback(null)
        }
    }

    /**
     * OLD: Convert ImageProxy to Bitmap with proper orientation
     * ImageAnalysis is configured with OUTPUT_IMAGE_FORMAT_RGBA_8888
     * IMPORTANT: Rewinds buffer after reading so HandLandmarkerHelper can use it
     *
     * NOTE: Camera sensor captures in LANDSCAPE (640x480)
     *       but preview shows PORTRAIT (480x640) due to rotation.
     *       We must rotate the bitmap to match preview orientation!
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val planeProxy = imageProxy.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val pixelStride = planeProxy.pixelStride
        val rowStride = planeProxy.rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width

        // Save original buffer position
        val originalPosition = buffer.position()

        // Rewind to start
        buffer.rewind()

        // Create bitmap with padding
        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )

        // Copy RGBA pixels directly from buffer to bitmap
        bitmap.copyPixelsFromBuffer(buffer)

        // CRITICAL: Restore buffer position so detectLiveStream can read it!
        buffer.position(originalPosition)

        // Crop to actual size if there's row padding
        val croppedBitmap = if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
        }

        Log.d(TAG, "Original bitmap from sensor: ${croppedBitmap.width}x${croppedBitmap.height}")

        // Rotate 90° clockwise to match preview orientation
        // Camera sensor: 640x480 (landscape) → Preview: 480x640 (portrait)
        val matrix = android.graphics.Matrix()
        matrix.postRotate(90f)

        val rotatedBitmap = Bitmap.createBitmap(
            croppedBitmap,
            0,
            0,
            croppedBitmap.width,
            croppedBitmap.height,
            matrix,
            true
        )

        // Recycle original to save memory
        if (croppedBitmap != rotatedBitmap) {
            croppedBitmap.recycle()
        }

        Log.d(TAG, "Rotated bitmap to match preview: ${rotatedBitmap.width}x${rotatedBitmap.height}")

        return rotatedBitmap
    }

    /**
     * NEW OPTIMIZED METHOD: Directly crop fingerprint ROIs from camera frame
     * No need to re-run MediaPipe! We use the green boxes already calculated by OverlayView.
     */
    private fun captureAndProcessFrame(bitmap: Bitmap, landmarks: HandLandmarkerResult) {
        try {
            activity?.runOnUiThread {
                fragmentCameraBinding.progressBar.visibility = View.VISIBLE
                camera?.cameraControl?.enableTorch(false)
            }

            Log.d(TAG, "====================================================")
            Log.d(TAG, "🎯 SCREENSHOT-BASED CAPTURE (What you see = What you get!)")
            Log.d(TAG, "📸 Screenshot bitmap: ${bitmap.width}x${bitmap.height}, format: ${bitmap.config}")
            Log.d(TAG, "📦 Green boxes to capture: ${currentFingerROIs.size}")
            Log.d(TAG, "✅ ROIs are in preview coordinates (perfect match!)")

            // Check if we have valid ROIs
            if (currentFingerROIs.isEmpty()) {
                Log.e(TAG, "❌ No ROIs available - aborting capture")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "No fingerprint regions detected. Try again.", Toast.LENGTH_SHORT).show()
                    fragmentCameraBinding.progressBar.visibility = View.GONE
                    isProcessingCapture = false
                    fragmentCameraBinding.overlay.resetCapture()
                }
                return
            }

            // Directly crop fingerprint regions from bitmap using stored ROIs
            val fingerprints = mutableListOf<FingerprintExtractor.FingerprintImage>()

            for ((fingerType, roi) in currentFingerROIs) {
                try {
                    // Debug: Log ROI coordinates
                    Log.d(TAG, "🔲 $fingerType ROI: " +
                        "L=${roi.left.toInt()}, T=${roi.top.toInt()}, " +
                        "R=${roi.right.toInt()}, B=${roi.bottom.toInt()}, " +
                        "W=${roi.width().toInt()}, H=${roi.height().toInt()}")

                    // Validate ROI is within bitmap bounds
                    if (roi.left < 0 || roi.top < 0 || roi.right > bitmap.width || roi.bottom > bitmap.height) {
                        Log.w(TAG, "⚠ $fingerType ROI out of bounds - skipping " +
                            "(Screenshot: ${bitmap.width}x${bitmap.height})")
                        continue
                    }

                    // Ensure ROI has reasonable size
                    if (roi.width() < 30 || roi.height() < 30) {
                        Log.w(TAG, "⚠ $fingerType ROI too small (${roi.width().toInt()}x${roi.height().toInt()}) - skipping")
                        continue
                    }

                    // Crop the finger region directly from the bitmap
                    val fingerBitmap = Bitmap.createBitmap(
                        bitmap,
                        roi.left.toInt(),
                        roi.top.toInt(),
                        roi.width().toInt(),
                        roi.height().toInt()
                    )

                    // Ensure highest quality format (ARGB_8888)
                    val highQualityBitmap = if (fingerBitmap.config != Bitmap.Config.ARGB_8888) {
                        fingerBitmap.copy(Bitmap.Config.ARGB_8888, false).also { fingerBitmap.recycle() }
                    } else {
                        fingerBitmap
                    }

                    Log.d(TAG, "✓ Cropped $fingerType: ${highQualityBitmap.width}x${highQualityBitmap.height}px from ROI")

                    // Assess quality using OpenCV (with proper initialization check)
                    val quality = try {
                        // Initialize OpenCV if not already done
                        if (!org.opencv.android.OpenCVLoader.initDebug()) {
                            Log.w(TAG, "OpenCV not initialized, using default quality score")
                            80f  // Default quality score when OpenCV unavailable
                        } else {
                            assessFingerprintQuality(highQualityBitmap)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Quality assessment failed for $fingerType: ${e.message}")
                        75f  // Default quality on error
                    }

                    Log.d(TAG, "  $fingerType quality: ${quality.toInt()}/100")

                    // Add to list (accept all for now - server will validate)
                    fingerprints.add(
                        FingerprintExtractor.FingerprintImage(
                            fingerType = fingerType,
                            bitmap = highQualityBitmap,
                            qualityScore = quality,
                            roi = roi
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error cropping $fingerType: ${e.message}", e)
                }
            }

            if (fingerprints.isNotEmpty()) {
                val avgQuality = fingerprints.map { it.qualityScore }.average()
                Log.d(TAG, "====================================================")
                Log.d(TAG, "✅ SUCCESS: Captured ${fingerprints.size}/4 fingerprints from SCREENSHOT!")
                Log.d(TAG, "📸 Source: Preview surface (zoomed view - high quality!)")
                Log.d(TAG, "Average quality: ${avgQuality.toInt()}/100")
                fingerprints.forEach { fp ->
                    Log.d(TAG, "  ${fp.fingerType}: ${fp.bitmap.width}x${fp.bitmap.height}px @ ${fp.qualityScore.toInt()}%")
                }
                Log.d(TAG, "====================================================")

                // Save fingerprints (no full hand image needed)
                saveFingerprints(fingerprints, bitmap)

                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "✓ Captured ${fingerprints.size} fingerprints!",
                        Toast.LENGTH_LONG
                    ).show()

                    fragmentCameraBinding.progressBar.visibility = View.GONE
                    sharedViewModel.postResult(BiometricsResult.Success("LOCAL_SAVE_${System.currentTimeMillis()}"))
                }
            } else {
                Log.e(TAG, "====================================================")
                Log.e(TAG, "❌ FAILURE: No valid fingerprints captured")
                Log.e(TAG, "====================================================")

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to capture fingerprints. Try again.", Toast.LENGTH_LONG).show()
                    fragmentCameraBinding.progressBar.visibility = View.GONE
                    isProcessingCapture = false
                    fragmentCameraBinding.overlay.resetCapture()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in direct ROI capture: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Capture error. Please try again.", Toast.LENGTH_SHORT).show()
                fragmentCameraBinding.progressBar.visibility = View.GONE
                isProcessingCapture = false
                fragmentCameraBinding.overlay.resetCapture()
            }
        } finally {
            isProcessingCapture = false
        }
    }

    /**
     * Assess fingerprint image quality using OpenCV
     * NOTE: Caller must ensure OpenCV is initialized before calling this!
     */
    private fun assessFingerprintQuality(bitmap: Bitmap): Float {
        var mat: org.opencv.core.Mat? = null
        var gray: org.opencv.core.Mat? = null
        var laplacian: org.opencv.core.Mat? = null
        var mean: org.opencv.core.MatOfDouble? = null
        var stddev: org.opencv.core.MatOfDouble? = null

        try {
            mat = org.opencv.core.Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            gray = org.opencv.core.Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            var totalScore = 0f
            var scoreCount = 0

            // 1. Sharpness (Laplacian variance)
            laplacian = org.opencv.core.Mat()
            Imgproc.Laplacian(gray, laplacian, org.opencv.core.CvType.CV_64F)
            mean = org.opencv.core.MatOfDouble()
            stddev = org.opencv.core.MatOfDouble()
            org.opencv.core.Core.meanStdDev(laplacian, mean, stddev)
            val sharpness = (stddev.get(0, 0)[0] / 50.0).coerceIn(0.0, 1.0)
            totalScore += sharpness.toFloat() * 100
            scoreCount++

            // 2. Contrast (standard deviation)
            org.opencv.core.Core.meanStdDev(gray, mean, stddev)
            val contrast = (stddev.get(0, 0)[0] / 128.0).coerceIn(0.0, 1.0)
            totalScore += contrast.toFloat() * 100
            scoreCount++

            // 3. Brightness check
            val brightness = mean.get(0, 0)[0]
            val brightnessScore = if (brightness in 60.0..180.0) {
                1.0 - kotlin.math.abs(brightness - 120.0) / 120.0
            } else {
                0.0
            }
            totalScore += brightnessScore.toFloat() * 100
            scoreCount++

            return totalScore / scoreCount
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV quality assessment error: ${e.message}", e)
            return 75f  // Default quality on error
        } finally {
            // Cleanup resources
            try {
                mat?.release()
                gray?.release()
                laplacian?.release()
                mean?.release()
                stddev?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing OpenCV resources: ${e.message}")
            }
        }
    }

    /**
     * DEBUG: Save 3x3 grid of screenshot to see where hand actually appears
     */
    private fun saveDebugGrid(screenshot: Bitmap) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())

        Log.d(TAG, "🔍 DEBUG: Saving 3x3 grid of screenshot...")

        // Save full screenshot first
        saveImageToGallery(screenshot, "${timestamp}_00_FULL_SCREENSHOT")
        Log.d(TAG, "  ✓ Saved full screenshot: ${screenshot.width}x${screenshot.height}px")

        // Calculate grid dimensions
        val pieceWidth = screenshot.width / 3
        val pieceHeight = screenshot.height / 3

        val rows = listOf("TOP", "MIDDLE", "BOTTOM")
        val cols = listOf("LEFT", "CENTER", "RIGHT")

        var pieceNumber = 1
        for (row in 0..2) {
            for (col in 0..2) {
                try {
                    val x = col * pieceWidth
                    val y = row * pieceHeight

                    val piece = Bitmap.createBitmap(
                        screenshot,
                        x,
                        y,
                        pieceWidth,
                        pieceHeight
                    )

                    val pieceName = "${timestamp}_${String.format("%02d", pieceNumber)}_${rows[row]}_${cols[col]}"
                    saveImageToGallery(piece, pieceName)
                    Log.d(TAG, "  ✓ Saved piece $pieceNumber: ${rows[row]}_${cols[col]} (${pieceWidth}x${pieceHeight}px)")

                    pieceNumber++
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ Failed to save grid piece [${row},${col}]: ${e.message}")
                }
            }
        }

        Log.d(TAG, "🔍 DEBUG: Saved 9 grid pieces + full screenshot to Pictures/HandLandmarker/")
    }

    /**
     * Save fingerprints directly (only 4 finger images, no debug images)
     */
    private fun saveFingerprints(
        fingerprints: List<FingerprintExtractor.FingerprintImage>,
        handBitmap: Bitmap? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())

        Log.d(TAG, "💾 Saving ${fingerprints.size} fingerprint images to gallery...")

        // Save each fingerprint with high quality
        for (fingerprint in fingerprints) {
            val fingerprintName = "${timestamp}_${fingerprint.fingerType.name}_Q${fingerprint.qualityScore.toInt()}"
            saveImageToGallery(fingerprint.bitmap, fingerprintName)
            Log.d(TAG, "  ✓ Saved ${fingerprint.fingerType.name}: ${fingerprint.bitmap.width}x${fingerprint.bitmap.height}px @ Q${fingerprint.qualityScore.toInt()}")
        }

        Log.d(TAG, "💾 All fingerprint images saved to Pictures/HandLandmarker/")
    }

    /**
     * OLD METHOD (kept for compatibility)
     */
    private fun saveHandAndFingerprints(
        handBitmap: Bitmap,
        extraction: FingerprintExtractor.ExtractionResult
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())

        Log.d(TAG, "💾 Saving images to gallery...")

        // Save hand image
        val handName = "${timestamp}_HAND"
        saveImageToGallery(handBitmap, handName)
        Log.d(TAG, "  ✓ Saved hand image: $handName")

        // Save each fingerprint
        for (fingerprint in extraction.fingerprints) {
            val fingerprintName = "${timestamp}_${fingerprint.fingerType.name}"
            saveImageToGallery(fingerprint.bitmap, fingerprintName)
            Log.d(TAG, "  ✓ Saved ${fingerprint.fingerType.name}: ${fingerprint.bitmap.width}x${fingerprint.bitmap.height}px (quality: ${fingerprint.qualityScore.toInt()})")
        }

        Log.d(TAG, "💾 All images saved to Pictures/HandLandmarker/")
    }

    /**
     * Save a bitmap to gallery with maximum quality
     * Uses PNG for fingerprints (lossless) to preserve all detail for matching
     */
    private fun saveImageToGallery(bitmap: Bitmap, displayName: String): Uri? {
        // Ensure bitmap is in highest quality format
        val highQualityBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        // Use PNG for lossless compression (critical for fingerprint matching)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HandLandmarker")
        }

        val uri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                // PNG is lossless - quality parameter is ignored but set to 100 for clarity
                highQualityBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }

        return uri
    }

    private fun takePhoto() {
        Toast.makeText(requireContext(), "Capturing...", Toast.LENGTH_SHORT).show()
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped name and MediaStore entry.
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HandLandmarker")
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                    onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo saved locally: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

                    // === API UPLOAD DISABLED - IMAGE SAVED LOCALLY ===
                    // uploadImageToApi(output.savedUri)

                    Log.d(TAG, msg)

                    // Show success and finish
                    activity?.runOnUiThread {
                        fragmentCameraBinding.progressBar.visibility = View.GONE
                        sharedViewModel.postResult(BiometricsResult.Success("LOCAL_SAVE_${System.currentTimeMillis()}"))
                    }
                }
            }
        )
    }


    // ========================================================================
    // === API UPLOAD CODE - COMMENTED OUT (IMAGES NOW SAVED LOCALLY) ===
    // ========================================================================
    /*
    private fun uploadImageToApi(imageUri: Uri?) {
        if (imageUri == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            activity?.runOnUiThread {
                fragmentCameraBinding.progressBar.visibility = View.VISIBLE
                camera?.cameraControl?.enableTorch(false)
            }

            try {
                val token = sharedViewModel.token
                if (token == null) {
                    sharedViewModel.postResult(BiometricsResult.Error("Auth token not found"))
                    return@launch
                }

                // Decode the image
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, imageUri)

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val imageBytes = outputStream.toByteArray()

                // Convert image bytes to Base64
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                // Create JSON body
                val json = JSONObject().apply {
                    put("imageData", base64Image)
                }
                val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())

                // Build request
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://demo.rmstservices.com/biometrics/register")
                    .post(requestBody)
                    .addHeader("Authorization", "Token " + token)
                    .build()

                // Send
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    // Delete the image after successful upload
                    try {
                        requireContext().contentResolver.delete(imageUri, null, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting image: $imageUri", e)
                    }

                    val resultJson = JSONObject(responseBody)
                    val transactionId = resultJson.optString("transactionId", "N/A")
                    sharedViewModel.postResult(BiometricsResult.Success(transactionId))

                } else {
                    sharedViewModel.postResult(BiometricsResult.Error("Upload failed: ${response.code} ${response.message}"))
                }
            } catch (e: Exception) {
                sharedViewModel.postResult(BiometricsResult.Error("Upload error: ${e.message}"))
            } finally {
                activity?.runOnUiThread {
                    fragmentCameraBinding.progressBar.visibility = View.GONE
                }
            }
        }
    }
    */
    // ========================================================================

    // OLD onCapture METHOD REMOVED
    // Now using quality-based trigger: fragmentCameraBinding.overlay.onReadyToCapture
    // This ensures ALL quality checks pass before capture!

}
