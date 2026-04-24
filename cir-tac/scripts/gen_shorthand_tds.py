#!/usr/bin/python3

import argparse
import os
import textwrap

ATTRS_FILE_REL_PATH = "clang/include/clang/CIR/Dialect/IR/MLIRCIRAttrs.td"
ATTRS_FILE_CONTENTS = textwrap.dedent(
    """\
    include "mlir/IR/BuiltinAttributes.td"
    include "mlir/IR/BuiltinLocationAttributes.td"
    include "clang/CIR/Dialect/IR/CIRAttrs.td"
    include "clang/CIR/Dialect/IR/CIROps.td"
"""
)

TYPES_FILE_REL_PATH = "clang/include/clang/CIR/Dialect/IR/MLIRCIRTypes.td"
TYPES_FILE_CONTENTS = textwrap.dedent(
    """\
    include "mlir/IR/BuiltinTypes.td"
    include "clang/CIR/Dialect/IR/CIRTypes.td"
"""
)


def write_file(path, contents):
    with open(path, "w") as wfile:
        wfile.write(contents)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--path", help="path to ClangIR")
    args = parser.parse_args()
    clangir = os.path.expanduser(args.path)
    attrs_file_path = os.path.join(clangir, ATTRS_FILE_REL_PATH)
    types_file_path = os.path.join(clangir, TYPES_FILE_REL_PATH)
    write_file(attrs_file_path, ATTRS_FILE_CONTENTS)
    write_file(types_file_path, TYPES_FILE_CONTENTS)
    return 0


if __name__ == "__main__":
    exit(main())
