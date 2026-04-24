set(LIBS
    protoSerializer
)

add_executable(cir-ser-proto cir-ser-proto.cpp)

add_executable(parse-test parse-test.cpp)

target_link_libraries(cir-ser-proto ${LIBS})

target_link_libraries(parse-test ${LIBS})
