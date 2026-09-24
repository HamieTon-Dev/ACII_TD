package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the launcher icon to PNG.
 *
 * The icon has no raster form in this repository and never has: it is an
 * adaptive icon built from three vector drawables, which is the right way to
 * ship one — it stays sharp at every launcher size and supports themed icons
 * on Android 13+. The cost is that there is no file to hand anybody when they
 * ask to see it, and Play's Console wants a 512x512 PNG for the listing.
 *
 * Rendered through Android's own `VectorDrawable`, under Robolectric's native
 * graphics mode, rather than by converting the paths to SVG and rendering them
 * elsewhere. The point is to produce what a device produces: same path parser,
 * same fill rules, same anti-aliasing. A converted copy would be a picture of
 * a different icon that happens to look similar.
 *
 * Not an assertion suite — an exporter that happens to live where the test
 * runner can reach the Android resources, in the same spirit as `IdentPreview`.
 * Output lands in `app/build/icon/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconExport {

    private val outputDir = File("build/icon")

    /**
     * The Play Console store icon: 512x512, opaque, full bleed.
     *
     * Play requires 32-bit PNG with **no transparency** and applies its own
     * corner rounding, so this is the whole square with nothing masked out.
     */
    @Test
    fun `store icon 512`() {
        write("cyops-td-icon-512-play-store", render(512, mask = null))
    }

    /** What a launcher shows on a device that uses a circular mask. */
    @Test
    fun `launcher icon circular 432`() {
        write("cyops-td-icon-432-circle", render(432, mask = Mask.CIRCLE))
    }

    /** ...and on one that uses a rounded square, which most do. */
    @Test
    fun `launcher icon rounded square 432`() {
        write("cyops-td-icon-432-rounded", render(432, mask = Mask.ROUNDED))
    }

    /** A large flat copy, for anything that wants headroom. */
    @Test
    fun `full bleed 1024`() {
        write("cyops-td-icon-1024", render(1024, mask = null))
    }

    /**
     * The monochrome layer alone, as Android 13+ themed icons use it.
     *
     * Drawn white on black here only so it is visible in a file browser; the
     * launcher tints it to whatever the wallpaper palette asks for.
     */
    @Test
    fun `themed monochrome layer 512`() {
        write("cyops-td-icon-512-monochrome", monochrome(512))
    }

    /**
     * The monochrome layer at the sizes a launcher actually draws it.
     *
     * 48 through 192 is mdpi to xxxhdpi for a launcher icon. This is where a
     * themed icon either survives or turns to mush, and it is the only
     * question worth asking about one: the 512 render flatters everything.
     *
     * Written under a fixed name; a before-and-after is produced by running
     * this twice and renaming between runs. Passing the variant in as a system
     * property was the first attempt and was quietly worse: the default fired
     * whether or not the property arrived, so both runs could write the same
     * file and the comparison would have been a picture of one version twice.
     */
    @Test
    fun `themed monochrome layer at launcher sizes`() {
        for (size in listOf(48, 72, 96, 144, 192)) {
            write("mono-$size", monochrome(size), minBytes = 100)
        }
    }

    private fun monochrome(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        drawable(R.drawable.ic_launcher_monochrome).apply {
            setBounds(0, 0, size, size)
            setTint(Color.WHITE)
            draw(canvas)
        }
        return bitmap
    }

    // ------------------------------------------------------------- machinery

    private enum class Mask { CIRCLE, ROUNDED }

    /**
     * Background then foreground, composited at [size].
     *
     * The two layers are drawn directly rather than through
     * `AdaptiveIconDrawable`, because that applies the platform's own mask and
     * the full-bleed square is what the Play listing needs. The masked
     * variants above re-apply a mask deliberately, so both forms come from the
     * same composite.
     */
    private fun render(size: Int, mask: Mask?): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (mask != null) {
            // Clip first, then paint: the background path fills the whole
            // square, so masking afterwards would need a second layer.
            val path = Path()
            val r = size.toFloat()
            when (mask) {
                Mask.CIRCLE -> path.addCircle(r / 2f, r / 2f, r / 2f, Path.Direction.CW)
                // Android's own mask is a superellipse; a 25% corner radius is
                // close enough for a preview and honest about being one.
                Mask.ROUNDED -> path.addRoundRect(
                    0f, 0f, r, r, r * 0.25f, r * 0.25f, Path.Direction.CW
                )
            }
            canvas.clipPath(path)
        }

        for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            drawable(id).apply {
                setBounds(0, 0, size, size)
                draw(canvas)
            }
        }
        return bitmap
    }

    private fun drawable(id: Int): Drawable {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return requireNotNull(ResourcesCompat.getDrawable(context.resources, id, context.theme)) {
            "resource $id did not resolve to a drawable"
        }
    }

    private fun write(name: String, bitmap: Bitmap, minBytes: Int = 1_000) {
        outputDir.mkdirs()
        val file = File(outputDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // An exporter that silently writes an empty or blank file is worse
        // than one that fails: the whole point is that somebody looks at the
        // result, and a 0-byte PNG looks like a missing file rather than a
        // broken render.
        assertTrue("$name.png was not written", file.exists() && file.length() > minBytes)
        assertTrue(
            "$name.png rendered blank — nothing but one flat colour",
            distinctColours(bitmap) > 3
        )
        println("ICON_WRITTEN ${file.absolutePath} (${file.length()} bytes, ${bitmap.width}px)")
    }

    /** Cheap "is there actually a picture here" check. */
    private fun distinctColours(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        val step = (bitmap.width / 32).coerceAtLeast(1)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                seen += bitmap.getPixel(x, y)
                if (seen.size > 8) return seen.size
                y += step
            }
            x += step
        }
        return seen.size
    }
}
