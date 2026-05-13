set(LIBS
    protoSerializer
    llvm16_compat
)

add_executable(cir-llvm16-dump cir-llvm16-dump.cpp)
target_link_libraries(cir-llvm16-dump ${LIBS})
