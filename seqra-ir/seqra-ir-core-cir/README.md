# seqra-ir-core-cir

Concrete CIR implementation module. Реализует загрузку и хранение CIR IR, CFG traversal, persistence-слой через ERS.

## Зависимости

- `seqra-ir-api-cir` — API-контракты CIR
- `seqra-ir-storage` — shared persistence layer

## Точка входа

```kotlin
import org.seqra.ir.impl.cirDatabase

// через DSL
val db = cirDatabase {
    persistent(location = "/path/to/db", implSettings = CIRXodusKvErsSettings)
}

// через settings
val db = cirDatabase(CIRSettings().apply {
    persistenceImpl(CIRXodusKvErsSettings)
})
```

## Структура исходников

```
src/main/kotlin/org/seqra/ir/impl/
  cirDatabase.kt          — фабричные функции cirDatabase()
  CIRDatabaseImpl.kt      — реализация CIRDatabase
  CIRSettings.kt          — настройки
  cfg/                    — реализация CFG (CIRGraphImpl, CIRInstListImpl)
  cfg/builder/            — builders для MLIR/CIR-парсинга
  features/               — расширения (LoadStore, FunctionPointerCall и др.)
  ir/                     — реализация IR-сущностей (CIRFunctionImpl и др.)
  sources/                — загрузчики исходников (RPC, BitCode)
  storage/                — persistence (ERS, Xodus)
  types/                  — реализация типов

src/main/resources/META-INF/services/
  org.seqra.ir.impl.CIRDatabasePersistenceSPI  — ServiceLoader SPI descriptor
```

## История переименований (jacodb → seqra-ir-core-cir)

| Было | Стало |
|------|-------|
| `src/main/kotlin/org/jacodb/impl/` | `src/main/kotlin/org/seqra/ir/impl/` |
| `jacodb.kt` | `cirDatabase.kt` |
| `fun jacodb(...)` | `fun cirDatabase(...)` |
| `import org.seqra.ir.impl.jacodb` | `import org.seqra.ir.impl.cirDatabase` |
| `META-INF/services/org.jacodb.impl.CIRDatabasePersistenceSPI` | `META-INF/services/org.seqra.ir.impl.CIRDatabasePersistenceSPI` |
| Пути в `.cir`-фикстурах: `/Users/sergey/Documents/jacodb/jacodb-core-cir/` | `/Users/z.dmitriy/seqra-cir/seqra-ir/seqra-ir-core-cir/` |
| Пути в `.cir`-фикстурах: `/home/sergey/Documents/jacodb/jacodb-core-cir/` | `/Users/z.dmitriy/seqra-cir/seqra-ir/seqra-ir-core-cir/` |

Модуль происходит из проекта JacoDB (jacodb-core-cir). Все внешние ссылки на старое имя приведены к текущему.
