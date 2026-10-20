package com.duchamp.cine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * DUCHAMP CINE: Minimalist Cinema HUD & Jetpack Compose Viewfinder.
 * Includes Gyroscope 0.1° Horizon Lock, Sweet-Spot Face Distance Reticle,
 * Warm-Fill Screen Reflector, and One-Tap Director Bar.
 */
class MainActivity : ComponentActivity(), GLSurfaceView.Renderer {

    private lateinit var glSurfaceView: GLSurfaceView
    private val shaderPipeline = CinematicShaderPipeline()
    private lateinit var cameraEngine: CameraAndAutoDirectorEngine

    private val stMatrix = FloatArray(16)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            cameraEngine.start()
        } else {
            Toast.makeText(this, "Camera permission required for Zero-ISP capture", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock Fullscreen & Keep Screen On for Cinema Operation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        cameraEngine = CameraAndAutoDirectorEngine(this, shaderPipeline)

        // Setup OpenGL ES 3.2 View
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setRenderer(this@MainActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContent {
            CinemaCameraScreen(
                glSurfaceView = glSurfaceView,
                cameraEngine = cameraEngine,
                pipeline = shaderPipeline
            )
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            cameraEngine.start()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraEngine.start()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        cameraEngine.stop()
    }

    // ==========================================
    // GLSurfaceView.Renderer Callbacks
    // ==========================================
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shaderPipeline.initializeGL()
        android.opengl.Matrix.setIdentityM(stMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        shaderPipeline.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        shaderPipeline.render(stMatrix)
    }
}

@Composable
fun CinemaCameraScreen(
    glSurfaceView: GLSurfaceView,
    cameraEngine: CameraAndAutoDirectorEngine,
    pipeline: CinematicShaderPipeline
) {
    val context = LocalContext.current
    val telemetry by cameraEngine.telemetry.collectAsStateWithLifecycle()

    var is191Scope by remember { mutableStateOf(false) }
    var showFilmBorder by remember { mutableStateOf(false) }
    var exposureBiasEv by remember { mutableFloatStateOf(-0.7f) }

    // Shutter animation
    val captureAnimProgress by animateFloatAsState(
        targetValue = if (telemetry.isCapturing) telemetry.captureProgress else 0.0f,
        label = "CaptureProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 1. OpenGL Viewfinder Container ---
        val aspectRatio = if (is191Scope) 1.91f else (4.0f / 5.0f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 150.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(if (showFilmBorder) 0.dp else 4.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { glSurfaceView },
                    modifier = Modifier.fillMaxSize()
                )

                // Reticle Overlay (Horizon Level, Crosshair, Sweet-Spot Ring)
                CinemaReticleOverlay(
                    telemetry = telemetry,
                    isFrontCamera = cameraEngine.isFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- 2. Warm-Fill Screen Reflector (Front Camera Low-Light) ---
        if (telemetry.isWarmFillActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 24.dp,
                        color = Color(0xFFFFB36B).copy(alpha = 0.85f) // 2900K Tungsten Warm Amber
                    )
            )
        }

        // --- 3. Top HUD Bar ---
        TopHudBar(
            telemetry = telemetry,
            is191Scope = is191Scope,
            showFilmBorder = showFilmBorder,
            onToggleAspect = {
                is191Scope = !is191Scope
                pipeline.isAspect191 = is191Scope
            },
            onToggleFilmBorder = {
                showFilmBorder = !showFilmBorder
                pipeline.showFilmBorder = showFilmBorder
            },
            onToggleAutoLock = {
                cameraEngine.toggleAutoDirectorLock()
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // --- 4. Bottom Controls (Director Pill Bar, EV Slider, Shutter) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Exposure EV Slider (-1.5 EV to +0.5 EV)
            ExposureBiasSlider(
                currentEv = exposureBiasEv,
                onEvChanged = { ev ->
                    exposureBiasEv = ev
                    val step = (ev / 0.3333f).toInt()
                    cameraEngine.setExposureCompensation(step)
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 6.dp)
            )

            // Horizontal Director Pill Bar
            DirectorPillBar(
                profiles = pipeline.profiles,
                activeProfileId = telemetry.activeProfileId,
                recommendedProfileId = telemetry.recommendedProfileId,
                isAutoLocked = telemetry.isAutoDirectorLocked,
                onSelectProfile = { id ->
                    cameraEngine.selectDirectorProfile(id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Bottom Shutter & Switch Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preset Reset / Info
                IconButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            "ISO ${telemetry.iso} | ${telemetry.cctKelvin}K | ${telemetry.exposureTimeMs.toInt()}ms",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Sensor Telemetry",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Tactile Cinema Shutter Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(enabled = !telemetry.isCapturing) {
                            cameraEngine.capturePhoto { uri ->
                                if (uri != null) {
                                    Toast.makeText(context, "Captured: DCIM/DuchampCine", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    // Outer ring with capture burst progress
                    CircularProgressIndicator(
                        progress = { captureAnimProgress },
                        modifier = Modifier.size(76.dp),
                        color = Color(0xFF00E676),
                        strokeWidth = 3.dp,
                        trackColor = Color.White.copy(alpha = 0.4f)
                    )

                    // Inner shutter circle
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isCapturing) Color(0xFFFF5252) else Color.White)
                    )
                }

                // Front / Rear Camera Flip Button
                IconButton(
                    onClick = { cameraEngine.toggleCamera() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TopHudBar(
    telemetry: CameraAndAutoDirectorEngine.TelemetryState,
    is191Scope: Boolean,
    showFilmBorder: Boolean,
    onToggleAspect: () -> Unit,
    onToggleFilmBorder: () -> Unit,
    onToggleAutoLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Director Auto / Lock Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = if (telemetry.isAutoDirectorLocked) Color(0xFFFFB300) else Color(0xFF00E676),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onToggleAutoLock() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (telemetry.isAutoDirectorLocked) Color(0xFFFFB300) else Color(0xFF00E676))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (telemetry.isAutoDirectorLocked) "LOCKED" else "AUTO",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "• DIRECTING",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }

        // RAW Zero-ISP & Aspect Toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ZERO-ISP Indicator Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2E7D32).copy(alpha = 0.6f))
                    .border(0.5.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "RAW ZERO-ISP",
                    color = Color(0xFFE8F5E9),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Aspect Ratio Toggle (4:5 / 1.91:1)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262626))
                    .clickable { onToggleAspect() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (is191Scope) "1.91:1 SCOPE" else "4:5 IG VERTICAL",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 35mm Frame Toggle
            IconButton(
                onClick = { onToggleFilmBorder() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = "Toggle Film Border",
                    tint = if (showFilmBorder) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CinemaReticleOverlay(
    telemetry: CameraAndAutoDirectorEngine.TelemetryState,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Rule of Thirds Grid Lines (Subtle)
        val gridColor = Color.White.copy(alpha = 0.15f)
        val stroke1 = Stroke(width = 1f)

        // Vertical lines
        drawLine(gridColor, Offset(w * 0.333f, 0f), Offset(w * 0.333f, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(w * 0.666f, 0f), Offset(w * 0.666f, h), strokeWidth = 1f)

        // Horizontal lines
        drawLine(gridColor, Offset(0f, h * 0.333f), Offset(w, h * 0.333f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 0.666f), Offset(w, h * 0.666f), strokeWidth = 1f)

        // 2. Kubrick Center Crosshair
        val crosshairColor = Color.White.copy(alpha = 0.4f)
        val cx = w * 0.5f
        val cy = h * 0.5f
        val crossSize = 16f
        drawLine(crosshairColor, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokeWidth = 1.5f)
        drawLine(crosshairColor, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokeWidth = 1.5f)

        // 3. Gyroscope Horizon Level Indicator
        // Snaps and turns #00E676 within ±0.2°
        val levelColor = if (telemetry.isLevelLocked) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f)
        val levelAngle = if (telemetry.isLevelLocked) 0.0f else telemetry.tiltAngleDeg

        rotate(degrees = -levelAngle, pivot = Offset(cx, cy)) {
            val lineLength = 80f
            // Left horizon bar
            drawLine(levelColor, Offset(cx - lineLength - 30f, cy), Offset(cx - 30f, cy), strokeWidth = 2.5f)
            // Right horizon bar
            drawLine(levelColor, Offset(cx + 30f, cy), Offset(cx + lineLength + 30f, cy), strokeWidth = 2.5f)
            // Center level pip
            drawCircle(levelColor, radius = 2.5f, center = Offset(cx, cy))
        }

        // 4. Front Camera Sweet-Spot Distance Ring (40cm - 50cm)
        if (isFrontCamera && telemetry.hasFace) {
            val ringColor = if (telemetry.isFaceSweetSpot) Color(0xFF00E676) else Color(0xFFFFB300).copy(alpha = 0.6f)
            drawCircle(
                color = ringColor,
                radius = 110f,
                center = Offset(cx, cy * 0.85f),
                style = Stroke(width = if (telemetry.isFaceSweetSpot) 3f else 1.5f)
            )
        }
    }
}

@Composable
fun ExposureBiasSlider(
    currentEv: Float,
    onEvChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "EV ${if (currentEv >= 0) "+" else ""}${"%.1f".format(currentEv)}",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = currentEv,
            onValueChange = onEvChanged,
            valueRange = -1.5f..0.5f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF00E676),
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DirectorPillBar(
    profiles: Array<CinematicShaderPipeline.DirectorProfile>,
    activeProfileId: Int,
    recommendedProfileId: Int,
    isAutoLocked: Boolean,
    onSelectProfile: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        profiles.forEach { profile ->
            val isSelected = profile.id == activeProfileId
            val isRecommended = profile.id == recommendedProfileId && !isAutoLocked

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            isSelected -> Color(0xFFE0E0E0)
                            isRecommended -> Color(0xFF1B5E20)
                            else -> Color(0xFF1A1A1A)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isSelected -> Color.White
                            isRecommended -> Color(0xFF00E676)
                            else -> Color(0xFF333333)
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectProfile(profile.id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = profile.name,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
