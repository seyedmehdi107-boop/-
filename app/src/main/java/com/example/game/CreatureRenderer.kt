package com.example.game

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.ParchmentPaper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// Distinct creature ink-and-watercolor palettes matching the art style
private val FalconSlate = Color(0xFF2B2825)
private val FalconCream = Color(0xFFDFD2BC)
private val EagleUmber = Color(0xFF3E2B1C)
private val EagleGold = Color(0xFFC8963E)
private val EagleTailCream = Color(0xFFE7DAC2)
private val PeacockTeal = Color(0xFF1F5B54)
private val PeacockCobalt = Color(0xFF1D3E5E)
private val PeacockGold = Color(0xFFC89A3F)
private val PeacockBronze = Color(0xFF8C6324)
private val DragonScale = Color(0xFF221A19)
private val DragonMembrane = Color(0xFF3A2422)
private val DragonEmber = Color(0xFFC83F26)
private val DragonAmber = Color(0xFFE28A32)
private val DragonGoldRing = Color(0xFFC59432)

private const val MESH_COLS = 16
private const val MESH_ROWS = 6

class CreaturePathPool {
    val pathA = Path()
    val pathB = Path()
    val pathC = Path()
    val meshVerts = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2)
    val spritePaint = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
    )
}

fun DrawScope.drawHeroCreatureBehind(
    world: FlightWorldState,
    paths: CreaturePathPool,
    centerX: Float,
    horizonY: Float,
    unitScale: Float,
    focalLength: Float,
    zOffset: Float,
) {
    // Invulnerability blink after body damage
    if (world.invulnTimer > 0f && ((world.invulnTimer * 14f).toInt() % 2 == 1)) {
        return
    }

    val scale = focalLength / (GameViewModel.CROW_Z + zOffset)
    val sx = centerX + (world.crowX - world.cameraX * 0.65f) * scale * unitScale
    val sy = horizonY + (world.crowY - world.cameraY * 0.58f) * scale * unitScale

    // Ground shadow directly beneath
    val groundY = horizonY + (0.94f - world.cameraY * 0.58f) * scale * unitScale
    val heightFactor = ((0.94f - world.crowY) / 1.7f).coerceIn(0.2f, 1.0f)
    drawOval(
        color = InkCharcoal.copy(alpha = 0.24f * (1.15f - heightFactor * 0.55f)),
        topLeft = Offset(sx - 38f * scale * (1.2f - heightFactor * 0.3f), groundY - 6f * scale),
        size = Size(76f * scale * (1.2f - heightFactor * 0.3f), 13f * scale),
    )

    // Active Magnet Aura pulse ring around the flying creature
    if (world.magnetPowerTimer > 0f) {
        val pulse = 0.85f + 0.18f * sin(world.envTime * 9f)
        drawCircle(
            color = DragonGoldRing.copy(alpha = 0.38f),
            radius = scale * unitScale * 0.36f * pulse,
            center = Offset(sx, sy),
            style = Stroke(width = 2.6f),
        )
    }

    val bankDeg = world.bankAngle * (180f / PI.toFloat())
    val flap = sin(world.flapPhase)
    val whip = cos(world.flapPhase)

    val sprite = world.spriteAtlas?.creatures?.get(world.equippedSkin)
    if (sprite != null && !sprite.isRecycled) {
        // Subtle ember rim glow for Baby Dragon or golden aura for Peacock
        if (world.equippedSkin == CreatureSkin.BABY_DRAGON) {
            val glowAlpha = 0.14f + 0.08f * sin(world.envTime * 7f)
            drawCircle(
                color = DragonEmber.copy(alpha = glowAlpha),
                radius = scale * unitScale * 0.26f,
                center = Offset(sx, sy),
            )
        } else if (world.equippedSkin == CreatureSkin.PEACOCK) {
            val shimmerAlpha = 0.10f + 0.06f * cos(world.envTime * 5f)
            drawCircle(
                color = PeacockGold.copy(alpha = shimmerAlpha),
                radius = scale * unitScale * 0.25f,
                center = Offset(sx, sy + scale * unitScale * 0.05f),
            )
        }

        val spanMul = when (world.equippedSkin) {
            CreatureSkin.CROW -> 0.54f
            CreatureSkin.FALCON -> 0.55f
            CreatureSkin.EAGLE -> 0.63f
            CreatureSkin.PEACOCK -> 0.56f
            CreatureSkin.BABY_DRAGON -> 0.60f
        }
        val pivotV = when (world.equippedSkin) {
            CreatureSkin.PEACOCK -> 0.34f
            CreatureSkin.BABY_DRAGON -> 0.38f
            else -> 0.44f
        }
        val targetW = scale * unitScale * spanMul
        val targetH = targetW * (sprite.height.toFloat() / sprite.width.toFloat())

        rotate(degrees = bankDeg, pivot = Offset(sx, sy)) {
            drawAnimatedCreatureMesh(
                bitmap = sprite,
                paths = paths,
                cx = sx,
                cy = sy,
                spriteW = targetW,
                spriteH = targetH,
                pivotV = pivotV,
                flap = flap,
                whip = whip,
                pitch = world.pitchAngle,
                bank = world.bankAngle,
                envTime = world.envTime,
                tintColor = null,
                alpha = 255,
            )
        }
        return
    }

    // Procedural fallback only for first ~50ms while sprites load asynchronously
    val baseSize = scale * unitScale * 0.195f
    rotate(degrees = bankDeg, pivot = Offset(sx, sy)) {
        when (world.equippedSkin) {
            CreatureSkin.CROW -> drawCrowFromBehind(paths, sx, sy, baseSize, flap, whip, world.pitchAngle, world.bankAngle)
            CreatureSkin.FALCON -> drawFalconFromBehind(paths, sx, sy, baseSize, flap, whip, world.pitchAngle, world.bankAngle)
            CreatureSkin.EAGLE -> drawEagleFromBehind(paths, sx, sy, baseSize * 1.12f, flap, whip, world.pitchAngle, world.bankAngle)
            CreatureSkin.PEACOCK -> drawPeacockFromBehind(paths, sx, sy, baseSize, flap, whip, world.pitchAngle, world.bankAngle, world.envTime)
            CreatureSkin.BABY_DRAGON -> drawBabyDragonFromBehind(paths, sx, sy, baseSize * 1.05f, flap, whip, world.pitchAngle, world.bankAngle, world.envTime)
        }
    }
}

fun DrawScope.drawShadowCrowSprite(
    bitmap: Bitmap,
    paths: CreaturePathPool,
    cx: Float,
    cy: Float,
    widthPx: Float,
    flapPhase: Float,
    depthNorm: Float,
    tintColorInt: Int,
) {
    val targetH = widthPx * (bitmap.height.toFloat() / bitmap.width.toFloat())
    val flap = sin(flapPhase)
    val whip = cos(flapPhase)
    val alpha = (depthNorm * 255f).toInt().coerceIn(40, 255)
    drawAnimatedCreatureMesh(
        bitmap = bitmap,
        paths = paths,
        cx = cx,
        cy = cy,
        spriteW = widthPx,
        spriteH = targetH,
        pivotV = 0.44f,
        flap = flap,
        whip = whip,
        pitch = -0.1f,
        bank = 0f,
        envTime = flapPhase,
        tintColor = tintColorInt,
        alpha = alpha,
    )
}

private fun DrawScope.drawAnimatedCreatureMesh(
    bitmap: Bitmap,
    paths: CreaturePathPool,
    cx: Float,
    cy: Float,
    spriteW: Float,
    spriteH: Float,
    pivotV: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
    envTime: Float,
    tintColor: Int?,
    alpha: Int,
) {
    val verts = paths.meshVerts
    var idx = 0
    val pitchComp = 1f - abs(pitch) * 0.16f

    for (r in 0..MESH_ROWS) {
        val v = r.toFloat() / MESH_ROWS
        val tailT = ((v - 0.58f) / 0.42f).coerceIn(0f, 1f)
        val tailSwayX = sin(envTime * 4.2f) * tailT * spriteW * 0.024f -
            bank * tailT * tailT * spriteW * 0.10f
        val pitchShiftY = pitch * (1f - v) * spriteH * 0.08f

        for (c in 0..MESH_COLS) {
            val u = c.toFloat() / MESH_COLS
            val nx = (u - 0.5f) * 2f
            val absNx = abs(nx)
            val wingT = ((absNx - 0.14f) / 0.86f).coerceIn(0f, 1f)

            val flapY = -flap * wingT.pow(1.25f) * spriteH * 0.28f +
                whip * (wingT * wingT) * spriteH * 0.06f
            val inwardX = -sign(nx) * (abs(flap) * 0.075f * wingT) * spriteW
            val sweepBackY = abs(flap) * wingT * spriteH * 0.045f
            val bodyBob = flap * (1f - wingT) * spriteH * 0.032f
            val turnScaleX = 1f - bank * nx * 0.12f

            val x = cx + (u - 0.5f) * spriteW * turnScaleX + inwardX + tailSwayX * (1f - wingT)
            val y = cy + (v - pivotV) * spriteH * pitchComp + flapY + sweepBackY + bodyBob + pitchShiftY

            verts[idx++] = x
            verts[idx++] = y
        }
    }

    val paint = paths.spritePaint
    paint.alpha = alpha
    paint.colorFilter = if (tintColor != null) {
        PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_ATOP)
    } else {
        null
    }

    drawContext.canvas.nativeCanvas.drawBitmapMesh(
        bitmap,
        MESH_COLS,
        MESH_ROWS,
        verts,
        0,
        null,
        0,
        paint,
    )
}

// =========================================================================
// 1. CROW (کلاغ سیاه) — Deep charcoal ink, shaggy neck, splayed primaries
// =========================================================================
private fun DrawScope.drawCrowFromBehind(
    paths: CreaturePathPool,
    sx: Float,
    sy: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
) {
    // Wedge-shaped black tail
    val tailBaseY = sy + s * (0.28f + pitch * 0.45f)
    for (i in -2..2) {
        val ang = i * 0.18f + bank * 0.15f
        val len = s * (1.28f - abs(i) * 0.08f)
        val tx = sx + sin(ang) * len * 0.72f
        val ty = tailBaseY + cos(ang) * len
        paths.pathA.reset()
        paths.pathA.moveTo(sx + i * s * 0.06f, tailBaseY - s * 0.08f)
        paths.pathA.lineTo(tx - s * 0.09f, ty)
        paths.pathA.lineTo(tx + s * 0.09f, ty)
        paths.pathA.close()
        drawPath(paths.pathA, color = InkBlack)
        drawLine(ParchmentPaper.copy(alpha = 0.24f), Offset(sx + i * s * 0.05f, tailBaseY), Offset(tx, ty), strokeWidth = 1.1f)
    }

    // Broad raven wings with separated primary finger tips
    for (side in listOf(-1f, 1f)) {
        drawBirdWing(
            path = paths.pathA,
            originX = sx + side * s * 0.14f,
            originY = sy - s * 0.08f,
            side = side,
            s = s,
            flap = flap,
            whip = whip,
            pitch = pitch,
            spanMul = 2.65f,
            sweepBack = 0.08f,
            wingColor = InkBlack,
            barColor = ParchmentPaper.copy(alpha = 0.26f),
            slottedTips = false,
            barredPattern = false,
        )
    }

    // Sleek raven torso + shaggy neck hackles
    val bodyH = s * (1.06f - abs(pitch) * 0.24f)
    val bodyW = s * 0.62f
    drawOval(color = InkBlack, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.52f), size = Size(bodyW, bodyH))
    for (h in -3..3) {
        val hx = sx + h * s * 0.07f
        drawLine(ParchmentPaper.copy(alpha = 0.20f), Offset(hx, sy - bodyH * 0.34f), Offset(hx + h * 1.4f, sy + bodyH * 0.18f), strokeWidth = 1.2f)
    }

    val headY = sy - bodyH * 0.54f - pitch * s * 0.44f
    val headX = sx + bank * s * 0.14f
    val headR = s * 0.27f
    drawCircle(color = InkBlack, radius = headR, center = Offset(headX, headY))
    for (r in -3..3) {
        val rx = headX + r * headR * 0.25f
        drawLine(InkBlack, Offset(rx, headY + headR * 0.4f), Offset(rx + r * 3.4f, headY + headR * 1.15f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
    drawSideBeakWhenBanking(paths.pathB, headX, headY, headR, bank, InkBlack)
}

// =========================================================================
// 2. FALCON (شاهین تیزبال) — Swept crescent scythe wings & barred plumage
// =========================================================================
private fun DrawScope.drawFalconFromBehind(
    paths: CreaturePathPool,
    sx: Float,
    sy: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
) {
    // Long narrow barred falcon tail
    val tailBaseY = sy + s * (0.28f + pitch * 0.42f)
    val tailLen = s * 1.48f
    paths.pathA.reset()
    paths.pathA.moveTo(sx - s * 0.14f, tailBaseY)
    paths.pathA.lineTo(sx - s * 0.22f + bank * s * 0.25f, tailBaseY + tailLen)
    paths.pathA.lineTo(sx + s * 0.22f + bank * s * 0.25f, tailBaseY + tailLen)
    paths.pathA.lineTo(sx + s * 0.14f, tailBaseY)
    paths.pathA.close()
    drawPath(paths.pathA, color = FalconSlate)
    // Cream-and-ink tail stripes
    for (b in 1..4) {
        val t = b / 5f
        val bx = sx + bank * s * 0.25f * t
        val by = tailBaseY + tailLen * t
        val bw = s * (0.14f + t * 0.07f)
        drawLine(FalconCream, Offset(bx - bw, by), Offset(bx + bw, by), strokeWidth = 2.2f)
    }

    // Swept-back sharp scythe wings (distinct falcon silhouette!)
    for (side in listOf(-1f, 1f)) {
        drawFalconScytheWing(paths.pathA, sx + side * s * 0.11f, sy - s * 0.08f, side, s, flap * 1.08f, whip, pitch)
    }

    // Streamlined athletic body with cream shoulder highlights
    val bodyH = s * (1.0f - abs(pitch) * 0.22f)
    val bodyW = s * 0.56f
    drawOval(color = FalconSlate, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH))
    drawOval(color = FalconCream.copy(alpha = 0.45f), topLeft = Offset(sx - bodyW * 0.28f, sy - bodyH * 0.25f), size = Size(bodyW * 0.56f, bodyH * 0.5f))
    // Speckled falcon chest/back ticks
    for (i in -2..2) {
        drawLine(InkBlack, Offset(sx + i * s * 0.06f, sy - bodyH * 0.15f), Offset(sx + i * s * 0.06f, sy + bodyH * 0.15f), strokeWidth = 1.5f)
    }

    // Hooded falcon head
    val headY = sy - bodyH * 0.56f - pitch * s * 0.4f
    val headX = sx + bank * s * 0.12f
    val headR = s * 0.25f
    drawCircle(color = FalconSlate, radius = headR, center = Offset(headX, headY))
    drawCircle(color = FalconCream, radius = headR * 0.62f, center = Offset(headX, headY + headR * 0.28f))
    drawCircle(color = InkBlack, radius = headR * 0.45f, center = Offset(headX, headY - headR * 0.15f))
    drawSideBeakWhenBanking(paths.pathB, headX, headY, headR, bank, InkBlack)
}

private fun DrawScope.drawFalconScytheWing(
    path: Path,
    originX: Float,
    originY: Float,
    side: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
) {
    val wristX = originX + side * s * 1.15f
    val wristY = originY - s * (0.42f + flap * 0.72f) + pitch * s * 0.3f
    val tipX = originX + side * s * (2.68f - abs(flap) * 0.22f)
    // Sharp swept-back tip!
    val tipY = originY + s * (0.58f - flap * 0.95f - whip * 0.3f) + pitch * s * 0.6f

    path.reset()
    path.moveTo(originX, originY)
    path.quadraticTo((originX + wristX) * 0.55f, wristY - s * 0.28f, wristX, wristY)
    path.quadraticTo((wristX + tipX) * 0.6f, wristY + s * 0.06f, tipX, tipY)
    path.quadraticTo(wristX + side * s * 0.35f, wristY + s * 0.55f, originX, originY + s * 0.42f)
    path.close()
    drawPath(path, color = FalconSlate)
    drawPath(path, color = InkBlack, style = Stroke(width = 2f, join = StrokeJoin.Round))

    // Pale barred under-feathers along trailing edge
    for (f in 1..6) {
        val t = f / 7f
        val rx = originX + (tipX - originX) * t
        val ry = originY + (wristY - originY) * t * 0.8f
        drawLine(
            color = FalconCream.copy(alpha = 0.72f),
            start = Offset(rx, ry),
            end = Offset(rx - side * s * 0.16f, ry + s * 0.36f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
    }
}

// =========================================================================
// 3. EAGLE (عقاب طلایی) — Huge slotted soaring wings & golden neck mantle
// =========================================================================
private fun DrawScope.drawEagleFromBehind(
    paths: CreaturePathPool,
    sx: Float,
    sy: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
) {
    // Broad fanned cream-and-umber tail
    val tailBaseY = sy + s * (0.28f + pitch * 0.45f)
    for (i in -3..3) {
        val ang = i * 0.16f + bank * 0.14f
        val len = s * (1.25f - abs(i) * 0.05f)
        val tx = sx + sin(ang) * len * 0.76f
        val ty = tailBaseY + cos(ang) * len
        paths.pathA.reset()
        paths.pathA.moveTo(sx + i * s * 0.06f, tailBaseY - s * 0.08f)
        paths.pathA.lineTo(tx - s * 0.09f, ty)
        paths.pathA.lineTo(tx + s * 0.09f, ty)
        paths.pathA.close()
        drawPath(paths.pathA, color = EagleTailCream)
        drawPath(paths.pathA, color = EagleUmber, style = Stroke(width = 1.6f))
        // Dark terminal tail tip
        drawLine(EagleUmber, Offset(tx - s * 0.07f, ty - s * 0.12f), Offset(tx + s * 0.07f, ty - s * 0.12f), strokeWidth = 4.2f)
    }

    // Massive wide soaring eagle wings with deeply slotted upturned finger feathers
    for (side in listOf(-1f, 1f)) {
        drawBirdWing(
            path = paths.pathA,
            originX = sx + side * s * 0.18f,
            originY = sy - s * 0.08f,
            side = side,
            s = s,
            flap = flap * 0.88f,
            whip = whip,
            pitch = pitch,
            spanMul = 3.05f,
            sweepBack = -0.05f,
            wingColor = EagleUmber,
            barColor = EagleGold.copy(alpha = 0.85f),
            slottedTips = true,
            barredPattern = false,
        )
    }

    // Powerful umber torso + golden-ochre mantle
    val bodyH = s * (1.08f - abs(pitch) * 0.24f)
    val bodyW = s * 0.68f
    drawOval(color = EagleUmber, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH))
    // Golden feather mantle across upper back
    paths.pathB.reset()
    paths.pathB.moveTo(sx - bodyW * 0.45f, sy - bodyH * 0.42f)
    paths.pathB.lineTo(sx + bodyW * 0.45f, sy - bodyH * 0.42f)
    paths.pathB.lineTo(sx + bodyW * 0.22f, sy + bodyH * 0.12f)
    paths.pathB.lineTo(sx, sy + bodyH * 0.22f)
    paths.pathB.lineTo(sx - bodyW * 0.22f, sy + bodyH * 0.12f)
    paths.pathB.close()
    drawPath(paths.pathB, color = EagleGold)
    drawPath(paths.pathB, color = InkBlack, style = Stroke(width = 1.6f))

    // Golden-crowned eagle head
    val headY = sy - bodyH * 0.56f - pitch * s * 0.42f
    val headX = sx + bank * s * 0.12f
    val headR = s * 0.28f
    drawCircle(color = EagleGold, radius = headR, center = Offset(headX, headY))
    drawCircle(color = InkBlack, radius = headR, center = Offset(headX, headY), style = Stroke(width = 2.0f))
    drawSideBeakWhenBanking(paths.pathC, headX, headY, headR, bank, EagleGold)
}

// =========================================================================
// 4. PEACOCK (طاووس افسانه‌ای) — Royal crest, teal wings & 9-plume ocellus train
// =========================================================================
private fun DrawScope.drawPeacockFromBehind(
    paths: CreaturePathPool,
    sx: Float,
    sy: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
    envTime: Float,
) {
    // 9-plume undulating peacock train with ornate eye-spots!
    val tailBaseY = sy + s * (0.24f + pitch * 0.4f)
    for (p in -4..4) {
        val norm = p / 4f
        val wave = sin(envTime * 5.2f + p * 0.85f) * s * 0.16f
        val length = s * (2.35f - abs(norm) * 0.55f)
        val endX = sx + norm * s * 1.32f + wave + bank * s * 0.52f
        val endY = tailBaseY + length

        paths.pathA.reset()
        paths.pathA.moveTo(sx + p * s * 0.04f, tailBaseY)
        paths.pathA.quadraticTo(
            sx + norm * s * 0.65f + wave * 0.4f,
            tailBaseY + length * 0.55f,
            endX,
            endY,
        )
        drawPath(paths.pathA, color = PeacockTeal, style = Stroke(width = 2.8f, cap = StrokeCap.Round))
        drawPath(paths.pathA, color = PeacockGold.copy(alpha = 0.5f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))

        // Iconic Peacock Eye-Spot (Ocellus) at plume tip
        val eyeR = s * 0.145f
        drawCircle(color = PeacockBronze, radius = eyeR, center = Offset(endX, endY))
        drawCircle(color = PeacockGold, radius = eyeR * 0.78f, center = Offset(endX, endY))
        drawCircle(color = PeacockTeal, radius = eyeR * 0.52f, center = Offset(endX, endY))
        drawCircle(color = InkBlack, radius = eyeR * 0.28f, center = Offset(endX, endY))
    }

    // Ornate teal & gold wings
    for (side in listOf(-1f, 1f)) {
        drawBirdWing(
            path = paths.pathA,
            originX = sx + side * s * 0.14f,
            originY = sy - s * 0.08f,
            side = side,
            s = s,
            flap = flap,
            whip = whip,
            pitch = pitch,
            spanMul = 2.55f,
            sweepBack = 0.12f,
            wingColor = PeacockTeal,
            barColor = PeacockGold.copy(alpha = 0.85f),
            slottedTips = false,
            barredPattern = true,
        )
    }

    // Cobalt-teal body & slender neck
    val bodyH = s * (1.02f - abs(pitch) * 0.22f)
    val bodyW = s * 0.56f
    drawOval(color = PeacockCobalt, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH))
    drawOval(color = InkBlack, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH), style = Stroke(width = 1.8f))

    val headY = sy - bodyH * 0.62f - pitch * s * 0.42f
    val headX = sx + bank * s * 0.14f
    val headR = s * 0.23f
    drawLine(PeacockCobalt, Offset(sx, sy - bodyH * 0.3f), Offset(headX, headY), strokeWidth = s * 0.22f, cap = StrokeCap.Round)
    drawCircle(color = PeacockCobalt, radius = headR, center = Offset(headX, headY))

    // Royal 5-plume fan crest atop head
    for (c in -2..2) {
        val ang = -PI.toFloat() * 0.5f + c * 0.26f
        val tip = Offset(headX + cos(ang) * headR * 1.9f, headY + sin(ang) * headR * 1.9f)
        drawLine(InkBlack, Offset(headX, headY - headR * 0.4f), tip, strokeWidth = 1.5f)
        drawCircle(color = PeacockGold, radius = 3.2f, center = tip)
        drawCircle(color = InkBlack, radius = 1.4f, center = tip)
    }
    drawSideBeakWhenBanking(paths.pathB, headX, headY, headR, bank, PeacockGold)
}

// =========================================================================
// 5. BABY DRAGON (بچه اژدها) — Bat membrane wings, horns, spine spikes, S-tail
// =========================================================================
private fun DrawScope.drawBabyDragonFromBehind(
    paths: CreaturePathPool,
    sx: Float,
    sy: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    bank: Float,
    envTime: Float,
) {
    // S-curving reptilian tail with ember-edged spade tip
    val tailStartY = sy + s * 0.34f
    val tailWave1 = sin(envTime * 6.8f) * s * 0.28f + bank * s * 0.35f
    val tailWave2 = cos(envTime * 6.8f) * s * 0.35f + bank * s * 0.62f
    val tailTipX = sx + tailWave2
    val tailTipY = tailStartY + s * 1.72f

    paths.pathA.reset()
    paths.pathA.moveTo(sx - s * 0.14f, tailStartY)
    paths.pathA.cubicTo(
        sx + tailWave1 - s * 0.08f, tailStartY + s * 0.55f,
        sx + tailWave2 - s * 0.04f, tailStartY + s * 1.15f,
        tailTipX, tailTipY,
    )
    paths.pathA.cubicTo(
        sx + tailWave2 + s * 0.04f, tailStartY + s * 1.15f,
        sx + tailWave1 + s * 0.08f, tailStartY + s * 0.55f,
        sx + s * 0.14f, tailStartY,
    )
    paths.pathA.close()
    drawPath(paths.pathA, color = DragonScale)
    drawPath(paths.pathA, color = InkBlack, style = Stroke(width = 2.0f))

    // Spade arrow tip
    paths.pathB.reset()
    paths.pathB.moveTo(tailTipX, tailTipY + s * 0.28f)
    paths.pathB.lineTo(tailTipX - s * 0.24f, tailTipY - s * 0.06f)
    paths.pathB.lineTo(tailTipX, tailTipY + s * 0.04f)
    paths.pathB.lineTo(tailTipX + s * 0.24f, tailTipY - s * 0.06f)
    paths.pathB.close()
    drawPath(paths.pathB, color = DragonEmber)
    drawPath(paths.pathB, color = InkBlack, style = Stroke(width = 1.6f))

    // Wide bat-like dragon membrane wings with bone claws & glowing veins
    for (side in listOf(-1f, 1f)) {
        drawDragonMembraneWing(paths.pathA, sx + side * s * 0.15f, sy - s * 0.08f, side, s, flap, whip, pitch)
    }

    // Armored scaly torso + glowing dorsal spine ridges
    val bodyH = s * (1.08f - abs(pitch) * 0.24f)
    val bodyW = s * 0.62f
    drawOval(color = DragonScale, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH))
    drawOval(color = InkBlack, topLeft = Offset(sx - bodyW * 0.5f, sy - bodyH * 0.5f), size = Size(bodyW, bodyH), style = Stroke(width = 2.2f))

    for (sp in 0..4) {
        val spY = sy - bodyH * 0.42f + sp * bodyH * 0.21f
        paths.pathC.reset()
        paths.pathC.moveTo(sx - s * 0.07f, spY + s * 0.06f)
        paths.pathC.lineTo(sx, spY - s * 0.09f)
        paths.pathC.lineTo(sx + s * 0.07f, spY + s * 0.06f)
        paths.pathC.close()
        drawPath(paths.pathC, color = if (sp % 2 == 0) DragonEmber else DragonAmber)
    }

    // Horned reptilian dragon head
    val headY = sy - bodyH * 0.58f - pitch * s * 0.42f
    val headX = sx + bank * s * 0.14f
    val headR = s * 0.27f
    drawCircle(color = DragonScale, radius = headR, center = Offset(headX, headY))
    drawCircle(color = InkBlack, radius = headR, center = Offset(headX, headY), style = Stroke(width = 2.0f))

    // Twin curved horns + ear frills
    for (side in listOf(-1f, 1f)) {
        paths.pathB.reset()
        paths.pathB.moveTo(headX + side * headR * 0.40f, headY - headR * 0.35f)
        paths.pathB.quadraticTo(headX + side * headR * 1.15f, headY - headR * 1.15f, headX + side * headR * 1.65f, headY - headR * 1.65f)
        paths.pathB.lineTo(headX + side * headR * 0.85f, headY - headR * 0.08f)
        paths.pathB.close()
        drawPath(paths.pathB, color = DragonAmber)
        drawPath(paths.pathB, color = InkBlack, style = Stroke(width = 1.6f))
    }

    // Amber slit eye & snout when banking
    if (abs(bank) > 0.14f) {
        val lookSide = if (bank > 0f) 1f else -1f
        drawCircle(color = DragonAmber, radius = (headR * 0.18f).coerceAtLeast(2.2f), center = Offset(headX + lookSide * headR * 0.58f, headY - headR * 0.10f))
    }
}

private fun DrawScope.drawDragonMembraneWing(
    path: Path,
    originX: Float,
    originY: Float,
    side: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
) {
    val elbowX = originX + side * s * 1.18f
    val elbowY = originY - s * (0.38f + flap * 0.70f) + pitch * s * 0.32f
    val tipX = originX + side * s * (2.92f - abs(flap) * 0.24f)
    val tipY = originY - s * (0.28f + flap * 1.18f + whip * 0.32f) + pitch * s * 0.6f

    val f1X = originX + side * s * 2.25f
    val f1Y = elbowY + s * 0.82f
    val f2X = originX + side * s * 1.55f
    val f2Y = elbowY + s * 0.92f
    val f3X = originX + side * s * 0.90f
    val f3Y = originY + s * 0.72f

    path.reset()
    path.moveTo(originX, originY)
    path.lineTo(elbowX, elbowY)
    path.lineTo(tipX, tipY)
    path.quadraticTo(tipX - side * s * 0.35f, f1Y - s * 0.25f, f1X, f1Y)
    path.quadraticTo((f1X + f2X) * 0.5f, f2Y - s * 0.28f, f2X, f2Y)
    path.quadraticTo((f2X + f3X) * 0.5f, f3Y - s * 0.24f, f3X, f3Y)
    path.lineTo(originX, originY + s * 0.42f)
    path.close()

    drawPath(path, color = DragonMembrane)
    drawPath(path, color = InkBlack, style = Stroke(width = 2.4f, join = StrokeJoin.Round))

    // Wing bone struts & thumb claw at elbow
    drawLine(InkBlack, Offset(originX, originY), Offset(elbowX, elbowY), strokeWidth = 3.6f, cap = StrokeCap.Round)
    drawLine(InkBlack, Offset(elbowX, elbowY), Offset(tipX, tipY), strokeWidth = 3.0f, cap = StrokeCap.Round)
    drawLine(InkBlack, Offset(elbowX, elbowY), Offset(f1X, f1Y), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(InkBlack, Offset(elbowX, elbowY), Offset(f2X, f2Y), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(InkBlack, Offset(elbowX, elbowY), Offset(f3X, f3Y), strokeWidth = 2.0f, cap = StrokeCap.Round)
    // Wing-thumb claw horn
    drawLine(DragonAmber, Offset(elbowX, elbowY), Offset(elbowX + side * s * 0.14f, elbowY - s * 0.24f), strokeWidth = 2.6f, cap = StrokeCap.Round)
    // Subtle ember vein glow
    drawLine(DragonEmber.copy(alpha = 0.6f), Offset(originX, originY), Offset(elbowX, elbowY), strokeWidth = 1.6f)
}

// =========================================================================
// Shared Bird Wing & Side Beak Helpers (Zero Allocation)
// =========================================================================
private fun DrawScope.drawBirdWing(
    path: Path,
    originX: Float,
    originY: Float,
    side: Float,
    s: Float,
    flap: Float,
    whip: Float,
    pitch: Float,
    spanMul: Float,
    sweepBack: Float,
    wingColor: Color,
    barColor: Color,
    slottedTips: Boolean,
    barredPattern: Boolean,
) {
    val elbowX = originX + side * s * (spanMul * 0.47f)
    val elbowY = originY - s * (0.25f + flap * 0.65f) + pitch * s * 0.35f
    val tipX = originX + side * s * (spanMul - abs(flap) * 0.25f)
    val tipY = originY - s * (0.15f + flap * 1.15f + whip * 0.35f) + pitch * s * 0.65f + s * sweepBack

    path.reset()
    path.moveTo(originX, originY)
    path.quadraticTo((originX + elbowX) * 0.5f, elbowY - s * 0.2f, elbowX, elbowY)
    path.lineTo(tipX, tipY)
    path.lineTo(elbowX - side * s * 0.15f, elbowY + s * 0.62f)
    path.lineTo(originX, originY + s * 0.48f)
    path.close()
    drawPath(path, color = wingColor)

    val featherCount = if (slottedTips) 8 else 7
    for (f in 0 until featherCount) {
        val t = (f + 1) / featherCount.toFloat()
        val rootX = elbowX + (tipX - elbowX) * (t * 0.88f)
        val rootY = elbowY + (tipY - elbowY) * (t * 0.88f)
        val bladeLen = s * (0.72f + t * (if (slottedTips) 0.56f else 0.40f))
        val liftUp = if (slottedTips && f >= 4) -s * 0.14f * (f - 3) else 0f
        val bladeTipX = rootX + side * bladeLen * (0.45f + t * 0.48f)
        val bladeTipY = rootY + bladeLen * (0.65f - t * 0.45f + sweepBack * 0.4f) - whip * s * 0.12f + liftUp

        path.reset()
        path.moveTo(rootX, rootY - s * 0.06f)
        path.lineTo(bladeTipX, bladeTipY)
        path.lineTo(rootX - side * s * 0.10f, rootY + s * 0.16f)
        path.close()
        drawPath(path, color = wingColor)
        drawLine(barColor, Offset(rootX, rootY), Offset(bladeTipX, bladeTipY), strokeWidth = 1.2f)
    }

    if (barredPattern) {
        drawLine(barColor, Offset(originX, originY + s * 0.18f), Offset(elbowX, elbowY + s * 0.25f), strokeWidth = 2.0f)
    }

    path.reset()
    path.moveTo(originX, originY)
    path.quadraticTo((originX + elbowX) * 0.5f, elbowY - s * 0.22f, elbowX, elbowY)
    path.lineTo(tipX, tipY)
    drawPath(path, color = barColor, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSideBeakWhenBanking(
    path: Path,
    headX: Float,
    headY: Float,
    headR: Float,
    bank: Float,
    beakColor: Color,
) {
    if (abs(bank) > 0.16f) {
        val lookSide = if (bank > 0f) 1f else -1f
        path.reset()
        path.moveTo(headX + lookSide * headR * 0.7f, headY - headR * 0.15f)
        path.lineTo(headX + lookSide * headR * 1.65f, headY + headR * 0.05f)
        path.lineTo(headX + lookSide * headR * 0.75f, headY + headR * 0.25f)
        path.close()
        drawPath(path, color = beakColor)
        drawCircle(
            color = ParchmentPaper.copy(alpha = 0.85f),
            radius = (headR * 0.13f).coerceAtLeast(1.8f),
            center = Offset(headX + lookSide * headR * 0.55f, headY - headR * 0.12f),
        )
    }
}
