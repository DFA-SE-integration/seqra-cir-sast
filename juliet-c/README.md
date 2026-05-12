# Juliet Test Suite for C emitted as ClangIR

This repository contains the Juliet Test Suite for C (version 1.3) from NIST emitted as ClangIR.

## Directory Structure: analyze_db

```
analyze_db/
├── build/                # ClangIR files and dependencies
├── src/                  # Copy of source files
├── cir_commands.json     # Commands for compiling ClangIR files
├── compile_commands.json # Original compile commands in JSON Compilation Database format
├── compile_commands.raw  # Raw commands for CIR compilation
├── compiled.list         # List of compiled files
├── dependency.list       # List of dependency files
└── errors.list           # List of compilation errors
```

## About the Juliet Test Suite

The Juliet Test Suite is a collection of test cases for C and C++ programs that contain known software weaknesses. It is maintained by NIST (National Institute of Standards and Technology) as part of the SAMATE (Software Assurance Metrics And Tool Evaluation) project.

Official website: [https://samate.nist.gov/SARD/test-suites/112](https://samate.nist.gov/SARD/test-suites/112)

## About ClangIR

ClangIR is a new IR for Clang, which is an MLIR dialect for C/C++ based languages in Clang. Its representation level sits somewhere between Clang's AST and LLVM IR.

Documentation: [https://llvm.github.io/clangir](https://llvm.github.io/clangir)

```
clang version 21.0.0git (git@github.com:llvm/clangir.git 4e7a2236d38507e994cbe5b79c888cf86427e7db)
Target: x86_64-unknown-linux-gnu
Thread model: posix
InstalledDir: /workspaces/clangir/install/bin
Build config: +assertions
```

## License

The original Juliet Test Suite is released as "Public Domain" by NIST.