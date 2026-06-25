package com.moldraw.render

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.*

/* ═══════════════════════════════════════
 * 分子渲染库 — 渲染函数
 * 所有函数均为 DrawScope 扩展
 * ═══════════════════════════════════════ */

/** 绘制单个键 */
fun DrawScope.drawBond(a1: MoleculeAtom, a2: MoleculeAtom, type: BondType, kekuleIndex: Int = -1) {
    val p1 = Offset(a1.x, a1.y); val p2 = Offset(a2.x, a2.y)
    val dx = p2.x - p1.x; val dy = p2.y - p1.y
    val len = sqrt(dx * dx + dy * dy); if (len == 0f) return
    val ux = dx / len; val uy = dy / len

    val r1 = if (a1.element == Element.C) CARBON_DOT_R else HETERO_CIRCLE_R
    val r2 = if (a2.element == Element.C) CARBON_DOT_R else HETERO_CIRCLE_R
    val s1 = Offset(p1.x + ux * r1, p1.y + uy * r1)
    val s2 = Offset(p2.x - ux * r2, p2.y - uy * r2)

    val bondColor = Color(0xFF333333)
    when (type) {
        BondType.SINGLE -> drawLine(bondColor, s1, s2, strokeWidth = STROKE_WIDTH)
        BondType.WEDGE_UP -> {
            val wedgeHalfW = 5f
            val perpX = -uy * wedgeHalfW; val perpY = ux * wedgeHalfW
            val path = Path().apply {
                moveTo(s1.x, s1.y)
                lineTo(s2.x + perpX, s2.y + perpY)
                lineTo(s2.x - perpX, s2.y - perpY)
                close()
            }
            drawPath(path, bondColor)
        }
        BondType.WEDGE_DOWN -> {
            val segLen = 8f; val gapLen = 5f
            val totalLen = len - r1 - r2
            var drawn = 0f
            while (drawn < totalLen) {
                val start = drawn / totalLen
                var end = (drawn + segLen) / totalLen
                if (end > 1f) end = 1f
                val dashSx = s1.x + (s2.x - s1.x) * start
                val dashSy = s1.y + (s2.y - s1.y) * start
                val dashEx = s1.x + (s2.x - s1.x) * end
                val dashEy = s1.y + (s2.y - s1.y) * end
                val wedgeHalfW = 4.5f * end
                val perpX = -uy * wedgeHalfW; val perpY = ux * wedgeHalfW
                drawLine(bondColor, Offset(dashSx, dashSy), Offset(dashEx, dashEy), strokeWidth = 2.5f)
                drawn += segLen + gapLen
                if (end >= 1f) break
            }
        }
        BondType.DOUBLE -> {
            val px = -uy * 3f; val py = ux * 3f
            drawLine(bondColor, Offset(s1.x + px, s1.y + py), Offset(s2.x + px, s2.y + py), strokeWidth = STROKE_WIDTH - 0.5f)
            drawLine(bondColor, Offset(s1.x - px, s1.y - py), Offset(s2.x - px, s2.y - py), strokeWidth = STROKE_WIDTH - 0.5f)
        }
        BondType.TRIPLE -> {
            drawLine(bondColor, s1, s2, strokeWidth = STROKE_WIDTH - 1f)
            val px = -uy * 4f; val py = ux * 4f
            drawLine(bondColor, Offset(s1.x + px, s1.y + py), Offset(s2.x + px, s2.y + py), strokeWidth = STROKE_WIDTH - 1f)
            drawLine(bondColor, Offset(s1.x - px, s1.y - py), Offset(s2.x - px, s2.y - py), strokeWidth = STROKE_WIDTH - 1f)
        }
        BondType.AROMATIC -> {
            if (kekuleIndex >= 0) {
                if (kekuleIndex % 2 == 0) {
                    drawLine(bondColor, s1, s2, strokeWidth = STROKE_WIDTH)
                } else {
                    val px = -uy * 3f; val py = ux * 3f
                    drawLine(bondColor, Offset(s1.x + px, s1.y + py), Offset(s2.x + px, s2.y + py), strokeWidth = STROKE_WIDTH - 0.5f)
                    drawLine(bondColor, Offset(s1.x - px, s1.y - py), Offset(s2.x - px, s2.y - py), strokeWidth = STROKE_WIDTH - 0.5f)
                }
            } else {
                drawLine(bondColor, s1, s2, strokeWidth = STROKE_WIDTH)
            }
        }
    }
}

/** 绘制单个原子 */
fun DrawScope.drawAtom(a: MoleculeAtom, bonds: List<MoleculeBond> = emptyList(), annotations: List<MoleculeAnnotation> = emptyList()) {
    val c = Offset(a.x, a.y)
    val p = Paint().apply {
        color = android.graphics.Color.BLACK; textSize = FONT_SIZE
        textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    if (a.funGroupLabel != null) return
    if (a.element == Element.C) {
        drawCircle(Color(0xFF666666), CARBON_DOT_R, c)
    } else {
        val symbol = a.element.symbol
        val hCount = calcImplicitH(a, bonds)
        val displayText = if (hCount == 0) symbol
            else if (hCount == 1) "${symbol}H"
            else "${symbol}H${hCount}"
        drawContext.canvas.nativeCanvas.drawText(displayText, c.x, c.y + FONT_SIZE / 3f, p)
    }
}

/** 绘制文字标注（含 FUNC_GROUP 缩写） */
fun DrawScope.drawAnnotationText(ann: MoleculeAnnotation, selected: Boolean = false, textSizeMultiplier: Float = 1f) {
    val p = Paint().apply {
        color = if (selected) android.graphics.Color.parseColor("#1976D2") else android.graphics.Color.parseColor("#333333")
        textSize = ANN_TEXT_SIZE * ann.scale * textSizeMultiplier
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
    }
    val fm = p.fontMetrics
    val baseY = ann.y - (fm.ascent + fm.descent) / 2f
    if (ann.subScript && ann.text.any { it.isDigit() }) {
        val parts = mutableListOf<Pair<String, Boolean>>()
        val current = StringBuilder()
        var inDigit = false
        for (c in ann.text) {
            if (c.isDigit()) {
                if (!inDigit && current.isNotEmpty()) { parts.add(Pair(current.toString(), false)); current.clear() }
                inDigit = true; current.append(c)
            } else {
                if (inDigit && current.isNotEmpty()) { parts.add(Pair(current.toString(), true)); current.clear() }
                inDigit = false; current.append(c)
            }
        }
        if (current.isNotEmpty()) parts.add(Pair(current.toString(), inDigit))
        var xPos = ann.x
        val normalSize = ANN_TEXT_SIZE * ann.scale * textSizeMultiplier
        val subSize = normalSize * 0.65f
        val subOffset = normalSize * 0.35f
        for ((text, isSub) in parts) {
            p.textSize = if (isSub) subSize else normalSize
            val yOffset = if (isSub) subOffset else 0f
            drawContext.canvas.nativeCanvas.drawText(text, xPos, baseY + yOffset, p)
            xPos += p.measureText(text)
        }
    } else {
        p.textAlign = Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(ann.text, ann.x, baseY, p)
    }
}

/** 绘制箭头标注 */
fun DrawScope.drawAnnotationArrow(ann: MoleculeAnnotation, selected: Boolean = false) {
    val sx = ann.x; val sy = ann.y
    val ex = ann.endX; val ey = ann.endY
    val dx = ex - sx; val dy = ey - sy
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val ux = dx / len; val uy = dy / len
    val bc = if (selected) Color(0xFF1976D2) else Color(0xFF333333)
    drawLine(bc, Offset(sx, sy), Offset(ex, ey), strokeWidth = 2.5f)
    val headSize = ARROW_HEAD_SIZE * ann.scale
    val angle = Math.toRadians(25.0)
    val cosA = cos(angle).toFloat(); val sinA = sin(angle).toFloat()
    val lx = ex - ux * headSize * cosA + uy * headSize * sinA
    val ly = ey - uy * headSize * cosA - ux * headSize * sinA
    val rx = ex - ux * headSize * cosA - uy * headSize * sinA
    val ry = ey - uy * headSize * cosA + ux * headSize * sinA
    drawLine(bc, Offset(ex, ey), Offset(lx, ly), strokeWidth = 2.5f)
    drawLine(bc, Offset(ex, ey), Offset(rx, ry), strokeWidth = 2.5f)
}

/** 绘制鲍林式苯环内切圆 */
fun DrawScope.drawPaulingCircle(vertices: List<Offset>) {
    if (vertices.size != 6) return
    val cx = vertices.map { it.x }.average().toFloat()
    val cy = vertices.map { it.y }.average().toFloat()
    val radius = vertices.minOf { sqrt((it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy)) } * 0.58f
    drawCircle(Color(0xFF333333), radius, Offset(cx, cy), style = Stroke(width = 2f))
}

/**
 * 画布内容绘制 — 主入口
 *
 * @param atoms 分子原子列表
 * @param bonds 分子键列表
 * @param annotations 标注列表
 * @param selectedIds 选中的原子/标注 ID（负值表示标注）
 * @param tool 当前工具
 * @param ringType 环类型
 * @param selectedAtom 选中原子 ID
 * @param isDragging ATOM 拖拽中
 * @param dragSrc 拖拽源原子 ID
 * @param dragEnd 拖拽终点
 * @param isBondDragging BOND 拖拽中
 * @param bondStart 键起点
 * @param bondCur 键当前点
 * @param bondFirstAtom 键第一个原子
 * @param bondSnapping 吸附中
 * @param bondEndMerge 合并目标原子列表
 * @param isSelDragging 选框拖拽中
 * @param selRectStart 选框起点
 * @param selRectEnd 选框终点
 * @param isArrowDragging 箭头拖拽中
 * @param arrowEnd 箭头终点
 * @param arrowStart 箭头起点
 * @param isScaleDragging 缩放拖拽中
 * @param selBond 当前键类型
 * @param activeGroup 变换组
 * @param benzeneStyle 苯环样式
 * @param showAlignGuides 显示对齐辅助线
 * @param alignGuideX 对齐线 X
 * @param alignGuideY 对齐线 Y
 * @param scale 缩放因子
 * @param tx X坐标变换函数（可选，不传 = 1:1）
 * @param ty Y坐标变换函数（可选）
 */
fun DrawScope.drawCanvasContent(
    atoms: List<MoleculeAtom>,
    bonds: List<MoleculeBond>,
    annotations: List<MoleculeAnnotation>,
    tool: DrawTool = DrawTool.SELECT,
    ringType: RingType = RingType.RING_6,
    selectedAtom: Int? = null,
    selectedIds: Set<Int> = emptySet(),
    isDragging: Boolean = false,
    dragSrc: Int? = null,
    dragEnd: Offset = Offset.Zero,
    isBondDragging: Boolean = false,
    bondStart: Offset? = null,
    bondCur: Offset = Offset.Zero,
    bondFirstAtom: Int? = null,
    bondSnapping: Boolean = false,
    bondEndMerge: List<MoleculeAtom> = emptyList(),
    isSelDragging: Boolean = false,
    selRectStart: Offset? = null,
    selRectEnd: Offset = Offset.Zero,
    isArrowDragging: Boolean = false,
    arrowEnd: Offset = Offset.Zero,
    arrowStart: Offset? = null,
    isScaleDragging: Boolean = false,
    selBond: BondType = BondType.SINGLE,
    activeGroup: TransformGroup? = null,
    benzeneStyle: BenzeneStyle = BenzeneStyle.KEKULE,
    showAlignGuides: Boolean = false,
    alignGuideX: Float = Float.NaN,
    alignGuideY: Float = Float.NaN,
    scale: Float = 1f,
    tx: ((Float) -> Float)? = null,
    ty: ((Float) -> Float)? = null
) {
    val xf = { x: Float -> tx?.invoke(x) ?: x }
    val yf = { y: Float -> ty?.invoke(y) ?: y }
    val sf = scale
    fun pt(x: Float, y: Float) = Offset(xf(x), yf(y))

    // 变换组坐标转换
    fun transformX(a: MoleculeAtom): Float {
        val g = activeGroup ?: return xf(a.x)
        if (a.id !in g.atomIds) return xf(a.x)
        val rad = Math.toRadians(g.rotation.toDouble())
        val cosR = cos(rad).toFloat(); val sinR = sin(rad).toFloat()
        val lx = (a.x - g.pivotX) * g.scaleX
        val ly = (a.y - g.pivotY) * g.scaleY
        val rx = lx * cosR - ly * sinR
        return xf(g.pivotX + rx + g.translationX)
    }
    fun transformY(a: MoleculeAtom): Float {
        val g = activeGroup ?: return yf(a.y)
        if (a.id !in g.atomIds) return yf(a.y)
        val rad = Math.toRadians(g.rotation.toDouble())
        val cosR = cos(rad).toFloat(); val sinR = sin(rad).toFloat()
        val lx = (a.x - g.pivotX) * g.scaleX
        val ly = (a.y - g.pivotY) * g.scaleY
        val ry = lx * sinR + ly * cosR
        return yf(g.pivotY + ry + g.translationY)
    }

    // ── 绘制键 ──
    for (b in bonds) {
        val a1 = atoms.find { it.id == b.atom1 } ?: continue
        val a2 = atoms.find { it.id == b.atom2 } ?: continue
        if (a1.funGroupLabel != null && a1.funGroupLabel == a2.funGroupLabel) continue
        if (b.type == BondType.AROMATIC) continue
        drawBond(
            MoleculeAtom(a1.id, transformX(a1), transformY(a1), a1.element),
            MoleculeAtom(a2.id, transformX(a2), transformY(a2), a2.element),
            b.type
        )
    }

    // ── 绘制原子（跳过 FUNC_GROUP 组内原子） ──
    val coveredAtomIds = atoms.filter { it.funGroupLabel != null }.map { it.id }.toSet()
    for (a in atoms) {
        if (a.id in coveredAtomIds) continue
        drawAtom(MoleculeAtom(a.id, transformX(a), transformY(a), a.element), bonds, annotations)
    }

    // ── 选中高亮 ──
    if (selectedIds.isNotEmpty()) {
        for (aid in selectedIds) {
            if (aid < 0) continue
            val a = atoms.find { it.id == aid } ?: continue
            if (a.funGroupLabel != null) continue
            val px = transformX(a); val py = transformY(a)
            drawCircle(Color(0xFF2196F3).copy(alpha = 0.3f), (HETERO_CIRCLE_R + 8f) * sf, Offset(px, py))
            drawCircle(Color(0xFF2196F3).copy(alpha = 0.55f), (HETERO_CIRCLE_R + 3f) * sf, Offset(px, py))
        }
    }

    // ── 绘制标注 ──
    for (ann in annotations) {
        val annSelected = -ann.id in selectedIds
        when (ann.type) {
            AnnotationType.TEXT, AnnotationType.FUNC_GROUP -> {
                val drawX = if (ann.type == AnnotationType.FUNC_GROUP) {
                    atoms.find { it.funGroupLabel == ann.text && it.isFunGroupConnector }
                        ?.let { xf(it.x) } ?: xf(ann.x)
                } else xf(ann.x)
                val drawY = if (ann.type == AnnotationType.FUNC_GROUP) {
                    atoms.find { it.funGroupLabel == ann.text && it.isFunGroupConnector }
                        ?.let { yf(it.y) } ?: yf(ann.y)
                } else yf(ann.y)
                drawAnnotationText(ann.copy(x = drawX, y = drawY), selected = annSelected, textSizeMultiplier = sf)
            }
            AnnotationType.ARROW -> drawAnnotationArrow(
                ann.copy(x = xf(ann.x), y = yf(ann.y), endX = xf(ann.endX), endY = yf(ann.endY)),
                selected = annSelected
            )
        }
    }

    // ── ATOM拖拽预览（预览放置原子位置） ──
    if (isDragging && dragSrc != null && dragEnd != Offset.Zero) {
        val src = atoms.find { it.id == dragSrc } ?: return
        val previewAtom = MoleculeAtom(0, dragEnd.x, dragEnd.y, src.element)
        drawAtom(previewAtom, bonds, annotations)
    }

    // ── BOND拖拽预览 ──
    if (isBondDragging && bondStart != null) {
        val cur = bondCur
        if (bondFirstAtom != null) {
            val src = atoms.find { it.id == bondFirstAtom }
            if (src != null) {
                val bs = pt(src.x, src.y)
                drawLine(Color(0xFF666666).copy(alpha = PREVIEW_ALPHA), bs, Offset(xf(cur.x), yf(cur.y)), strokeWidth = STROKE_WIDTH)
            }
        } else {
            val bs = pt(bondStart.x, bondStart.y)
            drawLine(Color(0xFF666666).copy(alpha = PREVIEW_ALPHA), bs, Offset(xf(cur.x), yf(cur.y)), strokeWidth = STROKE_WIDTH)
            drawCircle(Color(0xFF666666).copy(alpha = PREVIEW_ALPHA), CARBON_DOT_R, bs)
        }
        // 吸附到合并目标时高亮
        if (bondSnapping && bondEndMerge.isNotEmpty()) {
            for (ma in bondEndMerge) {
                drawCircle(Color(0xFF4CAF50).copy(alpha = 0.5f), HETERO_CIRCLE_R * 1.3f, pt(ma.x, ma.y))
            }
        }
    }

    // ── 选框 ──
    if (isSelDragging && selRectStart != null) {
        val sx = xf(selRectStart.x); val sy = yf(selRectStart.y)
        val ex = xf(selRectEnd.x); val ey = yf(selRectEnd.y)
        drawRect(
            Color(0xFF2196F3).copy(alpha = 0.08f),
            topLeft = Offset(minOf(sx, ex), minOf(sy, ey)),
            size = androidx.compose.ui.geometry.Size(abs(ex - sx), abs(ey - sy))
        )
        drawRect(
            Color(0xFF2196F3).copy(alpha = 0.4f),
            topLeft = Offset(minOf(sx, ex), minOf(sy, ey)),
            size = androidx.compose.ui.geometry.Size(abs(ex - sx), abs(ey - sy)),
            style = Stroke(width = 1.5f)
        )
    }

    // ── 箭头拖拽预览 ──
    if (isArrowDragging && arrowStart != null) {
        val asPt = pt(arrowStart.x, arrowStart.y)
        val aePt = pt(arrowEnd.x, arrowEnd.y)
        drawLine(Color(0xFF333333).copy(alpha = PREVIEW_ALPHA), asPt, aePt, strokeWidth = 2.5f)
        val dx = aePt.x - asPt.x; val dy = aePt.y - asPt.y
        val len = sqrt(dx * dx + dy * dy)
        if (len > 5f) {
            val ux = dx / len; val uy = dy / len
            val headSize = 10f
            val angle = Math.toRadians(25.0)
            val cosA = cos(angle).toFloat(); val sinA = sin(angle).toFloat()
            val lx = aePt.x - ux * headSize * cosA + uy * headSize * sinA
            val ly = aePt.y - uy * headSize * cosA - ux * headSize * sinA
            val rx = aePt.x - ux * headSize * cosA - uy * headSize * sinA
            val ry = aePt.y - uy * headSize * cosA + ux * headSize * sinA
            drawLine(Color(0xFF333333).copy(alpha = PREVIEW_ALPHA), aePt, Offset(lx, ly), strokeWidth = 2.5f)
            drawLine(Color(0xFF333333).copy(alpha = PREVIEW_ALPHA), aePt, Offset(rx, ry), strokeWidth = 2.5f)
        }
    }

    // ── 芳香环渲染（按 benzeneStyle） ──
    val rings = findRings(atoms, bonds)
    if (rings.isNotEmpty()) {
        if (benzeneStyle == BenzeneStyle.PAULING) {
            for (ring0 in rings) {
                val ring = ring0 as List<Pair<Float, Float>>
                for (i in 0 until ring.size) {
                    val j = (i + 1) % ring.size
                    val p1 = ring[i]; val p2 = ring[j]
                    val a1 = MoleculeAtom(0, xf(p1.first), yf(p1.second), Element.C)
                    val a2 = MoleculeAtom(0, xf(p2.first), yf(p2.second), Element.C)
                    drawBond(a1, a2, BondType.SINGLE)
                }
                val verts = ring.map { (x, y) -> Offset(xf(x), yf(y)) }
                if (verts.size == 6) drawPaulingCircle(verts)
            }
        } else {
            for (ring0 in rings) {
                val ring = ring0 as List<Pair<Float, Float>>
                for (i in 0 until ring.size) {
                    val j = (i + 1) % ring.size
                    val p1 = ring[i]; val p2 = ring[j]
                    val a1 = MoleculeAtom(0, xf(p1.first), yf(p1.second), Element.C)
                    val a2 = MoleculeAtom(0, xf(p2.first), yf(p2.second), Element.C)
                    if (i % 2 == 0) drawBond(a1, a2, BondType.SINGLE)
                    else drawBond(a1, a2, BondType.DOUBLE)
                }
            }
        }
    }

    // ── 对齐辅助线 ──
    if (showAlignGuides && !alignGuideX.isNaN() && !alignGuideY.isNaN()) {
        val ax = xf(alignGuideX); val ay = yf(alignGuideY)
        val guideColor = Color(0xFFFF9800).copy(alpha = 0.6f)
        drawLine(guideColor, Offset(0f, ay), Offset(size.width, ay), strokeWidth = 1.5f)
        drawLine(guideColor, Offset(ax, 0f), Offset(ax, size.height), strokeWidth = 1.5f)
        drawCircle(guideColor, 4f * sf, Offset(ax, ay))
    }
}

/**
 * 检测芳香环（6元环，包含AROMATIC键的环）。
 * 返回每个环的顶点坐标列表。
 */
fun findRings(atoms: List<MoleculeAtom>, bonds: List<MoleculeBond>): List<List<Pair<Float, Float>>> {
    val aromaticBonds = bonds.filter { it.type == BondType.AROMATIC }
    if (aromaticBonds.size < 6) return emptyList()
    val adj = mutableMapOf<Int, MutableList<Int>>()
    for (b in aromaticBonds) {
        adj.getOrPut(b.atom1) { mutableListOf() }.add(b.atom2)
        adj.getOrPut(b.atom2) { mutableListOf() }.add(b.atom1)
    }
    val seenRings = mutableSetOf<Set<Int>>()
    val result = mutableListOf<List<Pair<Float, Float>>>()
    for (b in aromaticBonds) {
        val a1 = b.atom1; val a2 = b.atom2
        val parent = mutableMapOf<Int, Int>()
        val queue = ArrayDeque<Int>(); queue.addLast(a2)
        parent[a2] = -1
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == a1) break
            for (nb in adj[cur].orEmpty()) {
                if (cur == a2 && nb == a1) continue
                if (cur == a1 && nb == a2) continue
                if (nb !in parent) { parent[nb] = cur; if (parent.size < 12) queue.addLast(nb) }
            }
        }
        if (a1 in parent) {
            val ring = mutableListOf<Int>()
            var c = a1
            while (c != -1 && c != a2) { ring.add(c); c = parent[c] ?: -1 }
            if (c == a2) ring.add(a2)
            if (ring.size == 6) {
                val key = ring.toSortedSet()
                if (key !in seenRings) {
                    seenRings.add(key)
                    val verts = ring.mapNotNull { id -> atoms.find { it.id == id }?.let { a -> a.x to a.y } }
                    if (verts.size == 6) result.add(verts)
                }
            }
        }
    }
    return result
}