package com.moldraw.render

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 化合物信息查询结果
 */
data class CompoundInfo(
    val cid: Int = 0,
    /** 英文名 */
    val englishName: String = "",
    /** 中文名（可能为空） */
    val chineseName: String = "",
    /** CAS 登记号（可能为空） */
    val cas: String = "",
    /** 分子式 */
    val molecularFormula: String = "",
    /** 分子量 */
    val molecularWeight: String = "",
    /** 密度（g/cm³，可能为空） */
    val density: String = "",
    /** 熔点（可能为空） */
    val meltingPoint: String = "",
    /** 沸点（可能为空） */
    val boilingPoint: String = "",
    /** SMILES */
    val smiles: String = "",
    /** IUPAC 名 */
    val iupacName: String = ""
)

/**
 * 分子/化合物信息查询模块。
 * 使用 PubChem REST API（无需 API Key）。
 *
 * 用法：
 * ```kotlin
 * val info = MoleculeLookup.query("benzene")
 * val info = MoleculeLookup.query("C6H6")
 * ```
 */
object MoleculeLookup {

    private const val TIMEOUT_MS = 8000

    /**
     * 通过名称或分子式查询化合物信息。
     * @param query 化合物名称、分子式或 CAS 号
     * @return CompoundInfo（失败时各字段为空字符串）
     */
    fun query(query: String): CompoundInfo {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        if (encoded.isEmpty()) return CompoundInfo()

        // 第1步：通过 name 获取 CID 和基础属性
        val cid = getCID(encoded) ?: return CompoundInfo()
        if (cid <= 0) return CompoundInfo()

        // 第2步：获取属性（分子式、分子量、SMILES、IUPAC名）
        val props = fetchProperties(cid)
        // 第3步：获取同义词（英文名、中文名、CAS）
        val synonyms = fetchSynonyms(cid)
        // 第4步：获取物性（密度、熔点、沸点）
        val physProps = fetchPhysicalProperties(cid)

        return CompoundInfo(
            cid = cid,
            englishName = synonyms.englishName,
            chineseName = synonyms.chineseName,
            cas = synonyms.cas,
            molecularFormula = props.molecularFormula,
            molecularWeight = props.molecularWeight,
            density = physProps.density,
            meltingPoint = physProps.meltingPoint,
            boilingPoint = physProps.boilingPoint,
            smiles = props.smiles,
            iupacName = props.iupacName
        )
    }

    // ── 第1步：获取 CID ──

    private fun getCID(encoded: String): Int? {
        return try {
            val url = URL("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/$encoded/JSON?record_type=3")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val body = readResponse(conn)
            conn.disconnect()

            // 从 JSON 中提取 CID：查找 "cid": 数字
            val cidMarker = "\"cid\":"
            val idx = body.indexOf(cidMarker)
            if (idx < 0) return null
            val after = body.substring(idx + cidMarker.length)
            val numStr = after.trimStart().takeWhile { it.isDigit() || it == '-' }
            numStr.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    // ── 第2步：获取属性 ──

    private data class Props(
        val molecularFormula: String = "",
        val molecularWeight: String = "",
        val smiles: String = "",
        val iupacName: String = ""
    )

    private fun fetchProperties(cid: Int): Props {
        return try {
            val url = URL("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/property/MolecularFormula,MolecularWeight,CanonicalSMILES,IUPACName/JSON")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val body = readResponse(conn)
            conn.disconnect()

            Props(
                molecularFormula = extractJsonProperty(body, "MolecularFormula"),
                molecularWeight = extractJsonProperty(body, "MolecularWeight"),
                smiles = extractJsonProperty(body, "CanonicalSMILES"),
                iupacName = extractJsonProperty(body, "IUPACName")
            )
        } catch (_: Exception) {
            Props()
        }
    }

    // ── 第3步：获取同义词 ──

    private data class Synonyms(
        val englishName: String = "",
        val chineseName: String = "",
        val cas: String = ""
    )

    private fun fetchSynonyms(cid: Int): Synonyms {
        return try {
            val url = URL("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/synonyms/JSON")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val body = readResponse(conn)
            conn.disconnect()

            // 从 JSON 数组里提取同义词
            val all = extractStringArray(body)
            var enName = ""
            var cnName = ""
            var cas = ""

            // 优先取第一个作为英文名（PubChem 通常把主要名称放前面）
            if (all.isNotEmpty()) enName = all[0]

            // 找中文名：包含中文汉字
            for (s in all) {
                if (s.contains(Regex("[\\u4e00-\\u9fff]")) && cnName.isEmpty()) {
                    cnName = s
                }
                // 找 CAS 号：格式如 71-43-2
                if (cas.isEmpty() && s.matches(Regex("\\d{1,7}-\\d{2}-\\d"))) {
                    cas = s
                }
            }

            Synonyms(englishName = enName, chineseName = cnName, cas = cas)
        } catch (_: Exception) {
            Synonyms()
        }
    }

    // ── 第4步：获取物性（密度、熔点、沸点）──

    private data class PhysProps(
        val density: String = "",
        val meltingPoint: String = "",
        val boilingPoint: String = ""
    )

    private fun fetchPhysicalProperties(cid: Int): PhysProps {
        var density = ""
        var meltingPoint = ""
        var boilingPoint = ""

        try {
            // 用 pug_view 获取"Experimental Properties"下的物性数据
            val url = URL("https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/data/compound/$cid/JSON/?heading=Experimental+Properties")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val body = readResponse(conn)
            conn.disconnect()

            // 物性数据在 Record.Section[].Section[].Section[] 的层级里
            // 直接暴力搜索关键字段
            meltingPoint = extractFirstValueAfter(body, "\"Melting Point\"")
            boilingPoint = extractFirstValueAfter(body, "\"Boiling Point\"")
            density = extractFirstValueAfter(body, "\"Density\"")
        } catch (_: Exception) {
            // 忽略
        }

        return PhysProps(density, meltingPoint, boilingPoint)
    }

    // ── HTTP 响应读取 ──

    private fun readResponse(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream,
            "UTF-8"
        ))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append('\n')
        }
        reader.close()
        return sb.toString()
    }

    // ── JSON 提取辅助 ──

    /** 从 PubChem property 响应中提取指定属性的字符串值 */
    private fun extractJsonProperty(body: String, key: String): String {
        val marker = "\"$key\":"
        val idx = body.indexOf(marker)
        if (idx < 0) return ""
        val after = body.substring(idx + marker.length).trimStart()
        // 找引号内的值
        if (after.startsWith('"')) {
            val end = after.indexOf('"', 1)
            if (end < 0) return ""
            return after.substring(1, end)
        }
        return ""
    }

    /** 从 PubChem synonyms 响应中提取字符串数组 */
    private fun extractStringArray(body: String): List<String> {
        val result = mutableListOf<String>()
        val lines = body.lines()
        var inArray = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inArray = true
                continue
            }
            if (trimmed.startsWith("]")) break
            if (inArray && trimmed.startsWith('"')) {
                val end = trimmed.indexOf('"', 1)
                if (end > 0) {
                    result.add(trimmed.substring(1, end))
                }
            }
        }
        return result
    }

    /** 从 pug_view 响应中暴力提取 "TOCHeading":"xxx" 后面的第一个 String 值 */
    private fun extractFirstValueAfter(body: String, heading: String): String {
        val headingMarker = "\"TOCHeading\":\"$heading\""
        val idx = body.indexOf(headingMarker)
        if (idx < 0) return ""

        // 在 heading 之后找 "String":"..." 
        val searchFrom = idx + headingMarker.length
        val stringMarker = "\"String\":\""
        var searchPos = searchFrom
        while (true) {
            val si = body.indexOf(stringMarker, searchPos)
            if (si < 0 || si > searchFrom + 3000) break  // 只搜索附近
            val valStart = si + stringMarker.length
            val valEnd = body.indexOf('"', valStart)
            if (valEnd < 0) break
            val value = body.substring(valStart, valEnd)
            // 跳过参考号、°F 以外的温度单位等情况
            if (value.isNotEmpty() && !value.contains("NTP") && !value.startsWith("http")) {
                return value
            }
            searchPos = valEnd + 1
        }

        // 没找到，尝试找 "Value" 下面的 StringWithMarkup
        val valueMarker = "\"Value\":"
        val vi = body.indexOf(valueMarker, searchFrom)
        if (vi < 0 || vi > searchFrom + 3000) return ""
        val swmMarker = "\"String\":\""
        val swi = body.indexOf(swmMarker, vi)
        if (swi < 0) return ""
        val valStart = swi + swmMarker.length
        val valEnd = body.indexOf('"', valStart)
        if (valEnd < 0) return ""
        return body.substring(valStart, valEnd)
    }
}
