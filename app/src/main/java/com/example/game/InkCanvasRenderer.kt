package com.example.game

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.CrimsonInk
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkMist
import com.example.ui.theme.InkSketch
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.ParchmentShadow
import com.example.ui.theme.ParchmentWarm
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val FOCAL_LENGTH = 2.65f
private const val Z_OFFSET = 0.75f

private val RainSkyColor = Color(0xFFCED6DA)
private val RainShadowColor = Color(0xFFA7B3BA)
private val SnowSkyColor = Color(0xFFE7EFF3)
private val SnowShadowColor = Color(0xFFC4D1DA)
private val SnowBodyColor = Color(0xFFF6F9FB)
private val IceTintColor = Color(0xFFDCE8EE)

private val CoinGold = Color(0xFFD4A441)
private val CoinGoldShadow = Color(0xFF8C631E)
private val DragonEmberColor = Color(0xFFC84028)
private val MagnetRed = Color(0xFF9C2920)
private val MagnetBlue = Color(0xFF31607A)

private const val MIST_VEIL_AGSL = """
    uniform float2 uRes;
    uniform float uTime;
    uniform float uHorizonY;
    uniform float uWeather;
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(uRes, float2(1.0));
        float distHoriz = abs(fragCoord.y - uHorizonY) / max(uRes.y * 0.24, 1.0);
        float band = exp(-distHoriz * distHoriz * 1.8);
        float wave = sin(uv.x * 9.0 + uTime * 0.55) * 0.5 + cos(uv.x * 16.0 - uTime * 0.35) * 0.5;
        float mistAlpha = band * (0.12 + 0.06 * wave + uWeather * 0.04);
        return half4(0.95, 0.93, 0.88, clamp(mistAlpha, 0.0, 0.28));
    }
"""

private class InkRenderCache {
    val creaturePaths = CreaturePathPool()
    val pathA = Path()
    val pathB = Path()

    val srcRect = Rect()
    val dstRect = RectF()
    val spritePaint = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
    )
    val paperPaint = android.graphics.Paint().apply {
        alpha = 155
    }
    val mistPaint = android.graphics.Paint()
    val mistShader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(MIST_VEIL_AGSL) }.getOrNull()
    } else null

    val zDescObstacleCmp = Comparator<Obstacle3D> { a, b -> b.z.compareTo(a.z) }
    val zDescCoinCmp = Comparator<Coin3D> { a, b -> b.z.compareTo(a.z) }
    val zDescPropCmp = Comparator<GroundProp> { a, b -> b.z.compareTo(a.z) }
}

@Composable
fun InkFlightCanvas(
    world: FlightWorldState,
    frameTick: State<Long>,
    modifier: Modifier = Modifier,
) {
    val cache = remember { InkRenderCache() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Subscribe ONLY the Canvas draw phase to the 120Hz frame tick!
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick.value

        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val shakeX = if (world.screenShake > 0f) {
            sin(world.flapPhase * 19f) * world.screenShake * w * 0.018f
        } else 0f
        val shakeY = if (world.screenShake > 0f) {
            cos(world.flapPhase * 23f) * world.screenShake * h * 0.018f
        } else 0f

        val unitScale = min(w, h) * 0.36f
        val centerX = w * 0.5f + shakeX
        val horizonY = h * 0.44f - world.cameraY * h * 0.09f + shakeY
        val vanishingX = centerX - world.cameraX * w * 0.14f

        val skyColor = computeWeatherSky(world.weatherBlend)
        val shadowColor = computeWeatherShadow(world.weatherBlend)
        val atlas = world.spriteAtlas

        // 1. Textured parchment backdrop, paper-fiber shader & danger flash
        drawWeatherBackdrop(w, h, skyColor, shadowColor, world.dangerLevel, world.hitBannerTimer, atlas, cache)

        // 2. Subtle camera roll tied to creature banking
        val rollDeg = -world.bankAngle * 8.5f
        rotate(degrees = rollDeg, pivot = Offset(centerX, horizonY)) {
            drawDynamicSkyAndHorizon(w, h, vanishingX, horizonY, unitScale, world, skyColor, shadowColor, cache)
            drawPerspectiveGround(w, h, vanishingX, horizonY, unitScale, world, skyColor, atlas, cache)

            // Sort in-place without allocating new lists
            world.obstacles.sortWith(cache.zDescObstacleCmp)
            world.coins.sortWith(cache.zDescCoinCmp)

            for (i in 0 until world.obstacles.size) {
                val obs = world.obstacles[i]
                if (obs.z > GameViewModel.CROW_Z - 0.1f) {
                    drawObstacle3D(obs, centerX, horizonY, unitScale, world.cameraX, world.cameraY, world.flapPhase, world.envTime, skyColor, atlas, cache)
                }
            }
            for (i in 0 until world.coins.size) {
                val coin = world.coins[i]
                if (coin.z > GameViewModel.CROW_Z - 0.1f) {
                    drawCoin3D(coin, centerX, horizonY, unitScale, world.cameraX, world.cameraY, skyColor)
                }
            }
            for (i in 0 until world.magnets.size) {
                val mag = world.magnets[i]
                if (mag.z > GameViewModel.CROW_Z - 0.1f) {
                    drawMagnet3D(mag, centerX, horizonY, unitScale, world.cameraX, world.cameraY, skyColor, cache)
                }
            }

            if (world.phase == GamePhase.LAUNCHING && world.launchProgress < 0.85f) {
                drawLaunchingShoulderSilhouette(w, h, world.launchProgress, cache.pathA)
            }

            drawInkMotes(world, centerX, horizonY, unitScale)

            if (world.phase != GamePhase.MENU) {
                drawHeroCreatureBehind(
                    world = world,
                    paths = cache.creaturePaths,
                    centerX = centerX,
                    horizonY = horizonY,
                    unitScale = unitScale,
                    focalLength = FOCAL_LENGTH,
                    zOffset = Z_OFFSET,
                )
            }

            for (i in 0 until world.obstacles.size) {
                val obs = world.obstacles[i]
                if (obs.z <= GameViewModel.CROW_Z - 0.1f && obs.z > 0.15f) {
                    drawObstacle3D(obs, centerX, horizonY, unitScale, world.cameraX, world.cameraY, world.flapPhase, world.envTime, skyColor, atlas, cache)
                }
            }
            for (i in 0 until world.coins.size) {
                val coin = world.coins[i]
                if (coin.z <= GameViewModel.CROW_Z - 0.1f && coin.z > 0.15f) {
                    drawCoin3D(coin, centerX, horizonY, unitScale, world.cameraX, world.cameraY, skyColor)
                }
            }
            for (i in 0 until world.magnets.size) {
                val mag = world.magnets[i]
                if (mag.z <= GameViewModel.CROW_Z - 0.1f && mag.z > 0.15f) {
                    drawMagnet3D(mag, centerX, horizonY, unitScale, world.cameraX, world.cameraY, skyColor, cache)
                }
            }

            drawParticles(world, centerX, horizonY, unitScale, cache.pathA)
        }

        // 3. Rain streaks or Snowflakes over the scene
        if (world.weatherBlend > 0.08f) {
            drawWeatherOverlay(w, h, world)
        }

        // 4. Wind-rush edge streaks
        if (world.phase == GamePhase.PLAYING) {
            drawWindSpeedStreaks(w, h, world.envTime, world.speed)
        }
    }
}

private fun computeWeatherSky(blend: Float): Color {
    return if (blend <= 1f) {
        lerp(ParchmentPaper, RainSkyColor, blend.coerceIn(0f, 1f))
    } else {
        lerp(RainSkyColor, SnowSkyColor, (blend - 1f).coerceIn(0f, 1f))
    }
}

private fun computeWeatherShadow(blend: Float): Color {
    return if (blend <= 1f) {
        lerp(ParchmentShadow, RainShadowColor, blend.coerceIn(0f, 1f))
    } else {
        lerp(RainShadowColor, SnowShadowColor, (blend - 1f).coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawWeatherBackdrop(
    w: Float,
    h: Float,
    skyColor: Color,
    shadowColor: Color,
    dangerLevel: Float,
    hitBannerTimer: Float,
    atlas: InkSpriteAtlas?,
    cache: InkRenderCache,
) {
    drawRect(color = skyColor)

    // Real repeating parchment grain shader
    if (atlas != null && !atlas.paperTexture.isRecycled) {
        cache.paperPaint.shader = atlas.paperShader
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, w, h, cache.paperPaint)
    }

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, shadowColor.copy(alpha = 0.58f)),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = maxOf(w, h) * 0.78f,
        )
    )

    val flashAlpha = maxOf(dangerLevel * 0.24f, if (hitBannerTimer > 0.8f) 0.32f else 0f)
    if (flashAlpha > 0.04f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, CrimsonInk.copy(alpha = flashAlpha.coerceAtMost(0.32f))),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = maxOf(w, h) * 0.65f,
            )
        )
    }
}

private fun DrawScope.drawDynamicSkyAndHorizon(
    w: Float,
    h: Float,
    vanishingX: Float,
    horizonY: Float,
    unitScale: Float,
    world: FlightWorldState,
    skyColor: Color,
    shadowColor: Color,
    cache: InkRenderCache,
) {
    // Pale hand-drawn moon + halo
    val moonAlpha = (1f - world.weatherBlend * 0.38f).coerceIn(0.22f, 1f)
    val moonX = w * 0.72f - world.cameraX * w * 0.04f
    val moonY = horizonY - h * 0.25f - world.cameraY * h * 0.03f
    drawCircle(color = ParchmentWarm.copy(alpha = 0.52f * moonAlpha), radius = min(w, h) * 0.11f, center = Offset(moonX, moonY))
    drawCircle(color = InkMist.copy(alpha = 0.26f * moonAlpha), radius = min(w, h) * 0.088f, center = Offset(moonX, moonY), style = Stroke(width = 1.6f))

    // Drifting ink-wash clouds
    val cloudDarkness = (0.25f + world.weatherBlend * 0.12f).coerceAtMost(0.45f)
    for (i in 0 until world.skyClouds.size) {
        val cloud = world.skyClouds[i]
        val scale = FOCAL_LENGTH / (cloud.z + Z_OFFSET)
        val cx = w * 0.5f + (cloud.x - world.cameraX * 0.3f) * scale * unitScale
        val cy = horizonY + (cloud.y - world.cameraY * 0.25f) * scale * unitScale
        val cw = cloud.width * scale * unitScale
        val ch = cw * 0.27f
        val fogAlpha = (1f - cloud.z / GameViewModel.FAR_Z).coerceIn(0.06f, cloudDarkness)
        drawOval(color = InkMist.copy(alpha = fogAlpha), topLeft = Offset(cx - cw * 0.5f, cy - ch * 0.5f), size = Size(cw, ch))
        drawOval(color = InkMist.copy(alpha = fogAlpha * 0.72f), topLeft = Offset(cx - cw * 0.25f, cy - ch * 0.82f), size = Size(cw * 0.55f, ch * 0.75f))
    }

    // Distant V-flock
    val flockBaseX = ((world.envTime * 0.03f) % 1.3f - 0.15f) * w
    val flockBaseY = horizonY - h * 0.14f + sin(world.envTime * 0.8f) * 14f
    for (b in 0..3) {
        val bx = flockBaseX - b * 22f
        val by = flockBaseY + abs(b - 1.5f) * 8f
        val wingY = sin(world.envTime * 5f + b * 1.1f) * 4f
        drawLine(InkMist.copy(alpha = 0.36f), Offset(bx - 7f, by + wingY), Offset(bx, by), strokeWidth = 1.4f)
        drawLine(InkMist.copy(alpha = 0.36f), Offset(bx, by), Offset(bx + 7f, by + wingY), strokeWidth = 1.4f)
    }

    // 3-Layer Parallax Ink Mountains dissolving into horizon mist
    drawMountainLayer(w, horizonY, (vanishingX - w * 0.5f) * 0.16f, heightScale = 52f, seedOffset = 0.3f, color = InkMist.copy(alpha = 0.13f), path = cache.pathA)
    drawMountainLayer(w, horizonY, (vanishingX - w * 0.5f) * 0.28f, heightScale = 36f, seedOffset = 1.9f, color = InkMist.copy(alpha = 0.20f), path = cache.pathA)
    drawMountainLayer(w, horizonY, (vanishingX - w * 0.5f) * 0.42f, heightScale = 22f, seedOffset = 4.2f, color = InkCharcoal.copy(alpha = 0.22f), path = cache.pathA)

    // Animated AGSL Mist Veil on Android 13+ or soft vertical gradient fallback
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cache.mistShader is RuntimeShader) {
        val shader = cache.mistShader
        shader.setFloatUniform("uRes", w, h)
        shader.setFloatUniform("uTime", world.envTime)
        shader.setFloatUniform("uHorizonY", horizonY)
        shader.setFloatUniform("uWeather", world.weatherBlend)
        cache.mistPaint.shader = shader
        drawContext.canvas.nativeCanvas.drawRect(0f, horizonY - h * 0.18f, w, horizonY + h * 0.18f, cache.mistPaint)
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, shadowColor.copy(alpha = 0.58f), skyColor.copy(alpha = 0.36f), Color.Transparent),
                startY = horizonY - h * 0.14f,
                endY = horizonY + h * 0.12f,
            )
        )
    }

    for (lineIdx in 0..2) {
        val yOff = lineIdx * 2.5f
        drawLine(InkSketch.copy(alpha = 0.24f - lineIdx * 0.05f), Offset(-w * 0.1f, horizonY + yOff), Offset(w * 1.1f, horizonY + yOff), strokeWidth = 1.4f)
    }
}

private fun DrawScope.drawMountainLayer(
    w: Float,
    horizonY: Float,
    shift: Float,
    heightScale: Float,
    seedOffset: Float,
    color: Color,
    path: Path,
) {
    path.reset()
    path.moveTo(-w * 0.25f, horizonY + 14f)
    for (i in 0..28) {
        val t = i / 28f
        val rx = -w * 0.25f + t * w * 1.5f + shift
        val ridge = sin(i * 1.45f + seedOffset) * heightScale * 0.55f +
            cos(i * 2.9f + seedOffset * 1.7f) * heightScale * 0.35f
        val spire = if (i % 5 == 2) -heightScale * 0.55f else 0f
        path.lineTo(rx, horizonY - heightScale * 0.32f + ridge + spire)
    }
    path.lineTo(w * 1.3f, horizonY + 24f)
    path.close()
    drawPath(path, color = color)
}

private fun DrawScope.drawPerspectiveGround(
    w: Float,
    h: Float,
    vanishingX: Float,
    horizonY: Float,
    unitScale: Float,
    world: FlightWorldState,
    skyColor: Color,
    atlas: InkSpriteAtlas?,
    cache: InkRenderCache,
) {
    val groundWorldY = 0.94f
    val snowTint = (world.weatherBlend - 1f).coerceIn(0f, 1f)
    val groundLineColor = lerp(InkSketch.copy(alpha = 0.24f), SnowBodyColor.copy(alpha = 0.45f), snowTint)

    for (lane in -4..4) {
        val worldX = lane * 0.65f
        val nearScale = FOCAL_LENGTH / (1.0f + Z_OFFSET)
        val farScale = FOCAL_LENGTH / (GameViewModel.FAR_Z + Z_OFFSET)

        val xFar = vanishingX + (worldX - world.cameraX * 0.65f) * farScale * unitScale
        val yFar = horizonY + (groundWorldY - world.cameraY * 0.58f) * farScale * unitScale
        val xNear = vanishingX + (worldX - world.cameraX * 0.65f) * nearScale * unitScale
        val yNear = horizonY + (groundWorldY - world.cameraY * 0.58f) * nearScale * unitScale

        drawLine(
            color = groundLineColor,
            start = Offset(xFar, yFar),
            end = Offset(xNear, yNear),
            strokeWidth = 1.5f,
        )
    }

    world.groundProps.sortWith(cache.zDescPropCmp)
    val treeBitmap = atlas?.gnarledTree
    for (i in 0 until world.groundProps.size) {
        val prop = world.groundProps[i]
        val scale = FOCAL_LENGTH / (prop.z + Z_OFFSET)
        val sx = (w * 0.5f) + (prop.x - world.cameraX * 0.65f) * scale * unitScale
        val syBase = horizonY + (groundWorldY - world.cameraY * 0.58f) * scale * unitScale
        val propH = prop.height * scale * unitScale * 1.55f
        val fogFactor = (1f - prop.z / GameViewModel.FAR_Z).coerceIn(0.08f, 0.88f)
        val inkColor = lerp(skyColor, InkCharcoal, fogFactor)
        val windSway = sin(world.envTime * 2.2f + prop.seed) * 1.8f

        if (prop.isGnarledTree && treeBitmap != null && !treeBitmap.isRecycled) {
            val propW = propH * (treeBitmap.width.toFloat() / treeBitmap.height.toFloat())
            val flipX = prop.seed % 2 == 0
            rotate(degrees = windSway * 0.7f, pivot = Offset(sx, syBase)) {
                drawSpriteRect(
                    bitmap = treeBitmap,
                    left = sx - propW * 0.5f,
                    top = syBase - propH,
                    right = sx + propW * 0.5f,
                    bottom = syBase,
                    alpha = (fogFactor.pow(0.85f) * 235f).toInt().coerceIn(20, 235),
                    flipX = flipX,
                    flipY = false,
                    cache = cache,
                )
            }
        } else if (prop.isGnarledTree) {
            drawSketchTreeSilhouette(sx, syBase, propH, prop.seed, windSway * 2f, inkColor)
        } else {
            val lean = ((prop.seed % 11) - 5) * 0.04f * propH + windSway * 1.6f
            drawLine(inkColor, Offset(sx, syBase), Offset(sx + lean, syBase - propH * 0.45f), strokeWidth = (3.5f * scale).coerceIn(1.2f, 6f), cap = StrokeCap.Round)
            drawLine(inkColor.copy(alpha = fogFactor * 0.6f), Offset(sx - propH * 0.14f, syBase), Offset(sx + propH * 0.14f, syBase), strokeWidth = 1.5f)
        }
    }
}

private fun DrawScope.drawSpriteRect(
    bitmap: Bitmap,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    alpha: Int,
    flipX: Boolean,
    flipY: Boolean,
    cache: InkRenderCache,
) {
    cache.srcRect.set(0, 0, bitmap.width, bitmap.height)
    cache.dstRect.set(left, top, right, bottom)
    cache.spritePaint.alpha = alpha
    cache.spritePaint.colorFilter = null

    val nc = drawContext.canvas.nativeCanvas
    if (flipX || flipY) {
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        nc.save()
        nc.scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, cx, cy)
        nc.drawBitmap(bitmap, cache.srcRect, cache.dstRect, cache.spritePaint)
        nc.restore()
    } else {
        nc.drawBitmap(bitmap, cache.srcRect, cache.dstRect, cache.spritePaint)
    }
}

private fun DrawScope.drawSketchTreeSilhouette(
    baseX: Float,
    baseY: Float,
    height: Float,
    seed: Int,
    windSway: Float,
    color: Color,
) {
    val trunkW = (height * 0.12f).coerceIn(1.5f, 14f)
    val lean = ((seed % 9) - 4) * 0.03f * height + windSway
    val midX = baseX + lean * 0.5f
    val midY = baseY - height * 0.52f
    val topX = baseX + lean
    val topY = baseY - height

    drawLine(color, Offset(baseX, baseY), Offset(midX, midY), strokeWidth = trunkW, cap = StrokeCap.Round)
    drawLine(color, Offset(midX, midY), Offset(topX, topY), strokeWidth = trunkW * 0.65f, cap = StrokeCap.Round)
    drawLine(color, Offset(midX, midY), Offset(midX - height * 0.34f + windSway * 0.6f, midY - height * 0.25f), strokeWidth = (trunkW * 0.42f).coerceAtLeast(1f), cap = StrokeCap.Round)
    drawLine(color, Offset(midX + lean * 0.2f, midY - height * 0.14f), Offset(midX + height * 0.36f + windSway * 0.6f, midY - height * 0.34f), strokeWidth = (trunkW * 0.38f).coerceAtLeast(1f), cap = StrokeCap.Round)
}

private fun DrawScope.drawCoin3D(
    coin: Coin3D,
    centerX: Float,
    horizonY: Float,
    unitScale: Float,
    cameraX: Float,
    cameraY: Float,
    skyColor: Color,
) {
    val scale = FOCAL_LENGTH / (coin.z + Z_OFFSET)
    val cx = centerX + (coin.x - cameraX * 0.65f) * scale * unitScale
    val bob = sin(coin.spinPhase * 0.9f) * 6f * scale
    val cy = horizonY + (coin.y - cameraY * 0.58f) * scale * unitScale + bob
    val radiusY = (0.095f * scale * unitScale).coerceAtLeast(4f)
    val spinFactor = abs(cos(coin.spinPhase)).coerceAtLeast(0.22f)
    val radiusX = radiusY * spinFactor
    val depthNorm = (1f - (coin.z / GameViewModel.FAR_Z)).coerceIn(0.15f, 1f)

    // Warm ink aura
    drawOval(color = CoinGold.copy(alpha = 0.25f * depthNorm), topLeft = Offset(cx - radiusX * 1.4f, cy - radiusY * 1.4f), size = Size(radiusX * 2.8f, radiusY * 2.8f))
    // Outer embossed medallion
    drawOval(color = lerp(skyColor, CoinGold, depthNorm), topLeft = Offset(cx - radiusX, cy - radiusY), size = Size(radiusX * 2f, radiusY * 2f))
    drawOval(color = lerp(skyColor, InkBlack, depthNorm), topLeft = Offset(cx - radiusX, cy - radiusY), size = Size(radiusX * 2f, radiusY * 2f), style = Stroke(width = (2.1f * scale).coerceIn(1f, 3.8f)))
    // Inner engraved ring & vertical glint
    if (radiusX > 5f) {
        drawOval(color = CoinGoldShadow.copy(alpha = 0.65f * depthNorm), topLeft = Offset(cx - radiusX * 0.66f, cy - radiusY * 0.66f), size = Size(radiusX * 1.32f, radiusY * 1.32f), style = Stroke(width = (1.1f * scale).coerceAtLeast(0.8f)))
        drawLine(ParchmentWarm.copy(alpha = 0.85f * depthNorm), Offset(cx - radiusX * 0.2f, cy - radiusY * 0.42f), Offset(cx - radiusX * 0.2f, cy + radiusY * 0.42f), strokeWidth = (1.5f * scale).coerceAtLeast(1f), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawMagnet3D(
    mag: MagnetPickup3D,
    centerX: Float,
    horizonY: Float,
    unitScale: Float,
    cameraX: Float,
    cameraY: Float,
    skyColor: Color,
    cache: InkRenderCache,
) {
    val scale = FOCAL_LENGTH / (mag.z + Z_OFFSET)
    val cx = centerX + (mag.x - cameraX * 0.65f) * scale * unitScale
    val bob = sin(mag.spinPhase) * 8f * scale
    val cy = horizonY + (mag.y - cameraY * 0.58f) * scale * unitScale + bob
    val r = (0.145f * scale * unitScale).coerceAtLeast(8f)
    val depthNorm = (1f - (mag.z / GameViewModel.FAR_Z)).coerceIn(0.18f, 1f)

    // Pulsing magnetic field rings
    drawCircle(
        color = lerp(skyColor, MagnetBlue, depthNorm).copy(alpha = 0.28f),
        radius = r * (1.30f + 0.14f * sin(mag.spinPhase * 2f)),
        center = Offset(cx, cy),
    )
    drawCircle(
        color = lerp(skyColor, InkBlack, depthNorm).copy(alpha = 0.5f),
        radius = r * 1.25f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f),
    )

    val uPath = cache.pathA
    uPath.reset()
    uPath.moveTo(cx - r * 0.52f, cy - r * 0.55f)
    uPath.lineTo(cx - r * 0.52f, cy + r * 0.05f)
    uPath.quadraticTo(cx - r * 0.52f, cy + r * 0.65f, cx, cy + r * 0.65f)
    uPath.quadraticTo(cx + r * 0.52f, cy + r * 0.65f, cx + r * 0.52f, cy + r * 0.05f)
    uPath.lineTo(cx + r * 0.52f, cy - r * 0.55f)

    val strokeW = (r * 0.34f).coerceAtLeast(3f)
    drawPath(uPath, color = lerp(skyColor, MagnetRed, depthNorm), style = Stroke(width = strokeW, cap = StrokeCap.Butt))
    drawPath(uPath, color = lerp(skyColor, InkBlack, depthNorm), style = Stroke(width = 1.8f, cap = StrokeCap.Round))

    drawLine(ParchmentPaper, Offset(cx - r * 0.52f, cy - r * 0.62f), Offset(cx - r * 0.52f, cy - r * 0.32f), strokeWidth = strokeW * 1.05f)
    drawLine(ParchmentPaper, Offset(cx + r * 0.52f, cy - r * 0.62f), Offset(cx + r * 0.52f, cy - r * 0.32f), strokeWidth = strokeW * 1.05f)
}

private fun DrawScope.drawObstacle3D(
    obs: Obstacle3D,
    centerX: Float,
    horizonY: Float,
    unitScale: Float,
    cameraX: Float,
    cameraY: Float,
    flapPhase: Float,
    envTime: Float,
    skyColor: Color,
    atlas: InkSpriteAtlas?,
    cache: InkRenderCache,
) {
    val scale = FOCAL_LENGTH / (obs.z + Z_OFFSET)
    val left = centerX + (obs.minX - cameraX * 0.65f) * scale * unitScale
    val right = centerX + (obs.maxX - cameraX * 0.65f) * scale * unitScale
    val top = horizonY + (obs.minY - cameraY * 0.58f) * scale * unitScale
    val bottom = horizonY + (obs.maxY - cameraY * 0.58f) * scale * unitScale

    val obsW = (right - left).coerceAtLeast(2f)
    val obsH = (bottom - top).coerceAtLeast(2f)

    val depthNorm = (1f - (obs.z / GameViewModel.FAR_Z)).coerceIn(0.05f, 1f)
    val spriteAlpha = (depthNorm.pow(0.75f) * 255f).toInt().coerceIn(30, 255)
    val inkMain = lerp(skyColor, InkBlack, depthNorm.coerceIn(0.18f, 1f))
    val inkHatch = lerp(skyColor, InkCharcoal, depthNorm * 0.85f)
    val inkStroke = lerp(skyColor, InkSketch, depthNorm * 0.9f)

    if (obs.kind != ObstacleKind.ARCH_LINTEL && obs.kind != ObstacleKind.ICE_SPIRE) {
        val groundY = horizonY + (0.94f - cameraY * 0.58f) * scale * unitScale
        drawOval(
            color = InkCharcoal.copy(alpha = 0.16f * depthNorm),
            topLeft = Offset(left - obsW * 0.1f, groundY - 4f * scale),
            size = Size(obsW * 1.2f, (10f * scale).coerceAtLeast(3f)),
        )
    }

    when (obs.kind) {
        ObstacleKind.SNOWMAN -> {
            val bmp = atlas?.snowman
            if (bmp != null && !bmp.isRecycled) {
                drawSpriteRect(
                    bitmap = bmp,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    alpha = spriteAlpha,
                    flipX = obs.seed % 2 == 1,
                    flipY = false,
                    cache = cache,
                )
            } else {
                drawProceduralSnowman(left, right, top, bottom, obsW, obsH, scale, depthNorm, skyColor, inkMain, inkHatch, cache)
            }
        }

        ObstacleKind.ICE_SPIRE -> {
            val bmp = atlas?.iceSpire
            if (bmp != null && !bmp.isRecycled) {
                val hangingFromTop = obs.minY < -0.5f
                drawSpriteRect(
                    bitmap = bmp,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    alpha = spriteAlpha,
                    flipX = obs.seed % 2 == 1,
                    flipY = hangingFromTop,
                    cache = cache,
                )
            } else {
                val cx = (left + right) * 0.5f
                cache.pathA.reset()
                cache.pathA.moveTo(left, top)
                cache.pathA.lineTo(right, top)
                cache.pathA.lineTo(cx + obsW * 0.15f, bottom - obsH * 0.22f)
                cache.pathA.lineTo(cx, bottom)
                cache.pathA.lineTo(cx - obsW * 0.18f, bottom - obsH * 0.28f)
                cache.pathA.close()
                drawPath(cache.pathA, color = lerp(skyColor, IceTintColor, depthNorm))
                drawPath(cache.pathA, color = inkMain, style = Stroke(width = (2.4f * scale).coerceIn(1.2f, 4.5f), join = StrokeJoin.Miter))
            }
        }

        ObstacleKind.GNARLED_PILLAR -> {
            val bmp = atlas?.gnarledTree
            if (bmp != null && !bmp.isRecycled) {
                val sway = sin(envTime * 1.6f + obs.seed) * 0.9f
                rotate(degrees = sway, pivot = Offset((left + right) * 0.5f, bottom)) {
                    drawSpriteRect(
                        bitmap = bmp,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        alpha = spriteAlpha,
                        flipX = obs.seed % 2 == 0,
                        flipY = false,
                        cache = cache,
                    )
                }
            } else {
                drawProceduralGnarledPillar(left, right, top, bottom, obsW, obsH, obs.seed, scale, depthNorm, envTime, inkMain, cache)
            }
        }

        ObstacleKind.ARCH_PILLAR -> {
            val bmp = atlas?.stonePillar
            if (bmp != null && !bmp.isRecycled) {
                drawSpriteRect(
                    bitmap = bmp,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    alpha = spriteAlpha,
                    flipX = obs.seed % 2 == 1,
                    flipY = false,
                    cache = cache,
                )
            } else {
                drawProceduralStoneRect(left, right, top, bottom, obsW, obsH, scale, depthNorm, inkMain, inkHatch, isLintel = false)
            }
        }

        ObstacleKind.ARCH_LINTEL -> {
            val bmp = atlas?.stonePillar
            if (bmp != null && !bmp.isRecycled) {
                val cx = (left + right) * 0.5f
                val cy = (top + bottom) * 0.5f
                rotate(degrees = 90f, pivot = Offset(cx, cy)) {
                    drawSpriteRect(
                        bitmap = bmp,
                        left = cx - obsH * 0.5f,
                        top = cy - obsW * 0.5f,
                        right = cx + obsH * 0.5f,
                        bottom = cy + obsW * 0.5f,
                        alpha = spriteAlpha,
                        flipX = false,
                        flipY = false,
                        cache = cache,
                    )
                }
            } else {
                drawProceduralStoneRect(left, right, top, bottom, obsW, obsH, scale, depthNorm, inkMain, inkHatch, isLintel = true)
            }
        }

        ObstacleKind.OVERHANGING_BOUGH -> {
            val boughPath = cache.pathA
            boughPath.reset()
            boughPath.moveTo(left, top)
            boughPath.lineTo(right, top)
            boughPath.lineTo(right, bottom - obsH * 0.08f)
            for (i in 11 downTo 0) {
                val t = i / 11f
                val bx = left + t * obsW
                val sag = (0.5f - 0.5f * cos(t * PI.toFloat() * 3.0f + obs.seed + envTime * 1.4f)) * obsH * 0.14f
                boughPath.lineTo(bx, bottom - sag)
            }
            boughPath.close()
            drawPath(boughPath, color = lerp(skyColor, InkCharcoal, depthNorm * 0.9f))
            drawPath(boughPath, color = inkMain, style = Stroke(width = (2.4f * scale).coerceIn(1.2f, 4.5f)))
            for (i in 1..14) {
                val bx = left + obsW * (i / 15f)
                drawLine(inkMain.copy(alpha = 0.65f * depthNorm), Offset(bx, top + obsH * 0.1f), Offset(bx - obsW * 0.04f, bottom - obsH * 0.06f), strokeWidth = (1.4f * scale).coerceAtLeast(0.7f))
            }
        }

        ObstacleKind.THORN_BARRIER -> {
            drawRect(color = lerp(skyColor, InkCharcoal, depthNorm * 0.92f), topLeft = Offset(left, top + obsH * 0.08f), size = Size(obsW, obsH * 0.92f))
            val spikePath = cache.pathA
            spikePath.reset()
            for (i in 0 until 12) {
                val sx0 = left + obsW * (i / 12f)
                val sxMid = left + obsW * ((i + 0.5f) / 12f)
                val sx1 = left + obsW * ((i + 1f) / 12f)
                val spikeTipY = top + obsH * (if (i % 2 == 0) 0.0f else 0.03f)
                spikePath.moveTo(sx0, top + obsH * 0.09f)
                spikePath.lineTo(sxMid, spikeTipY)
                spikePath.lineTo(sx1, top + obsH * 0.09f)
            }
            drawPath(spikePath, color = inkMain, style = Stroke(width = (2.2f * scale).coerceIn(1f, 4.2f), join = StrokeJoin.Miter))
            for (i in 0..9) {
                val x1 = left + obsW * (i / 9f)
                drawLine(inkStroke, Offset(x1, top + obsH * 0.09f), Offset((x1 + obsW * 0.14f).coerceAtMost(right), bottom), strokeWidth = (1.6f * scale).coerceAtLeast(0.8f))
            }
        }

        ObstacleKind.SHADOW_CROW -> {
            val cx = (left + right) * 0.5f
            val cy = (top + bottom) * 0.5f
            val crowBmp = atlas?.creatures?.get(CreatureSkin.CROW)
            if (crowBmp != null && !crowBmp.isRecycled) {
                drawShadowCrowSprite(
                    bitmap = crowBmp,
                    paths = cache.creaturePaths,
                    cx = cx,
                    cy = cy,
                    widthPx = obsW,
                    flapPhase = flapPhase * 1.15f + obs.seed,
                    depthNorm = depthNorm,
                    tintColorInt = CrimsonInk.copy(alpha = 0.28f).toArgb(),
                )
            } else {
                val wingSpan = obsW * 0.58f
                val flap = sin(flapPhase * 1.1f + obs.seed) * obsH * 0.6f
                drawOval(color = inkMain, topLeft = Offset(cx - obsW * 0.12f, cy - obsH * 0.25f), size = Size(obsW * 0.24f, obsH * 0.5f))
                cache.pathA.reset()
                cache.pathA.moveTo(cx, cy)
                cache.pathA.quadraticTo(cx - wingSpan * 0.5f, cy - obsH * 0.4f + flap * 0.6f, cx - wingSpan, cy + flap)
                cache.pathA.quadraticTo(cx - wingSpan * 0.45f, cy + obsH * 0.2f + flap * 0.3f, cx, cy + obsH * 0.15f)
                cache.pathA.close()
                drawPath(cache.pathA, color = inkMain)
                cache.pathB.reset()
                cache.pathB.moveTo(cx, cy)
                cache.pathB.quadraticTo(cx + wingSpan * 0.5f, cy - obsH * 0.4f + flap * 0.6f, cx + wingSpan, cy + flap)
                cache.pathB.quadraticTo(cx + wingSpan * 0.45f, cy + obsH * 0.2f + flap * 0.3f, cx, cy + obsH * 0.15f)
                cache.pathB.close()
                drawPath(cache.pathB, color = inkMain)
            }
        }
    }
}

private fun DrawScope.drawProceduralSnowman(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    obsW: Float,
    obsH: Float,
    scale: Float,
    depthNorm: Float,
    skyColor: Color,
    inkMain: Color,
    inkHatch: Color,
    cache: InkRenderCache,
) {
    val cx = (left + right) * 0.5f
    val baseR = min(obsW * 0.48f, obsH * 0.25f)
    val midR = baseR * 0.76f
    val headR = baseR * 0.54f
    val baseCy = bottom - baseR
    val midCy = baseCy - baseR * 0.85f - midR * 0.75f
    val headCy = midCy - midR * 0.82f - headR * 0.78f
    val fillCol = lerp(skyColor, SnowBodyColor, depthNorm)
    val strokeW = (2.4f * scale).coerceIn(1.2f, 4.5f)
    for ((cy, r) in listOf(baseCy to baseR, midCy to midR, headCy to headR)) {
        drawCircle(color = fillCol, radius = r, center = Offset(cx, cy))
        drawCircle(color = inkMain, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeW))
        for (h in -2..2) {
            val hy = cy + h * r * 0.24f
            drawLine(inkHatch.copy(alpha = 0.45f), Offset(cx - r * 0.8f, hy), Offset(cx - r * 0.35f, hy + r * 0.15f), strokeWidth = 1.2f)
        }
    }
    cache.pathB.reset()
    cache.pathB.moveTo(cx, headCy)
    cache.pathB.lineTo(cx + headR * 0.72f, headCy + headR * 0.15f)
    cache.pathB.lineTo(cx, headCy + headR * 0.24f)
    cache.pathB.close()
    drawPath(cache.pathB, color = lerp(skyColor, DragonEmberColor, depthNorm))
}

private fun DrawScope.drawProceduralGnarledPillar(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    obsW: Float,
    obsH: Float,
    seed: Int,
    scale: Float,
    depthNorm: Float,
    envTime: Float,
    inkMain: Color,
    cache: InkRenderCache,
) {
    val sway = sin(envTime * 1.8f + seed) * obsW * 0.04f
    val path = cache.pathA
    path.reset()
    path.moveTo(left + obsW * 0.08f, bottom)
    for (i in 1..7) {
        val t = i / 7f
        path.lineTo(left + sin(seed + i * 2.3f) * obsW * 0.09f + sway * t, bottom - t * obsH)
    }
    path.lineTo(right - obsW * 0.05f + sway, top)
    for (i in 7 downTo 0) {
        val t = i / 7f
        path.lineTo(right + cos(seed + i * 2.1f) * obsW * 0.09f + sway * t, bottom - t * obsH)
    }
    path.close()
    drawPath(path, color = lerp(ParchmentWarm, InkCharcoal, depthNorm * 0.92f))
    drawPath(path, color = inkMain, style = Stroke(width = (2.6f * scale).coerceIn(1.2f, 5f), join = StrokeJoin.Round))
}

private fun DrawScope.drawProceduralStoneRect(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    obsW: Float,
    obsH: Float,
    scale: Float,
    depthNorm: Float,
    inkMain: Color,
    inkHatch: Color,
    isLintel: Boolean,
) {
    val stoneFill = lerp(ParchmentWarm, InkSketch, depthNorm * 0.88f)
    drawRect(color = stoneFill, topLeft = Offset(left, top), size = Size(obsW, obsH))
    drawRect(color = inkMain, topLeft = Offset(left, top), size = Size(obsW, obsH), style = Stroke(width = (2.8f * scale).coerceIn(1.2f, 5.5f)))
    val blockRows = if (isLintel) 3 else 7
    for (r in 1 until blockRows) {
        val ry = top + obsH * (r / blockRows.toFloat())
        drawLine(inkMain.copy(alpha = 0.7f * depthNorm), Offset(left + 2f, ry), Offset(right - 2f, ry), strokeWidth = (1.5f * scale).coerceAtLeast(0.8f))
    }
    for (k in 0 until 10) {
        val hy = top + obsH * (k / 10f)
        drawLine(inkHatch.copy(alpha = 0.6f * depthNorm), Offset(left, hy), Offset(left + obsW * 0.42f, hy + min(obsW, obsH) * 0.22f), strokeWidth = (1.2f * scale).coerceAtLeast(0.6f))
    }
}

private fun DrawScope.drawLaunchingShoulderSilhouette(w: Float, h: Float, progress: Float, path: Path) {
    val alpha = (1f - progress / 0.85f).coerceIn(0f, 1f)
    val driftX = progress * w * 0.12f
    val driftY = progress * h * 0.48f

    path.reset()
    path.moveTo(w * 0.32f + driftX, h * 1.08f + driftY)
    path.quadraticTo(w * 0.46f + driftX, h * 0.72f + driftY, w * 0.66f + driftX, h * 0.76f + driftY)
    path.quadraticTo(w * 0.85f + driftX, h * 0.66f + driftY, w * 1.1f + driftX, h * 0.78f + driftY)
    path.lineTo(w * 1.1f + driftX, h * 1.2f + driftY)
    path.close()
    drawPath(path, color = InkBlack.copy(alpha = alpha * 0.92f))

    val headCenterX = w * 0.78f + driftX
    val headCenterY = h * 0.64f + driftY
    drawOval(color = InkBlack.copy(alpha = alpha * 0.9f), topLeft = Offset(headCenterX - w * 0.13f, headCenterY - h * 0.12f), size = Size(w * 0.26f, h * 0.25f))
    for (i in 0..8) {
        val a = -0.7f + i * 0.22f
        drawLine(InkCharcoal.copy(alpha = alpha * 0.75f), Offset(headCenterX - w * 0.11f, headCenterY - h * 0.04f + i * 7f), Offset(headCenterX - w * 0.17f + cos(a) * 18f, headCenterY - h * 0.07f + i * 9f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawInkMotes(world: FlightWorldState, centerX: Float, horizonY: Float, unitScale: Float) {
    for (i in 0 until world.inkMotes.size) {
        val m = world.inkMotes[i]
        val scale = FOCAL_LENGTH / (m.z + Z_OFFSET)
        val sx = centerX + (m.x - world.cameraX * 0.65f) * scale * unitScale
        val sy = horizonY + (m.y - world.cameraY * 0.58f) * scale * unitScale
        val alpha = (1f - m.z / GameViewModel.FAR_Z).coerceIn(0.08f, 0.45f)
        drawCircle(InkCharcoal.copy(alpha = alpha), radius = (m.size * scale * 0.45f).coerceIn(1f, 4.5f), center = Offset(sx, sy))
    }
}

private fun DrawScope.drawParticles(world: FlightWorldState, centerX: Float, horizonY: Float, unitScale: Float, path: Path) {
    val scale = FOCAL_LENGTH / (GameViewModel.CROW_Z + Z_OFFSET)
    for (i in 0 until world.particles.size) {
        val p = world.particles[i]
        val sx = centerX + (p.x - world.cameraX * 0.65f) * scale * unitScale
        val sy = horizonY + (p.y - world.cameraY * 0.58f) * scale * unitScale
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        when {
            p.isMagnetSpark -> drawCircle(MagnetBlue.copy(alpha = alpha * 0.9f), radius = (5.4f * p.size * scale).coerceAtLeast(2f), center = Offset(sx, sy))
            p.isGoldSpark -> drawCircle(CoinGold.copy(alpha = alpha * 0.9f), radius = (5.2f * p.size * scale).coerceAtLeast(2f), center = Offset(sx, sy))
            p.isEmber -> drawCircle(DragonEmberColor.copy(alpha = alpha * 0.85f), radius = (5.0f * p.size * scale).coerceAtLeast(2f), center = Offset(sx, sy))
            p.isDrop -> drawCircle(InkBlack.copy(alpha = alpha * 0.75f), radius = (4.5f * p.size * scale).coerceAtLeast(1.5f), center = Offset(sx, sy))
            else -> {
                rotate(degrees = p.angle, pivot = Offset(sx, sy)) {
                    val len = 16f * p.size * scale
                    path.reset()
                    path.moveTo(sx - len, sy)
                    path.quadraticTo(sx, sy - len * 0.34f, sx + len, sy)
                    path.quadraticTo(sx, sy + len * 0.28f, sx - len, sy)
                    path.close()
                    drawPath(path, color = InkBlack.copy(alpha = alpha * 0.9f))
                }
            }
        }
    }
}

private fun DrawScope.drawWeatherOverlay(w: Float, h: Float, world: FlightWorldState) {
    val blend = world.weatherBlend
    if (blend < 1.35f) {
        val rainAlpha = (if (blend <= 1f) blend else (1.35f - blend) / 0.35f).coerceIn(0f, 1f) * 0.48f
        for (i in 0 until world.weatherParticles.size) {
            val wp = world.weatherParticles[i]
            val sx = wp.x * w
            val sy = wp.y * h
            val len = wp.length * h
            drawLine(
                color = InkSketch.copy(alpha = rainAlpha),
                start = Offset(sx, sy),
                end = Offset(sx - len * 0.22f, sy + len),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round,
            )
        }
    }
    if (blend > 0.95f) {
        val snowAlpha = ((blend - 0.95f) / 0.55f).coerceIn(0f, 1f) * 0.88f
        for (i in 0 until world.weatherParticles.size) {
            val wp = world.weatherParticles[i]
            val sx = wp.x * w
            val sy = wp.y * h
            val r = (2.2f + (i % 4) * 1.3f)
            drawCircle(color = SnowBodyColor.copy(alpha = snowAlpha), radius = r, center = Offset(sx, sy))
            drawCircle(color = InkMist.copy(alpha = snowAlpha * 0.5f), radius = r, center = Offset(sx, sy), style = Stroke(width = 0.8f))
        }
    }
}

private fun DrawScope.drawWindSpeedStreaks(w: Float, h: Float, envTime: Float, speed: Float) {
    val intensity = ((speed - 5.5f) / 6.5f).coerceIn(0.25f, 1.0f)
    for (i in 0..9) {
        val isLeft = i % 2 == 0
        val lane = (i / 2) / 5f
        val progress = (envTime * (1.8f + (i % 3) * 0.45f) + lane) % 1f
        val xBase = if (isLeft) w * (0.04f + (i % 3) * 0.04f) else w * (0.96f - (i % 3) * 0.04f)
        val y = h * (0.15f + progress * 0.75f)
        val len = 36f + intensity * 55f
        drawLine(
            color = InkSketch.copy(alpha = 0.16f * intensity * sin(progress * PI.toFloat())),
            start = Offset(xBase, y),
            end = Offset(xBase + (if (isLeft) -12f else 12f), y + len),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round,
        )
    }
}
