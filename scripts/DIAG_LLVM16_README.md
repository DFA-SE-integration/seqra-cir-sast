# Diagnostic: LLVM keywords after CIR → LLVM lowering (CWE416)

## `diag_llvm16_keywords.sh`

Requires a built **`cir-llvm16-dump`** binary (from `cir-tac`, same as `make cir-tac` / `ninja cir-llvm16-dump`).

```bash
export CIR_LLVM16_DUMP=/tmp/cir-tac-build/tools/cir-llvm16-dump/cir-llvm16-dump   # default
export JULIET_CWE416=/workspace/juliet-c/samples/CWE416_Use_After_Free              # optional
bash scripts/diag_llvm16_keywords.sh
```

Writes:

- `/tmp/cwe416_ll/*.ll` — lowered IR per sample (override with `DIAG_OUT_DIR`)
- `/tmp/cwe416_keywords.txt` — sorted unique tokens matching the plan’s grep set (override with `KEYWORDS_FILE`)

Use this list to decide which extra removals belong in `prepareLlvmModuleForLlvm16`. If the keywords file is **empty**, the stock Juliet CWE416 `.cir` corpus does not emit those constructs after lowering, and you should not add corresponding strip logic until a corpus reports them.
