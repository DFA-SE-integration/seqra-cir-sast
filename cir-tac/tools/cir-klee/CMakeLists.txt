set(LIBS
    protoSerializer
    LLVMBitWriter
    LLVMAsmParser
)

add_executable(cir-klee
    cir-klee.cpp
    TraceUtil.cpp
    TracePathUtils.cpp
    TraceGuidePass.cpp
    TraceAssertPass.cpp)

target_link_libraries(cir-klee ${LIBS} trace_proto llvm16_compat)
