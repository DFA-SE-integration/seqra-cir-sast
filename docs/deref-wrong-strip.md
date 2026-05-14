MethodAnalyzerEdgeSearcher.findMatchingEdgesInitialFacts запрашивает индекс не по самому стейтменту, а через possiblePreconditionAtStatement → factsForPrecondition(predecessor, fact). Если предшественник — call (free(var(40))), и его precondition возвращает Unchanged, факты для текущего stmt не добавляются и collectMatchingInitialFacts(stmt=id=42, …) не вызывается. В нашем кейсе:

factBase=var(0) (alloca-слот),
argBase=var(40) → канонически var(39) (т.к. bitcast прозрачен), и var(39) = *var(0) (load),
в CIRMethodCallFactMapper.factIsRelevantToMethodCall все проверки возвращают false:
argBase==factBase — нет;
basesAliasSymmetric(var(39), var(0)) — нет;
derefAlias(factBase=var(0), argBase=var(39)) — нет (var(0) ниоткуда не загружается, это alloca);
pointerDerivedFromSameLoadedSlotAsAddress — нет;
zeroStridePtrStrideRhsBase — нет.
Не хватало симметричной проверки обратного направления (argBase загружается из factBase). derefAliasesOf(var(39)) корректно возвращает [Access(var(0), ReferenceAccessor)], но эта связь не используется в relevance-проверке. Прямой forward-bridge для этого случая в mapMethodCallToStartFlowFactImpl не нужен (вызываемая функция free — внешняя и слот ей не доступен), достаточно того, что трасса перестанет обрезаться.

Применённое исправление
Файл: seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/CIRMethodCallFactMapper.kt


CIRMethodCallFactMapper.kt
Lines 313-342
            // Reverse direction: argument is loaded from the fact's slot (e.g. `free(load(slot))`).
            // Forward analysis materializes the call's effect on the slot via alias propagation, so the
            // IFDS index keeps facts on the slot at this call. Backward trace resolution must treat the
            // call as relevant for those slot-based facts so the precondition is not collapsed to
            // [CallPrecondition.Unchanged] and the index at the call's predecessor gets queried.
            if (aa?.derefAlias(argBase, factBase) == true) {
                return true
            }
            if (aa?.pointerDerivedFromSameLoadedSlotAsAddress(factBase, argBase) == true) {
                return true
            }
            if (MethodFlowFunctionUtils.zeroStridePtrStrideRhsBase(callExpr.location.method, arg) == factBase) {
                return true
            }
        }
        if (returnValue != null) {
            val retValBase = accessPathBaseForCallArg(aa, returnValue)
            if (retValBase == factBase) {
                return true
            }
            if (retValBase != null && aa?.basesAliasSymmetric(retValBase, factBase) == true) {
                return true
            }
            if (retValBase != null && aa?.derefAlias(factBase, retValBase) == true) {
                return true
            }
            if (retValBase != null && aa?.derefAlias(retValBase, factBase) == true) {
                return true
            }
        }
Аналогичную обратную проверку добавил и для returnValue, чтобы покрыть lhs = call(...) где lhs — это slot/dest-loaded value.

Безопасность для forward-анализа
factIsRelevantToMethodCall также используется в CIRMethodCallFlowFunction (строка 164). Расширение relevance:

applySinkRules — для free нет sink-правил (free — это source), так что эффекта нет;
applySourceRules — emits source-факты независимо от входного факта; могут продублировать одну и ту же эмиссию, IFDS дедуплицирует;
mapMethodCallToStartFlowFact — обратная ветвь (derefAlias(argBase, factBase)) в forward-bridge не добавлялась, поэтому факт var(0)!mark не пропихивается внутрь free (что корректно — слот alloca недоступен callee).
Существующие unit-тесты factIsRelevantToMethodCall (CIRCallPositionResolverTest.kt:496,571,622) передают aa=null либо имеют свои стабы, и моя проверка aa?.derefAlias(...) == true для null-aa возвращает false → семантика для тестов сохранилась.

Что ожидать в следующем прогоне с SEQRA_TRACE_DEBUG=1
[TR] empty trace-edge на id=42 для _01_bad должен исчезнуть.
[ES] должен показать новый storedFact=var(0)!mark.$.*/* со z2fMatch≥1 для варианта var(0).&!mark.$.*/*.
[EP] synthetic fallback fired — на этом коммите ещё может остаться, потому что отсутствие fallback полагается также на H5/H1; но startNodes должно стать >0, и visitedEp>0, что означает, что путь до entry-point достроился через Precond/CallToStart.
Если после прогона [EP] synthetic fallback пропадёт сам по себе для всех 8 кейсов — переходим к удалению fallback'а (h3-remove-fallback). Если не пропадёт — посмотрим новый лог: симптом сместится либо в H5 (propagateCall пропускает Call2Start при матче source-правила), либо в H4 (вариация target-pattern'ов). Жду новый seqra-trace.log после ребилда.

## Прогон 2 — relevance-fix недостаточно

После применения relevance-fix-а получили:
- `[TR] empty trace-edge` исчез — поиск находит `Z2F`.
- `[ES] z2f=1(match=1)` для `.&`-variant — совпадает с индексом.
- НО `[EP] startNodes=0` остался для всех 8 кейсов, synthetic fallback по-прежнему срабатывает.

Добавил [CE]/[PE]/[CP] инструменты:
- `[CE] reject src stmt=id=42 fact=var(0)!mark.$.*/* sample=var(0).&!mark.$` — `containsEntryEdge` строгий по форме AP.
- `[CE] reject src stmt=id=43 fact=var(42)!mark.$.*/* sample=var(42).&!mark.$` — то же на printLine sink.
- `[CP] preconditionForFact fact=var(0)!mark.$.*/* mappingsFired=0 preconditions=0 callee=free` — `mapMethodCallToStartFlowFact` для slot-факта (без `.&`) не фитит ни одной ветки, поэтому source-правило free не распознаётся бэквард.
- `[PE] bail call/seq` — выход на пустых `callActions`/`actions`, потому что
  1. `preconditionFacts=[]` для `var(0)!mark` (mapping не фитит), и
  2. для `.&`-варианта нет `PreconditionFactsForInitialFact` записи (фактPrecondition не enumerates `.&`-варианты).

## Применённые фиксы (раунд 2)

### B. Обратный deref-bridge в `mapMethodCallToStartFlowFactImpl`

Файл: `seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/CIRMethodCallFactMapper.kt`

Добавил зеркало существующей forward-ветки (`derefAlias(factBase, argBase) -> prepend(.&)`):
```kotlin
aa != null && aa.derefAlias(argBase, factBase) && factOk.startsWithAccessor(ReferenceAccessor) -> {
    // factBase=slot, argBase=loaded-from-slot.
    // var(slot).&!mark = "value at slot tainted" = Argument(i)!mark.
    // Strip .& and map to Argument(i).
    val stripped = factOk.readAccessor(ReferenceAccessor) as? F ?: ...
    onMappedFact(stripped, AccessPathBase.Argument(i))
}
```

Семантика: симметрично forward-бриджу. Для forward-IFDS даёт дополнительную точку propagation для slot-фактов с `.&` — alias-propagation уже даёт эквивалентные факты, так что соундность не страдает (только лишний путь генерации того же edge).

### C. Variant-widening в `factPrecondition` (Call + Sequent)

Файлы:
- `seqra-cir-dataflow/.../analysis/CIRMethodCallPrecondition.kt`
- `seqra-cir-dataflow/.../analysis/CIRMethodSequentPrecondition.kt`

Зеркало `MethodTraceResolver.traceResolutionTargetPatterns`: дополнительно к самому `fact` пробуем `.&`-prepended/readAccessor(.&) и `[]`-prepended/readAccessor([]) варианты. Каждый успешный вариант добавляется отдельным `PreconditionFactsForInitialFact`.

Это нужно потому, что `containsEntryEdge` сравнивает строго по форме AP против индекса. Если индекс хранит `var(p).&!mark.$`, а target — `var(p)!mark.$.*/*`, нужно явно подсунуть `.&`-вариант, чтобы initialEdge прошёл строгий `.contains`.

### Цепочка после фиксов A+B+C

Для `_01_bad`:
1. Sink-search at id=43 (printLine) → `SourceTraceEdge(var(42)!mark.$.*/*)`.
2. propagateEntryNew sequent at id=42 (load `var(42) = MLIRValueRef(var(0))`):
   - With C: пробуем `var(42).&!mark` вариант → simpleAssign → preconditionFacts=[var(0).&!mark].
   - containsEntryEdge для `SourceTraceEdge(var(42).&!mark)` строго проходит против `var(42).&!mark.$` в индексе.
   - Sequential action создан → новая edge `SourceTraceEdge(var(0).&!mark)`.
3. propagateEntryNew call at id=41 (free) с `var(0).&!mark`:
   - With B: новая ветка mapping срабатывает (derefAlias(var(39), var(0)) и `.&` префикс), strip `.&` → Argument(0)!mark.
   - factSourceRulePrecondition: rebase к Argument(0)!mark, match free's source rule → `Source(rule, action)`.
   - preconditionFacts = [CallToReturnTaintRule(Source), CallToStart].
   - containsEntryEdge для `SourceTraceEdge(var(0).&!mark)` строго проходит.
   - propagateCall fires → CallSourceRule + Call2Start.
4. addPredecessorAction → tryCreateSourceStart → `SourceStartEntry` создан.
5. resolveTrace.fullTrace returns FullTrace with `SourceStartEntry`.
6. InterProceduralTraceGraphBuilder: rootNodes.add(node) → startNodes>0.
7. EntryPointToStartTraceBuilder: walks back to entryPoint method → entryPointNodes populated → НЕТ synthetic fallback.

## Что ожидать в следующем прогоне (раунд 3)

- `[CE] reject` count должен сильно упасть (или 0 для основных кейсов).
- `[PE] bail` для основных кейсов должен исчезнуть.
- `[CP] preconditionForFact` теперь должен показывать `preconditions>0` для slot-фактов с `.&` вариантом (forward-bridge fired backward через новую ветку).
- `[EP] startNodes>0`, `entryPointNodes>0` — entry-point trace соберётся корректно.
- `[EP] synthetic fallback fired` должен пропасть для всех 8 `*_bad` кейсов.

## Прогон 3 — A+B+C+инструменты [CE/PE/CP]: synthetic fallback пропал у большинства кейсов, но 8 ещё остаются

Лог `seqra-trace.log` (~30k строк, 16:49 UTC):

- `[EP] startNodes=1, visitedEp=1, entryPointNodes=1` теперь стало нормой. Большинство `*_bad` кейсов трасса собирается.
- Осталось 8 кейсов с `[EP] synthetic fallback fired`:
  - `_17_bad` (loop control flow, char)
  - `_433badEv` (C++ mangled, char)
  - `_623badEv` (C++ mangled, char)
  - `int64_t_01_bad`, `int64_t_02_bad`, `int64_t_03_bad`
- Анализ `[CE] reject` для оставшихся показывает 2 разных pattern'а:

### Паттерн 1: индекс хранит более глубокую форму, чем widening даёт
`int64_t_01_bad`: query = `var(52)[*]!mark`, IFDS sample = `var(52).&[*]!mark`. Variant widening C даёт `var(52)[*]!mark`, `var(52).&[*]!mark`, `var(52)[*][*]!mark`, `var(52)!mark` — всё депт-1 от query. Но `containsEntryEdge` сейчас делает `statementFact.contains(entryEdge.fact)` (а не через `traceResolutionMatchFact`), поэтому даже когда варианты есть в widening C, `containsEntryEdge` тестирует только сырой `entryEdge.fact`.

### Паттерн 2: индекс хранит более простую форму, чем query
`_433badEv` / `_623badEv`: query = `var(4).&!mark`, IFDS sample = `var(4)!mark`. Тоже мимо `.contains` — `var(4)!mark` не содержит путь `var(4).&!mark` (это другой access-path).

### Паттерн 3: индекс полностью пуст на entry-стейтменте (только `_17_bad`)
`indexSize=0 sample=null` на `BrOp(id=71)` (после printLine, в loop-control). Сюда trace builder доходит через sequent-Unchanged ветку (BrOp = sequent с unchanged precondition), который не валидирует факт через индекс при создании entry. → "ghost entry". Fix D не помогает; нужно отдельное решение (валидировать факт-existence на Unchanged-sequent шаге).

## Применённые фиксы (раунд 3)

### D. `containsEntryEdge` использует `traceResolutionMatchFact` (depth-1 widening)

Файл: `seqra-dataflow-core/seqra-dataflow/src/main/kotlin/org/seqra/dataflow/ap/ifds/trace/MethodTraceResolver.kt`

Замена:
```kotlin
val ok = entryFacts.any { statementFact -> statementFact.contains(entryEdge.fact) }
```
на
```kotlin
val ok = entryFacts.any { statementFact -> traceResolutionMatchFact(statementFact, entryEdge.fact) }
```

`traceResolutionMatchFact` уже используется в `resolveIntraProceduralTraceEdge.matchFact` и пробует `traceResolutionTargetPatterns` (5 вариантов: target, prepend(.&), read(.&), prepend([]), read([])). Это точно та же ширина, которую использует sink-search, поэтому `containsEntryEdge` теперь толерантен к тому же набору shape-mismatch'ов.

Покрывает паттерны 1 и 2:
- `int64_t_01_bad`: query `var(52)[*]!mark` → widening `[var(52)[*]!mark, var(52).&[*]!mark, var(52)[*][*]!mark, var(52)!mark]`. Среди них `var(52).&[*]!mark` совпадает с IFDS-sample. Match!
- `_433badEv`: query `var(4).&!mark` → widening `[var(4).&!mark, var(4).&.&!mark, var(4)!mark, var(4).&[*]!mark]`. Среди них `var(4)!mark` совпадает с IFDS-sample. Match!

## Что ожидать в следующем прогоне (раунд 4)

- `[CE] reject` count должен ещё сильно упасть (закроется паттерн 1 и 2).
- Кейсы `int64_t_01_bad/02/03`, `_433badEv`, `_623badEv` должны собрать `startNodes>=1` и не firit'ь synthetic fallback.
- Останется только `_17_bad` (loop control + ghost entry на BrOp). Если только он один — переходим к h-fix-loop-ghost (валидация Unchanged-sequent предка через индекс) или принимаем pragmatic solution (оставить fallback только для loop-cases).
- Если synthetic fallback пропадёт у всех 8 — переходим к h3-remove-fallback и тестам.

## Прогон 4 — A+B+C+D, расширенная test-scope (5 типов)

После Fix D тестовый scope расширился с `char` (1 тип) до 5 типов: `char`, `int`, `int64_t`, `long`, `struct`. Большинство `*_bad` кейсов работает.

Остаются 14 fallback'ов, кластеризуются в **3 паттерна**:

| Паттерн | char | int | int64_t | long | struct | total |
|---|---|---|---|---|---|---|
| `_17_bad` (loop control) | 1 | 2 | 2 | 2 | 1 | 8 |
| `_43_bad` (badSource fn callout, `data[0]` ptrStride) | ✓ FIXED | 1 | 1 | 1 | — | 3 |
| `_62_bad` (cross-file split) | ✓ FIXED | 1 | 1 | 1 | — | 3 |

**Ключевая инсайт**: char/struct — single-byte/single-element access, не требуют `ptrStride`. int/int64_t/long — multi-byte, генерят `data[0]` → `ptrStride(base, 0); load(via ptrStride)`. Forward IFDS sparse-индексирует факты, и через цепочку `ptrStride+load` факт не сохраняется в индексе на промежуточных стейтментах. То же касается `BrOp(id=71)` для `_17_bad`: forward IFDS не хранит факт на этом транзитном control-flow op.

### Архитектурный инсайт

`MethodAnalyzerEdges.add` сохраняет факты ТОЛЬКО на тех стейтментах, где forward IFDS явно создал edge. Транзитные op'ы (BrOp, простые присваивания, ptrStride) могут не иметь edges → пустой индекс. `MethodTraceResolver.containsEntryEdge` делал прямой 1-statement query → возвращал empty → синтетический fallback.

`MethodAnalyzerEdgeSearcher.findMatchingEdgesInitialFacts` (используется в sink-trace search) **уже** делает smart walk через preconditions, но он недоступен для `containsEntryEdge` (там другая семантика — нужно проверить TraceEdge с initialFact constraint).

## Применённые фиксы (раунд 4)

### E. Материализация Unchanged-edges в forward IFDS

Файл: `seqra-dataflow-core/seqra-dataflow/src/main/kotlin/org/seqra/dataflow/ap/ifds/MethodAnalyzer.kt` → `propagateEdgeToSuccessors`.

**Корневая причина sparse-индексации** найдена в forward IFDS:

```kotlin
private fun propagateEdgeToSuccessors(edge: Edge, edgeUnchanged: Boolean) {
    methodInstGraph.forEachSuccessor(analysisManager, edge.statement) {
        val nextEdge = edge.replaceStatement(it)
        if (!edgeUnchanged) {
            addSequentialEdge(nextEdge)  // adds to index AND enqueues
        } else {
            if (enqueuedUnchangedEdges.add(nextEdge)) {
                enqueueNewEdge(nextEdge)  // ONLY enqueues, NOT added to index!
            }
        }
    }
}
```

Когда sequent precondition возвращает `Unchanged` (или call-edge не меняет факт), edge **только enqueue'ится** на дальнейшую обработку, но **не добавляется в `edges` индекс**. Это и есть причина: транзитные op'ы (`BrOp`, простые assigns, `ptrStride` chain) не материализованы в индексе.

#### Применённый фикс

Добавлен `edges.add(nextEdge)` в `else`-ветку:

```kotlin
} else {
    edges.add(nextEdge)  // materialize in index for trace verification (idempotent)
    if (enqueuedUnchangedEdges.add(nextEdge)) {
        enqueueNewEdge(nextEdge)
    }
}
```

`edges.add` идемпотентна (возвращает пустой список для дубликатов), так что вызов всегда безопасен. `enqueuedUnchangedEdges` дедупликация для enqueue сохранена — нет регрессии в количестве шагов.

#### Преимущества по сравнению с walk-back в trace builder

- **Не модифицирует `MethodTraceResolver`** (структурный фикс, а не симптоматический).
- Покрывает **все** sparse-index случаи разом: `BrOp`/transit/`ptrStride+load`/Unchanged-call.
- Trace builder работает без изменений — `containsEntryEdge` остаётся в Fix D-варианте (`traceResolutionMatchFact` widening).

#### Возможный downside

- Размер индекса вырастет: для каждого факта, который пассирует через ряд транзитных op'ов, индекс получит запись на каждом из них. Это контролируемо: рост ограничен количеством stmt × кол-во facts × (длина unchanged-цепочки).
- Memory tradeoff: при больших методах с длинными цепочками без модификаций, индекс может стать заметно больше.
- Если профиль покажет регрессию — можно гибридизировать (материализовать только на CFG-merge points, blockTerminators).

## Что ожидать в следующем прогоне (раунд 4 → 5)

- `[CE] reject` count должен сильно упасть (или исчезнуть) для всех 14 fallback кейсов.
- Кейсы `_17_bad`, `_43_bad`, `_62_bad` (для всех 5 типов) должны собрать `startNodes>=1` и не firit'ь synthetic fallback.
- `[EP] synthetic fallback fired` должен сократиться с 14 до 0 (или близко к 0).
- Возможно, потребуется проверить размер индекса/память (для дебага) — но это уже tradeoff.

Если что-то осталось — посмотрим, не помог ли фикс из-за более глубокой проблемы (например, fact-shape mismatch на definition-points).