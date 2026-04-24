#!/usr/bin/python3

import argparse
import os
import shutil
import subprocess


def create_file_name(name, ext):
    return "{0}.{1}".format(name, ext)


def run_translation_cmd(cmd, fr, to):
    return (
        subprocess.run("{0} {1} > {2}".format(cmd, fr, to), shell=True).returncode == 0
    )


def test_parse(cmd, fr):
    return subprocess.run("{0} {1}".format(cmd, fr), shell=True).returncode == 0


def filter_ast_attrs(file_path):
    with open(file_path, "r") as file:
        code = file.read()
        code = code.replace(" #cir.record.decl.ast", "")
        # removing CXXMethod AST from Attributes list,
        # keeping the list structure intact
        code = code.replace(", ast = #cir.cxxmethod.decl.ast", "")
        code = code.replace("ast = #cir.cxxmethod.decl.ast, ", "")
        code = code.replace("ast = #cir.cxxmethod.decl.ast", "")
        # removing the entire list if it's now empty
        code = code.replace(" attributes {}", "")
    with open(file_path, "w") as file:
        file.write(code)


def get_cir_code(test_src, test_cir, clang_path, skip_compile=False):
    if skip_compile:
        shutil.copy(test_src, test_cir)
        return 0
    compile_cmd = "{2} -S -Xclang -emit-cir-flat -o {1} {0}".format(
        test_src, test_cir, clang_path
    )
    return subprocess.run(compile_cmd, shell=True).returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cir_tac")
    parser.add_argument("source_file")
    parser.add_argument("-c", "--clang", default="clang")
    parser.add_argument("-s", "--skip-compile", action="store_true")
    args = parser.parse_args()

    ser_tool_path = os.path.join(
        os.path.expanduser(args.cir_tac),
        "build",
        "tools",
        "cir-ser-proto",
        "cir-ser-proto",
    )
    des_tool_path = os.path.join(
        os.path.expanduser(args.cir_tac),
        "build",
        "tools",
        "cir-deser-proto",
        "cir-deser-proto",
    )
    parse_test_path = os.path.join(
        os.path.expanduser(args.cir_tac),
        "build",
        "tools",
        "cir-ser-proto",
        "parse-test",
    )

    test_src = os.path.expanduser(args.source_file)
    test_name = "test"
    test_cir = create_file_name(test_name, "s")
    test_ser = create_file_name(test_name, "proto")
    test_deser = create_file_name(test_name, "cir")

    clang_path = os.path.expanduser(args.clang)

    if get_cir_code(test_src, test_cir, clang_path, args.skip_compile) != 0:
        print("Compile error!")
        return 1

    # removing sometimes appearing empty ast attributes
    filter_ast_attrs(test_cir)

    if not test_parse(parse_test_path, test_cir):
        print("Parse error!")
        return 4
    if not run_translation_cmd(ser_tool_path, test_cir, test_ser):
        print("Serialization error!")
        return 2
    if not run_translation_cmd(des_tool_path, test_ser, test_deser):
        print("Deserialization error!")
        return 3

    print("\n\nDIFF OUTPUT:\n\n")
    subprocess.run("diff {0} {1}".format(test_cir, test_deser), shell=True)


if __name__ == "__main__":
    exit(main())
