import numpy as np
import argparse


def deserialize_matrix(path):
    with open(path, 'r') as f:
        lines = f.readlines()
        matrix = []
        for l in lines:
            tokens = l.split(',')
            row = [float(el) for el in tokens]
            matrix.append(row)
    return np.asarray(matrix)


def main(args):
    # deserialize matrix
    matrix = deserialize_matrix(args.input_matrix)


if __name__ == "__main__":
    print("Beta matrix analysis")

    # Argument parsing
    parser = argparse.ArgumentParser()
    parser.add_argument('--input_matrix', default='nofile', help='whether to process a split file or not')
    parser.add_argument('--output_results_path', default="results", help='output_script')
    parser.add_argument('--batch_size', type=int, default=30, help='Question batch size')

    args = parser.parse_args()

    main(args)
