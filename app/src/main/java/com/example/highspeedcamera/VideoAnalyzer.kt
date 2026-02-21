package com.example.highspeedcamera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

// Data Models

enum class FrameClass { NORMAL, FRAME_DROP, FRAME_MERGE }

data class FrameResult(
    val index: Int,
    val timestampMs: Long,
    val classification: FrameClass,
    val meanAbsDiff: Double,
    val motionMagnitude: Double,
    val ssimNeighbor: Double,
    val ssimSynthetic: Double,
    val edgeCount: Int,
    val reason: String,
    val annotatedBitmap: Bitmap?
)

data class AnalysisReport(
    val videoUri: Uri,
    val totalFrames: Int,
    val fps: Double,
    val durationMs: Long,
    val dropCount: Int,
    val mergeCount: Int,
    val normalCount: Int,
    val frames: List<FrameResult>,
    val processingTimeMs: Long
)

// ─── Progress Callback ────────────────────────────────────────────────────────

interface AnalysisProgressCallback {
    fun onProgress(processed: Int, total: Int, currentClass: FrameClass)
    fun onComplete(report: AnalysisReport)
    fun onError(message: String)
}

//  Main Analyzer

class VideoAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "VideoAnalyzer"

        private const val BASE_FPS                   = 30.0
        private const val BASE_DUPLICATE_THRESHOLD   = 1.5
        private const val DROP_MOTION_SIGMA          = 2.5
        private const val DROP_SSIM_SIGMA            = 2.0
        private const val BASE_MERGE_SSIM_SYNTHETIC  = 0.92
        private const val MERGE_EDGE_SIGMA           = 2.0
        private const val ROLLING_WINDOW             = 30
        private const val TRUE_DUPLICATE_SSIM        = 0.997
        private const val SAMPLE_STEP               = 1
        private const val MAX_FRAMES                = 300
        private const val FRAME_W = 640
        private const val FRAME_H = 360
    }

    private fun fpsScaledDuplicateThreshold(fps: Double): Double {
        val ratio = (fps / BASE_FPS).coerceAtLeast(1.0)
        if (ratio <= 1.0) return BASE_DUPLICATE_THRESHOLD
        return BASE_DUPLICATE_THRESHOLD / kotlin.math.ln(ratio + 1.0)
    }

    private fun fpsScaledMergeSsimThreshold(fps: Double): Double {
        val ratio = (fps / BASE_FPS).coerceAtLeast(1.0)
        if (ratio <= 1.0) return BASE_MERGE_SSIM_SYNTHETIC
        val scale = 1.0 - 1.0 / ratio
        return (BASE_MERGE_SSIM_SYNTHETIC + (1.0 - BASE_MERGE_SSIM_SYNTHETIC) * scale)
            .coerceAtMost(0.995)
    }

    fun analyze(uri: Uri, callback: AnalysisProgressCallback) {
        val startTime = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw IllegalStateException("Cannot read video duration")
            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull()
            val videoFps = fpsStr ?: estimateFps(retriever, durationMs)
            Log.d(TAG, "Video: duration=${durationMs}ms fps=$videoFps")

            val frameIntervalMs = (1000.0 / videoFps).toLong().coerceAtLeast(1L)
            val totalPossible = (durationMs / frameIntervalMs).toInt()
            val sampleCount = minOf(totalPossible, MAX_FRAMES)
            val timestamps = (0 until sampleCount step SAMPLE_STEP)
                .map { i -> i * frameIntervalMs }

            Log.d(TAG, "Sampling $sampleCount frames from $totalPossible possible")

            val frames = mutableListOf<Pair<Long, Mat>>()
            for (ts in timestamps) {
                val bmp = retriever.getFrameAtTime(
                    ts * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue
                val mat = bitmapToGrayMat(bmp, FRAME_W, FRAME_H)
                bmp.recycle()
                frames.add(Pair(ts, mat))
            }

            Log.d(TAG, "Extracted ${frames.size} frames")
            if (frames.size < 3) {
                callback.onError("Not enough frames to analyze (need at least 3)")
                return
            }

            val motionQueue = ArrayDeque<Double>(ROLLING_WINDOW)
            val ssimQueue   = ArrayDeque<Double>(ROLLING_WINDOW)
            val edgeQueue   = ArrayDeque<Int>(ROLLING_WINDOW)
            val results     = mutableListOf<FrameResult>()

            for (i in frames.indices) {
                val (ts, grayMat) = frames[i]

                if (i == 0 || i == frames.size - 1) {
                    results.add(FrameResult(i, ts, FrameClass.NORMAL, 0.0, 0.0, 1.0, 0.0, 0, "Boundary frame", null))
                    callback.onProgress(i + 1, frames.size, FrameClass.NORMAL)
                    continue
                }

                val prev = frames[i - 1].second
                val curr = grayMat
                val next = frames[i + 1].second

                val diffMat = Mat()
                Core.absdiff(curr, prev, diffMat)
                val meanAbsDiff = Core.mean(diffMat).`val`[0]
                diffMat.release()

                val flowMat = Mat()
                Video.calcOpticalFlowFarneback(prev, curr, flowMat, 0.5, 3, 15, 3, 5, 1.2, 0)
                val motionMag = computeFlowMagnitude(flowMat)

                val ssimNeighbor = computeSSIM(curr, prev)

                val syntheticBlend = Mat()
                Core.addWeighted(prev, 0.5, next, 0.5, 0.0, syntheticBlend)
                val ssimSynthetic = computeSSIM(curr, syntheticBlend)
                syntheticBlend.release()

                val edges = Mat()
                Imgproc.Canny(curr, edges, 50.0, 150.0)
                val edgeCount = Core.countNonZero(edges)

                motionQueue.addLast(motionMag);  if (motionQueue.size > ROLLING_WINDOW) motionQueue.removeFirst()
                ssimQueue.addLast(ssimNeighbor); if (ssimQueue.size > ROLLING_WINDOW) ssimQueue.removeFirst()
                edgeQueue.addLast(edgeCount);    if (edgeQueue.size > ROLLING_WINDOW) edgeQueue.removeFirst()

                val motionMean = motionQueue.average()
                val motionStd  = stdDev(motionQueue, motionMean)
                val ssimMean   = ssimQueue.average()
                val ssimStd    = stdDev(ssimQueue, ssimMean)
                val edgeMean   = edgeQueue.average()
                val edgeStd    = stdDev(edgeQueue.map { it.toDouble() }, edgeMean)

                val (classification, reason) = classify(
                    meanAbsDiff, motionMag, ssimNeighbor, ssimSynthetic, edgeCount,
                    motionMean, motionStd, ssimMean, ssimStd, edgeMean, edgeStd,
                    motionQueue.size, videoFps
                )

                val annotatedBitmap = if (classification != FrameClass.NORMAL) {
                    annotateFrame(frames[i].second, edges, flowMat, classification, reason,
                        meanAbsDiff, motionMag, ssimNeighbor, ssimSynthetic)
                } else {
                    annotateFrameNormal(frames[i].second, motionMag, ssimNeighbor)
                }

                flowMat.release()
                edges.release()

                val result = FrameResult(
                    index = i, timestampMs = ts, classification = classification,
                    meanAbsDiff = meanAbsDiff, motionMagnitude = motionMag,
                    ssimNeighbor = ssimNeighbor, ssimSynthetic = ssimSynthetic,
                    edgeCount = edgeCount, reason = reason,
                    annotatedBitmap = annotatedBitmap
                )
                results.add(result)
                callback.onProgress(i + 1, frames.size, classification)
            }

            frames.forEach { it.second.release() }

            val report = AnalysisReport(
                videoUri         = uri,
                totalFrames      = results.size,
                fps              = videoFps,
                durationMs       = durationMs,
                dropCount        = results.count { it.classification == FrameClass.FRAME_DROP },
                mergeCount       = results.count { it.classification == FrameClass.FRAME_MERGE },
                normalCount      = results.count { it.classification == FrameClass.NORMAL },
                frames           = results,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
            callback.onComplete(report)

        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            callback.onError(e.message ?: "Unknown error during analysis")
        } finally {
            retriever.release()
        }
    }

    private fun classify(
        meanAbsDiff: Double, motionMag: Double,
        ssimNeighbor: Double, ssimSynthetic: Double, edgeCount: Int,
        motionMean: Double, motionStd: Double,
        ssimMean: Double, ssimStd: Double,
        edgeMean: Double, edgeStd: Double,
        windowSize: Int, fps: Double
    ): Pair<FrameClass, String> {

        val hasEnoughHistory = windowSize >= 5
        val isHighFps = fps > BASE_FPS * 1.5

        val dupThreshold = fpsScaledDuplicateThreshold(fps)
        val mergeSsimThreshold = fpsScaledMergeSsimThreshold(fps)

        if (meanAbsDiff < dupThreshold) {
            val hasHighSsim = ssimNeighbor > TRUE_DUPLICATE_SSIM
            val hasZeroMotion = motionMag < 0.15
            val hasBothSignals = ssimNeighbor > (TRUE_DUPLICATE_SSIM - 0.005) && motionMag < 0.5

            if (hasHighSsim || hasZeroMotion || hasBothSignals) {
                return Pair(FrameClass.FRAME_DROP,
                    "Duplicate frame (Δpx=${f1(meanAbsDiff)}, SSIM=${f3(ssimNeighbor)}, flow=${f2(motionMag)}). Transcoder masked a true drop.")
            }
            if (isHighFps) {
                return Pair(FrameClass.NORMAL,
                    "High-FPS normal (Δpx=${f1(meanAbsDiff)}, SSIM=${f3(ssimNeighbor)}, flow=${f2(motionMag)})")
            }
            return Pair(FrameClass.FRAME_DROP,
                "Duplicate frame (Δpx=${f1(meanAbsDiff)} < ${f1(dupThreshold)}). Transcoder masked a true drop.")
        }

        val motionThresh = if (hasEnoughHistory) motionMean + DROP_MOTION_SIGMA * motionStd else Double.MAX_VALUE
        val ssimDropThresh = if (hasEnoughHistory) ssimMean - DROP_SSIM_SIGMA * ssimStd else Double.MIN_VALUE
        if (hasEnoughHistory && motionMag > motionThresh && ssimNeighbor < ssimDropThresh) {
            return Pair(FrameClass.FRAME_DROP,
                "Motion spike: ${f2(motionMag)} > threshold ${f2(motionThresh)} | SSIM drop: ${f3(ssimNeighbor)} < ${f3(ssimDropThresh)}")
        }

        if (hasEnoughHistory && motionMag > motionThresh * 1.5) {
            return Pair(FrameClass.FRAME_DROP,
                "Strong motion spike: ${f2(motionMag)} >> threshold ${f2(motionThresh)}")
        }

        val edgeThresh = if (hasEnoughHistory) edgeMean + MERGE_EDGE_SIGMA * edgeStd else Double.MAX_VALUE
        if (ssimSynthetic > mergeSsimThreshold) {
            val hasEdgeEvidence = hasEnoughHistory && edgeCount > edgeThresh

            if (isHighFps) {
                val isExtremeBlendMatch = ssimSynthetic > mergeSsimThreshold + 0.005
                val hasMotion = motionMag > 0.5

                if (hasEdgeEvidence) {
                    return Pair(FrameClass.FRAME_MERGE,
                        "Merge (blend=${f3(ssimSynthetic)}, edges=${edgeCount} > ${f0(edgeThresh)})")
                }
                if (isExtremeBlendMatch && hasMotion) {
                    return Pair(FrameClass.FRAME_MERGE,
                        "Merge (blend=${f3(ssimSynthetic)} >> ${f3(mergeSsimThreshold)}, flow=${f2(motionMag)})")
                }
                return Pair(FrameClass.NORMAL,
                    "High-FPS normal (blend=${f3(ssimSynthetic)}, threshold=${f3(mergeSsimThreshold)})")
            }

            val edgeReason = if (hasEdgeEvidence)
                " + double-edge ghosting (edges=${edgeCount} > ${f0(edgeThresh)})" else ""
            return Pair(FrameClass.FRAME_MERGE,
                "Blend similarity: SSIM(F_t, synthetic)=${f3(ssimSynthetic)} > ${f3(mergeSsimThreshold)}$edgeReason")
        }

        val rule5SsimFloor = if (isHighFps) mergeSsimThreshold - 0.02 else 0.80
        if (hasEnoughHistory && edgeCount > edgeThresh && ssimSynthetic > rule5SsimFloor) {
            return Pair(FrameClass.FRAME_MERGE,
                "Edge spike (ghosting): ${edgeCount} > ${f0(edgeThresh)} | blend sim=${f3(ssimSynthetic)}")
        }

        return Pair(FrameClass.NORMAL, "Temporal continuity normal")
    }

    private fun bitmapToGrayMat(bmp: Bitmap, w: Int, h: Int): Mat {
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val rgbMat = Mat()
        Utils.bitmapToMat(scaled, rgbMat)
        if (scaled != bmp) scaled.recycle()
        val grayMat = Mat()
        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)
        rgbMat.release()
        return grayMat
    }

    private fun computeFlowMagnitude(flow: Mat): Double {
        val channels = ArrayList<Mat>()
        Core.split(flow, channels)
        if (channels.size < 2) return 0.0
        val magnitude = Mat()
        val angle = Mat()
        Core.cartToPolar(channels[0], channels[1], magnitude, angle)
        val mean = Core.mean(magnitude).`val`[0]
        magnitude.release(); angle.release()
        channels.forEach { it.release() }
        return mean
    }

    private fun computeSSIM(img1: Mat, img2: Mat): Double {
        val C1 = (0.01 * 255) * (0.01 * 255)
        val C2 = (0.03 * 255) * (0.03 * 255)

        val i1 = Mat(); val i2 = Mat()
        img1.convertTo(i1, CvType.CV_64F)
        img2.convertTo(i2, CvType.CV_64F)

        val mu1 = Mat(); val mu2 = Mat()
        Imgproc.GaussianBlur(i1, mu1, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(i2, mu2, Size(11.0, 11.0), 1.5)

        val mu1_sq = Mat(); val mu2_sq = Mat(); val mu1_mu2 = Mat()
        Core.multiply(mu1, mu1, mu1_sq)
        Core.multiply(mu2, mu2, mu2_sq)
        Core.multiply(mu1, mu2, mu1_mu2)

        val sigma1_sq = Mat(); val sigma2_sq = Mat(); val sigma12 = Mat()
        val tmp1 = Mat(); val tmp2 = Mat()

        Core.multiply(i1, i1, tmp1)
        Imgproc.GaussianBlur(tmp1, sigma1_sq, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma1_sq, mu1_sq, sigma1_sq)

        Core.multiply(i2, i2, tmp1)
        Imgproc.GaussianBlur(tmp1, sigma2_sq, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma2_sq, mu2_sq, sigma2_sq)

        Core.multiply(i1, i2, tmp1)
        Imgproc.GaussianBlur(tmp1, sigma12, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma12, mu1_mu2, sigma12)

        val t1 = Mat(); val t2 = Mat()
        Core.addWeighted(mu1_mu2, 2.0, mu1_mu2, 0.0, C1, t1)
        Core.addWeighted(sigma12, 2.0, sigma12, 0.0, C2, t2)
        val numerator = Mat()
        Core.multiply(t1, t2, numerator)

        val d1 = Mat(); val d2 = Mat()
        Core.add(mu1_sq, mu2_sq, d1)
        Core.add(d1, Mat.ones(d1.size(), d1.type()).also { Core.multiply(it, Scalar(C1), it) }, d1)
        Core.add(sigma1_sq, sigma2_sq, d2)
        Core.add(d2, Mat.ones(d2.size(), d2.type()).also { Core.multiply(it, Scalar(C2), it) }, d2)
        val denominator = Mat()
        Core.multiply(d1, d2, denominator)

        val ssimMap = Mat()
        Core.divide(numerator, denominator, ssimMap)
        val result = Core.mean(ssimMap).`val`[0]

        listOf(i1, i2, mu1, mu2, mu1_sq, mu2_sq, mu1_mu2,
               sigma1_sq, sigma2_sq, sigma12, tmp1, tmp2,
               t1, t2, numerator, d1, d2, denominator, ssimMap)
            .forEach { it.release() }

        return result.coerceIn(-1.0, 1.0)
    }

    private fun annotateFrame(
        grayMat: Mat, edges: Mat, flow: Mat,
        cls: FrameClass, reason: String,
        meanAbsDiff: Double, motionMag: Double,
        ssimNeighbor: Double, ssimSynthetic: Double
    ): Bitmap {
        val color = Mat()
        Imgproc.cvtColor(grayMat, color, Imgproc.COLOR_GRAY2BGR)

        val overlayColor = when (cls) {
            FrameClass.FRAME_DROP  -> Scalar(0.0, 0.0, 255.0)
            FrameClass.FRAME_MERGE -> Scalar(0.0, 200.0, 255.0)
            FrameClass.NORMAL      -> Scalar(0.0, 255.0, 0.0)
        }

        when (cls) {
            FrameClass.FRAME_DROP -> {
                if (flow.channels() == 2) {
                    val channels = ArrayList<Mat>()
                    Core.split(flow, channels)
                    val mag = Mat(); val ang = Mat()
                    Core.cartToPolar(channels[0], channels[1], mag, ang)
                    val magNorm = Mat()
                    Core.normalize(mag, magNorm, 0.0, 255.0, Core.NORM_MINMAX)
                    val highMotion = Mat()
                    Imgproc.threshold(magNorm, highMotion, 180.0, 255.0, Imgproc.THRESH_BINARY)
                    highMotion.convertTo(highMotion, CvType.CV_8U)
                    val redOverlay = Mat(color.size(), color.type(), Scalar(0.0, 0.0, 60.0))
                    redOverlay.copyTo(color, highMotion)
                    listOf(channels[0], channels[1], mag, ang, magNorm, highMotion, redOverlay).forEach { it.release() }
                }
                Imgproc.rectangle(color, Point(0.0, 0.0),
                    Point(color.width().toDouble() - 2, color.height().toDouble() - 2),
                    overlayColor, 6)
            }
            FrameClass.FRAME_MERGE -> {
                val edgeColor = Mat()
                Imgproc.cvtColor(edges, edgeColor, Imgproc.COLOR_GRAY2BGR)
                val tinted = Mat(color.size(), color.type(), Scalar(0.0, 80.0, 120.0))
                tinted.copyTo(color, edges)
                edgeColor.release(); tinted.release()
                Imgproc.rectangle(color, Point(0.0, 0.0),
                    Point(color.width().toDouble() - 2, color.height().toDouble() - 2),
                    overlayColor, 6)
            }
            else -> {}
        }

        val label = when (cls) {
            FrameClass.FRAME_DROP  -> "FRAME DROP"
            FrameClass.FRAME_MERGE -> "FRAME MERGE"
            FrameClass.NORMAL      -> "NORMAL"
        }
        Imgproc.rectangle(color, Point(0.0, 0.0), Point(color.width().toDouble(), 60.0),
            Scalar(0.0, 0.0, 0.0), -1)
        Imgproc.putText(color, label, Point(8.0, 24.0),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, overlayColor, 2)
        Imgproc.putText(color, "ΔPx:${f1(meanAbsDiff)}  Flow:${f2(motionMag)}  SSIM:${f3(ssimNeighbor)}  Syn:${f3(ssimSynthetic)}",
            Point(8.0, 50.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.38, Scalar(200.0, 200.0, 200.0), 1)

        val bmp = Bitmap.createBitmap(color.cols(), color.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(color, bmp)
        color.release()
        return bmp
    }

    private fun annotateFrameNormal(grayMat: Mat, motionMag: Double, ssimNeighbor: Double): Bitmap {
        val color = Mat()
        Imgproc.cvtColor(grayMat, color, Imgproc.COLOR_GRAY2BGR)
        Imgproc.rectangle(color, Point(0.0, 0.0), Point(color.width().toDouble(), 42.0),
            Scalar(0.0, 0.0, 0.0), -1)
        Imgproc.putText(color, "NORMAL", Point(8.0, 22.0),
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(0.0, 220.0, 0.0), 1)
        Imgproc.putText(color, "Flow:${f2(motionMag)}  SSIM:${f3(ssimNeighbor)}",
            Point(8.0, 38.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.35, Scalar(160.0, 160.0, 160.0), 1)
        val bmp = Bitmap.createBitmap(color.cols(), color.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(color, bmp)
        color.release()
        return bmp
    }

    private fun estimateFps(retriever: MediaMetadataRetriever, durationMs: Long): Double {
        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toDoubleOrNull() ?: 30.0
        return (frameCount / (durationMs / 1000.0)).coerceIn(1.0, 240.0)
    }

    private fun stdDev(queue: Collection<Double>, mean: Double): Double {
        if (queue.size < 2) return 0.0
        val variance = queue.sumOf { (it - mean) * (it - mean) } / queue.size
        return sqrt(variance)
    }

    private fun f0(v: Double) = "%.0f".format(v)
    private fun f1(v: Double) = "%.1f".format(v)
    private fun f2(v: Double) = "%.2f".format(v)
    private fun f3(v: Double) = "%.3f".format(v)
}
