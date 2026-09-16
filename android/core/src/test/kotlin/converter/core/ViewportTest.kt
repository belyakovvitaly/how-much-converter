package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportTest {

    @Test
    fun `an image the same shape as the view fills it with no margin`() {
        val viewport = Viewport(imageWidth = 960, imageHeight = 1280, viewWidth = 480f, viewHeight = 640f)
        assertEquals(0.5f, viewport.scale)
        assertEquals(0f, viewport.offsetX)
        assertEquals(0f, viewport.offsetY)
        assertEquals(Box(50.0, 100.0, 150.0, 200.0), viewport.map(Box(100.0, 200.0, 300.0, 400.0)))
    }

    @Test
    fun `a wider view leaves margin at the sides, not at the top`() {
        // 1:1 image in a 2:1 view: half the width is empty, split evenly.
        val viewport = Viewport(100, 100, viewWidth = 200f, viewHeight = 100f)
        assertEquals(1f, viewport.scale)
        assertEquals(50f, viewport.offsetX)
        assertEquals(0f, viewport.offsetY)
    }

    @Test
    fun `a taller view leaves margin above and below`() {
        val viewport = Viewport(100, 100, viewWidth = 100f, viewHeight = 200f)
        assertEquals(1f, viewport.scale)
        assertEquals(0f, viewport.offsetX)
        assertEquals(50f, viewport.offsetY)
    }

    @Test
    fun `the whole image is inside the view, corner to corner`() {
        // The property that matters: nothing the camera saw lands off-screen.
        val viewport = Viewport(1280, 960, viewWidth = 400f, viewHeight = 800f)
        val whole = viewport.map(Box(0.0, 0.0, 1280.0, 960.0))

        assertEquals(0.0, whole.x0, 1e-6)
        assertEquals(400.0, whole.x1, 1e-6)
        assertEquals(true, whole.y0 >= 0.0)
        assertEquals(true, whole.y1 <= 800.0)
    }

    @Test
    fun `a box keeps its shape, so a label is not stretched`() {
        val viewport = Viewport(1000, 500, viewWidth = 400f, viewHeight = 400f)
        val mapped = viewport.map(Box(0.0, 0.0, 100.0, 100.0))
        assertEquals(mapped.x1 - mapped.x0, mapped.y1 - mapped.y0, 1e-6)
    }

    @Test
    fun `a view or image with no size does not divide by zero`() {
        // Compose measures a layout at zero before it measures it properly.
        val viewport = Viewport(0, 0, viewWidth = 100f, viewHeight = 100f)
        assertEquals(0f, viewport.scale)
        assertEquals(Box(50.0, 50.0, 50.0, 50.0), viewport.map(Box(10.0, 10.0, 20.0, 20.0)))
    }
}
