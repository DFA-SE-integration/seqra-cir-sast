#!/usr/bin/python3

import argparse
import filecmp
import os
import shutil
from pathlib import Path

import utils

CIR_ORIGINAL = "test.s"
DESERIALIZED_FILE = "test.cir"
TEST_OUTPUT = "test.out"


def run_and_check_test_result(test_path, cir_tac_path, clang, is_cir=False):
    # preparing files for the test run to have a clean state
    utils.remove_if_exists(CIR_ORIGINAL)
    utils.remove_if_exists(DESERIALIZED_FILE)
    utils.remove_if_exists(TEST_OUTPUT)

    test_res = utils.run_test(
        cir_tac_path, test_path, enable_output=True, custom_clang=clang, is_cir=is_cir
    )

    if test_res == utils.TestResult.ParseError:
        print("Could not parse CIR file [{0}]! Skipping test...".format(test_path))
        return True
    if test_res != utils.TestResult.Success:
        print("Failed to run test! Received result: {0}".format(test_res))
        return False

    if not os.path.exists(CIR_ORIGINAL):
        print("Test succeeded but no original cir file found!")
        return False
    if not os.path.exists(DESERIALIZED_FILE):
        print("Test succeeded but no deserialized file found!")
        return False

    return filecmp.cmp(DESERIALIZED_FILE, CIR_ORIGINAL)


def save_failed_test(test_path):
    Path("failures").mkdir(exist_ok=True)
    test_name = Path(test_path).stem
    for test_outs in [CIR_ORIGINAL, DESERIALIZED_FILE, TEST_OUTPUT]:
        if not os.path.exists(test_outs):
            continue
        save_copy_name = "{0}_{1}".format(test_name, test_outs)
        save_copy_path = os.path.join("failures", save_copy_name)
        shutil.copyfile(test_outs, save_copy_path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cir_tac")
    parser.add_argument("test_suite")
    parser.add_argument("-c", "--clang", default="clang")
    parser.add_argument("-s", "--search-for-cir", action="store_true")
    args = parser.parse_args()

    cir_tac_path = os.path.expanduser(args.cir_tac)
    gsac_path = os.path.expanduser(args.test_suite)
    clang = os.path.expanduser(args.clang)
    is_cir = args.search_for_cir

    test_ext = "cir" if is_cir else "c"
    test_files = utils.get_test_paths(gsac_path, ext=test_ext)

    res = 0
    for test in test_files:
        print("Testing file [{0}]\n".format(test))
        if not run_and_check_test_result(test, cir_tac_path, clang, is_cir):
            print("Failure! Saving test outputs...\n")
            save_failed_test(test)
            res = 1
            continue
        print("Success!\n")
    return res


if __name__ == "__main__":
    exit(main())
