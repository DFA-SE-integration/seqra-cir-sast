#!/usr/bin/python3

import filecmp
import os
import sys

import cir_tblgen_util


def main():
    argc = len(sys.argv)
    if argc != 4:
        print(
            "Expected paths to two directories for comparison"
            "and csv file with definitions!"
        )
        return -1
    dir1 = os.path.expanduser(sys.argv[1])
    dir2 = os.path.expanduser(sys.argv[2])
    csv_path = os.path.expanduser(sys.argv[3])

    res = 0
    for file_info in cir_tblgen_util.read_infos_from_csv(csv_path):
        file1 = os.path.join(dir1, file_info.path)
        file2 = os.path.join(dir2, file_info.path)
        if not filecmp.cmp(file1, file2, shallow=False):
            print("[{0}] returned a difference!".format(file_info.path))
            res = 1
        else:
            print("[{0}] and [{1}] are the same!".format(file1, file2))
    return res


if __name__ == "__main__":
    exit(main())
