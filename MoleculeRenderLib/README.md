# MoleculeRenderLib

一个独立的 Android 分子结构渲染库，基于 Jetpack Compose `DrawScope` 扩展函数。

## 极简用法

```kotlin
import com.moldraw.render.*

// ── 组装分子数据 ──
val atoms = mutableListOf<MoleculeAtom>()
val bonds = mutableListOf<MoleculeBond>()
val annotations = mutableListOf<MoleculeAnnotation>()

// 1. 画苯环
atoms.addRings(6, 100f, 100f, BOND_LENGTH, benzene = true)  // 加 6 个 C

// ★ 2. 加官能团：一行搞定！
val no2 = FuncGroupManager.create("NO₂", 200f, 100f, 
    nextAtomId = nextId, nextBondId = nextId, nextAnnId = nextId)!!
atoms.addAll(no2.atoms)
bonds.addAll(no2.bonds)
annotations.add(no2.annotation)

// 3. 连接苯环和官能团
bonds.add(MoleculeBond(nextId(), 6, no2.atoms[0].id, BondType.SINGLE))

// ── 渲染 ──
Canvas(modifier = Modifier.fillMaxSize()) {
    drawCanvasContent(atoms, bonds, annotations, benzeneStyle = BenzeneStyle.PAULING)
}
```

## FuncGroupManager API

```kotlin
// 在 (200, 100) 位置创建一个 NO₂ 官能团
val result = FuncGroupManager.create("NO₂", 200f, 100f)

// result 包含：
result.atoms       // [N(funGroupLabel="NO₂", connector=true), O(...), O(...)]
result.bonds       // [N=O DOUBLE, N=O DOUBLE]
result.annotation  // FUNC_GROUP标注，text="NO₂"

// 用中文名：
FuncGroupManager.createByName("硝基", 200f, 100f)

// 替换已有原子（自动重连邻居）：
val result = FuncGroupManager.replace("NO₂", targetAtom, neighborIds)
```

支持的所有官能团（`FUNCTIONAL_GROUPS`）：

| 名称 | 标签 | 展开原子 |
|------|------|---------|
| 硝基 | NO₂ | N, O, O |
| 甲基 | CH₃ | C, H, H, H |
| 乙基 | C₂H₅ | C, C, H×5 |
| 羟基 | OH | O, H |
| 羰基 | C=O | C, O |
| 羧基 | COOH | C, O, O, H |
| 氨基 | NH₂ | N, H, H |
| 苯基 | Ph | C×6 |
| 醛基 | CHO | C, O, H |
| 磺酸基 | SO₃H | S, O, O, O, H |
| 氰基 | CN | C, N |

## 也支持直接调渲染函数

```kotlin
drawBond(a1, a2, BondType.DOUBLE)         // 画键
drawAtom(atom, bonds)                      // 画原子（自动跳过组内原子）
drawAnnotationText(ann)                    // 画文字标注
drawAnnotationArrow(ann)                   // 画箭头
drawPaulingCircle(vertices)                // 苯环内切圆
drawCanvasContent(atoms, bonds, annotations) // 全画布
```

## 文件清单

只需拷贝 `render/` 目录的三个文件：

```
render/
├── Model.kt            # 数据模型 + 常量 + calcImplicitH
├── Renderer.kt         # 所有 DrawScope 扩展渲染函数
└── FuncGroupManager.kt # 官能团管理器（一行创建官能团）
```