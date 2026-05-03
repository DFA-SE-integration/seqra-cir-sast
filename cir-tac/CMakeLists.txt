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

if (NOT DEFINED SEA_DSA_DIR)
  set(SEA_DSA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../sea-dsa")
endif()

# Sea-dsa uses add_llvm_library; ensure AddLLVM is available (MLIR usually sets
# LLVM_CMAKE_DIR).
if (NOT DEFINED LLVM_CMAKE_DIR)
  find_package(LLVM REQUIRED CONFIG HINTS "${CLANGIR_BUILD_DIR}"
               NO_DEFAULT_PATH)
endif()
message(STATUS "Using LLVM CMake dir: ${LLVM_CMAKE_DIR}")
list(APPEND CMAKE_MODULE_PATH "${LLVM_CMAKE_DIR}")
include(AddLLVM)

find_package(Boost 1.65 REQUIRED)
message(STATUS "Found Boost ${Boost_VERSION}")
include_directories(${Boost_INCLUDE_DIRS})

set(SEA_DSA_BUILD_TOOLS_TESTS OFF CACHE BOOL "Sea-dsa tools/tests" FORCE)
add_subdirectory("${SEA_DSA_DIR}" sea-dsa-build EXCLUDE_FROM_ALL)

add_subdirectory(proto)

include_directories(${CMAKE_CURRENT_BINARY_DIR})
include_directories(${Protobuf_INCLUDE_DIRS})

include_directories(include)

add_subdirectory(src)
add_subdirectory(tools)
