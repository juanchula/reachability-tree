#!/usr/bin/python3

import os
import sys
import argparse
import subprocess
import petrinet_gen_v2
import time

output_folder           = "gen_nets"
n_nets                  = 1
n_circuits              = 0
n_fj_init_train         = 0
n_fj_final_train        = 0
n_fj_init_final_train   = 0
n_fork_join             = 0
n_cmpx_src              = 0
min_shrd_src            = 0
max_shrd_src            = 0


def dir_path(string):
    if os.path.isdir(string):
        return string

    # Crear directorio si no existe
    if (not os.path.exists(string)):
        os.makedirs(string)
        return string
    else:
        raise NotADirectoryError(string)


def main():

    global output_folder
    global n_nets
    global n_circuits
    global n_fj_init_train
    global n_fj_final_train
    global n_fj_init_final_train
    global n_fork_join
    global n_cmpx_src
    global min_shrd_src
    global max_shrd_src

    # Verificación de argumentos 
    if (len(sys.argv) > 1):

        ap = argparse.ArgumentParser(description='Parametrización del generador de redes de petri')

        required = ap.add_argument_group('Argumentos requeridos')

        required.add_argument("-n", "--net_num", type=int, required=True, default=n_nets,
            help="Cantidad de redes de petri a generar")

        required.add_argument("-o", "--out_dir", type=dir_path, required=False,
            default=output_folder,help="Directorio donde se almacenarán las redes generadas")

        required.add_argument("-c", "--circuits", type=int, required=False,
            default=n_circuits, help="Cantidad circuitos aleatorios a generar en la red")

        required.add_argument("-ifj", "--init_fj", type=int, required=False,
            default=n_fj_init_train, help="Cantidad circuitos con fork/join y tren inicial aleatorios a generar en la red")

        required.add_argument("-ffj", "--final_fj", type=int, required=False,
            default=n_fj_final_train, help="Cantidad circuitos con fork/join y tren final aleatorios a generar en la red")

        required.add_argument("-iffj", "--init_final_fj", type=int, required=False,
            default=n_fj_init_final_train, help="Cantidad circuitos con fork/join y tren inicial y final aleatorios a generar en la red")

        required.add_argument("-fj", "--fork_join", type=int, required=False,
            default=n_fork_join, help="Cantidad circuitos con fork/join aleatorios a generar en la red")

        required.add_argument("-cr", "--cmpx_src", type=int, required=False,
            default=n_cmpx_src, help="Cantidad de recursos compartidos complejos a generar en la red")
            
        required.add_argument("-msr", "--min_shrd_src", type=int, required=False,
            default=min_shrd_src, help="Cantidad mínima de recursos compartidos simples a generar en la red")

        required.add_argument("-Msr", "--max_shrd_src", type=int, required=False,
            default=max_shrd_src, help="Cantidad máxima de recursos compartidos simples a generar en la red")

        # Parseo los argumentos recibidos
        args = vars(ap.parse_args())
        n_nets = args['net_num']
        output_folder = args['out_dir']
        n_circuits = args['circuits']
        n_fj_init_train = args['init_fj']
        n_fj_final_train = args['final_fj']
        n_fj_init_final_train = args['init_final_fj']
        n_fork_join = args['fork_join']
        n_cmpx_src = args['cmpx_src']
        min_shrd_src = args['min_shrd_src']
        max_shrd_src = args['max_shrd_src']

        # Verifico que se hayan ingresado valores válidos
        n_figures = n_circuits + n_fork_join + n_fj_init_train + n_fj_final_train + n_fj_init_final_train 
        if (n_figures < 2):
            print("Revise la cantidad de figuras a generar ({}). Deben ser como mínimo 2.".format(n_figures))
            exit(1)

        if (n_nets < 0):
            print("Revise la cantidad de redes a generar ({}). Debe ser como mínimo 1.".format(n_figures))
            exit(1)

        # Seteo de cantidades de recursos compartidos simples
        # Si no se especifican en los valores mínimos y máximo se setean en base a la cantidad de figuras
        if (min_shrd_src < 1):
            min_shrd_src = n_figures + 3

        if (max_shrd_src < min_shrd_src):
            max_shrd_src = min_shrd_src * 2

        for item in range(n_nets):
            new_folder = output_folder + '/' + time.strftime("%Y%m%d-%H%M%S") + '_' + str(item)
            subprocess.Popen("mkdir " + new_folder, shell=True).communicate()
            petrinet_gen_v2.generate_petri_networks(new_folder,circuits_quantity=n_circuits, cir_fj_w_init_train=n_fj_init_train, 
                cir_fj_w_final_train=n_fj_final_train, cir_fj_w_init_final_train=n_fj_init_final_train, cir_fork_join_quantity=n_fork_join, cmpx_resources_quantity=n_cmpx_src, min_share_resources=min_shrd_src, max_share_resources=max_shrd_src)

        print("Se generaron {} redes en el directorio {}".
            format(args['net_num'], args['out_dir']))

    else:
        print("Argumentos inválidos. Revise las posibilidades con -h")

if __name__ == "__main__":
    main()
