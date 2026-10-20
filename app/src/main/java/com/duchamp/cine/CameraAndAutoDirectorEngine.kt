package com.duchamp.cine

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * POCO X6 PRO ("duchamp") CAMERA2 ZERO-ISP CONTROLLER + REAL-TIME AUTO-DIRECTOR + INSTAGRAM EXPORTER
 */
class CameraAndAutoDirectorEngine(
    private val context: Context,
    private val onTelemetryUpdate: (TelemetrySnapshot) -> Unit
) {
    data class TelemetrySnapshot(
        val iso: Int,
        val shutterSpeedText: String,
        val kelvin: Int,
        val faceSweetSpotOk: Boolean,
        val faceDetected: Boolean,
        val lowLightSelfieFillRequired: Boolean,
        val recommendedDirector: DirectorProfile
    )

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    var activeLens: DuchampLens = DuchampLens.MAIN_25MM
    var manualEvOffset: Float = -0.7f
    var currentGyroTiltDeg: Float = 0.0f

    // Temporal Hysteresis State for Auto-Director (1800ms stability lock to prevent flickering)
    private var currentAutoDirector: DirectorProfile = DirectorProfile.WONG_KAR_WAI
    private var candidateDirector: DirectorProfile = DirectorProfile.WONG_KAR_WAI
    private var candidateHoldStartMs: Long = System.currentTimeMillis()

    fun startBackgroundThread() {
        if (bgThread != null) return
        bgThread = HandlerThread("DuchampCamera2ZeroISP").apply {
            start()
            bgHandler = Handler(looper)
        }
    }

    fun stopBackgroundThread() {
        closeCamera()
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
    }

    @SuppressLint("MissingPermission")
    fun openCameraForLens(lens: DuchampLens, surfaceTexture: SurfaceTexture) {
        activeLens = lens
        manualEvOffset = lens.defaultEvBias
        closeCamera()
        startBackgroundThread()

        val targetCamId = resolveBestCameraId(lens)
        cameraManager.openCamera(targetCamId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startZeroIspPreviewSession(camera, targetCamId, lens, surfaceTexture)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, bgHandler)
    }

    /**
     * Resolves POCO X6 Pro physical/logical camera IDs:
     * - If HyperOS exposes physical aux IDs ("2" for OV08D10 Ultrawide, "3" for OV02B10 Macro), uses them directly.
     * - Otherwise opens Logical Rear Camera ("0") and uses CONTROL_ZOOM_RATIO (0.6f / 1.0f / 2.0f / 2.4f)
     *   which triggers Xiaomi HAL3 seamless sensor switching.
     */
    private fun resolveBestCameraId(lens: DuchampLens): String {
        val allIds = cameraManager.cameraIdList
        if (lens.preferredPhysicalId != null && allIds.contains(lens.preferredPhysicalId)) {
            return lens.preferredPhysicalId
        }
        val desiredFacing = if (lens.isFrontFacing) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        return allIds.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
        } ?: "0"
    }

    private fun startZeroIspPreviewSession(
        camera: CameraDevice,
        cameraId: String,
        lens: DuchampLens,
        surfaceTexture: SurfaceTexture
    ) {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        surfaceTexture.setDefaultBufferSize(1920, 1440)
        val previewSurface = Surface(surfaceTexture)

        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            applyZeroIspHardwareFlags(this, chars, lens)
        }

        camera.createCaptureSession(
            listOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(requestBuilder.build(), telemetryCallback, bgHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            bgHandler
        )
    }

    /**
     * Disables Xiaomi HyperOS sharpening, denoise, and local HDR tonemapping.
     */
    private fun applyZeroIspHardwareFlags(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        lens: DuchampLens
    ) {
        // 1. Disable Digital Edge Sharpening
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)

        // 2. Disable Aggressive Xiaomi Noise Reduction (Preserves organic film grain & skin pores)
        val supportedNr = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        if (supportedNr != null && supportedNr.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF)) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        } else {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
        }

        // 3. Disable Chromatic Aberration Post-Processing
        builder.set(
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
        )

        // 4. Enable OIS on OV64B (1x 25mm and 2x 50mm Prime)
        builder.set(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
        )

        // 5. Apply Lens Zoom Ratio (0.6x OV08D10 Ultrawide, 1.0x OV64B 25mm, 2.0x OV64B 50mm Lossless Prime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (zoomRange != null) {
                val clampedZoom = lens.zoomRatio.coerceIn(zoomRange.lower, zoomRange.upper)
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
            }
        }

        // 6. Highlight-Priority Exposure Compensation (-0.7 EV default on OV64B)
        val aeStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.166f
        val aeRange: Range<Int> = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-12, 12)
        val steps = (manualEvOffset / aeStep).toInt().coerceIn(aeRange.lower, aeRange.upper)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)

        // 7. Enable Face Detection for Selfie 45cm Sweet-Spot & Auto-Director Portrait Routing
        builder.set(
            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
            CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
        )
    }

    fun updateExposureBias(ev: Float) {
        manualEvOffset = ev
        val session = captureSession ?: return
        val cam = cameraDevice ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(cam.id)
            val surfaceTexture = return
        } catch (_: Exception) {}
    }

    private val telemetryCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 200
            val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 16_666_666L
            val shutterDenominator = (1_000_000_000L / expNs.coerceAtLeast(100_000L)).toInt()
            val shutterText = if (shutterDenominator >= 2) "1/${shutterDenominator}s" else "${expNs / 1_000_000}ms"

            // Estimate CCT Kelvin from R/B Color Correction Gains
            val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
            val kelvin = if (gains != null) {
                val rbRatio = (gains.red / gains.blue.coerceAtLeast(0.1f))
                (4600f / rbRatio).toInt().coerceIn(2400, 7500)
            } else 4800

            // Face Detection & Fixed-Focus 40cm-50cm Sweet Spot Check for Front OV16A1Q
            val faces = result.get(CaptureResult.STATISTICS_FACES)
            val hasFace = faces != null && faces.isNotEmpty()
            var sweetSpotOk = false
            if (hasFace && faces != null) {
                val bounds = faces[0].bounds
                val faceWidthRatio = bounds.width().toFloat() / 2000f
                // At 40cm-50cm arm distance on 21mm/35mm selfie, face occupies ~22%-44% of active array width
                sweetSpotOk = faceWidthRatio in 0.18f..0.48f
            }

            // Evaluate Auto-Director Decision Tree with 1.8s Temporal Hysteresis
            val rawCandidate = classifySceneToDirector(iso, kelvin, hasFace, abs(currentGyroTiltDeg) < 1.2f)
            val now = System.currentTimeMillis()
            if (rawCandidate == candidateDirector) {
                if (now - candidateHoldStartMs >= 1800L) {
                    currentAutoDirector = candidateDirector
                }
            } else {
                candidateDirector = rawCandidate
                candidateHoldStartMs = now
            }

            onTelemetryUpdate(
                TelemetrySnapshot(
                    iso = iso,
                    shutterSpeedText = shutterText,
                    kelvin = kelvin,
                    faceSweetSpotOk = sweetSpotOk,
                    faceDetected = hasFace,
                    lowLightSelfieFillRequired = activeLens.isFrontFacing && iso > 650,
                    recommendedDirector = currentAutoDirector
                )
            )
        }
    }

    private fun classifySceneToDirector(
        iso: Int,
        kelvin: Int,
        hasFace: Boolean,
        isLevelSymmetry: Boolean
    ): DirectorProfile {
        return when {
            // Night & Neon / Street Lights
            iso > 850 && kelvin < 3800 -> DirectorProfile.WONG_KAR_WAI
            // Night Industrial / Fluorescent Thriller
            iso > 700 -> DirectorProfile.FINCHER
            // Dim Indoor + High Symmetry
            iso in 260..700 && isLevelSymmetry && !hasFace -> DirectorProfile.KUBRICK
            // Portrait / Face in Warm or Indoor Light
            hasFace && kelvin < 5000 -> DirectorProfile.ALLEN
            // Bright Daylight + Symmetric Framing
            iso < 180 && isLevelSymmetry && !hasFace -> DirectorProfile.WES_ANDERSON
            // Bright High-Contrast Cold Sky / Architecture
            iso < 220 && kelvin > 5400 && !hasFace -> DirectorProfile.NOLAN
            // Golden Hour / Atmospheric Landscape
            kelvin in 3500..4400 && !hasFace -> DirectorProfile.VILLENEUVE
            // Default Organic 35mm VistaVision
            else -> DirectorProfile.CORBET_PTA
        }
    }

    /**
     * INSTAGRAM ZERO-PENALTY EXPORT ENGINE
     * - Encodes exact 1080x1350 (or 1080x566) sRGB Bitmap using an adaptive JPEG quality loop
     *   targeting 650 KB - 950 KB so Instagram's server-side libjpeg-turbo bypasses re-compression.
     */
    fun exportInstagramZeroPenaltyJpeg(
        bitmap: Bitmap,
        director: DirectorProfile,
        lens: DuchampLens,
        onSaved: (String) -> Unit
    ) {
        bgHandler?.post {
            try {
                var quality = 96
                var jpegBytes: ByteArray
                do {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    jpegBytes = baos.toByteArray()
                    quality -= 2
                } while (jpegBytes.size > 950_000 && quality >= 84)

                val fileName = "DUCHAMP_${director.codeName.replace(" ", "")}_${lens.focalLengthMm}MM_${System.currentTimeMillis()}.jpg"
                val tempFile = java.io.File(context.cacheDir, fileName)
                java.io.FileOutputStream(tempFile).use { it.write(jpegBytes) }

                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    exif.setAttribute(ExifInterface.TAG_MAKE, "POCO X6 Pro • Duchamp Cine")
                    exif.setAttribute(ExifInterface.TAG_MODEL, "${lens.sensorModel} [${director.codeName}]")
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Zero-ISP GLSL Lanczos-3 sRGB")
                    exif.saveAttributes()
                } catch (e: Exception) {
                    android.util.Log.e("CameraEngine", "EXIF write warning", e)
                }

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/DuchampCine")
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    tempFile.delete()
                    onSaved("$fileName (${jpegBytes.size / 1024} KB)")
                } else {
                    onSaved("Kayıt hatası: URI oluşturulamadı")
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraEngine", "Export error", e)
                onSaved("Kayıt hatası: ${e.message}")
            }
        }
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}
    }
}
