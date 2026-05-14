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