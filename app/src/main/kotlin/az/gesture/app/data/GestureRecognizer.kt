package az.gesture.app.data

import android.gesture.Gesture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class StrokeMatch(
    val id: String?,
    val bestScore: Double
)

/**
 * Orientation- and aspect-ratio-faithful gesture recognizer.
 *
 * Unlike Android's legacy GestureStore/InstanceLearner — which snaps or rotates straight strokes
 * across 45°/90° buckets and makes vertical vs. horizontal lines unreliable — this recognizer:
 *  - Works directly in the active screen's visual coordinate frame (+x right, +y down), so a
 *    top-to-bottom stroke in Portrait and a top-to-bottom stroke in Landscape produce identical
 *    normalized geometry.
 *  - Uses isotropic (aspect-ratio-preserving) scaling so 1D straight strokes (e.g. "|" or "—")
 *    are never stretched along their minor axis.
 *  - Allows only a small natural hand-slant tolerance (±12°), never matching a 90°-rotated shape.
 */
object GestureRecognizer {

    internal const val SAMPLES_PER_STROKE = 48
    private const val RMSE_ZERO_SCORE = 0.52
    private const val MIN_STROKE_SPAN = 1e-4f
    private val SLANT_RELAXED_DEG = floatArrayOf(-16f, -8f, 0f, 8f, 16f)
    private val SLANT_BALANCED_DEG = floatArrayOf(-12f, -6f, 0f, 6f, 12f)
    private val SLANT_STRICT_DEG = floatArrayOf(-6f, -3f, 0f, 3f, 6f)

    fun minSimilarityForSensitivity(sensitivityPercent: Int): Double {
        return sensitivityPercent.coerceIn(50, 95) / 100.0
    }

    private fun slantAnglesForSensitivity(sensitivityPercent: Int): FloatArray {
        return when {
            sensitivityPercent <= 60 -> SLANT_RELAXED_DEG
            sensitivityPercent >= 90 -> SLANT_STRICT_DEG
            else -> SLANT_BALANCED_DEG
        }
    }

    fun extractStrokes(gesture: Gesture): List<List<StrokePoint>> {
        val rawStrokes = gesture.strokes ?: return emptyList()
        return rawStrokes.mapNotNull { stroke ->
            val pts = stroke.points ?: return@mapNotNull null
            if (pts.size < 2) return@mapNotNull null
            val count = pts.size / 2
            val list = ArrayList<StrokePoint>(count)
            for (i in 0 until count) {
                list.add(StrokePoint(pts[i * 2], pts[i * 2 + 1]))
            }
            list.takeIf { it.isNotEmpty() }
        }
    }

    fun recognize(
        candidate: Gesture,
        templates: List<Pair<Shortcut, Gesture>>,
        sensitivityPercent: Int,
        directionPriority: Boolean,
        multiStroke: Boolean
    ): RecognitionResult {
        val candidateStrokes = extractStrokes(candidate)
        if (candidateStrokes.isEmpty() || templates.isEmpty()) {
            return RecognitionResult(null, 0.0)
        }
        val byId = HashMap<String, Shortcut>(templates.size)
        val templateStrokes = ArrayList<Pair<String, List<List<StrokePoint>>>>(templates.size)
        for ((shortcut, templateGesture) in templates) {
            val strokes = extractStrokes(templateGesture)
            if (strokes.isNotEmpty()) {
                byId[shortcut.id] = shortcut
                templateStrokes.add(shortcut.id to strokes)
            }
        }
        val match = recognizeStrokes(
            candidateStrokes = candidateStrokes,
            templates = templateStrokes,
            sensitivityPercent = sensitivityPercent,
            directionPriority = directionPriority,
            multiStroke = multiStroke
        )
        return RecognitionResult(
            shortcut = match.id?.let { byId[it] },
            bestScore = match.bestScore
        )
    }

    fun recognizeStrokes(
        candidateStrokes: List<List<StrokePoint>>,
        templates: List<Pair<String, List<List<StrokePoint>>>>,
        sensitivityPercent: Int,
        directionPriority: Boolean,
        multiStroke: Boolean
    ): StrokeMatch {
        val normalizedCandidate = normalizeGesture(candidateStrokes, multiStroke)
            ?: return StrokeMatch(null, 0.0)

        val slantAngles = slantAnglesForSensitivity(sensitivityPercent)
        var bestId: String? = null
        var bestScore = 0.0

        for ((id, rawTemplate) in templates) {
            val normalizedTemplate = normalizeGesture(rawTemplate, multiStroke) ?: continue
            val score = similarity(normalizedCandidate, normalizedTemplate, directionPriority, slantAngles)
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }

        val threshold = minSimilarityForSensitivity(sensitivityPercent)
        return if (bestId != null && bestScore >= threshold) {
            StrokeMatch(bestId, bestScore)
        } else {
            StrokeMatch(null, bestScore)
        }
    }

    internal fun similarity(
        candidate: List<List<StrokePoint>>,
        template: List<List<StrokePoint>>,
        directionPriority: Boolean,
        slantAnglesDeg: FloatArray = SLANT_BALANCED_DEG
    ): Double {
        if (candidate.isEmpty() || candidate.size != template.size) return 0.0

        var bestRmse = Double.POSITIVE_INFINITY
        for (angleDeg in slantAnglesDeg) {
            val rotatedCandidate = if (angleDeg == 0f) {
                candidate
            } else {
                rotateGesture(candidate, angleDeg)
            }
            val rmse = matchRmse(rotatedCandidate, template, directionPriority)
            if (rmse < bestRmse) {
                bestRmse = rmse
            }
        }

        if (!bestRmse.isFinite()) return 0.0
        return (1.0 - bestRmse / RMSE_ZERO_SCORE).coerceIn(0.0, 1.0)
    }

    internal fun normalizeGesture(
        strokes: List<List<StrokePoint>>,
        multiStroke: Boolean
    ): List<List<StrokePoint>>? {
        val usable = strokes.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return null
        val selected = if (multiStroke) usable else listOf(usable.first())

        val resampled = selected.map { resampleStroke(it, SAMPLES_PER_STROKE) }
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        for (stroke in resampled) {
            for (pt in stroke) {
                minX = min(minX, pt.x)
                maxX = max(maxX, pt.x)
                minY = min(minY, pt.y)
                maxY = max(maxY, pt.y)
                sumX += pt.x
                sumY += pt.y
                count++
            }
        }

        val scale = max(maxX - minX, maxY - minY)
        if (count == 0 || !scale.isFinite() || scale < MIN_STROKE_SPAN) {
            return null
        }

        val cx = (sumX / count).toFloat()
        val cy = (sumY / count).toFloat()
        return resampled.map { stroke ->
            stroke.map { pt ->
                StrokePoint((pt.x - cx) / scale, (pt.y - cy) / scale)
            }
        }
    }

    internal fun resampleStroke(
        points: List<StrokePoint>,
        n: Int = SAMPLES_PER_STROKE
    ): List<StrokePoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1 || n <= 1) return List(n.coerceAtLeast(1)) { points.first() }

        val cumDist = FloatArray(points.size)
        var total = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            total += hypot(dx, dy)
            cumDist[i] = total
        }
        if (total < MIN_STROKE_SPAN) {
            return List(n) { points.first() }
        }

        val result = ArrayList<StrokePoint>(n)
        val step = total / (n - 1)
        var segIdx = 0
        for (k in 0 until n) {
            val target = if (k == n - 1) total else k * step
            while (segIdx < points.size - 2 && cumDist[segIdx + 1] < target) {
                segIdx++
            }
            val segStart = cumDist[segIdx]
            val segEnd = cumDist[segIdx + 1]
            val segLen = segEnd - segStart
            val t = if (segLen > 1e-6f) ((target - segStart) / segLen).coerceIn(0f, 1f) else 0f
            val p0 = points[segIdx]
            val p1 = points[segIdx + 1]
            result.add(
                StrokePoint(
                    x = p0.x + t * (p1.x - p0.x),
                    y = p0.y + t * (p1.y - p0.y)
                )
            )
        }
        return result
    }

    private fun rotateGesture(
        strokes: List<List<StrokePoint>>,
        angleDeg: Float
    ): List<List<StrokePoint>> {
        val rad = angleDeg * (PI / 180.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return strokes.map { stroke ->
            stroke.map { pt ->
                StrokePoint(
                    x = pt.x * c - pt.y * s,
                    y = pt.x * s + pt.y * c
                )
            }
        }
    }

    private fun matchRmse(
        candidate: List<List<StrokePoint>>,
        template: List<List<StrokePoint>>,
        directionPriority: Boolean
    ): Double {
        val strokeCount = candidate.size
        val totalPoints = strokeCount * SAMPLES_PER_STROKE

        if (directionPriority || strokeCount == 1) {
            var sumSq = 0.0
            for (i in 0 until strokeCount) {
                val fwd = strokeSumSq(candidate[i], template[i], reversed = false)
                sumSq += if (directionPriority) {
                    fwd
                } else {
                    min(fwd, strokeSumSq(candidate[i], template[i], reversed = true))
                }
            }
            return sqrt(sumSq / totalPoints)
        }

        // Direction-agnostic multi-stroke matching: allow best stroke permutation for 2..3 strokes
        val permutations = when (strokeCount) {
            2 -> listOf(intArrayOf(0, 1), intArrayOf(1, 0))
            3 -> listOf(
                intArrayOf(0, 1, 2),
                intArrayOf(0, 2, 1),
                intArrayOf(1, 0, 2),
                intArrayOf(1, 2, 0),
                intArrayOf(2, 0, 1),
                intArrayOf(2, 1, 0)
            )
            else -> listOf(IntArray(strokeCount) { it })
        }

        var bestSumSq = Double.POSITIVE_INFINITY
        for (perm in permutations) {
            var sumSq = 0.0
            for (i in 0 until strokeCount) {
                val cStroke = candidate[perm[i]]
                val tStroke = template[i]
                sumSq += min(
                    strokeSumSq(cStroke, tStroke, reversed = false),
                    strokeSumSq(cStroke, tStroke, reversed = true)
                )
            }
            if (sumSq < bestSumSq) {
                bestSumSq = sumSq
            }
        }
        return sqrt(bestSumSq / totalPoints)
    }

    private fun strokeSumSq(
        a: List<StrokePoint>,
        b: List<StrokePoint>,
        reversed: Boolean
    ): Double {
        val n = min(a.size, b.size)
        var sumSq = 0.0
        for (i in 0 until n) {
            val pa = a[i]
            val pb = if (reversed) b[n - 1 - i] else b[i]
            val dx = (pa.x - pb.x).toDouble()
            val dy = (pa.y - pb.y).toDouble()
            sumSq += dx * dx + dy * dy
        }
        return sumSq
    }
}
