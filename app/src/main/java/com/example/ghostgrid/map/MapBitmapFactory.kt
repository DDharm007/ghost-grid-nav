package com.example.ghostgrid.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Generates crisp, high-DPI Android Bitmaps for MapLibre Native layers
 * styled strictly in GhostGrid's Minimal Black, Grey, and White aesthetic:
 * - Playful 3D-shaded monochrome Sports Coupe (Obsidian Matte, Pearl White, Titanium Silver)
 *   with crisp LED headlight cones, panoramic dark cabin, side mirrors & crimson LED tail bar.
 * - Minimalist White & Graphite stationary GPS user beacon with directional cone.
 * - Crisp 3D teardrop location pins for search results, dropped pins & saved spots.
 */
object MapBitmapFactory {

    fun create3dCarBitmap(bodyPrimaryHex: String, bodyHighlightHex: String, bodyDarkHex: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f + 10f

        val primary = Color.parseColor(bodyPrimaryHex)
        val highlight = Color.parseColor(bodyHighlightHex)
        val dark = Color.parseColor(bodyDarkHex)

        // 1. Clean white volumetric LED headlight cones
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, cy - 52f,
                cx, 6f,
                intArrayOf(Color.argb(145, 255, 255, 255), Color.argb(45, 212, 212, 216), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val leftBeam = Path().apply {
            moveTo(cx - 22f, cy - 46f)
            lineTo(cx - 46f, 8f)
            lineTo(cx - 4f, 8f)
            lineTo(cx - 10f, cy - 46f)
            close()
        }
        val rightBeam = Path().apply {
            moveTo(cx + 10f, cy - 46f)
            lineTo(cx + 4f, 8f)
            lineTo(cx + 46f, 8f)
            lineTo(cx + 22f, cy - 46f)
            close()
        }
        c.drawPath(leftBeam, beamPaint)
        c.drawPath(rightBeam, beamPaint)

        // 2. Soft 3D contact shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx + 4f, cy + 8f, 64f,
                intArrayOf(Color.argb(190, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0.2f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawOval(RectF(cx - 44f, cy - 56f, cx + 48f, cy + 66f), shadowPaint)

        // 3. Four subtle matte-black tires
        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0A") }
        c.drawRoundRect(RectF(cx - 33f, cy - 38f, cx - 23f, cy - 16f), 4f, 4f, wheelPaint)
        c.drawRoundRect(RectF(cx + 23f, cy - 38f, cx + 33f, cy - 16f), 4f, 4f, wheelPaint)
        c.drawRoundRect(RectF(cx - 33f, cy + 16f, cx - 23f, cy + 38f), 4f, 4f, wheelPaint)
        c.drawRoundRect(RectF(cx + 23f, cy + 16f, cx + 33f, cy + 38f), 4f, 4f, wheelPaint)

        // 4. Lower 3D chassis bevel
        val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark }
        val lowerRect = RectF(cx - 29f, cy - 51f, cx + 29f, cy + 54f)
        c.drawRoundRect(lowerRect, 20f, 22f, chassisPaint)

        // 5. Sculpted metallic monochrome upper body
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 28f, cy - 50f,
                cx + 28f, cy + 48f,
                intArrayOf(highlight, primary, dark),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val bodyRect = RectF(cx - 27f, cy - 52f, cx + 27f, cy + 48f)
        c.drawRoundRect(bodyRect, 19f, 21f, bodyPaint)

        // Thin high-contrast rim so dark cars remain razor-sharp on dark maps
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(bodyRect, 19f, 21f, rimPaint)

        // 6. Aerodynamic side mirrors
        val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlight }
        c.drawRoundRect(RectF(cx - 34f, cy - 18f, cx - 24f, cy - 10f), 4f, 4f, mirrorPaint)
        c.drawRoundRect(RectF(cx + 24f, cy - 18f, cx + 34f, cy - 10f), 4f, 4f, mirrorPaint)

        // 7. Hood sculpted crease lines
        val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        c.drawLine(cx - 12f, cy - 45f, cx - 15f, cy - 23f, creasePaint)
        c.drawLine(cx + 12f, cy - 45f, cx + 15f, cy - 23f, creasePaint)

        // 8. Panoramic smoked-obsidian glass cabin
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, cy - 26f,
                cx, cy + 32f,
                intArrayOf(Color.parseColor("#27272A"), Color.parseColor("#09090B"), Color.parseColor("#27272A")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(cx - 20f, cy - 24f, cx + 20f, cy + 32f), 13f, 14f, glassPaint)

        // 9. Elevated 3D roof panel
        val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 14f, cy - 8f,
                cx + 14f, cy + 20f,
                intArrayOf(highlight, primary),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(cx - 14f, cy - 8f, cx + 14f, cy + 19f), 8f, 9f, roofPaint)

        // Windshield crisp specular highlight
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 255, 255, 255)
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(cx - 11f, cy - 18f, cx + 9f, cy - 18f, sheenPaint)

        // 10. Crisp pure-white LED headlights
        val headlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(RectF(cx - 21f, cy - 50f, cx - 10f, cy - 44f), 3f, 3f, headlightPaint)
        c.drawRoundRect(RectF(cx + 10f, cy - 50f, cx + 21f, cy - 44f), 3f, 3f, headlightPaint)

        // 11. Minimal crimson LED taillight bar
        val tailGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(135, 229, 72, 77) }
        c.drawRoundRect(RectF(cx - 22f, cy + 43f, cx + 22f, cy + 51f), 4f, 4f, tailGlowPaint)
        val tailCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") }
        c.drawRoundRect(RectF(cx - 19f, cy + 45f, cx + 19f, cy + 48.5f), 2f, 2f, tailCorePaint)

        return bmp
    }

    /**
     * Minimalist Black & White glowing GPS user beacon with crisp directional radar cone.
     */
    fun createUserBeaconBitmap(): Bitmap {
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        // Forward heading cone in soft silver-white
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, cy, cx, 8f,
                intArrayOf(Color.argb(165, 255, 255, 255), Color.argb(10, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val cone = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - 26f, 16f)
            quadTo(cx, 6f, cx + 26f, 16f)
            close()
        }
        c.drawPath(cone, conePaint)

        // Subtle monochrome pulse halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, 44f,
                intArrayOf(Color.argb(95, 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cy, 44f, haloPaint)

        // Drop shadow
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        c.drawCircle(cx, cy + 3f, 21f, shadow)

        // Pure white outer ring
        val whiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(cx, cy, 19.5f, whiteRing)

        // Deep obsidian core with subtle silver highlight
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - 4f, cy - 5f, 15f,
                intArrayOf(Color.parseColor("#52525B"), Color.parseColor("#18181B"), Color.parseColor("#000000")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(cx, cy, 13.5f, corePaint)

        // Center crisp white pinpoint dot
        c.drawCircle(cx, cy, 4.2f, whiteRing)

        return bmp
    }

    /**
     * Renders a crisp minimal 3D teardrop location pin anchored at its bottom tip.
     */
    fun createTeardropPinBitmap(
        topHex: String,
        bottomHex: String,
        strokeHex: String,
        centerBgHex: String,
        innerDotHex: String
    ): Bitmap {
        val w = 96
        val h = 120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, h - 10f, 20f,
                intArrayOf(Color.argb(165, 0, 0, 0), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawOval(RectF(cx - 20f, h - 18f, cx + 20f, h - 2f), shadowPaint)

        val pinPath = Path().apply {
            moveTo(cx, h - 10f)
            cubicTo(cx - 8f, h - 34f, cx - 34f, 52f, cx - 34f, 40f)
            arcTo(RectF(cx - 34f, 6f, cx + 34f, 74f), 180f, 180f, false)
            cubicTo(cx + 34f, 52f, cx + 8f, h - 34f, cx, h - 10f)
            close()
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(strokeHex)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        c.drawPath(pinPath, strokePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 24f, 8f,
                cx + 24f, h - 14f,
                Color.parseColor(topHex),
                Color.parseColor(bottomHex),
                Shader.TileMode.CLAMP
            )
        }
        c.drawPath(pinPath, fillPaint)

        val innerCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(centerBgHex) }
        c.drawCircle(cx, 40f, 14f, innerCircle)

        val innerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(innerDotHex) }
        c.drawCircle(cx, 40f, 6.5f, innerDot)

        return bmp
    }
}
