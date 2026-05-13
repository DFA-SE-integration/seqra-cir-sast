# LLVM IR 20 → llvm-as-16 compatibility (`Llvm16Compat`)

## `prepareLlvmModuleForLlvm16` (`Llvm16Compat`)

Prepares an in-memory `llvm::Module` (from ClangIR / MLIR lowering) so its **printed** LLVM IR can be
assembled by **LLVM 16** (`llvm-as` / bitcode reader), e.g. for KLEE. The pass only strips IR
features LLVM 16 rejects; it is not a general IR fixer.

### Workflow

1. **Discover** LLVM 17–20-only keywords in lowered IR for your corpus (e.g. Juliet CWE416):
   - Build `cir-llvm16-dump`: `ninja -C /tmp/cir-tac-build cir-llvm16-dump`.
   - Run `scripts/diag_llvm16_keywords.sh` (see `scripts/DIAG_LLVM16_README.md`).
2. **Extend** `cir-tac/src/Llvm16Compat.cpp` only for constructs listed in the diagnostic output
   (empty list = no change for that corpus).
3. **Regression**: in Docker, `make test-llvm16` — gtest lowers each `juliet-c/samples/CWE416_Use_After_Free/*.cir`,
   runs `prepareLlvmModuleForLlvm16`, then `llvm-as-16`.

### Currently stripped (always)

- On functions / parameters / return values / call sites: `nofpclass`, `initializes`, `dead_on_unwind`.
- If the module has no target triple, sets `x86_64-unknown-linux-gnu` (existing behaviour).

### Assembling

`runLlvmAs` in `Llvm16Compat.h` runs an external `llvm-as` binary on a `.ll` file.

### Tools

- `cir-tac/tools/cir-llvm16-dump` — parse `.cir`, lower to LLVM IR, optional `--strip`, print `.ll`
  (diagnostics; kept in-tree).
