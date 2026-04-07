# seqra-configuration-rules

## Роль на cb2989d

`seqra-configuration-rules` — это workspace со схемой и моделью правил конфигурации Seqra на коммите `cb2989d`. Его верхнеуровневый `settings.gradle.kts` разделяет работу на два публикуемых subproject: `configuration-rules-common` и `configuration-rules-jvm`, а не рассматривает правила как один недифференцированный артефакт.

Общий subproject содержит shared configuration interfaces и контракты sink metadata. JVM subproject строится поверх этих общих типов, добавляет JVM IR-aware rule entities и сериализованные формы, ориентированные на YAML, и публикует артефакт, который потребляют downstream-модули. Поэтому этот репозиторий сфокусирован на моделях, а не на хранении конечного YAML rule inventory.

## Граница сборки

Граница сборки — это небольшой workspace из двух артефактов.

- `seqra-configuration-rules/settings.gradle.kts` включает ровно `configuration-rules-common` и `configuration-rules-jvm`.
- `seqra-configuration-rules/configuration-rules-common/build.gradle.kts` публикует `configuration-rules-common` и зависит только от `seqra_ir_api_common`.
- `seqra-configuration-rules/configuration-rules-jvm/build.gradle.kts` публикует `configuration-rules-jvm`, зависит от `project(":configuration-rules-common")` и добавляет JVM-specific IR и serialization dependencies.
- `seqra-config/build.gradle.kts` потребляет downstream-зависимость `seqraRulesJvm`, то есть опубликованный JVM rules artifact пересекает границу модуля и используется в `seqra-config`.
ОПИСАТЬ!!! в draw.io


Итак, граница выглядит так: shared rule contracts в `configuration-rules-common`, JVM rule schema и loaders в `configuration-rules-jvm`, а конкретные packaged configuration data — в downstream `seqra-config`.

## Отношения между сущностями

Важные связи здесь работают на уровне артефактов и схем.

- `configuration-rules-common` определяет общие интерфейсы, такие как `CommonTaintConfigurationItem`, `CommonTaintConfigurationSink` и `CommonTaintConfigurationSinkMeta`.
- `configuration-rules-jvm` расширяет эти общие интерфейсы JVM-aware сущностями, такими как `TaintConfigurationItem`, `TaintMethodSource`, `TaintMethodSink` и `TaintStaticFieldSource`.
- `configuration-rules-jvm` также определяет сериализованные формы правил, такие как `SerializedRule` и `SerializedTaintConfig`, чтобы YAML-backed configuration можно было декодировать в типизированную JVM rule model.
- `seqra-config` расположен downstream: он зависит от опубликованного артефакта `configuration-rules-jvm`, а не владеет схемой сам.

Это разделение важно, потому что общий артефакт даёт остальным модулям стабильный shared contract, а JVM-артефакт несёт language-specific rule structure и serialization layer.

## Архитектура

Архитектура здесь — это layered schema workspace, а не каталог готовых правил.

В основании `configuration-rules-common` задаёт минимальные контракты, описывающие taint configuration concepts без привязки к JVM-only типам. Поверх него `configuration-rules-jvm` вводит JVM-сущности, которые ссылаются на IR-концепции вроде `CommonMethod` и `JIRField`, а также sealed serialized classes, совпадающие со структурой YAML, которую читают downstream loaders.

Внутри JVM subproject `SerializedTaintConfig` является контейнером для сериализованных групп правил вроде `entryPoint`, `source`, `sink`, `passThrough`, `cleaner` и static-field sources. `SerializedRule` описывает сериализованные варианты этих видов правил, а `TaintConfigurationItem` — более богатую JVM-side in-memory model. Downstream-код, например `seqra-config`, может зависеть от `configuration-rules-jvm`, чтобы парсить и переносить типизированные rule-data, в то время как сами shipped YAML остаются вне этого модуля.

## Высокоуровневый UML

```mermaid
classDiagram
    class ConfigurationRulesCommon {
        <<artifact>>
        CommonTaintConfigurationItem
        CommonTaintConfigurationSink
        CommonTaintConfigurationSinkMeta
        CommonTaintRulesProvider
    }

    class ConfigurationRulesJvm {
        <<artifact>>
        TaintConfigurationItem
        TaintMethodSource
        TaintMethodSink
        SerializedRule
        SerializedTaintConfig
    }

    class SeqraConfig {
        <<downstream module>>
        depends on seqraRulesJvm
        packages concrete config resources
    }

    ConfigurationRulesJvm --> ConfigurationRulesCommon : api(project(":configuration-rules-common"))
    SeqraConfig --> ConfigurationRulesJvm : implementation(seqraRulesJvm)
```

## Доказательства

- `seqra-configuration-rules/settings.gradle.kts`: подтверждает, что workspace разделён на `configuration-rules-common` и `configuration-rules-jvm`.
- `seqra-configuration-rules/configuration-rules-common/build.gradle.kts`: показывает, что общий артефакт публикуется отдельно и опирается на common IR APIs.
- `seqra-configuration-rules/configuration-rules-common/src/main/kotlin/org/seqra/dataflow/configuration/CommonTaintConfigurationItem.kt`: показывает shared interfaces и sink metadata contracts, лежащие ниже JVM-specific rule types.
- `seqra-configuration-rules/configuration-rules-jvm/build.gradle.kts`: показывает, что JVM artifact зависит от `configuration-rules-common` и добавляет JVM и serialization dependencies.
- `seqra-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/seqra/dataflow/configuration/jvm/TaintConfigurationItem.kt`: показывает JVM-specific taint rule entities, построенные поверх common contracts.
- `seqra-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/seqra/dataflow/configuration/jvm/serialized/SerializedRule.kt`: показывает сериализованные варианты правил для YAML-backed JVM rules.
- `seqra-configuration-rules/configuration-rules-jvm/src/main/kotlin/org/seqra/dataflow/configuration/jvm/serialized/SerializedTaintConfig.kt`: показывает контейнер сериализованной конфигурации и YAML loader entry point.
- `seqra-config/build.gradle.kts`: показывает downstream-зависимость от `seqraRulesJvm`, из-за чего конкретная packaged configuration остаётся вне этого модуля.
- `seqra-config/buildSrc/src/main/kotlin/SeqraConfigurationDependency.kt`: сопоставляет `seqraRulesJvm` с опубликованным артефактом `org.seqra.configuration:configuration-rules-jvm`.
