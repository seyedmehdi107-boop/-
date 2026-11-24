package com.example.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import com.example.R
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Holds processed, alpha-masked hand-drawn ink sprites and repeating parchment grain texture
 * matching the user's charcoal-ink-on-parchment illustration style.
 */
class InkSpriteAtlas(
    val creatures: Map<CreatureSkin, Bitmap>,
    val gnarledTree: Bitmap,
    val stonePillar: Bitmap,
    val snowman: Bitmap,
    val iceSpire: Bitmap,
    val paperTexture: Bitmap,
    val paperShader: BitmapShader,
) {
    companion object {
        fun load(context: Context): InkSpriteAtlas {
            val creatures = mapOf(
                CreatureSkin.CROW to loadMaskedSprite(context, R.drawable.img_sprite_crow_1795336896168, targetMaxDim = 540),
                CreatureSkin.FALCON to loadMaskedSprite(context, R.drawable.img_sprite_falcon_1795336897476, targetMaxDim = 540),
                CreatureSkin.EAGLE to loadMaskedSprite(context, R.drawable.img_sprite_eagle_1795336898577, targetMaxDim = 560),
                CreatureSkin.PEACOCK to loadMaskedSprite(context, R.drawable.img_sprite_peacock_1795336919296, targetMaxDim = 560),
                CreatureSkin.BABY_DRAGON to loadMaskedSprite(context, R.drawable.img_sprite_dragon_1795336918922, targetMaxDim = 560),
            )

            // Slice 2x2 hand-drawn obstacles sheet into 4 independent alpha-masked bitmaps
            val sheet = decodeSampledBitmap(context, R.drawable.img_obstacles_ink_1795336919805, maxDim = 900)
            val halfW = sheet.width / 2
            val halfH = sheet.height / 2

            val gnarledTree = processObstacleTile(sheet, 0, 0, halfW, halfH, targetMaxDim = 400)
            val stonePillar = processObstacleTile(sheet, halfW, 0, sheet.width - halfW, halfH, targetMaxDim = 400)
            val snowman = processObstacleTile(sheet, 0, halfH, halfW, sheet.height - halfH, targetMaxDim = 380)
            val iceSpire = processObstacleTile(sheet, halfW, halfH, sheet.width - halfW, sheet.height - halfH, targetMaxDim = 380)
            if (!sheet.isRecycled) sheet.recycle()

            val paperTex = createParchmentGrainBitmap(256)
            val paperShader = BitmapShader(paperTex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

            return InkSpriteAtlas(
                creatures = creatures,
                gnarledTree = gnarledTree,
                stonePillar = stonePillar,
                snowman = snowman,
                iceSpire = iceSpire,
                paperTexture = paperTex,
                paperShader = paperShader,
            )
        }

        private fun loadMaskedSprite(context: Context, resId: Int, targetMaxDim: Int): Bitmap {
            val raw = decodeSampledBitmap(context, resId, maxDim = targetMaxDim)
            val masked = removeExteriorPaperAndTrim(raw, padding = 4)
            if (masked !== raw && !raw.isRecycled) raw.recycle()
            return masked
        }

        private fun processObstacleTile(
            sheet: Bitmap,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            targetMaxDim: Int,
        ): Bitmap {
            val crop = Bitmap.createBitmap(sheet, x, y, w, h)
            val scaled = scaleDownIfNeeded(crop, targetMaxDim)
            if (scaled !== crop && !crop.isRecycled) crop.recycle()
            val masked = removeExteriorPaperAndTrim(scaled, padding = 3)
            if (masked !== scaled && !scaled.isRecycled) scaled.recycle()
            return masked
        }

        private fun decodeSampledBitmap(context: Context, resId: Int, maxDim: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            val srcMax = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (srcMax / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeResource(context.resources, resId, opts)
            val scaled = scaleDownIfNeeded(decoded, maxDim)
            if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
            return scaled
        }

        private fun scaleDownIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
            val w = src.width
            val h = src.height
            val curMax = max(w, h)
            if (curMax <= maxDim) {
                return if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, true)
            }
            val ratio = maxDim.toFloat() / curMax.toFloat()
            val nw = (w * ratio).toInt().coerceAtLeast(1)
            val nh = (h * ratio).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, nw, nh, true)
        }

        /**
         * Removes exterior parchment paper via border flood-fill (BFS) so white/cream interiors
         * (such as Snowman bodies, Stone Pillars, Ice Spires, and Eagle tail bands) stay opaque.
         * Also feathers 1px boundary pixels smoothly to eliminate paper halos.
         */
        private fun removeExteriorPaperAndTrim(src: Bitmap, padding: Int): Bitmap {
            val w = src.width
            val h = src.height
            val total = w * h
            val pixels = IntArray(total)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            // Sample corner paper color to adapt accurately to exact warm cream shade
            val corner = pixels[0]
            val cR = (corner ushr 16) and 0xFF
            val cG = (corner ushr 8) and 0xFF
            val cB = corner and 0xFF
            val cornerLum = (cR * 299 + cG * 587 + cB * 114) / 1000

            val visited = BooleanArray(total)
            val isBg = BooleanArray(total)
            val queue = IntArray(total)
            var head = 0
            var tail = 0

            fun isPaperCandidate(argb: Int): Boolean {
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = maxC - minC
                val diffCorner = kotlin.math.abs(r - cR) + kotlin.math.abs(g - cG) + kotlin.math.abs(b - cB)
                return (lum >= cornerLum - 36 && sat <= 42) || diffCorner <= 48
            }

            fun enqueueIfPaper(idx: Int) {
                if (!visited[idx]) {
                    visited[idx] = true
                    if (isPaperCandidate(pixels[idx])) {
                        isBg[idx] = true
                        queue[tail++] = idx
                    }
                }
            }

            for (x in 0 until w) {
                enqueueIfPaper(x)
                enqueueIfPaper((h - 1) * w + x)
            }
            for (y in 0 until h) {
                enqueueIfPaper(y * w)
                enqueueIfPaper(y * w + (w - 1))
            }

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x > 0) enqueueIfPaper(idx - 1)
                if (x + 1 < w) enqueueIfPaper(idx + 1)
                if (y > 0) enqueueIfPaper(idx - w)
                if (y + 1 < h) enqueueIfPaper(idx + w)
            }

            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1

            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    val idx = rowOff + x
                    if (isBg[idx]) {
                        pixels[idx] = 0x00000000
                    } else {
                        // Feather 1px anti-aliased edge pixels touching transparent paper
                        val touchesBg = (x > 0 && isBg[idx - 1]) ||
                            (x + 1 < w && isBg[idx + 1]) ||
                            (y > 0 && isBg[idx - w]) ||
                            (y + 1 < h && isBg[idx + w])
                        if (touchesBg) {
                            val p = pixels[idx]
                            val r = (p ushr 16) and 0xFF
                            val g = (p ushr 8) and 0xFF
                            val b = p and 0xFF
                            val lum = (r * 299 + g * 587 + b * 114) / 1000
                            val maxC = max(r, max(g, b))
                            val minC = min(r, min(g, b))
                            if (lum > 165 && (maxC - minC) < 48) {
                                val t = ((cornerLum - lum) / 72f).coerceIn(0.18f, 1f)
                                val alpha = (t * 255f).toInt()
                                pixels[idx] = (alpha shl 24) or (p and 0x00FFFFFF)
                            }
                        }
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (maxX <= minX || maxY <= minY) {
                return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            }

            val cropL = (minX - padding).coerceAtLeast(0)
            val cropT = (minY - padding).coerceAtLeast(0)
            val cropR = (maxX + padding + 1).coerceAtMost(w)
            val cropB = (maxY + padding + 1).coerceAtMost(h)
            val cw = (cropR - cropL).coerceAtLeast(1)
            val ch = (cropB - cropT).coerceAtLeast(1)

            val trimmed = IntArray(cw * ch)
            for (row in 0 until ch) {
                System.arraycopy(pixels, (cropT + row) * w + cropL, trimmed, row * cw, cw)
            }
            return Bitmap.createBitmap(trimmed, cw, ch, Bitmap.Config.ARGB_8888)
        }

        /**
         * Generates a seamless repeating paper grain + subtle charcoal ink fiber tile.
         */
        private fun createParchmentGrainBitmap(size: Int): Bitmap {
            val rng = Random(4049)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val noise = (rng.nextInt(19) - 9)
                    val speck = if (rng.nextInt(140) == 0) -24 else 0
                    val delta = noise + speck
                    val alpha = if (delta < 0) (-delta * 3).coerceIn(4, 58) else 10
                    val shade = if (delta < 0) 0x22 else 0xFF
                    pixels[y * size + x] = (alpha shl 24) or (shade shl 16) or (shade shl 8) or shade
                }
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
