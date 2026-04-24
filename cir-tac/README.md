# cir-tac

A Protobuf-based implementation for the CIR (Clang IR) dialect of MLIR (Multi-Level IR).

## Overview

**cir-tac** provides a Protocol Buffers model for working with CIR dialect code. The project enables efficient representation and manipulation of MLIR modules, including functions, blocks, operations, and type systems.

## Prerequisites

- CMake 3.x or higher
- Protocol Buffers compiler (protoc)
- LLVM/Clang build with CIR support
- Ninja build system

## Building from Source

### 1. Build ClangIR (Required)

First, build [clangir](https://github.com/llvm/clangir) by following these steps:

```bash
git clone git@github.com:llvm/clangir.git
cd clangir/llvm
mkdir build && cd build
cmake -GNinja \
    -DLLVM_ENABLE_PROJECTS="clang;mlir" \
    -DCMAKE_BUILD_TYPE=Debug \
    -DCLANG_ENABLE_CIR=ON ..
ninja
```

### 2. Build cir-tac

After building clangir, build this project:

```bash
mkdir build && cd build
cmake -DCLANGIR_BUILD_DIR=${PATH_TO_CLANGIR_BUILD} .. && make
```

Where `PATH_TO_CLANGIR_BUILD` is the path to your clangir build directory (e.g., `.../clangir/llvm/build`).

## Project Structure

### Protocol Buffers Definitions
- `proto/model.proto` - Core module and function definitions
- `proto/type.proto` - Type system definitions
- `proto/op.proto` - Operation definitions
- `proto/attr.proto` - Attribute system definitions
- `proto/enum.proto` - Enumeration definitions
- `proto/setup.proto` - Setup definitions

### Tools
- `tools/cir-ser-proto/` - Serialization tool for CIR Protocol Buffers
  - Provides functionality for serializing CIR modules to Protocol Buffer format

## Generated Files

The build process generates Protocol Buffers files for each `.proto` definition:
- `build/proto/*.pb.cc` - Generated C++ source files
- `build/proto/*.pb.h` - Generated C++ headers

## Development

### Code Style
- C++ code follows LLVM coding standards
- Protocol Buffers definitions use `snake_case` for field names

### Contributing
0. Fork the repository
1. Create a feature branch
2. Commit your changes
3. Push to the branch
4. Create a Pull Request

## License

This project is licensed under the terms included in the [LICENSE](LICENSE) file.
