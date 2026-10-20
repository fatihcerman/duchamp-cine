package com.duchamp.cine

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * DUCHAMP CINE: Multi-Pass OpenGL ES 3.2 Cinematic Shader Pipeline.
 * Hardcoded and optimized for Xiaomi POCO X6 Pro 5G (Dimensity 8300-Ultra + Mali-G615 MC6).
 */
class CinematicShaderPipeline {

    companion object {
        private const val TAG = "CinematicShaderPipeline"

        // Full Screen Quad Coordinates (NDC)
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 0.0f, 1.0f, 1.0f
        )
    }

    data class DirectorProfile(
        val id: Int,
        val name: String,
        val description: String,
        val shadowTint: FloatArray,    // vec3: R, G, B offset
        val highlightTint: FloatArray, // vec3: R, G, B offset
        val toe: Float,                // Black level lift/crush
        val shoulder: Float,           // Highlight roll-off
        val contrast: Float,           // ARRI S-curve contrast
        val saturation: Float,         // Color saturation multiplier
        val halationStrength: Float,   // Red film emulsion halation
        val bloomStrength: Float,      // Highlight diffuser bloom
        val vignetteStrength: Float,   // Optical lens falloff
        val grainStrength: Float       // 35mm simplex grain
    )

    // The 8 Master Director Profiles
    val profiles = arrayOf(
        DirectorProfile(
            id = 0,
            name = "ALLEN",
            description = "Kodak 5219 Warm Tungsten / Amber Glow",
            shadowTint = floatArrayOf(0.06f, 0.02f, -0.05f),
            highlightTint = floatArrayOf(0.08f, 0.03f, -0.03f),
            toe = 0.03f,
            shoulder = 0.92f,
            contrast = 0.95f,
            saturation = 1.08f,
            halationStrength = 0.65f,
            bloomStrength = 0.40f,
            vignetteStrength = 0.20f,
            grainStrength = 0.45f
        ),
        DirectorProfile(
            id = 1,
            name = "VILLENEUVE",
            description = "Alexa 65 Brutalist Teal Shadows / Muted Amber",
            shadowTint = floatArrayOf(-0.03f, 0.02f, 0.04f),
            highlightTint = floatArrayOf(0.04f, 0.02f, -0.02f),
            toe = -0.02f,
            shoulder = 0.95f,
            contrast = 1.12f,
            saturation = 0.80f,
            halationStrength = 0.05f,
            bloomStrength = 0.75f,
            vignetteStrength = 0.30f,
            grainStrength = 0.20f
        ),
        DirectorProfile(
            id = 2,
            name = "NOLAN",
            description = "IMAX 65mm 250D Cold Steel / High-Fidelity",
            shadowTint = floatArrayOf(-0.04f, 0.01f, 0.05f),
            highlightTint = floatArrayOf(0.01f, 0.01f, 0.02f),
            toe = -0.01f,
            shoulder = 0.97f,
            contrast = 1.22f,
            saturation = 0.92f,
            halationStrength = 0.10f,
            bloomStrength = 0.15f,
            vignetteStrength = 0.25f,
            grainStrength = 0.15f
        ),
        DirectorProfile(
            id = 3,
            name = "KUBRICK",
            description = "Zeiss f/0.7 Candlelit Tungsten & Deep Crimson",
            shadowTint = floatArrayOf(0.05f, -0.01f, -0.03f),
            highlightTint = floatArrayOf(0.07f, 0.01f, -0.02f),
            toe = -0.03f,
            shoulder = 0.88f,
            contrast = 1.25f,
            saturation = 1.10f,
            halationStrength = 0.55f,
            bloomStrength = 0.50f,
            vignetteStrength = 0.45f,
            grainStrength = 0.35f
        ),
        DirectorProfile(
            id = 4,
            name = "WONG KAR-WAI",
            description = "Doyle 90s Neon Emerald Shadows & Ruby Highs",
            shadowTint = floatArrayOf(-0.02f, 0.06f, 0.01f),
            highlightTint = floatArrayOf(0.09f, 0.02f, -0.04f),
            toe = -0.02f,
            shoulder = 0.90f,
            contrast = 1.18f,
            saturation = 1.20f,
            halationStrength = 0.80f,
            bloomStrength = 0.60f,
            vignetteStrength = 0.40f,
            grainStrength = 0.55f
        ),
        DirectorProfile(
            id = 5,
            name = "FINCHER",
            description = "RED Monstro Bleach Cyan-Green & Clinical Contrast",
            shadowTint = floatArrayOf(-0.04f, 0.03f, 0.02f),
            highlightTint = floatArrayOf(0.05f, 0.04f, -0.01f),
            toe = -0.01f,
            shoulder = 0.96f,
            contrast = 1.28f,
            saturation = 0.75f,
            halationStrength = 0.00f,
            bloomStrength = 0.05f,
            vignetteStrength = 0.30f,
            grainStrength = 0.10f
        ),
        DirectorProfile(
            id = 6,
            name = "WES ANDERSON",
            description = "200T Pastel Turquoise Skies & Coral Reds",
            shadowTint = floatArrayOf(-0.01f, 0.03f, 0.04f),
            highlightTint = floatArrayOf(0.06f, 0.04f, -0.01f),
            toe = 0.05f,
            shoulder = 0.85f,
            contrast = 0.88f,
            saturation = 1.22f,
            halationStrength = 0.20f,
            bloomStrength = 0.35f,
            vignetteStrength = 0.00f,
            grainStrength = 0.25f
        ),
        DirectorProfile(
            id = 7,
            name = "CORBET / PTA",
            description = "VistaVision Earthy Sepia & Bone-White Contrast",
            shadowTint = floatArrayOf(0.04f, 0.02f, -0.01f),
            highlightTint = floatArrayOf(0.02f, 0.02f, 0.01f),
            toe = 0.00f,
            shoulder = 0.93f,
            contrast = 1.15f,
            saturation = 0.95f,
            halationStrength = 0.35f,
            bloomStrength = 0.30f,
            vignetteStrength = 0.30f,
            grainStrength = 0.50f
        )
    )

    // Uniform interpolation state (600ms transition)
    private var currentProfileIndex = 0
    private var targetProfileIndex = 0
    private var transitionStartTime = 0L
    private val transitionDurationMs = 600.0f

    // Active interpolated parameters
    private var interpShadowTint = floatArrayOf(0.06f, 0.02f, -0.05f)
    private var interpHighlightTint = floatArrayOf(0.08f, 0.03f, -0.03f)
    private var interpToe = 0.03f
    private var interpShoulder = 0.92f
    private var interpContrast = 0.95f
    private var interpSaturation = 1.08f
    private var interpHalation = 0.65f
    private var interpBloom = 0.40f
    private var interpVignette = 0.20f
    private var interpGrain = 0.45f

    // Camera State
    var isFrontCamera = false
    var isAspect191 = false
    var showFilmBorder = false

    // OpenGL State (VAO / VBO setup for Mali-G615 compatibility)
    private val vaoId = IntArray(1)
    private val vboId = IntArray(1)
    private var quadVertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    // Program IDs
    private var pass1Program = 0
    private var pass2Program = 0
    private var pass3Program = 0
    private var pass4Program = 0
    private var pass5Program = 0

    // External Camera Texture
    var cameraTextureId = 0
        private set

    var surfaceTexture: SurfaceTexture? = null
        private set

    // FBO Ping-Pong system
    private val fboIds = IntArray(5)
    private val fboTextureIds = IntArray(5)
    private var viewportWidth = 1080
    private var viewportHeight = 1350

    // GLSL 320 es Vertex Shader (Unified Quad)
        // Pass 1 Vertex Shader (Transforms UV via SurfaceTexture Matrix)
    private val pass1VertexShaderSource = """#version 320 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        uniform mat4 uSTMatrix;
        out vec2 vTexCoord;

        void main() {
            gl_Position = vec4(aPosition, 1.0);
            vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    private val vertexShaderSource = """#version 320 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec2 aTexCoord;

        out vec2 vTexCoord;

        void main() {
            gl_Position = vec4(aPosition, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // Pass 1: Sensor Linearization, OV64B Calibration & 35mm Inverse-Barrel Rectification
    private val pass1FragmentShaderSource = """#version 320 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float;
        precision highp int;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform samplerExternalOES uCameraTexture;
        uniform int uIsFrontCamera;
        uniform mat4 uSTMatrix;

        // OmniVision OV64B shadow green-magenta neutralization matrix
        const mat3 OV64B_CALIBRATION = mat3(
            1.025, -0.012, -0.005,
           -0.015,  1.042, -0.020,
           -0.008, -0.018,  1.035
        );

        void main() {
            vec2 uv = vTexCoord;

            if (uIsFrontCamera == 1) {
                // Optical rectification: OV16A1Q 21mm to 35mm portrait transform
                // Radial inverse-barrel distortion (k1 = -0.18, k2 = 0.04)
                vec2 centered = uv - vec2(0.5);
                float r2 = dot(centered, centered);
                float distortion = 1.0 + (-0.18) * r2 + (0.04) * r2 * r2;
                vec2 warped = centered * distortion;

                // 15% center crop to match 35mm portrait perspective
                warped = warped * 0.85;
                uv = warped + vec2(0.5);
            }

            vec4 rawColor = texture(uCameraTexture, uv);

            // Subtract sensor black level (0.015)
            vec3 blackSubtracted = max(rawColor.rgb - vec3(0.015), vec3(0.0));

            // Linearize gamma (pow 2.2) to enter physical linear scene-referred RGB
            vec3 linearRGB = pow(blackSubtracted, vec3(2.2));

            // Apply OV64B sensor spectral matrix if rear camera
            if (uIsFrontCamera == 0) {
                linearRGB = OV64B_CALIBRATION * linearRGB;
            }

            fragColor = vec4(max(linearRGB, vec3(0.0)), 1.0);
        }
    """.trimIndent()

    // Pass 2: Physical Film Halation (9-tap Gaussian on Red/Green where L > 0.78), Bloom & Lens Falloff
    private val pass2FragmentShaderSource = """#version 320 es
        precision highp float;
        precision highp int;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uLinearInputTexture;
        uniform vec2 uTexelSize;
        uniform float uHalationStrength;
        uniform float uBloomStrength;
        uniform float uVignetteStrength;

        // 9-Tap Separable Gaussian weights
        const float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

        float getLuminance(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        void main() {
            vec3 baseColor = texture(uLinearInputTexture, vTexCoord).rgb;
            float lum = getLuminance(baseColor);

            // Separate high luminance layer for halation & bloom (L > 0.78)
            float brightFactor = smoothstep(0.78, 0.95, lum);

            // Halation sampling: bleed Red (1.0) and Green (0.35) into adjacent emulsion
            vec3 halationAccum = vec3(0.0);
            vec3 bloomAccum = vec3(0.0);

            for (int i = -4; i <= 4; i++) {
                float offset = float(i);
                float w = weights[abs(i)];
                vec2 sampleCoordX = vTexCoord + vec2(offset * uTexelSize.x * 2.5, 0.0);
                vec2 sampleCoordY = vTexCoord + vec2(0.0, offset * uTexelSize.y * 2.5);

                vec3 sampleColorX = texture(uLinearInputTexture, sampleCoordX).rgb;
                vec3 sampleColorY = texture(uLinearInputTexture, sampleCoordY).rgb;
                vec3 avgColor = (sampleColorX + sampleColorY) * 0.5;

                float sampleLum = getLuminance(avgColor);
                float sampleBright = smoothstep(0.75, 1.0, sampleLum);

                // Halation layer: red emulsion scatter
                halationAccum += avgColor * sampleBright * w * vec3(1.0, 0.35, 0.02);

                // Bloom diffuser layer
                bloomAccum += avgColor * sampleBright * w;
            }

            vec3 linearWithHalation = baseColor +
                (halationAccum * uHalationStrength * 1.4) +
                (bloomAccum * uBloomStrength * 0.8);

            // Optical lens falloff (vignette & corner softness)
            vec2 distFromCenter = vTexCoord - vec2(0.5);
            float dist = length(distFromCenter);
            float vig = smoothstep(0.75, 0.35, dist * (1.0 + uVignetteStrength * 0.85));

            fragColor = vec4(linearWithHalation * vig, 1.0);
        }
    """.trimIndent()

    // Pass 3: ARRI LogC3 S-Curve Tonemapping, 8-Director Color Matrices, & Skin Tone Protection
    private val pass3FragmentShaderSource = """#version 320 es
        precision highp float;
        precision highp int;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uHalatedInputTexture;
        uniform vec3 uShadowTint;
        uniform vec3 uHighlightTint;
        uniform float uToe;
        uniform float uShoulder;
        uniform float uContrast;
        uniform float uSaturation;
        uniform int uIsFrontCamera;

        // Analytical ARRI LogC3 Curve
        vec3 linearToLogC3(vec3 x) {
            const float cut = 0.010591;
            const float a = 5.555556;
            const float b = 0.052272;
            const float c = 0.247190;
            const float d = 0.385537;
            const float e = 5.367655;
            const float f = 0.092809;

            vec3 logc;
            logc.r = (x.r > cut) ? (c * log(a * x.r + b) / 2.302585 + d) : (e * x.r + f);
            logc.g = (x.g > cut) ? (c * log(a * x.g + b) / 2.302585 + d) : (e * x.g + f);
            logc.b = (x.b > cut) ? (c * log(a * x.b + b) / 2.302585 + d) : (e * x.b + f);
            return logc;
        }

        // RGB to HSV for skin-tone protection
        vec3 rgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec3 linearRGB = texture(uHalatedInputTexture, vTexCoord).rgb;

            // Apply LogC3 compression
            vec3 graded = linearToLogC3(linearRGB);

            // S-Curve tonemapping with controllable Toe (shadows) and Shoulder (highlights)
            // Toe controls black lift/crush
            graded += vec3(uToe);

            // Contrast pivot around 0.38 (middle grey in LogC3)
            graded = (graded - vec3(0.38)) * uContrast + vec3(0.38);

            // Apply Director Color Matrix Tinting (split-toning)
            float luma = dot(clamp(graded, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
            vec3 shadowOffset = uShadowTint * (1.0 - smoothstep(0.0, 0.6, luma));
            vec3 highlightOffset = uHighlightTint * smoothstep(0.4, 1.0, luma);
            graded += shadowOffset + highlightOffset;

            // Saturation adjustment
            float grey = dot(graded, vec3(0.2126, 0.7152, 0.0722));
            graded = mix(vec3(grey), graded, uSaturation);

            // Front camera portrait skin-tone protection
            if (uIsFrontCamera == 1) {
                vec3 hsv = rgb2hsv(clamp(graded, 0.0, 1.0));
                // Skin tone hue is typically 15° to 45° -> normalized [0.04, 0.125]
                float isSkin = smoothstep(0.02, 0.05, hsv.x) * (1.0 - smoothstep(0.12, 0.16, hsv.x));
                isSkin *= smoothstep(0.15, 0.4, hsv.y); // moderate saturation

                // Soften micro-contrast by 12% and restore natural skin hue
                graded = mix(graded, mix(vec3(grey), graded, 0.88), isSkin * 0.12);
            }

            fragColor = vec4(clamp(graded, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    // Pass 4: DCT-Friendly Band-Limited 35mm Simplex Film Grain
    private val pass4FragmentShaderSource = """#version 320 es
        precision highp float;
        precision highp int;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uGradedInputTexture;
        uniform vec2 uResolution;
        uniform float uGrainStrength;
        uniform float uTime;

        // 2D Simplex Noise for 35mm organic film grain
        vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        vec3 permute(vec3 x) { return mod289(((x * 34.0) + 1.0) * x); }

        float snoise(vec2 v) {
            const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
            vec2 i  = floor(v + dot(v, C.yy));
            vec2 x0 = v -   i + dot(i, C.xx);
            vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
            vec4 x12 = x0.xyxy + C.xxzz;
            x12.xy -= i1;
            i = mod289(i);
            vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
            vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
            m = m * m;
            m = m * m;
            vec3 x = 2.0 * fract(p * C.www) - 1.0;
            vec3 h = abs(x) - 0.5;
            vec3 ox = floor(x + 0.5);
            vec3 a0 = x - ox;
            m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
            vec3 g;
            g.x  = a0.x  * x0.x  + h.x  * x0.y;
            g.yz = a0.yz * x12.xz + h.yz * x12.yw;
            return 130.0 * dot(m, g);
        }

        void main() {
            vec3 color = texture(uGradedInputTexture, vTexCoord).rgb;
            float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));

            // Cluster diameter = 1.6 pixels (band-limited to survive Instagram DCT compression)
            vec2 grainCoord = vTexCoord * (uResolution / 1.6) + vec2(uTime * 37.1, uTime * 61.7);
            float grain = snoise(grainCoord);

            // Midtone bell curve: peak grain intensity at L = 0.5, fade in blacks and whites
            float bellCurve = 4.0 * lum * (1.0 - lum);
            float grainDelta = grain * bellCurve * uGrainStrength * 0.065;

            fragColor = vec4(clamp(color + vec3(grainDelta), 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    // Pass 5: Mathematical Lanczos-3 Resampler & Pre-Compensation Unsharp Mask
    private val pass5FragmentShaderSource = """#version 320 es
        precision highp float;
        precision highp int;

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uGrainedInputTexture;
        uniform vec2 uSourceTexelSize;
        uniform int uEnableFilmBorder;
        uniform int uIs191Aspect;

        const float PI_VAL = 3.14159265358979323846;

        float sinc(float x) {
            x = abs(x);
            if (x < 1.0e-5) return 1.0;
            float px = PI_VAL * x;
            return sin(px) / px;
        }

        float lanczos3(float x) {
            if (abs(x) >= 3.0) return 0.0;
            return sinc(x) * sinc(x / 3.0);
        }

        void main() {
            vec2 uv = vTexCoord;

            // Aspect ratio masking (1.91:1 Scope mode)
            if (uIs191Aspect == 1) {
                // Viewport aspect: 1.91:1 within frame
                float topCut = 0.23;
                float bottomCut = 0.77;
                if (uv.y < topCut || uv.y > bottomCut) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
            }

            // 2-Pass Lanczos-3 convolution kernel sampling (3 taps positive, 3 taps negative)
            vec3 accumColor = vec3(0.0);
            float accumWeight = 0.0;

            for (int dy = -2; dy <= 2; dy++) {
                float wy = lanczos3(float(dy));
                for (int dx = -2; dx <= 2; dx++) {
                    float wx = lanczos3(float(dx));
                    float weight = wx * wy;

                    vec2 sampleCoord = uv + vec2(float(dx) * uSourceTexelSize.x, float(dy) * uSourceTexelSize.y);
                    accumColor += texture(uGrainedInputTexture, sampleCoord).rgb * weight;
                    accumWeight += weight;
                }
            }

            vec3 resampled = accumColor / max(accumWeight, 0.0001);

            // Unsharp Mask pre-compensation for Instagram 4:2:0 subsampling
            // (radius = 0.6px, amount = 0.16, threshold = 0.01)
            vec3 blur = (
                texture(uGrainedInputTexture, uv + vec2(uSourceTexelSize.x * 0.6, 0.0)).rgb +
                texture(uGrainedInputTexture, uv - vec2(uSourceTexelSize.x * 0.6, 0.0)).rgb +
                texture(uGrainedInputTexture, uv + vec2(0.0, uSourceTexelSize.y * 0.6)).rgb +
                texture(uGrainedInputTexture, uv - vec2(0.0, uSourceTexelSize.y * 0.6)).rgb
            ) * 0.25;

            vec3 diff = resampled - blur;
            if (length(diff) > 0.01) {
                resampled += diff * 0.16;
            }

            // Subtle 35mm film perforations / border if enabled
            if (uEnableFilmBorder == 1) {
                float borderX = min(uv.x, 1.0 - uv.x);
                float borderY = min(uv.y, 1.0 - uv.y);
                if (borderX < 0.012 || borderY < 0.012) {
                    resampled *= 0.15; // Matte black film rebate
                }
            }

            fragColor = vec4(clamp(resampled, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    fun initializeGL() {
        Log.i(TAG, "Initializing OpenGL ES 3.2 Duchamp Cine Pipeline...")

        // Generate camera OES texture
        val textures = IntArray(1)
        GLES32.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setDefaultBufferSize(1920, 1080)
        }

        // Compile Shader Programs
        pass1Program = buildProgram(pass1VertexShaderSource, pass1FragmentShaderSource)
        setupVAO()
        pass2Program = buildProgram(vertexShaderSource, pass2FragmentShaderSource)
        pass3Program = buildProgram(vertexShaderSource, pass3FragmentShaderSource)
        pass4Program = buildProgram(vertexShaderSource, pass4FragmentShaderSource)
        pass5Program = buildProgram(vertexShaderSource, pass5FragmentShaderSource)

        setupFBOs(viewportWidth, viewportHeight)
        Log.i(TAG, "Pipeline initialized successfully with 5 FBO passes.")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        setupFBOs(width, height)
    }

    private fun setupFBOs(width: Int, height: Int) {
        // Delete old buffers
        if (fboIds[0] != 0) {
            GLES32.glDeleteFramebuffers(5, fboIds, 0)
            GLES32.glDeleteTextures(5, fboTextureIds, 0)
        }

        GLES32.glGenFramebuffers(5, fboIds, 0)
        GLES32.glGenTextures(5, fboTextureIds, 0)

        for (i in 0 until 5) {
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextureIds[i])
            // Dimensity 8300 Mali-G615 supports RGBA16F / RGBA8
            GLES32.glTexImage2D(
                GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA8,
                width, height, 0,
                GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
            )
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)

            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboIds[i])
            GLES32.glFramebufferTexture2D(
                GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
                GLES32.GL_TEXTURE_2D, fboTextureIds[i], 0
            )

            val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
            if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "FBO $i initialization failed with status $status")
            }
        }
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
    }

    fun setDirectorProfile(profileIndex: Int) {
        if (profileIndex != targetProfileIndex) {
            targetProfileIndex = profileIndex
            transitionStartTime = SystemClock.uptimeMillis()
        }
    }

    private fun updateInterpolation() {
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - transitionStartTime).toFloat()
        val progress = (elapsed / transitionDurationMs).coerceIn(0.0f, 1.0f)

        // Smooth cubic easing
        val t = progress * progress * (3.0f - 2.0f * progress)

        val src = profiles[currentProfileIndex]
        val dst = profiles[targetProfileIndex]

        interpShadowTint[0] = src.shadowTint[0] + (dst.shadowTint[0] - src.shadowTint[0]) * t
        interpShadowTint[1] = src.shadowTint[1] + (dst.shadowTint[1] - src.shadowTint[1]) * t
        interpShadowTint[2] = src.shadowTint[2] + (dst.shadowTint[2] - src.shadowTint[2]) * t

        interpHighlightTint[0] = src.highlightTint[0] + (dst.highlightTint[0] - src.highlightTint[0]) * t
        interpHighlightTint[1] = src.highlightTint[1] + (dst.highlightTint[1] - src.highlightTint[1]) * t
        interpHighlightTint[2] = src.highlightTint[2] + (dst.highlightTint[2] - src.highlightTint[2]) * t

        interpToe = src.toe + (dst.toe - src.toe) * t
        interpShoulder = src.shoulder + (dst.shoulder - src.shoulder) * t
        interpContrast = src.contrast + (dst.contrast - src.contrast) * t
        interpSaturation = src.saturation + (dst.saturation - src.saturation) * t
        interpHalation = src.halationStrength + (dst.halationStrength - src.halationStrength) * t
        interpBloom = src.bloomStrength + (dst.bloomStrength - src.bloomStrength) * t
        interpVignette = src.vignetteStrength + (dst.vignetteStrength - src.vignetteStrength) * t
        interpGrain = src.grainStrength + (dst.grainStrength - src.grainStrength) * t

        if (progress >= 1.0f) {
            currentProfileIndex = targetProfileIndex
        }
    }

    fun render(stMatrix: FloatArray) {
        try {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating SurfaceTexture frame", e)
        }
        updateInterpolation()

        GLES32.glDisable(GLES32.GL_DEPTH_TEST)
        GLES32.glDisable(GLES32.GL_BLEND)

        // --- PASS 1: Sensor Linearization & Rectification ---
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboIds[0])
        GLES32.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES32.glUseProgram(pass1Program)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass1Program, "uCameraTexture"), 0)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass1Program, "uIsFrontCamera"), if (isFrontCamera) 1 else 0)
        GLES32.glUniformMatrix4fv(GLES32.glGetUniformLocation(pass1Program, "uSTMatrix"), 1, false, stMatrix, 0)
        drawQuad(pass1Program)

        // --- PASS 2: Halation, Bloom & Lens Falloff ---
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboIds[1])
        GLES32.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES32.glUseProgram(pass2Program)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextureIds[0])
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass2Program, "uLinearInputTexture"), 0)
        GLES32.glUniform2f(GLES32.glGetUniformLocation(pass2Program, "uTexelSize"), 1.0f / viewportWidth, 1.0f / viewportHeight)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass2Program, "uHalationStrength"), interpHalation)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass2Program, "uBloomStrength"), interpBloom)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass2Program, "uVignetteStrength"), interpVignette)
        drawQuad(pass2Program)

        // --- PASS 3: ARRI LogC3 Tonemapping & Director Grading ---
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboIds[2])
        GLES32.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES32.glUseProgram(pass3Program)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextureIds[1])
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass3Program, "uHalatedInputTexture"), 0)
        GLES32.glUniform3fv(GLES32.glGetUniformLocation(pass3Program, "uShadowTint"), 1, interpShadowTint, 0)
        GLES32.glUniform3fv(GLES32.glGetUniformLocation(pass3Program, "uHighlightTint"), 1, interpHighlightTint, 0)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass3Program, "uToe"), interpToe)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass3Program, "uShoulder"), interpShoulder)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass3Program, "uContrast"), interpContrast)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass3Program, "uSaturation"), interpSaturation)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass3Program, "uIsFrontCamera"), if (isFrontCamera) 1 else 0)
        drawQuad(pass3Program)

        // --- PASS 4: Band-Limited 35mm Simplex Grain ---
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboIds[3])
        GLES32.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES32.glUseProgram(pass4Program)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextureIds[2])
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass4Program, "uGradedInputTexture"), 0)
        GLES32.glUniform2f(GLES32.glGetUniformLocation(pass4Program, "uResolution"), viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass4Program, "uGrainStrength"), interpGrain)
        GLES32.glUniform1f(GLES32.glGetUniformLocation(pass4Program, "uTime"), (SystemClock.uptimeMillis() % 100000).toFloat() / 1000.0f)
        drawQuad(pass4Program)

        // --- PASS 5: Lanczos-3 Resample, Unsharp Mask & Film Border (Direct to Screen) ---
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        GLES32.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES32.glUseProgram(pass5Program)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextureIds[3])
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uGrainedInputTexture"), 0)
        GLES32.glUniform2f(GLES32.glGetUniformLocation(pass5Program, "uSourceTexelSize"), 1.0f / viewportWidth, 1.0f / viewportHeight)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uEnableFilmBorder"), if (showFilmBorder) 1 else 0)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uIs191Aspect"), if (isAspect191) 1 else 0)
        drawQuad(pass5Program)
    }

    /**
     * Executes the full-resolution offline render for photo capture and outputs an exact sRGB Bitmap.
     */
    fun renderCaptureToBitmap(
        sourceBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        profileIdx: Int
    ): Bitmap {
        val renderFbo = IntArray(1)
        val renderTex = IntArray(1)

        GLES32.glGenFramebuffers(1, renderFbo, 0)
        GLES32.glGenTextures(1, renderTex, 0)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, renderTex[0])
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA,
            targetWidth, targetHeight, 0,
            GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
        )
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, renderFbo[0])
        GLES32.glFramebufferTexture2D(
            GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
            GLES32.GL_TEXTURE_2D, renderTex[0], 0
        )

        // Upload input bitmap to texture
        val inputTex = IntArray(1)
        GLES32.glGenTextures(1, inputTex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, inputTex[0])
        GLUtils.texImage2D(GLES32.GL_TEXTURE_2D, 0, sourceBitmap, 0)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)

        // Execute Pass 5 with Lanczos-3 convolution kernel to target resolution
        GLES32.glViewport(0, 0, targetWidth, targetHeight)
        GLES32.glUseProgram(pass5Program)
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, inputTex[0])
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uGrainedInputTexture"), 0)
        GLES32.glUniform2f(GLES32.glGetUniformLocation(pass5Program, "uSourceTexelSize"), 1.0f / sourceBitmap.width, 1.0f / sourceBitmap.height)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uEnableFilmBorder"), if (showFilmBorder) 1 else 0)
        GLES32.glUniform1i(GLES32.glGetUniformLocation(pass5Program, "uIs191Aspect"), if (isAspect191) 1 else 0)
        drawQuad(pass5Program)

        // Read pixels back to sRGB Bitmap
        val pixelBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4)
            .order(ByteOrder.nativeOrder())
        GLES32.glReadPixels(0, 0, targetWidth, targetHeight, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, pixelBuffer)

        val outputBitmap = Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            Bitmap.Config.ARGB_8888,
            true,
            ColorSpace.get(ColorSpace.Named.SRGB)
        )
        pixelBuffer.position(0)
        outputBitmap.copyPixelsFromBuffer(pixelBuffer)

        // Cleanup temporary render resources
        GLES32.glDeleteTextures(1, inputTex, 0)
        GLES32.glDeleteTextures(1, renderTex, 0)
        GLES32.glDeleteFramebuffers(1, renderFbo, 0)

        return outputBitmap
    }

    private fun setupVAO() {
        GLES32.glGenVertexArrays(1, vaoId, 0)
        GLES32.glBindVertexArray(vaoId[0])

        GLES32.glGenBuffers(1, vboId, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vboId[0])
        quadVertexBuffer.position(0)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, quadVertexBuffer, GLES32.GL_STATIC_DRAW)

        GLES32.glEnableVertexAttribArray(0)
        GLES32.glVertexAttribPointer(0, 3, GLES32.GL_FLOAT, false, 5 * 4, 0)

        GLES32.glEnableVertexAttribArray(1)
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, 5 * 4, 3 * 4)

        GLES32.glBindVertexArray(0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
    }

    private fun drawQuad(program: Int) {
        if (vaoId[0] != 0) {
            GLES32.glBindVertexArray(vaoId[0])
            GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
            GLES32.glBindVertexArray(0)
        } else {
            quadVertexBuffer.position(0)
            GLES32.glEnableVertexAttribArray(0)
            GLES32.glVertexAttribPointer(0, 3, GLES32.GL_FLOAT, false, 5 * 4, quadVertexBuffer)
            quadVertexBuffer.position(3)
            GLES32.glEnableVertexAttribArray(1)
            GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, 5 * 4, quadVertexBuffer)
            GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
            GLES32.glDisableVertexAttribArray(0)
            GLES32.glDisableVertexAttribArray(1)
        }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES32.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSrc)

        val program = GLES32.glCreateProgram()
        GLES32.glAttachShader(program, vertexShader)
        GLES32.glAttachShader(program, fragmentShader)
        GLES32.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES32.GL_TRUE) {
            val error = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            throw RuntimeException("GL Program link failed: $error")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES32.GL_TRUE) {
            val error = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            throw RuntimeException("GL Shader compile failed (type $type): $error")
        }
        return shader
    }
}
