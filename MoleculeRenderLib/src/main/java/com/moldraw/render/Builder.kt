package com.moldraw.render

import kotlin.math.*

/* ═══════════════════════════════════════
 * 分子渲染库 — 分子构建辅助函数
 * 封装环创建、自动键等常用操作
 * ═══════════════════════════════════════ */

/** 在 MutableList<MoleculeAtom> 上添加环的扩展 */
fun MutableList<MoleculeAtom>.addRings(
    n: Int, cx: Float, cy: Float, bondLength: Float = BOND_LENGTH,
    benzene: Boolean = false,
    nextId: () -> Int = { (this.maxOfOrNull { it.id } ?: 0) + 1 }
) {
    val r = bondLength / (2f * sin(Math.toRadians(180.0 / n).toFloat()))
    for (i in 0 until n) {
        val deg = i * (360f / n) - 90f
        val rad = Math.toRadians(deg.toDouble())
        val px = cx + r * cos(rad).toFloat()
        val py = cy + r * sin(rad).toFloat()
        add(MoleculeAtom(nextId(), px, py, Element.C, aromatic = benzene))
    }
}

/** 在 MutableList<MoleculeBond> 上把环的原子依次连起来的扩展 */
fun MutableList<MoleculeBond>.connectRing(
    atoms: List<MoleculeAtom>,
    indices: IntRange,
    bondType: BondType = BondType.SINGLE,
    benzene: Boolean = false,
    nextId: () -> Int = { (this.maxOfOrNull { it.id } ?: 0) + 1 }
) {
    val list = indices.toList()
    for (i in list.indices) {
        val a = list[i]
        val b = list[(i + 1) % list.size]
        if (a in atoms.indices && b in atoms.indices) {
            val bt = if (benzene) BondType.AROMATIC else bondType
            add(MoleculeBond(nextId(), atoms[a].id, atoms[b].id, bt))
        }
    }
}

/**
 * 完整的硝基苯快速构建
 * 返回可直接传给 drawCanvasContent 的三个列表
 */
fun buildNitrobenzene(
    centerX: Float = 100f, centerY: Float = 100f,
    nextAtomId: () -> Int = { 1 }
): Triple<List<MoleculeAtom>, List<MoleculeBond>, List<MoleculeAnnotation>> {
    val id = { nextAtomId().also { nextAtomId() }; nextAtomId() - 1 } // 简化ID分配
    // 实际使用请管理好ID
    TODO("Use FuncGroupManager + addRings manually for full control")
}