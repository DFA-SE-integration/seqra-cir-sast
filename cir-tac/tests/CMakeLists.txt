set(CWE416_SAMPLES_DIR "${CMAKE_SOURCE_DIR}/../juliet-c/samples/CWE416_Use_After_Free")
configure_file(
  llvm16_compat_test_paths.h.in
  ${CMAKE_CURRENT_BINARY_DIR}/llvm16_compat_test_paths.h
  @ONLY)

add_executable(llvm16_compat_test llvm16_compat_test.cpp)
target_include_directories(llvm16_compat_test PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(llvm16_compat_test PRIVATE
  protoSerializer
  llvm16_compat
  GTest::gtest
  GTest::gtest_main
)

add_test(NAME llvm16_compat_test COMMAND llvm16_compat_test)
set_tests_properties(llvm16_compat_test PROPERTIES
  ENVIRONMENT "GTEST_COLOR=1"
)
