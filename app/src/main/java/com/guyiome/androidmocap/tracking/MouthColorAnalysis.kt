package com.guyiome.androidmocap.tracking

/**
 * Étage 2 de la cascade de détection de la langue tirée (revue technique, point 15) : analyse
 * couleur pure du recadrage buccal produit par [mouthCropRegion], à la recherche d'une teinte
 * rose/rouge saturée dépassant la ligne des lèvres inférieures. Toujours aucun réseau de neurones
 * à ce stade -- juste de la géométrie couleur.
 *
 * Opère volontairement sur un `IntArray` ARGB brut (format exact de `Bitmap.getPixels()`), jamais
 * sur `android.graphics.Bitmap`/`Color` directement -- `Color.RGBToHSV` est un appel du framework
 * Android indisponible en test JVM pur (`testDebugUnitTest` ne configure pas Robolectric), donc la
 * conversion HSV est réécrite à la main ici, même discipline d'extraction que `RotationMath`.
 *
 * Seuils provisoires ci-dessous, à affiner sur device via `TONGUE_DIAGNOSTIC_LOGGING`
 * (`MainViewModel`) une fois de vraies images de recadrage buccal disponibles.
 */
internal const val TONGUE_MIN_SATURATION = 0.35f
internal const val TONGUE_MIN_VALUE = 0.25f
internal const val TONGUE_MAX_HUE_RED = 25f
internal const val TONGUE_MIN_HUE_PINK = 335f

/** Conversion RGB (0-255) -> HSV pure, formule standard. Retourne `[hue 0-360, saturation 0-1, value 0-1]`. */
internal fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    val hue = when {
        delta <= 0f -> 0f
        max == rf -> 60f * mod6((gf - bf) / delta)
        max == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }
    val saturation = if (max <= 0f) 0f else delta / max
    val value = max
    return floatArrayOf(hue, saturation, value)
}

private fun mod6(x: Float): Float {
    val m = x % 6f
    return if (m < 0f) m + 6f else m
}

/**
 * `true` si le pixel ARGB correspond à une teinte rose/rouge saturée compatible avec une langue
 * (bande de teinte qui enjambe 0°/360°, d'où le test en deux moitiés). Saturation/valeur basses
 * (peau très éclairée, dents, ombre) sont exclues même si la teinte tombe dans la bonne bande.
 */
internal fun isTongueColoredPixel(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val (hue, saturation, value) = rgbToHsv(r, g, b).let { Triple(it[0], it[1], it[2]) }
    if (saturation < TONGUE_MIN_SATURATION || value < TONGUE_MIN_VALUE) return false
    return hue <= TONGUE_MAX_HUE_RED || hue >= TONGUE_MIN_HUE_PINK
}

/** Fraction de pixels "couleur langue" dans le recadrage -- 0f si le tableau est vide. */
internal fun tonguePixelRatio(pixelsArgb: IntArray): Float {
    if (pixelsArgb.isEmpty()) return 0f
    val matching = pixelsArgb.count { isTongueColoredPixel(it) }
    return matching.toFloat() / pixelsArgb.size
}

/** Seuil provisoire, à affiner sur device. */
internal const val DEFAULT_COLOR_RATIO_THRESHOLD = 0.12f

/** `true` si [ratio] dépasse le seuil minimal de pixels "couleur langue" dans le recadrage. */
internal fun colorGateOpen(ratio: Float, threshold: Float = DEFAULT_COLOR_RATIO_THRESHOLD): Boolean =
    ratio >= threshold
