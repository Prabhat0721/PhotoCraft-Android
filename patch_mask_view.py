from pathlib import Path

p = Path("app/src/main/kotlin/com/prabhat/photocraft/ui/CropPreviewView.kt")
s = p.read_text()

def add_once(old, new):
    global s
    if new.strip() not in s:
        if old not in s:
            raise SystemExit("Expected code not found; original file unchanged.")
        s = s.replace(old, new, 1)

add_once(
    "import android.graphics.Canvas\n",
    "import android.graphics.Canvas\nimport android.graphics.Color\n"
)
add_once(
    "import android.view.View\n",
    "import android.view.View\nimport android.view.MotionEvent\n"
)
add_once(
    "import com.prabhat.photocraft.edit.CropSelection\n",
    "import com.prabhat.photocraft.edit.CropSelection\nimport com.prabhat.photocraft.edit.MaskSelection\n"
)
add_once(
    "import kotlin.math.min\n",
    "import kotlin.math.min\nimport kotlin.math.roundToInt\n"
)
add_once(
    "    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)\n",
    """    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var maskSelection: MaskSelection? = null
    private var overlayBitmap: Bitmap? = null
    private var maskTouchListener: ((Float, Float, Int) -> Boolean)? = null

    fun setMaskTouchListener(listener: ((Float, Float, Int) -> Boolean)?) {
        maskTouchListener = listener
    }

    fun setMaskSelection(selection: MaskSelection?) {
        maskSelection = selection
        overlayBitmap?.recycle()
        overlayBitmap = null

        if (selection != null) {
            val scale = min(1f, 512f / maxOf(selection.width, selection.height).toFloat())
            val ow = (selection.width * scale).roundToInt().coerceAtLeast(1)
            val oh = (selection.height * scale).roundToInt().coerceAtLeast(1)
            val pixels = IntArray(ow * oh)

            for (y in 0 until oh) {
                val sy = (y.toFloat() / oh * selection.height).toInt()
                    .coerceIn(0, selection.height - 1)
                for (x in 0 until ow) {
                    val sx = (x.toFloat() / ow * selection.width).toInt()
                        .coerceIn(0, selection.width - 1)
                    val alpha = selection.coverageAt(sx, sy) * 110 / 255
                    pixels[y * ow + x] = Color.argb(alpha, 255, 45, 45)
                }
            }
            overlayBitmap = Bitmap.createBitmap(pixels, ow, oh, Bitmap.Config.ARGB_8888)
        }
        invalidate()
    }
"""
)
add_once(
    "        canvas.drawBitmap(image, sourceRect, destinationRect, paint)\n",
    """        canvas.drawBitmap(image, sourceRect, destinationRect, paint)

        val overlay = overlayBitmap
        if (overlay != null && maskSelection != null) {
            val ol = floor(crop.left * overlay.width).toInt().coerceIn(0, overlay.width - 1)
            val ot = floor(crop.top * overlay.height).toInt().coerceIn(0, overlay.height - 1)
            val oright = ceil(crop.right * overlay.width).toInt().coerceIn(ol + 1, overlay.width)
            val obottom = ceil(crop.bottom * overlay.height).toInt().coerceIn(ot + 1, overlay.height)
            canvas.drawBitmap(
                overlay, Rect(ol, ot, oright, obottom), destinationRect, overlayPaint
            )
        }
"""
)
add_once(
    "    override fun onDraw(canvas: Canvas) {",
    """    override fun onTouchEvent(event: MotionEvent): Boolean {
        val listener = maskTouchListener ?: return super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            !destinationRect.contains(event.x, event.y)
        ) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Unit
            else -> return super.onTouchEvent(event)
        }

        if (destinationRect.width() <= 0f || destinationRect.height() <= 0f) return false

        val sx = (sourceRect.left +
            (event.x - destinationRect.left) / destinationRect.width() * sourceRect.width())
            .coerceIn(sourceRect.left.toFloat(), sourceRect.right.toFloat() - 1f)
        val sy = (sourceRect.top +
            (event.y - destinationRect.top) / destinationRect.height() * sourceRect.height())
            .coerceIn(sourceRect.top.toFloat(), sourceRect.bottom.toFloat() - 1f)

        return listener(sx, sy, event.actionMasked)
    }

    override fun onDraw(canvas: Canvas) {"""
)

p.write_text(s)
print("Mask preview touch/overlay patch applied.")
