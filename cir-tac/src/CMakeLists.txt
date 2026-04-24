set(LIBS
    MLIRCIR
    MLIRParser
    proto
)

add_library(protoSerializer
  EnumSerializer.cpp
  TypeSerializer.cpp
  AttrSerializer.cpp
  OpSerializer.cpp
  AliasSerializer.cpp
  Deserializer.cpp
  EnumDeserializer.cpp
  AttrDeserializer.cpp
  OpDeserializer.cpp
)

target_link_libraries(protoSerializer ${LIBS})
