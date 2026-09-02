// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import java.io.InputStream

/** Parses the deliberately limited Lottie subset accepted from imported theme packs. */
internal object VisualThemeLottie {
    const val MAX_BYTES = 2L * 1024L * 1024L
    private const val MAX_LAYERS = 256
    private const val MAX_DURATION_MS = 30_000f

    fun parse(input: InputStream): LottieComposition {
        val result = LottieCompositionFactory.fromJsonInputStreamSync(input, null)
        val composition = result.value
            ?: throw IllegalArgumentException(
                "Invalid Lottie animation",
                result.exception,
            )
        validate(composition)
        return composition
    }

    private fun validate(composition: LottieComposition) {
        val bounds = composition.bounds
        require(bounds.width() in 1..VisualThemeSvg.MAX_DIMENSION &&
                bounds.height() in 1..VisualThemeSvg.MAX_DIMENSION &&
                bounds.width().toLong() * bounds.height() <= VisualThemeSvg.MAX_PIXELS) {
            "Lottie animation dimensions are invalid or too large"
        }
        require(composition.duration in 1f..MAX_DURATION_MS) {
            "Lottie animation duration is invalid or too long"
        }
        require(composition.layers.size <= MAX_LAYERS) {
            "Lottie animation has too many top-level layers"
        }
        require(composition.images.isEmpty()) {
            "Lottie bitmap image layers are not supported in theme packs"
        }
    }
}
