package com.moldraw.render

import kotlin.math.*

/* ═══════════════════════════════════════
 * 分子渲染库 — 数据模型
 * 自包含，无外部依赖（除 Compose UI）
 * ═══════════════════════════════════════ */

/** 化学元素 */
enum class Element(val symbol: String, val valence: Int, val atomicNumber: Int) {
    C("C", 4, 6), H("H", 1, 1), O("O", 2, 8), N("N", 3, 7),
    S("S", 2, 16), P("P", 3, 15), F("F", 1, 9), Cl("Cl", 1, 17),
    Br("Br", 1, 35), I("I", 1, 53), Na("Na", 1, 11), K("K", 1, 19),
    Fe("Fe", 2, 26), Cu("Cu", 2, 29), Zn("Zn", 2, 30), Mg("Mg", 2, 12), Ca("Ca", 2, 20), B("B", 3, 5),
    Si("Si", 4, 14), Se("Se", 2, 34), As("As", 3, 33), Te("Te", 2, 52),
    Li("Li", 1, 3), Be("Be", 2, 4), Al("Al", 3, 13), Ti("Ti", 4, 22),
    Mn("Mn", 2, 25), Co("Co", 2, 27), Ni("Ni", 2, 28), Pd("Pd", 2, 46),
    Pt("Pt", 2, 78), Au("Au", 3, 79), Ag("Ag", 1, 47);
    companion object { fun fromSymbol(s: String): Element = entries.find { it.symbol == s } ?: C }
}

/** 键类型 */
enum class BondType { SINGLE, DOUBLE, TRIPLE, WEDGE_UP, WEDGE_DOWN, AROMATIC }

/** 苯环显示样式 */
enum class BenzeneStyle { KEKULE, PAULING }

/** 标注类型 */
enum class AnnotationType { TEXT, ARROW, FUNC_GROUP }

/** 画图工具 */
enum class DrawTool { ATOM, BOND, RING, SELECT, TEXT, ARROW, SCALE, UNDO, REDO, DELETE, MOVE, ERASER }

/** 环类型 */
enum class RingType(val n: Int, val benzene: Boolean = false) { RING_5(5), RING_6(6, benzene = true), RING_7(7), RING_3(3), RING_4(4), RING_8(8) }

/** 分子原子 */
data class MoleculeAtom(
    val id: Int,
    var x: Float,
    var y: Float,
    var element: Element = Element.C,
    var aromatic: Boolean = false,
    var chiral: String = "",
    /** 如果非空，表示该原子属于某个官能团缩写（如 "NO₂"），渲染时跳过 */
    var funGroupLabel: String? = null,
    /** 对于官能团缩写，true 表示该原子是连接点 */
    var isFunGroupConnector: Boolean = false
)

/** 分子键 */
data class MoleculeBond(val id: Int, val atom1: Int, val atom2: Int, val type: BondType = BondType.SINGLE)

/** 标注（文字、箭头、官能团） */
data class MoleculeAnnotation(
    val id: Int, val type: AnnotationType,
    var x: Float, var y: Float,
    var text: String = "",
    var endX: Float = 0f, var endY: Float = 0f,
    var scale: Float = 1f,
    var subScript: Boolean = false
)

/** 变换组（选中即编组） */
data class TransformGroup(
    val id: Int = 0,
    var translationX: Float = 0f, var translationY: Float = 0f,
    var rotation: Float = 0f,
    var scaleX: Float = 1f, var scaleY: Float = 1f,
    var pivotX: Float = 0f, var pivotY: Float = 0f,
    val atomIds: MutableSet<Int> = mutableSetOf(),
    val bondIds: MutableSet<Int> = mutableSetOf(),
    val annotationIds: MutableSet<Int> = mutableSetOf()
)

/* ── 渲染常量 ── */
const val HIT_THRESHOLD = 28f
const val PREVIEW_ALPHA = 0.35f
const val MERGE_THRESHOLD = 22f

/** 渲染参数（var，可在加载JSON时覆盖） */
var CARBON_DOT_R = 5f
var HETERO_CIRCLE_R = 16f
/** 键长（像素），可被外部修改 */
var BOND_LENGTH = 55f
var STROKE_WIDTH = 3.5f
var FONT_SIZE = 18f
var ANN_TEXT_SIZE = 18f
var ARROW_HEAD_SIZE = 12f

/** 自动化学下标转换：H2O → H₂O, CH4 → CH₄ */
fun autoSubscript(text: String): String {
    val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉'
    )
    val sb = StringBuilder()
    for (c in text) sb.append(subscripts[c] ?: c)
    return sb.toString()
}

/** 计算原子的隐式氢数量 */
fun calcImplicitH(atom: MoleculeAtom, bonds: List<MoleculeBond>): Int {
    if (atom.element == Element.H || atom.element == Element.C) return 0
    val connected = bonds.filter { it.atom1 == atom.id || it.atom2 == atom.id }
    var bondOrderSum = 0
    var aromaticNeighborCount = 0
    for (b in connected) {
        when (b.type) {
            BondType.SINGLE, BondType.WEDGE_UP, BondType.WEDGE_DOWN -> bondOrderSum += 1
            BondType.DOUBLE -> bondOrderSum += 2
            BondType.TRIPLE -> bondOrderSum += 3
            BondType.AROMATIC -> { bondOrderSum += 1; aromaticNeighborCount++ }
        }
    }
    var h = (atom.element.valence - bondOrderSum).coerceAtLeast(0)
    if (aromaticNeighborCount >= 2 && h > 0) h -= 1
    return h
}

/* ═══════════════════════════════════════
 * JSON 序列化 / 反序列化（零依赖，纯 Kotlin）
 * 用法：
 *   val json = buildJson(atoms, bonds, annotations)
 *   loadJson(json, atoms, bonds, annotations) { key, value -> ... }
 * ═══════════════════════════════════════ */

/**
 * 将分子数据构建为 JSON 字符串（含所有渲染参数当前值）。
 * 参数之外的渲染参数（CARBON_DOT_R、STROKE_WIDTH 等）读取 Model.kt 当前 var 值。
 * @param bondLength 可选，默认使用 BOND_LENGTH 当前值
 */
fun buildJson(
    atoms: List<MoleculeAtom>,
    bonds: List<MoleculeBond>,
    annotations: List<MoleculeAnnotation>,
    bondLength: Float = BOND_LENGTH,
    benzeneStyle: BenzeneStyle = BenzeneStyle.KEKULE
): String {
    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("  \"format\": \"moldraw\",")
    sb.appendLine("  \"version\": 1,")
    sb.appendLine("  \"bondLength\": $bondLength,")
    sb.appendLine("  \"strokeWidth\": $STROKE_WIDTH,")
    sb.appendLine("  \"fontSize\": $FONT_SIZE,")
    sb.appendLine("  \"annTextSize\": $ANN_TEXT_SIZE,")
    sb.appendLine("  \"carbonDotR\": $CARBON_DOT_R,")
    sb.appendLine("  \"heteroCircleR\": $HETERO_CIRCLE_R,")
    sb.appendLine("  \"arrowHeadSize\": $ARROW_HEAD_SIZE,")
    sb.appendLine("  \"benzeneStyle\": \"${benzeneStyle.name}\",")
    // 原子
    sb.appendLine("  \"atoms\": [")
    for ((i, a) in atoms.withIndex()) {
        sb.append("    {\"id\":${a.id},\"x\":${a.x},\"y\":${a.y},\"element\":\"${a.element.name}\"")
        if (a.aromatic) sb.append(",\"aromatic\":true")
        if (a.chiral.isNotEmpty()) sb.append(",\"chiral\":\"${a.chiral}\"")
        if (a.funGroupLabel != null) sb.append(",\"funGroupLabel\":\"${a.funGroupLabel}\"")
        if (a.isFunGroupConnector) sb.append(",\"isFunGroupConnector\":true")
        sb.append(if (i < atoms.lastIndex) "}," else "}")
        sb.appendLine()
    }
    sb.appendLine("  ],")
    // 键
    sb.appendLine("  \"bonds\": [")
    for ((i, b) in bonds.withIndex()) {
        sb.appendLine("    {\"id\":${b.id},\"a1\":${b.atom1},\"a2\":${b.atom2},\"type\":\"${b.type.name}\"}${if (i < bonds.lastIndex) "," else ""}")
    }
    sb.appendLine("  ],")
    // 标注
    sb.appendLine("  \"annotations\": [")
    for ((i, ann) in annotations.withIndex()) {
        sb.append("    {\"id\":${ann.id},\"type\":\"${ann.type.name}\",\"x\":${ann.x},\"y\":${ann.y}")
        if (ann.text.isNotEmpty()) sb.append(",\"text\":\"${ann.text.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        if (ann.type == AnnotationType.ARROW || ann.endX != 0f || ann.endY != 0f) {
            sb.append(",\"endX\":${ann.endX},\"endY\":${ann.endY}")
        }
        if (abs(ann.scale - 1f) > 0.01f) sb.append(",\"scale\":${ann.scale}")
        if (ann.subScript) sb.append(",\"subScript\":true")
        sb.append(if (i < annotations.lastIndex) "}," else "}")
        sb.appendLine()
    }
    sb.appendLine("  ]")
    sb.appendLine("}")
    return sb.toString()
}

/**
 * 单个反应条目
 */
data class ReactionEntry(
    val reactants: List<Pair<String, Double>> = emptyList(),
    val products: List<Pair<String, Double>> = emptyList(),
    val conditions: String = ""
)

/**
 * 从 JSON 字符串加载分子数据，清空并填充 [atoms]、[bonds]、[annotations]。
 * 同时通过 [renderParamCallback] 回传渲染参数（strokeWidth/fontSize/carbonDotR 等），
 * 调用方自行赋值给对应的 var。
 *
 * @param reactionCallback 可选，解析到 reaction 字段时回调（ReactionData 包含 reactions 列表）
 *   传 null 则跳过 reaction 解析。
 * @param renderParamCallback(key, value) 每个渲染参数的回调，例如：
 *   loadJson(json, atoms, bonds, annotations) { key, v ->
 *       when (key) {
 *           "strokeWidth" -> STROKE_WIDTH = v
 *           "fontSize" -> FONT_SIZE = v
 *           ...
 *       }
 *   }
 *   传 null 则不回传任何渲染参数。
 */
fun loadJson(
    json: String,
    atoms: MutableList<MoleculeAtom>,
    bonds: MutableList<MoleculeBond>,
    annotations: MutableList<MoleculeAnnotation>,
    reactionCallback: ((reactions: List<ReactionEntry>) -> Unit)? = null,
    renderParamCallback: ((key: String, value: Float) -> Unit)? = null,
    stringParamCallback: ((key: String, value: String) -> Unit)? = null
) {
    atoms.clear(); bonds.clear(); annotations.clear()
    try {
        val lines = json.lines()
        var section = ""
        val tempAtoms = mutableListOf<MoleculeAtom>()
        val tempBonds = mutableListOf<MoleculeBond>()
        val tempAnnotations = mutableListOf<MoleculeAnnotation>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("\"atoms\"")) { section = "atoms"; continue }
            if (trimmed.startsWith("\"bonds\"")) { section = "bonds"; continue }
            if (trimmed.startsWith("\"annotations\"")) { section = "annotations"; continue }
            if (trimmed.startsWith("\"reaction\"")) { section = "reaction"; continue }
            if (trimmed.startsWith("\"bondLength\"")) {
                val v = trimmed.substringAfter(":").substringBefore(",").substringBefore("}").trim()
                v.toFloatOrNull()?.let { renderParamCallback?.invoke("bondLength", it) }
            }
            // 渲染参数
            for (key in listOf("strokeWidth", "fontSize", "annTextSize", "carbonDotR", "heteroCircleR", "arrowHeadSize")) {
                if (trimmed.startsWith("\"$key\"")) {
                    val v = trimmed.substringAfter(":").substringBefore(",").substringBefore("}").trim().toFloatOrNull()
                    if (v != null) renderParamCallback?.invoke(key, v)
                }
            }
            // 字符串参数（如 benzeneStyle）
            for (key in listOf("benzeneStyle")) {
                if (trimmed.startsWith("\"$key\"")) {
                    val v = extractJsonString(trimmed, "\"$key\"")
                    if (v != null) stringParamCallback?.invoke(key, v)
                }
            }

            when (section) {
                "atoms" -> {
                    if (trimmed.startsWith("{")) {
                        val id = extractJsonInt(trimmed, "\"id\"").toInt()
                        val x = extractJsonFloat(trimmed, "\"x\"")
                        val y = extractJsonFloat(trimmed, "\"y\"")
                        val elemStr = extractJsonString(trimmed, "\"element\"")
                        val elem = elemStr?.let { runCatching { Element.valueOf(it) }.getOrNull() } ?: Element.C
                        val aromatic = extractJsonString(trimmed, "\"aromatic\"")?.toBooleanStrictOrNull() ?: false
                        val chiral = extractJsonString(trimmed, "\"chiral\"") ?: ""
                        val funGroupLabel = extractJsonString(trimmed, "\"funGroupLabel\"")?.takeIf { it.isNotEmpty() }
                        val isFunGroupConnector = extractJsonString(trimmed, "\"isFunGroupConnector\"")?.toBooleanStrictOrNull() ?: false
                        tempAtoms.add(MoleculeAtom(id, x, y, elem, aromatic, chiral, funGroupLabel, isFunGroupConnector))
                    }
                }
                "bonds" -> {
            if (trimmed.startsWith("{")) {
                val id = extractJsonInt(trimmed, "\"id\"").toInt()
                val a1 = extractJsonInt(trimmed, "\"a1\"").toInt()
                val a2 = extractJsonInt(trimmed, "\"a2\"").toInt()
                val typeStr = extractJsonString(trimmed, "\"type\"")
                val type = typeStr?.let { runCatching { BondType.valueOf(it) }.getOrNull() } ?: BondType.SINGLE
                tempBonds.add(MoleculeBond(id, a1, a2, type))
            }
        }
                "annotations" -> {
                    if (trimmed.startsWith("{")) {
                        val id = extractJsonInt(trimmed, "\"id\"").toInt()
                        val typeStr = extractJsonString(trimmed, "\"type\"")
                        val type = typeStr?.let { runCatching { AnnotationType.valueOf(it) }.getOrNull() } ?: continue
                        val x = extractJsonFloat(trimmed, "\"x\"")
                        val y = extractJsonFloat(trimmed, "\"y\"")
                        val text = extractJsonString(trimmed, "\"text\"") ?: ""
                        val endX = extractJsonFloat(trimmed, "\"endX\"")
                        val endY = extractJsonFloat(trimmed, "\"endY\"")
                        val scale = extractJsonFloat(trimmed, "\"scale\"")
                        val subScript = trimmed.contains("\"subScript\":true")
                        tempAnnotations.add(MoleculeAnnotation(id, type, x, y, text, endX, endY, if (scale != 0f) scale else 1f, subScript))
                    }
                }
            }
        }
        atoms.addAll(tempAtoms)
        bonds.addAll(tempBonds)
        annotations.addAll(tempAnnotations)
        // ── 解析 reaction 字段 ──
        if (reactionCallback != null) {
            val reactionJson = extractJsonBlock(json, "\"reaction\"")
            if (reactionJson != null) {
                val reactions = mutableListOf<ReactionEntry>()
                val reactLines = reactionJson.lines()
                var inEntry = false
                var currentReactants = mutableListOf<Pair<String, Double>>()
                var currentProducts = mutableListOf<Pair<String, Double>>()
                var currentConditions = ""
                var inReactants = false
                var inProducts = false
                for (rl in reactLines) {
                    val rt = rl.trim()
                    if (rt.startsWith("{")) { inEntry = true; currentReactants.clear(); currentProducts.clear(); currentConditions = ""; inReactants = false; inProducts = false }
                    if (rt.startsWith("\"reactants\"")) { inReactants = true; inProducts = false; continue }
                    if (rt.startsWith("\"products\"")) { inReactants = false; inProducts = true; continue }
                    if (rt.startsWith("\"conditions\"")) {
                        inReactants = false; inProducts = false
                        currentConditions = extractJsonString(rt, "\"conditions\"") ?: ""
                        continue
                    }
                    if (rt.startsWith("}")) {
                        if (inEntry) {
                            reactions.add(ReactionEntry(currentReactants.toList(), currentProducts.toList(), currentConditions))
                            inEntry = false; inReactants = false; inProducts = false
                        }
                        continue
                    }
                    if (inReactants && rt.startsWith("{")) {
                        val smiles = extractJsonString(rt, "\"smiles\"") ?: continue
                        val mw = extractJsonFloat(rt, "\"mw\"")
                        currentReactants.add(Pair(smiles, mw.toDouble()))
                    }
                    if (inProducts && rt.startsWith("{")) {
                        val smiles = extractJsonString(rt, "\"smiles\"") ?: continue
                        val mw = extractJsonFloat(rt, "\"mw\"")
                        currentProducts.add(Pair(smiles, mw.toDouble()))
                    }
                }
                if (reactions.isNotEmpty()) reactionCallback(reactions)
            }
        }
    } catch (e: Exception) {
        // 解析失败时保持列表为空
    }
}

// ── JSON 行内提取辅助 ──
private fun extractJsonInt(line: String, key: String): Int {
    val idx = line.indexOf(key)
    if (idx < 0) return 0
    val after = line.substring(idx + key.length)
    val numStart = after.indexOfAny("0123456789-".toCharArray())
    if (numStart < 0) return 0
    return after.substring(numStart).takeWhile { it.isDigit() || it == '-' }.toIntOrNull() ?: 0
}

private fun extractJsonFloat(line: String, key: String): Float {
    val idx = line.indexOf(key)
    if (idx < 0) return 0f
    val after = line.substring(idx + key.length)
    val numStart = after.indexOfAny("0123456789-.".toCharArray())
    if (numStart < 0) return 0f
    val numStr = after.substring(numStart).takeWhile { it.isDigit() || it == '-' || it == '.' }
    return numStr.toFloatOrNull() ?: 0f
}

private fun extractJsonString(line: String, key: String): String? {
    val idx = line.indexOf(key)
    if (idx < 0) return null
    val after = line.substring(idx + key.length)
    // 跳过 ": " 或 ":"
    val colonIdx = after.indexOf(':')
    if (colonIdx < 0) return null
    var quoteStart = after.indexOf('"', colonIdx)
    if (quoteStart < 0) return null
    quoteStart++ // 跳过开引号
    val quoteEnd = after.indexOf('"', quoteStart)
    if (quoteEnd < 0) return null
    return after.substring(quoteStart, quoteEnd)
}

/**
 * 从 JSON 中提取指定 key 对应的值块（花括号包围的整个对象）。
 * 例如 extractJsonBlock(json, "\"reaction\"") 返回 "reaction": { ... } 的花括号内容。
 */
private fun extractJsonBlock(json: String, key: String): String? {
    val keyIdx = json.indexOf(key)
    if (keyIdx < 0) return null
    val afterKey = json.substring(keyIdx + key.length)
    // 找到第一个 '{'
    val braceStart = afterKey.indexOf('{')
    if (braceStart < 0) return null
    var depth = 0
    var start = -1
    for (i in braceStart until afterKey.length) {
        when (afterKey[i]) {
            '{' -> { if (depth == 0) start = i; depth++ }
            '}' -> { depth--; if (depth == 0 && start >= 0) return afterKey.substring(start, i + 1) }
        }
    }
    return null
}
