package az.gesture.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GestureRecognizerTest {

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 24): List<StrokePoint> {
        return List(steps + 1) { i ->
            val t = i.toFloat() / steps
            StrokePoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
        }
    }

    private fun shapeL(x0: Float, y0: Float, h: Float, w: Float): List<StrokePoint> {
        val down = line(x0, y0, x0, y0 + h, steps = 20)
        val right = line(x0, y0 + h, x0 + w, y0 + h, steps = 16).drop(1)
        return down + right
    }

    private fun rotatedLine(cx: Float, cy: Float, length: Float, angleFromVerticalDeg: Double): List<StrokePoint> {
        val rad = angleFromVerticalDeg * (PI / 180.0)
        val dx = (sin(rad) * length / 2.0).toFloat()
        val dy = (cos(rad) * length / 2.0).toFloat()
        return line(cx - dx, cy - dy, cx + dx, cy + dy)
    }

    private val defaultTemplates = listOf(
        "instagram" to listOf(line(540f, 300f, 540f, 1100f)), // vertical top-to-bottom "|" in Portrait
        "phone" to listOf(shapeL(300f, 350f, 500f, 360f))     // "L" in Portrait
    )

    @Test
    fun verticalLineMatchesInPortraitAndLandscape() {
        // Portrait vertical stroke (different position and size)
        val portraitCandidate = listOf(line(200f, 100f, 204f, 650f))
        val portraitMatch = GestureRecognizer.recognizeStrokes(
            candidateStrokes = portraitCandidate,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("instagram", portraitMatch.id)
        assertTrue("Expected high score, got ${portraitMatch.bestScore}", portraitMatch.bestScore > 0.95)

        // Landscape vertical stroke (top-to-bottom in wide screen coordinates)
        val landscapeCandidate = listOf(line(1200f, 120f, 1196f, 520f))
        val landscapeMatch = GestureRecognizer.recognizeStrokes(
            candidateStrokes = landscapeCandidate,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("instagram", landscapeMatch.id)
        assertTrue("Expected high score, got ${landscapeMatch.bestScore}", landscapeMatch.bestScore > 0.95)
    }

    @Test
    fun verticalLineAllowsSmallNaturalSlant() {
        val slightlySlanted = listOf(rotatedLine(400f, 600f, 500f, angleFromVerticalDeg = 9.0))
        val match = GestureRecognizer.recognizeStrokes(
            candidateStrokes = slightlySlanted,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("instagram", match.id)
        assertTrue("Expected score > 0.90, got ${match.bestScore}", match.bestScore > 0.90)
    }

    @Test
    fun horizontalLineNeverMatchesVerticalLine() {
        val leftToRight = listOf(line(200f, 500f, 900f, 500f))
        val rightToLeft = listOf(line(900f, 500f, 200f, 500f))

        for (candidate in listOf(leftToRight, rightToLeft)) {
            val strictMatch = GestureRecognizer.recognizeStrokes(
                candidateStrokes = candidate,
                templates = defaultTemplates,
                sensitivityPercent = 50, // even at minimum sensitivity!
                directionPriority = true,
                multiStroke = true
            )
            assertNull("Horizontal line must not match vertical '|', got ${strictMatch.id} (${strictMatch.bestScore})", strictMatch.id)

            val directionOffMatch = GestureRecognizer.recognizeStrokes(
                candidateStrokes = candidate,
                templates = defaultTemplates,
                sensitivityPercent = 50,
                directionPriority = false,
                multiStroke = true
            )
            assertNull("Horizontal line must not match vertical '|' even with directionPriority=false", directionOffMatch.id)
        }
    }

    @Test
    fun directionPriorityDistinguishesTopDownVsBottomUp() {
        val bottomToTop = listOf(line(540f, 1100f, 540f, 300f))

        val strict = GestureRecognizer.recognizeStrokes(
            candidateStrokes = bottomToTop,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertNull("Bottom-to-top should not match top-to-bottom when directionPriority=true", strict.id)

        val relaxed = GestureRecognizer.recognizeStrokes(
            candidateStrokes = bottomToTop,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = false,
            multiStroke = true
        )
        assertEquals("instagram", relaxed.id)
    }

    @Test
    fun shapeLMatchesLAndRejects90DegRotatedL() {
        val landscapeL = listOf(shapeL(950f, 150f, 340f, 250f))
        val matchL = GestureRecognizer.recognizeStrokes(
            candidateStrokes = landscapeL,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("phone", matchL.id)

        // L rotated 90° clockwise: right-to-left then down
        val rotated90L = listOf(
            line(700f, 400f, 200f, 400f, 20) + line(200f, 400f, 200f, 750f, 16).drop(1)
        )
        val matchRotated = GestureRecognizer.recognizeStrokes(
            candidateStrokes = rotated90L,
            templates = defaultTemplates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertNull("90°-rotated L must not match L", matchRotated.id)
    }

    @Test
    fun multiStrokeXMatchesXAndRejectsSingleStroke() {
        val templateX = listOf(
            line(200f, 200f, 600f, 600f),
            line(600f, 200f, 200f, 600f)
        )
        val templates = defaultTemplates + ("close" to templateX)

        val candidateX = listOf(
            line(100f, 100f, 420f, 420f),
            line(420f, 100f, 100f, 420f)
        )
        val matchX = GestureRecognizer.recognizeStrokes(
            candidateStrokes = candidateX,
            templates = templates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("close", matchX.id)

        val singleSlash = listOf(line(100f, 100f, 420f, 420f))
        val matchSlash = GestureRecognizer.recognizeStrokes(
            candidateStrokes = singleSlash,
            templates = templates,
            sensitivityPercent = 80,
            directionPriority = true,
            multiStroke = true
        )
        assertNull("Single stroke slash must not match 2-stroke X or '|'", matchSlash.id)
    }

    @Test
    fun sensitivityControlsStrictnessOneToOne() {
        // Wider sloppy L (still clearly an L, not '|', with different leg proportions)
        val sloppyL = listOf(shapeL(200f, 200f, 360f, 480f))

        val relaxedMatch = GestureRecognizer.recognizeStrokes(
            candidateStrokes = sloppyL,
            templates = defaultTemplates,
            sensitivityPercent = 55,
            directionPriority = true,
            multiStroke = true
        )
        assertEquals("phone", relaxedMatch.id)

        val strictMatch = GestureRecognizer.recognizeStrokes(
            candidateStrokes = sloppyL,
            templates = defaultTemplates,
            sensitivityPercent = 95,
            directionPriority = true,
            multiStroke = true
        )
        assertNull("Strict 95% sensitivity must reject sloppy L (score=${strictMatch.bestScore})", strictMatch.id)

        // Exact 1-to-1 threshold mapping
        assertEquals(0.50, GestureRecognizer.minSimilarityForSensitivity(50), 1e-6)
        assertEquals(0.80, GestureRecognizer.minSimilarityForSensitivity(80), 1e-6)
        assertEquals(0.95, GestureRecognizer.minSimilarityForSensitivity(95), 1e-6)
    }
}
