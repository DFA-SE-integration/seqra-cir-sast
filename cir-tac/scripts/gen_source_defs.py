#!/usr/bin/python3

import argparse
import os

import cir_tblgen_util


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--path", help="path for the resulting .csv")
    args = parser.parse_args()
    path = os.path.expanduser(args.path)
    cir_tblgen_util.write_infos_to_csv(path)
    return 0


if __name__ == "__main__":
    exit(main())
