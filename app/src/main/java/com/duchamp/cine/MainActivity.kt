package com.duchamp.cine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var cameraEngine: CameraAndAutoDirectorEngine
    private lateinit var shaderPipeline: CinematicShaderPipeline
    private var glSurfaceTexture: SurfaceTexture? = null
    private lateinit var sensorManager: SensorManager

    private var telemetryState by mutableStateOf(
        CameraAndAutoDirectorEngine.TelemetrySnapshot(
            iso = 200,
            shutterSpeedText = "1/100s",
            kelvin = 4800,
            faceSweetSpotOk = false,
            faceDetected = false,
            lowLightSelfieFillRequired = false,
            recommendedDirector = DirectorProfile.WONG_KAR_WAI
        )
    )
    private var gyroRollDeg by mutableFloatStateOf(0f)
    private var lastSavedToast by mutableStateOf("POCO X6 Pro Zero-ISP Hazır")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) glSurfaceTexture?.let { cameraEngine.openCameraForLens(cameraEngine.activeLens, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        cameraEngine = CameraAndAutoDirectorEngine(this) { snapshot ->
            telemetryState = snapshot
        }

        shaderPipeline = CinematicShaderPipeline { surfaceTex ->
            glSurfaceTexture = surfaceTex
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraEngine.openCameraForLens(DuchampLens.MAIN_25MM, surfaceTex)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }.apply {
            onFrameCaptured = { bitmap, director, lens ->
                cameraEngine.exportInstagramZeroPenaltyJpeg(bitmap, director, lens) { msg ->
                    runOnUiThread {
                        lastSavedToast = msg
                        android.widget.Toast.makeText(this@MainActivity, "KAYDEDİLDİ: $msg", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setContent {
            DuchampCinemaHud()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        cameraEngine.stopBackgroundThread()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            val rollRad = atan2(rotMatrix[3].toDouble(), rotMatrix[4].toDouble())
            val deg = Math.toDegrees(rollRad).toFloat()
            gyroRollDeg = gyroRollDeg * 0.8f + deg * 0.2f
            cameraEngine.currentGyroTiltDeg = gyroRollDeg
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerHapticClick() {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vib?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Composable
    fun DuchampCinemaHud() {
        var isAutoMode by remember { mutableStateOf(true) }
        var manualDirector by remember { mutableStateOf(DirectorProfile.WONG_KAR_WAI) }
        var selectedLens by remember { mutableStateOf(DuchampLens.MAIN_25MM) }
        var isScopeAspect by remember { mutableStateOf(false) }
        var showFilmBorder by remember { mutableStateOf(false) }
        var evBias by remember { mutableFloatStateOf(-0.7f) }

        val effectiveDirector = if (isAutoMode) telemetryState.recommendedDirector else manualDirector
        LaunchedEffect(effectiveDirector, selectedLens, isScopeAspect, showFilmBorder) {
            shaderPipeline.targetDirector = effectiveDirector
            shaderPipeline.activeLens = selectedLens
            shaderPipeline.isScopeAspect = isScopeAspect
            shaderPipeline.showFilmBorder = showFilmBorder
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07080C))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP TELEMETRY & AUTO-DIRECTOR HUD BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isAutoMode) Color(0x33F59E0B) else Color(0xFF161924))
                        .border(1.dp, if (isAutoMode) Color(0xFFF59E0B) else Color(0xFF2D3348), RoundedCornerShape(999.dp))
                        .clickable {
                            triggerHapticClick()
                            if (isAutoMode) {
                                manualDirector = telemetryState.recommendedDirector
                                isAutoMode = false
                            } else {
                                isAutoMode = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isAutoMode) "AUTO • ${effectiveDirector.codeName}" else "KİLİTLİ • ${effectiveDirector.codeName}",
                        color = if (isAutoMode) Color(0xFFFBBF24) else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF161924))
                            .clickable {
                                triggerHapticClick()
                                isScopeAspect = !isScopeAspect
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isScopeAspect) "1.91:1 SCOPE" else "4:5 IG DİKEY",
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (showFilmBorder) Color(0x3310B981) else Color(0xFF161924))
                            .clickable {
                                triggerHapticClick()
                                showFilmBorder = !showFilmBorder
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "35mm ÇERÇEVE",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2. VIEWFINDER (4:5 OR 1.91:1) WITH GYRO LEVEL & SELFIE WARM-FILL LIGHT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = if (telemetryState.lowLightSelfieFillRequired) 18.dp else 1.dp,
                        color = if (telemetryState.lowLightSelfieFillRequired) Color(0xFFFFB36B) else Color(0xFF232736),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(3)
                            setRenderer(shaderPipeline)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Center Kubrick Symmetry Crosshair & 0.1° Gyro Horizon Indicator (Sleek Minimalist)
                val isLevel = abs(gyroRollDeg) <= 0.35f
                Box(
                    modifier = Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(1.5.dp)
                            .background(if (isLevel) Color(0xFF10B981) else Color(0x88FFFFFF))
                    )
                }

                // Bottom Overlay Sensor Telemetry Strip
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ISO ${telemetryState.iso} • ${telemetryState.shutterSpeedText} • ${telemetryState.kelvin}K",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (selectedLens.isFrontFacing) {
                            if (telemetryState.faceSweetSpotOk) "45cm NET [OK]" else "MESAFE: ~45cm TUTUN"
                        } else {
                            "EĞİM: ${"%.1f".format(gyroRollDeg)}°"
                        },
                        color = if (isLevel || telemetryState.faceSweetSpotOk) Color(0xFF10B981) else Color(0xFFFBBF24),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            // 3. POCO X6 PRO 5-LENS SELECTOR BAR (16mm UW, 25mm Main, 50mm Prime, Macro, 35mm Selfie)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DuchampLens.entries.forEach { lens ->
                    val active = selectedLens == lens
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFFF59E0B) else Color(0xFF141722))
                            .clickable {
                                triggerHapticClick()
                                selectedLens = lens
                                evBias = lens.defaultEvBias
                                glSurfaceTexture?.let { cameraEngine.openCameraForLens(lens, it) }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lens.badgeTitle,
                            color = if (active) Color.Black else Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 4. SCROLLABLE 8-DIRECTOR + AUTO SELECTOR BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AUTO Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isAutoMode) Color(0xFF10B981) else Color(0xFF141722))
                        .clickable {
                            triggerHapticClick()
                            isAutoMode = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "★ AUTO",
                        color = if (isAutoMode) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                DirectorProfile.entries.forEach { profile ->
                    val active = !isAutoMode && manualDirector == profile
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Color(0xFFF59E0B) else Color(0xFF141722))
                            .clickable {
                                triggerHapticClick()
                                isAutoMode = false
                                manualDirector = profile
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = profile.codeName,
                            color = if (active) Color.Black else Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 5. EV BIAS SLIDER & CINEMA SHUTTER BUTTON
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "POZLAMA KORUMA: ${"%.1f".format(evBias)} EV  •  ${lastSavedToast}",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Slider(
                        value = evBias,
                        onValueChange = {
                            evBias = it
                            cameraEngine.updateExposureBias(it)
                        },
                        valueRange = -1.5f..0.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF59E0B),
                            activeTrackColor = Color(0xFFF59E0B)
                        )
                    )
                }

                // Shutter Trigger
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable {
                            triggerHapticClick()
                            lastSavedToast = "LANCZOS-3 İŞLENİYOR..."
                            shaderPipeline.captureRequested = true
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                }
            }
        }
    }
}
