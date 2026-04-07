---
name: Rename jacodb seqra-ir
overview: "Переименовать все вхождения `jacodb` в модуле `seqra-ir-core-cir`: переместить исходники в правильную папку, переименовать функцию-фабрику, обновить SPI, .cir-фикстуры и документацию."
todos:
  - id: move-folder
    content: Переместить все файлы из src/main/kotlin/org/jacodb/impl/ в src/main/kotlin/org/seqra/ir/impl/
    status: completed
  - id: rename-factory
    content: Переименовать jacodb.kt → cirDatabase.kt и функции fun jacodb(...) → fun cirDatabase(...)
    status: completed
  - id: update-tests
    content: Обновить import и вызовы jacodb(...) → cirDatabase(...) в 7 тестовых файлах
    status: completed
  - id: spi
    content: Переименовать SPI-файл META-INF/services/org.jacodb.impl.* → org.seqra.ir.impl.* и задокументировать в AGENTS.md
    status: completed
  - id: cir-fixtures
    content: "Обновить пути в 5 .cir-фикстурах: заменить /home/sergey и /Users/sergey пути на /Users/z.dmitriy/seqra-cir/seqra-ir/seqra-ir-core-cir/"
    status: completed
  - id: readme
    content: Обновить/создать README модуля seqra-ir-core-cir с описанием переименования
    status: completed
  - id: report
    content: Добавить секцию с отчётом об изменениях в seqra-cb2989d-presentation.md
    status: completed
isProject: false
---

# Rename `jacodb` → `cirDatabase` / `seqra-ir-core-cir` в `seqra-ir-core-cir`

**seqra-ir-api-cir:** вхождений нет, изменений не требует.

## Шаг 1 — Переместить исходники в правильную папку

`src/main/kotlin/org/jacodb/impl/` → `src/main/kotlin/org/seqra/ir/impl/`

Package-декларации уже `org.seqra.ir.impl`, папка просто не совпадает.

## Шаг 2 — Переименовать фабричный файл и функции

- `jacodb.kt` → `cirDatabase.kt`
- `fun jacodb(builder: CIRSettings.() -> Unit)` → `fun cirDatabase(builder: CIRSettings.() -> Unit)`
- `fun jacodb(settings: CIRSettings)` → `fun cirDatabase(settings: CIRSettings)`

## Шаг 3 — Обновить тестовые файлы (7 файлов)

Заменить:
- `import org.seqra.ir.impl.jacodb` → `import org.seqra.ir.impl.cirDatabase`
- все вызовы `jacodb(...)` → `cirDatabase(...)`

Файлы:
- `src/testFixtures/kotlin/StorageImpl.kt`
- `src/test/kotlin/SyntheticStorageTest.kt`
- `src/test/kotlin/StorageTestWithLinkCommands.kt`
- `src/test/kotlin/ModuleTest.kt`
- `src/test/kotlin/JulietTest.kt`
- `src/test/kotlin/DurabilityTest.kt`
- `src/test/kotlin/DatabaseTest.kt`

## Шаг 4 — SPI-дескриптор

Переименовать файл:
`META-INF/services/org.jacodb.impl.CIRDatabasePersistenceSPI`
→ `META-INF/services/org.seqra.ir.impl.CIRDatabasePersistenceSPI`

Содержимое файла (`org.seqra.ir.impl.storage.ers.CIRErsDatabasePersistenceSPI`) не меняется.

Задокументировать в `seqra-ir/seqra-ir-core-cir/AGENTS.md` (создать, если не существует):
> SPI-интерфейс находится в пакете `org.seqra.ir.impl`, дескриптор — `META-INF/services/org.seqra.ir.impl.CIRDatabasePersistenceSPI`.

## Шаг 5 — .cir-фикстуры (5 файлов)

Заменить захардкоженные пути от чужих машин на текущие:

- `/home/sergey/Documents/jacodb/jacodb-core-cir/` → `/Users/z.dmitriy/seqra-cir/seqra-ir/seqra-ir-core-cir/`
- `/Users/sergey/Documents/jacodb/jacodb-core-cir/` → `/Users/z.dmitriy/seqra-cir/seqra-ir/seqra-ir-core-cir/`

Файлы:
- `src/test/resources/failingParsingTest/helperModule.cir`
- `src/test/resources/doubleModuleWithLinkCommandsTypes/mainModule.cir`
- `src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.cir`
- `src/test/resources/doubleModuleWithLinkCommands/mainModule.cir`
- `src/test/resources/doubleModuleWithLinkCommands/helperModule.cir`

## Шаг 6 — README модуля

Обновить или создать `seqra-ir/seqra-ir-core-cir/README.md` с секцией:
> **Переименование (jacodb → seqra-ir-core-cir):** фабричная функция была `jacodb()`, теперь `cirDatabase()`; SPI переименован; пути в .cir-фикстурах обновлены под `/Users/z.dmitriy/seqra-cir/...`.

## Шаг 7 — Отчёт

Добавить секцию в `seqra-cb2989d-presentation.md` (или отдельный `.md`) с итогами переименования: что переименовано, какие файлы затронуты.