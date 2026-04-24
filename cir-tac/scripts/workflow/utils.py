#!/usr/bin/python3

import os
import subprocess
from enum import Enum
from pathlib import Path


class TestResult(Enum):
    Success = 0
    BuildError = 1
    SerializationError = 2
    DeserializationError = 3
    ParseError = 4


def get_test_paths(root, ext="c"):
    root = os.path.expanduser(root)
    return [x.absolute().as_posix() for x in Path(root).rglob("*.{0}".format(ext))]


def run_test(
    cir_tac_path, test_path, enable_output=False, custom_clang=None, is_cir=False
):
    cir_tac_path = os.path.expanduser(cir_tac_path)
    script_path = os.path.join(cir_tac_path, "scripts", "run_ser_deser_cycle.py")

    clang = "" if custom_clang is None else '-c "{0}" '.format(custom_clang)
    skip_compile = "" if not is_cir else "-s "

    test_cmd = '{2} "{0}" "{1}" {3}{4}> test.out'.format(
        cir_tac_path, test_path, script_path, clang, skip_compile
    )
    kwargs = {}
    kwargs["shell"] = True
    if not enable_output:
        kwargs["stdin"] = subprocess.DEVNULL
        kwargs["stdout"] = subprocess.DEVNULL
    res = subprocess.run(test_cmd, **kwargs).returncode
    return TestResult(res)


def remove_if_exists(file_path):
    if os.path.exists(file_path):
        os.remove(file_path)
