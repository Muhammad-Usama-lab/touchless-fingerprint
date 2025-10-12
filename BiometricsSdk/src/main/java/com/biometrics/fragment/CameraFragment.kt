
package com.biometrics.fragment

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
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
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import com.biometrics.BiometricsCompletedDialogFragment

import java.io.ByteArrayOutputStream
import android.Manifest


import androidx.activity.OnBackPressedCallback

import com.biometrics.model.BiometricsResult
import com.biometrics.viewmodel.BiometricsSharedViewModel

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener, OverlayView.CaptureListener, ConfirmationDialogFragment.ConfirmationListener {

    companion object {
        private const val TAG = "RMST Biomterics"
    }

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

        fragmentCameraBinding.overlay.setCaptureListener(this)

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
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
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
//                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
//                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

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

    private fun takePhoto() {
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
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    uploadImageToApi(output.savedUri)
                    Log.d(TAG, msg)


                }
            }
        )
    }


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

                // Decode and compress the image
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, imageUri)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val compressedBytes = outputStream.toByteArray()

                // Convert to Base64
                val base64Image = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

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

    

    override fun onCapture(result: HandLandmarkerResult) {
        latestHandLandmarkerResult = result
        activity?.runOnUiThread {
            val landmarks = result.landmarks().first()
            val centerX = landmarks.map { it.x() }.average().toFloat()
            val centerY = landmarks.map { it.y() }.average().toFloat()

            val viewWidth = fragmentCameraBinding.viewFinder.width
            val viewHeight = fragmentCameraBinding.viewFinder.height

            val point = fragmentCameraBinding.viewFinder.meteringPointFactory.createPoint(centerX * viewWidth, centerY * viewHeight)
            val action = FocusMeteringAction.Builder(point).build()
            val future = camera?.cameraControl?.startFocusAndMetering(action)
            future?.addListener({
                takePhoto()
            }, ContextCompat.getMainExecutor(requireContext()))
        }
    }


}
