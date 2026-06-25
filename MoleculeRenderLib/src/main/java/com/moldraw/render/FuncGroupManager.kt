package com.moldraw.render

import kotlin.math.*

/* ═══════════════════════════════════════
 * 分子渲染库 — 官能团管理器
 * 封装官能团缩写（FUNC_GROUP）的创建与管理
 * ═══════════════════════════════════════ */

/**
 * 官能团缩写定义
 * @param name 显示名称
 * @param label 显示文字（如 "NO₂"）
 * @param expandAtoms 展开原子列表：(元素符号, 与连接点之间的键型)
 * @param connectIndex 连接点原子在 expandAtoms 中的索引（默认0）
 */
data class FunctionalGroup(
    val name: String,
    val label: String,
    val expandAtoms: List<Pair<String, BondType>>,
    val connectIndex: Int = 0
)

/** 预定义的常见官能团 */
val FUNCTIONAL_GROUPS = listOf(
    FunctionalGroup("硝基", "NO₂", listOf(
        "N" to BondType.SINGLE, "O" to BondType.DOUBLE, "O" to BondType.DOUBLE
    )),
    FunctionalGroup("甲基", "CH₃", listOf(
        "C" to BondType.SINGLE, "H" to BondType.SINGLE, "H" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("乙基", "C₂H₅", listOf(
        "C" to BondType.SINGLE, "C" to BondType.SINGLE,
        "H" to BondType.SINGLE, "H" to BondType.SINGLE, "H" to BondType.SINGLE,
        "H" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("羟基", "OH", listOf(
        "O" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("羰基", "C=O", listOf(
        "C" to BondType.SINGLE, "O" to BondType.DOUBLE
    )),
    FunctionalGroup("羧基", "COOH", listOf(
        "C" to BondType.SINGLE, "O" to BondType.DOUBLE, "O" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("氨基", "NH₂", listOf(
        "N" to BondType.SINGLE, "H" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("苯基", "Ph", listOf(
        "C" to BondType.AROMATIC, "C" to BondType.AROMATIC, "C" to BondType.AROMATIC,
        "C" to BondType.AROMATIC, "C" to BondType.AROMATIC, "C" to BondType.AROMATIC
    )),
    FunctionalGroup("醛基", "CHO", listOf(
        "C" to BondType.SINGLE, "O" to BondType.DOUBLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("磺酸基", "SO₃H", listOf(
        "S" to BondType.SINGLE, "O" to BondType.DOUBLE, "O" to BondType.DOUBLE,
        "O" to BondType.SINGLE, "H" to BondType.SINGLE
    )),
    FunctionalGroup("氰基", "CN", listOf(
        "C" to BondType.SINGLE, "N" to BondType.TRIPLE
    ))
)

/**
 * 官能团创建结果
 */
data class FuncGroupResult(
    val atoms: List<MoleculeAtom>,
    val bonds: List<MoleculeBond>,
    val annotation: MoleculeAnnotation
)

/**
 * 官能团管理器 — 极简 API
 *
 * 外部项目只要调用：
 * ```
 * val result = FuncGroupManager.create("NO₂", 200f, 100f)
 * // result.atoms 有 N, O1, O2，已标记 funGroupLabel
 * // result.bonds 有 N=O1, N=O2
 * // result.annotation 是 FUNC_GROUP 标注
 * ```
 *
 * 然后加到自己的 atoms/bonds/annotations 列表即可。
 */
object FuncGroupManager {

    /** 在指定位置创建官能团的原子+键+标注 */
    fun create(
        label: String,
        x: Float,
        y: Float,
        nextAtomId: Int = 1,
        nextBondId: Int = 1,
        nextAnnId: Int = 1,
        bondLengthRatio: Float = 0.6f
    ): FuncGroupResult? {
        val fg = FUNCTIONAL_GROUPS.firstOrNull { it.label == label } ?: return null

        val connElem = Element.fromSymbol(fg.expandAtoms[fg.connectIndex].first)
        val connector = MoleculeAtom(nextAtomId, x, y, connElem,
            funGroupLabel = fg.label, isFunGroupConnector = true)

        val memberAtoms = mutableListOf<MoleculeAtom>()
        val memberBonds = mutableListOf<MoleculeBond>()
        val nonConnectList = fg.expandAtoms.filterIndexed { i, _ -> i != fg.connectIndex }
        var newIdx = 0
        var aid = nextAtomId + 1

        for ((j, pair) in nonConnectList.withIndex()) {
            val (elemSym, bondType) = pair
            val elem = Element.fromSymbol(elemSym)
            if (elem == Element.H) continue
            val nonHCount = nonConnectList.filter { Element.fromSymbol(it.first) != Element.H }.size.coerceAtLeast(1)
            val angle = newIdx * (360.0 / nonHCount)
            val rad = Math.toRadians(angle)
            val offset = BOND_LENGTH * bondLengthRatio
            val ax = x + offset * cos(rad).toFloat()
            val ay = y + offset * sin(rad).toFloat()
            memberAtoms.add(MoleculeAtom(aid, ax, ay, elem,
                funGroupLabel = fg.label, isFunGroupConnector = false))
            memberBonds.add(MoleculeBond(nextBondId + memberBonds.size, connector.id, aid, bondType))
            aid++
            newIdx++
        }

        val allAtoms = listOf(connector) + memberAtoms
        val allBonds = memberBonds.toList()
        val annotation = MoleculeAnnotation(nextAnnId, AnnotationType.FUNC_GROUP, x, y, text = fg.label)

        return FuncGroupResult(allAtoms, allBonds, annotation)
    }

    /** 简写：用名称查找（如 "硝基"） */
    fun createByName(
        name: String,
        x: Float, y: Float,
        nextAtomId: Int = 1,
        nextBondId: Int = 1,
        nextAnnId: Int = 1
    ): FuncGroupResult? {
        val fg = FUNCTIONAL_GROUPS.firstOrNull { it.name == name } ?: return null
        return create(fg.label, x, y, nextAtomId, nextBondId, nextAnnId)
    }

    /** 在已有原子的位置创建官能团，替换该原子（返回结果 + 被替换原子的邻居ID） */
    fun replace(
        label: String,
        targetAtom: MoleculeAtom,
        neighborIds: List<Int>,
        nextAtomId: Int = 1,
        nextBondId: Int = 1,
        nextAnnId: Int = 1
    ): FuncGroupReplaceResult? {
        val fg = FUNCTIONAL_GROUPS.firstOrNull { it.label == label } ?: return null
        val result = create(label, targetAtom.x, targetAtom.y, nextAtomId, nextBondId, nextAnnId) ?: return null

        val neighborBonds = neighborIds.map { nid ->
            MoleculeBond(nextBondId + result.bonds.size + neighborIds.indexOf(nid),
                result.atoms.first().id, nid, BondType.SINGLE)
        }

        return FuncGroupReplaceResult(result, neighborBonds, fg)
    }
}

data class FuncGroupReplaceResult(
    val group: FuncGroupResult,
    val neighborBonds: List<MoleculeBond>,
    val definition: FunctionalGroup
)
