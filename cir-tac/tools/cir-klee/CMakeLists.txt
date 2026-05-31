set(LIBS
    protoSerializer
    LLVMBitWriter
    LLVMAsmParser
    LLVMTransformUtils
)

add_executable(cir-klee
    cir-klee.cpp
    TraceUtil.cpp
    TracePathUtils.cpp
    TraceGuidePass.cpp
    TraceAssertPass.cpp)

# llvm16_compat pulls in SeaDsaAnalysis (via protoSerializer) *after* the first
# pass over LLVMTransformUtils, so isLegalToPromote from CallPromotionUtils.cpp
# is not resolved unless TransformUtils is linked again at the end.
target_link_libraries(cir-klee ${LIBS} trace_proto llvm16_compat LLVMTransformUtils)
