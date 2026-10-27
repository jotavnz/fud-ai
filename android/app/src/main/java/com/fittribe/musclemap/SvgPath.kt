//
// SvgPath.kt
//
// Kotlin port of the SVG path parser + builder from the MIT-licensed MuscleMap
// Swift package (github.com/melihcolpan/MuscleMap, Copyright (c) 2026 Melih Colpan).
//
// Paths are built in raw SVG coordinates (no scaling). The Canvas applies a
// scale/translate transform at draw time, so a single parsed Path can be cached
// and reused across re-layouts. Hit-testing converts taps back into SVG space.
//

package com.fittribe.musclemap

import android.graphics.Region
import android.graphics.RectF
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import java.util.concurrent.ConcurrentHashMap

/**
 * Parses an SVG path string and builds a Compose [Path] in raw (unscaled) SVG
 * coordinates. Supports M/L/H/V/C/S/Q/T/A/Z (arcs are approximated as a line to
 * the endpoint, matching the reference implementation).
 */
internal object SvgPathBuilder {

    fun build(svg: String): Path {
        val path = Path()
        val n = svg.length
        var i = 0

        var curX = 0f; var curY = 0f
        var startX = 0f; var startY = 0f
        var lastCtrlX = 0f; var lastCtrlY = 0f
        var hasLastCtrl = false
        var command = 'M'

        fun isWs(c: Char) = c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r'
        fun skipWs() { while (i < n && isWs(svg[i])) i++ }

        fun parseNumber(): Float? {
            skipWs()
            if (i >= n) return null
            val sb = StringBuilder()
            var hasDecimal = false
            var hasExp = false
            if (svg[i] == '-' || svg[i] == '+') { sb.append(svg[i]); i++ }
            while (i < n) {
                val c = svg[i]
                when {
                    c.isDigit() -> { sb.append(c); i++ }
                    c == '.' && !hasDecimal && !hasExp -> { hasDecimal = true; sb.append(c); i++ }
                    (c == 'e' || c == 'E') && !hasExp -> {
                        hasExp = true; sb.append(c); i++
                        if (i < n && (svg[i] == '-' || svg[i] == '+')) { sb.append(svg[i]); i++ }
                    }
                    else -> return@parseNumber sb.toString().toFloatOrNull()
                }
            }
            return sb.toString().toFloatOrNull()
        }

        fun parseFlag(): Boolean? {
            skipWs()
            if (i >= n) return null
            val c = svg[i]
            if (c == '0' || c == '1') { i++; return c == '1' }
            return null
        }

        while (i < n) {
            skipWs()
            if (i >= n) break

            val ch = svg[i]
            if (ch.isLetter() && ch != 'e' && ch != 'E') {
                command = ch
                i++
            }

            val relative = command.isLowerCase()
            when (command.uppercaseChar()) {
                'M' -> {
                    val x = parseNumber() ?: break
                    val y = parseNumber() ?: break
                    val px = if (relative) curX + x else x
                    val py = if (relative) curY + y else y
                    path.moveTo(px, py)
                    curX = px; curY = py; startX = px; startY = py
                    hasLastCtrl = false
                    // Subsequent implicit coordinates are treated as line-to.
                    command = if (relative) 'l' else 'L'
                }
                'L' -> {
                    val x = parseNumber() ?: break
                    val y = parseNumber() ?: break
                    val px = if (relative) curX + x else x
                    val py = if (relative) curY + y else y
                    path.lineTo(px, py)
                    curX = px; curY = py
                    hasLastCtrl = false
                }
                'H' -> {
                    val x = parseNumber() ?: break
                    val px = if (relative) curX + x else x
                    path.lineTo(px, curY)
                    curX = px
                    hasLastCtrl = false
                }
                'V' -> {
                    val y = parseNumber() ?: break
                    val py = if (relative) curY + y else y
                    path.lineTo(curX, py)
                    curY = py
                    hasLastCtrl = false
                }
                'C' -> {
                    val x1 = parseNumber() ?: break; val y1 = parseNumber() ?: break
                    val x2 = parseNumber() ?: break; val y2 = parseNumber() ?: break
                    val x = parseNumber() ?: break; val y = parseNumber() ?: break
                    val c1x = if (relative) curX + x1 else x1
                    val c1y = if (relative) curY + y1 else y1
                    val c2x = if (relative) curX + x2 else x2
                    val c2y = if (relative) curY + y2 else y2
                    val ex = if (relative) curX + x else x
                    val ey = if (relative) curY + y else y
                    path.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                    curX = ex; curY = ey
                    lastCtrlX = c2x; lastCtrlY = c2y; hasLastCtrl = true
                }
                'S' -> {
                    val x2 = parseNumber() ?: break; val y2 = parseNumber() ?: break
                    val x = parseNumber() ?: break; val y = parseNumber() ?: break
                    val c1x = if (hasLastCtrl) 2 * curX - lastCtrlX else curX
                    val c1y = if (hasLastCtrl) 2 * curY - lastCtrlY else curY
                    val c2x = if (relative) curX + x2 else x2
                    val c2y = if (relative) curY + y2 else y2
                    val ex = if (relative) curX + x else x
                    val ey = if (relative) curY + y else y
                    path.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                    curX = ex; curY = ey
                    lastCtrlX = c2x; lastCtrlY = c2y; hasLastCtrl = true
                }
                'Q' -> {
                    val x1 = parseNumber() ?: break; val y1 = parseNumber() ?: break
                    val x = parseNumber() ?: break; val y = parseNumber() ?: break
                    val cx = if (relative) curX + x1 else x1
                    val cy = if (relative) curY + y1 else y1
                    val ex = if (relative) curX + x else x
                    val ey = if (relative) curY + y else y
                    path.quadraticTo(cx, cy, ex, ey)
                    curX = ex; curY = ey
                    lastCtrlX = cx; lastCtrlY = cy; hasLastCtrl = true
                }
                'T' -> {
                    val x = parseNumber() ?: break; val y = parseNumber() ?: break
                    val cx = if (hasLastCtrl) 2 * curX - lastCtrlX else curX
                    val cy = if (hasLastCtrl) 2 * curY - lastCtrlY else curY
                    val ex = if (relative) curX + x else x
                    val ey = if (relative) curY + y else y
                    path.quadraticTo(cx, cy, ex, ey)
                    curX = ex; curY = ey
                    lastCtrlX = cx; lastCtrlY = cy; hasLastCtrl = true
                }
                'A' -> {
                    parseNumber() ?: break // rx
                    parseNumber() ?: break // ry
                    parseNumber() ?: break // angle
                    parseFlag() ?: break   // large-arc
                    parseFlag() ?: break   // sweep
                    val x = parseNumber() ?: break; val y = parseNumber() ?: break
                    val ex = if (relative) curX + x else x
                    val ey = if (relative) curY + y else y
                    path.lineTo(ex, ey)
                    curX = ex; curY = ey
                    hasLastCtrl = false
                }
                'Z' -> {
                    path.close()
                    curX = startX; curY = startY
                    hasLastCtrl = false
                }
                else -> i++
            }
        }
        return path
    }
}

/**
 * Caches parsed [Path]s and their hit-testing [Region]s, keyed by the raw SVG
 * string. Paths/regions are in unscaled SVG space and therefore stable.
 */
internal object PathCache {
    private val paths = ConcurrentHashMap<String, Path>()
    private val regions = ConcurrentHashMap<String, Region>()

    fun path(svg: String): Path = paths.getOrPut(svg) { SvgPathBuilder.build(svg) }

    fun region(svg: String): Region = regions.getOrPut(svg) {
        val androidPath = path(svg).asAndroidPath()
        val bounds = RectF()
        androidPath.computeBounds(bounds, true)
        Region().apply {
            setPath(
                androidPath,
                Region(
                    Math.floor(bounds.left.toDouble()).toInt(),
                    Math.floor(bounds.top.toDouble()).toInt(),
                    Math.ceil(bounds.right.toDouble()).toInt(),
                    Math.ceil(bounds.bottom.toDouble()).toInt(),
                ),
            )
        }
    }
}
