# seqra-ir-core-cir — Agent Notes

## SPI-дескриптор

ServiceLoader-дескриптор для `CIRDatabasePersistenceSPI` находится по пути:

```
src/main/resources/META-INF/services/org.seqra.ir.impl.CIRDatabasePersistenceSPI
```

Имя файла должно совпадать с FQN интерфейса: `org.seqra.ir.impl.CIRDatabasePersistenceSPI`.

Зарегистрированная реализация: `org.seqra.ir.impl.storage.ers.CIRErsDatabasePersistenceSPI`.

> **История:** до переименования файл назывался `org.jacodb.impl.CIRDatabasePersistenceSPI` — устаревшее наименование из исходного проекта JacoDB.

## Фабричная функция

Точка входа для создания `CIRDatabase`:

```kotlin
// src/main/kotlin/org/seqra/ir/impl/cirDatabase.kt
fun cirDatabase(settings: CIRSettings): CIRDatabase
fun cirDatabase(builder: CIRSettings.() -> Unit): CIRDatabase
```

> **История:** функция ранее называлась `jacodb()` — переименована в `cirDatabase()`.

## Расположение исходников

Все исходники модуля лежат в `src/main/kotlin/org/seqra/ir/impl/`.

> **История:** ранее файлы лежали в `org/jacodb/impl/`, но package-декларации уже тогда были `org.seqra.ir.impl`. Папка приведена в соответствие с пакетом.
