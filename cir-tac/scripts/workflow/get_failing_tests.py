#!/usr/bin/python3

import sys

import utils


def main():
    if len(sys.argv) != 4:
        print("Expected paths to cir_tac and GSAC directories, and output file!")
        return -1
    cir_tac_path = sys.argv[1]
    gsac_path = sys.argv[2]
    output_file = sys.argv[3]
    with open(output_file, "w") as output:
        for test in utils.get_test_paths(gsac_path):
            res = utils.run_test(cir_tac_path, test)
            print("Test [{0}] ran with the result {1}".format(test, res))
            if res == utils.TestResult.BuildError:
                print(test, file=output)


if __name__ == "__main__":
    exit(main())
