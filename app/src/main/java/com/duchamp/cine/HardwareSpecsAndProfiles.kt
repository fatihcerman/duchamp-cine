package com.duchamp.cine

/**
 * HARDCODED HARDWARE CALIBRATION FOR XIAOMI POCO X6 PRO 5G (Codename: "duchamp" / Redmi K70E)
 * SoC: MediaTek Dimensity 8300-Ultra | ISP: Imagiq 980 | GPU: ARM Mali-G615 MC6
 *
 * Sensors:
 * 1. MAIN WIDE (1x 25mm): OmniVision OV64B40 (64MP Quad-Bayer -> 16MP 1.4um, f/1.78, 1/2.0", OIS)
 * 2. LOSSLESS PRIME (2x 50mm): OmniVision OV64B40 In-Sensor 2x Zoom Crop (50mm Cinema Prime Equivalent, OIS)
 * 3. ULTRAWIDE (0.6x 16mm): OmniVision OV08D10 (8MP, f/2.2, 1/4.0", 1.12um, 120° FOV, Fixed Focus)
 * 4. MACRO (4cm / Telemacro): OmniVision OV02B10 (2MP, f/2.4, 1/5.0", 1.75um) + OV64B 2.5x Hybrid Close-Up
 * 5. SELFIE (21mm -> 35mm): OmniVision OV16A1Q (16MP, f/2.4, 1/3.06", 1.0um, Fixed Focus @ 45cm)
 */

enum class DuchampLens(
    val id: String,
    val badgeTitle: String,
    val sensorModel: String,
    val focalLengthMm: Int,
    val isFrontFacing: Boolean,
    val zoomRatio: Float,
    val preferredPhysicalId: String?,
    val defaultEvBias: Float,
    // Lens optical rectification k1/k2 + crop factor to eliminate barrel/fisheye distortion
    val barrelK1: Float,
    val barrelK2: Float,
    val centerCropFactor: Float,
    // Sensor spectral compensation (Red, Green, Blue gain multipliers & saturation boost)
    val spectralGainR: Float,
    val spectralGainG: Float,
    val spectralGainB: Float,
    val sensorSatCompensation: Float,
    val burstFrameCount: Int
) {
    ULTRAWIDE_16MM(
        id = "uw_16mm",
        badgeTitle = "0.6x 16mm",
        sensorModel = "OmniVision OV08D10 (8MP f/2.2 120°)",
        focalLengthMm = 16,
        isFrontFacing = false,
        zoomRatio = 0.6f,
        preferredPhysicalId = "2",
        defaultEvBias = -0.5f,
        // Rectifies OV08D10 120-degree edge stretching for Kubrick/Villeneuve architecture
        barrelK1 = -0.22f,
        barrelK2 = 0.05f,
        centerCropFactor = 0.08f,
        // OV08D10 has muted/anemic colors in daylight; boost chroma & warm up shadows
        spectralGainR = 1.05f,
        spectralGainG = 0.98f,
        spectralGainB = 1.03f,
        sensorSatCompensation = 1.18f,
        burstFrameCount = 6
    ),
    MAIN_25MM(
        id = "main_25mm",
        badgeTitle = "1x 25mm",
        sensorModel = "OmniVision OV64B40 (64MP->16MP f/1.78 OIS)",
        focalLengthMm = 25,
        isFrontFacing = false,
        zoomRatio = 1.0f,
        preferredPhysicalId = "0",
        defaultEvBias = -0.7f,
        barrelK1 = -0.03f,
        barrelK2 = 0.01f,
        centerCropFactor = 0.0f,
        // Neutralize OV64B green-magenta shadow tint and red clipping
        spectralGainR = 0.98f,
        spectralGainG = 0.96f,
        spectralGainB = 1.02f,
        sensorSatCompensation = 1.02f,
        burstFrameCount = 5
    ),
    PRIME_50MM(
        id = "prime_50mm",
        badgeTitle = "2x 50mm",
        sensorModel = "OV64B40 In-Sensor Lossless 50mm Prime (OIS)",
        focalLengthMm = 50,
        isFrontFacing = false,
        zoomRatio = 2.0f,
        preferredPhysicalId = "0",
        defaultEvBias = -0.7f,
        barrelK1 = 0.0f,
        barrelK2 = 0.0f,
        centerCropFactor = 0.0f,
        spectralGainR = 0.99f,
        spectralGainG = 0.97f,
        spectralGainB = 1.02f,
        sensorSatCompensation = 1.04f,
        burstFrameCount = 5
    ),
    MACRO_INSERT(
        id = "macro_insert",
        badgeTitle = "MAKRO",
        sensorModel = "OV02B10 (2MP 4cm) + OV64B 2.5x OIS Telemacro",
        focalLengthMm = 60,
        isFrontFacing = false,
        zoomRatio = 2.4f,
        preferredPhysicalId = "3",
        defaultEvBias = -0.5f,
        barrelK1 = -0.06f,
        barrelK2 = 0.01f,
        centerCropFactor = 0.04f,
        // Boost micro-contrast and color richness for extreme close-up insert shots
        spectralGainR = 1.04f,
        spectralGainG = 1.00f,
        spectralGainB = 1.02f,
        sensorSatCompensation = 1.15f,
        burstFrameCount = 6
    ),
    SELFIE_35MM(
        id = "selfie_35mm",
        badgeTitle = "35mm ÖN",
        sensorModel = "OmniVision OV16A1Q (16MP f/2.4 -> 35mm Portre)",
        focalLengthMm = 35,
        isFrontFacing = true,
        zoomRatio = 1.0f,
        preferredPhysicalId = "1",
        defaultEvBias = -0.4f,
        // Transforms 21mm wide selfie into natural 35mm close-up portrait (eliminates big nose)
        barrelK1 = 0.0f,
        barrelK2 = 0.0f,
        centerCropFactor = 0.08f,
        // Fix OV16A1Q indoor white balance drift toward golden skin line
        spectralGainR = 1.03f,
        spectralGainG = 0.99f,
        spectralGainB = 0.96f,
        sensorSatCompensation = 1.05f,
        burstFrameCount = 8
    )
}

enum class DirectorProfile(
    val index: Int,
    val codeName: String,
    val filmStockTitle: String,
    val shadowTint: FloatArray,
    val highlightTint: FloatArray,
    val rgbMatrix: FloatArray,
    val toeLift: Float,
    val contrast: Float,
    val saturation: Float,
    val halationStrength: Float,
    val bloomStrength: Float,
    val vignetteStrength: Float,
    val grainStrength: Float
) {
    ALLEN(
        index = 0,
        codeName = "ALLEN",
        filmStockTitle = "Kodak Vision3 500T 5219 • Sıcak Nostalji",
        shadowTint = floatArrayOf(0.05f, 0.02f, -0.04f),
        highlightTint = floatArrayOf(0.08f, 0.04f, -0.06f),
        rgbMatrix = floatArrayOf(
            1.06f, 0.04f, -0.02f,
            0.02f, 1.01f, -0.01f,
            -0.03f, 0.01f, 0.92f
        ),
        toeLift = 0.035f,
        contrast = 0.96f,
        saturation = 1.08f,
        halationStrength = 0.65f,
        bloomStrength = 0.42f,
        vignetteStrength = 0.22f,
        grainStrength = 0.45f
    ),
    VILLENEUVE(
        index = 1,
        codeName = "VILLENEUVE",
        filmStockTitle = "ARRI Alexa 65 (Deakins) • Atmosferik Brütalist",
        shadowTint = floatArrayOf(-0.04f, 0.02f, 0.05f),
        highlightTint = floatArrayOf(0.06f, 0.03f, -0.05f),
        rgbMatrix = floatArrayOf(
            0.96f, 0.02f, 0.00f,
            0.01f, 0.99f, 0.02f,
            -0.02f, 0.04f, 0.98f
        ),
        toeLift = -0.020f,
        contrast = 1.14f,
        saturation = 0.80f,
        halationStrength = 0.06f,
        bloomStrength = 0.78f,
        vignetteStrength = 0.32f,
        grainStrength = 0.20f
    ),
    NOLAN(
        index = 2,
        codeName = "NOLAN",
        filmStockTitle = "IMAX 15/70mm Kodak 250D • Soğuk Gerçekçilik",
        shadowTint = floatArrayOf(-0.04f, 0.01f, 0.06f),
        highlightTint = floatArrayOf(0.02f, 0.02f, 0.00f),
        rgbMatrix = floatArrayOf(
            0.98f, 0.01f, 0.01f,
            0.00f, 1.02f, 0.02f,
            -0.02f, 0.03f, 1.05f
        ),
        toeLift = -0.015f,
        contrast = 1.24f,
        saturation = 0.92f,
        halationStrength = 0.12f,
        bloomStrength = 0.15f,
        vignetteStrength = 0.26f,
        grainStrength = 0.16f
    ),
    KUBRICK(
        index = 3,
        codeName = "KUBRICK",
        filmStockTitle = "Zeiss Planar f/0.7 & Eastman 100T • Simetrik Klinik",
        shadowTint = floatArrayOf(0.02f, -0.01f, 0.01f),
        highlightTint = floatArrayOf(0.09f, 0.03f, -0.05f),
        rgbMatrix = floatArrayOf(
            1.10f, 0.02f, -0.02f,
            -0.01f, 0.99f, 0.00f,
            -0.02f, 0.01f, 0.96f
        ),
        toeLift = -0.028f,
        contrast = 1.26f,
        saturation = 1.12f,
        halationStrength = 0.58f,
        bloomStrength = 0.50f,
        vignetteStrength = 0.45f,
        grainStrength = 0.36f
    ),
    WONG_KAR_WAI(
        index = 4,
        codeName = "WONG KAR-WAI",
        filmStockTitle = "Christopher Doyle • Zümrüt Gece & %80 Neon Halation",
        shadowTint = floatArrayOf(-0.05f, 0.07f, 0.01f),
        highlightTint = floatArrayOf(0.11f, 0.04f, -0.07f),
        rgbMatrix = floatArrayOf(
            1.08f, 0.05f, -0.04f,
            -0.02f, 1.07f, 0.02f,
            -0.04f, 0.05f, 0.90f
        ),
        toeLift = -0.022f,
        contrast = 1.18f,
        saturation = 1.20f,
        halationStrength = 0.82f,
        bloomStrength = 0.62f,
        vignetteStrength = 0.42f,
        grainStrength = 0.55f
    ),
    FINCHER(
        index = 5,
        codeName = "FINCHER",
        filmStockTitle = "RED Monstro Bleach-Bypass • Kehribar & Camgöbeği",
        shadowTint = floatArrayOf(-0.05f, 0.03f, 0.04f),
        highlightTint = floatArrayOf(0.05f, 0.05f, -0.06f),
        rgbMatrix = floatArrayOf(
            0.94f, 0.06f, -0.02f,
            0.02f, 1.03f, 0.01f,
            -0.04f, 0.05f, 0.92f
        ),
        toeLift = -0.015f,
        contrast = 1.28f,
        saturation = 0.76f,
        halationStrength = 0.00f,
        bloomStrength = 0.08f,
        vignetteStrength = 0.30f,
        grainStrength = 0.12f
    ),
    WES_ANDERSON(
        index = 6,
        codeName = "WES ANDERSON",
        filmStockTitle = "Kodak Vision3 200T • Pastel Simetri & Turkuaz/Mercan",
        shadowTint = floatArrayOf(0.03f, 0.03f, 0.01f),
        highlightTint = floatArrayOf(0.06f, 0.05f, -0.02f),
        rgbMatrix = floatArrayOf(
            1.05f, 0.04f, -0.02f,
            0.02f, 1.04f, 0.04f,
            -0.04f, 0.08f, 1.02f
        ),
        toeLift = 0.052f,
        contrast = 0.88f,
        saturation = 1.24f,
        halationStrength = 0.22f,
        bloomStrength = 0.36f,
        vignetteStrength = 0.02f,
        grainStrength = 0.25f
    ),
    CORBET_PTA(
        index = 7,
        codeName = "CORBET / PTA",
        filmStockTitle = "VistaVision 35mm / 65mm • Organik Selüloz Dokusu",
        shadowTint = floatArrayOf(0.03f, 0.01f, -0.02f),
        highlightTint = floatArrayOf(0.04f, 0.03f, 0.01f),
        rgbMatrix = floatArrayOf(
            1.03f, 0.02f, -0.01f,
            0.01f, 1.00f, 0.00f,
            -0.01f, 0.01f, 0.96f
        ),
        toeLift = 0.005f,
        contrast = 1.16f,
        saturation = 0.96f,
        halationStrength = 0.36f,
        bloomStrength = 0.30f,
        vignetteStrength = 0.30f,
        grainStrength = 0.50f
    )
}
