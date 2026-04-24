cmake_minimum_required(VERSION 3.20)

project(cir-tac)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# https://github.com/protocolbuffers/protobuf/issues/4806
# Without that variable find_package does not set variables such as Protobuf_INLCUDE_DIRS, Protobuf_LIBRARIES...
# (however, I do not think it is a correct approach, but at least it works)
set(protobuf_MODULE_COMPATIBLE 1)

if (NOT DEFINED CLANGIR_BUILD_DIR)
  message(FATAL_ERROR "CLANGIR_BUILD_DIR must be specified.")
endif()

find_package(MLIR REQUIRED HINTS ${CLANGIR_BUILD_DIR})
message(STATUS "Found MLIR ${MLIR_PACKAGE_VERSION}")
message(STATUS "Using MLIRConfig.cmake in: ${MLIR_DIR}")

find_package(Clang REQUIRED HINTS ${CLANGIR_BUILD_DIR})
message(STATUS "Found Clang ${CLANG_PACKAGE_VERSION}")
message(STATUS "Using ClangConfig.cmake in: ${CLANG_DIR}")

# Spread includes to entire project
include_directories(SYSTEM ${CLANG_INCLUDE_DIRS})
include_directories(SYSTEM ${MLIR_INCLUDE_DIRS})
include_directories(SYSTEM ${LLVM_INCLUDE_DIRS})

find_package(Protobuf CONFIG REQUIRED)
message(STATUS "Found Protobuf ${Protobuf_PACKAGE_VERSION}")
message(STATUS "Using Protobuf.cmake in: ${Protobuf_DIR}")

add_subdirectory(proto)

include_directories(${CMAKE_CURRENT_BINARY_DIR})
include_directories(${Protobuf_INCLUDE_DIRS})

include_directories(include)

add_subdirectory(src)
add_subdirectory(tools)
