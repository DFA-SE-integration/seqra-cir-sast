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

# Lowering llvm20 -> llvm16 (klee)
add_library(llvm16_compat STATIC Llvm16Compat.cpp)

target_link_libraries(protoSerializer ${LIBS})
target_link_libraries(llvm16_compat ${LIBS})
