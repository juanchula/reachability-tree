import numpy as np
from itertools import accumulate
from scipy.linalg import block_diag
from random import getrandbits

# Generate empty matrix
ip = np.empty((0, 0), dtype=int)
im = np.empty((0, 0), dtype=int)

# Shapes list
shapes = []
acc_shapes = []

# Generate random matrixs I+ and I- of carousels
# And concatenates them diagonally
for rand in np.random.randint(low=3, high=7, size=5):
    plus_matrix = np.identity(rand)
    minus_matrix = np.roll(np.identity(rand), -1, axis=0)
    # print("\nDim: [{}]".format(rand))
    # print("Plus matrix:\n {}".format(plus_matrix))
    # print("Minus matrix:\n {}".format(minus_matrix))
    ip = block_diag(ip, plus_matrix)
    im = block_diag(im, minus_matrix)
    # print("I+\n{}".format(ip))
    # print("I-\n{}".format(im))
    shapes.append(np.shape(plus_matrix)[0])

# Generate random places buffer joins
if (getrandbits(1)):
    num_buff_joins = np.random.randint(low=1, high=len(shapes))
    acc_shapes = list(accumulate(map(int, shapes)))
    # print("Train shapes: {}".format(shapes))
    # print("Accumulated shapes: {}".format(acc_shapes))
    # for s in acc_shapes:
        # print("{} -> {}".format(s-1, im[s-1]))

    for i in range(0, num_buff_joins, 2):
        ip[acc_shapes[i]-1] = ip[acc_shapes[i]-1] + ip[acc_shapes[i+1]-1]
        ip = np.delete(ip, acc_shapes[i+1]-1, axis=0)

        im[acc_shapes[i]-1] = im[acc_shapes[i]-1] + im[acc_shapes[i+1]-1]
        im = np.delete(im, acc_shapes[i+1]-1, axis=0)
    # print("I+ with buffer joins. Shape: {}\n{}".format(np.shape(ip),ip))
    # print("I- with buffer joins. Shape: {}\n{}".format(np.shape(im),im)) 

# Generate random shared resources and add to I+ and I- matrix
num_resources = np.random.randint(low=3, high=10)
ip = np.vstack((ip, np.random.choice([0, 1], size=(
    num_resources, np.shape(ip)[1]), p=[9./10, 1./10])))
im = np.vstack((im, np.random.choice([0, 1], size=(
    num_resources, np.shape(im)[1]), p=[9./10, 1./10])))

# print("I+ with resources. Shape: {}\n{}".format(np.shape(ip),ip))
# print("I- with resources. Shape: {}\n{}".format(np.shape(im),im))

# save to txt
# np.savetxt()
