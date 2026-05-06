set(LIBS
    MLIRCIR
    MLIRParser
    MLIRTargetLLVMIRExport
    MLIRBuiltinToLLVMIRTranslation
    MLIRLLVMToLLVMIRTranslation
    MLIRIR
    clangCIRLoweringDirectToLLVM
    SeaDsaAnalysis
    LLVMCore
    LLVMAnalysis
    LLVMTransformUtils
    LLVMSupport
    proto
)

add_library(protoSerializer
  EnumSerializer.cpp
  TypeSerializer.cpp
  AttrSerializer.cpp
  OpSerializer.cpp
  AliasSerializer.cpp
  CirToLlvmIr.cpp
  Deserializer.cpp
  EnumDeserializer.cpp
  AttrDeserializer.cpp
  OpDeserializer.cpp
)

target_link_libraries(protoSerializer ${LIBS})
