import numpy as np
import random
from numpy.lib import npyio
from scipy.linalg import block_diag
from xml.etree.ElementTree import Element, SubElement, ElementTree
import time


"""
    Este script contiene los métodos necesarios para generar aleatoriamente redes de petri compuesta por 5 tipos de figuras distintas.
    Genera un archivo de salida de extensión '.pflow' que puede ser abierto desde la aplicación Petrineitor.

    Autores:
    - Lenta, Luis Alejandro
    - Monsierra, Lucas Gabriel
    - Nicolaide, Christian
"""


# Genero las matrices I+ e I- vacías
ip = np.empty((0, 0), dtype=int)
im = np.empty((0, 0), dtype=int)

# Genero el documento '.xml' que se convertirá en el archivo de salida '.pflow'
document = Element('document')
subnet = SubElement(document, 'subnet')
SubElement(subnet, 'x').text = '0'
SubElement(subnet, 'y').text = '0'
# Los ejes del dibujo del petrinator.
# 0,0 es el medio de la imagen, por eso empiezo en -200, -200.
global_axis_x = -200
global_axis_y = -200

# Variables globales
place_index = 1
transition_index = 1
min_tokens_idle = 1
max_tokens_idle = 3
max_circuits = 3
max_cir_forks_joins = 2
min_transitions_trains = 3
max_transitions_trains = 6
invariants = []


"""
    Método para dibujar los arcos en el archivo '.pflow'.
    Recibe las matrices I+ e I- para saber cuales son los arcos que entran a las plazas
    y cuales los que salen de una plaza.

    :param ip_matrix: matriz I+
    :param im_matrix: matriz I-
"""
def draw_arcs(ip_matrix, im_matrix):
    global subnet

    # Recorro matriz I+ para dibujar arcos desde las transiciones a las plazas
    for place_idx, place in enumerate(ip_matrix):
        for transition_idx, transition in enumerate(place):
            if transition == 1:
                xml_arc = SubElement(subnet, 'arc')
                SubElement(xml_arc, 'type').text = 'regular'
                SubElement(xml_arc, 'sourceId').text = 'T' + str(transition_idx + 1)
                SubElement(xml_arc, 'destinationId').text = 'P' + str(place_idx + 1)
                SubElement(xml_arc, 'multiplicity').text = "1"

    # Recorro matriz I- para dibujar arcos desde las plazas a las transiciones
    for place_idx, place in enumerate(im_matrix):
        for transition_idx, transition in enumerate(place):
            if transition == 1:
                xml_arc = SubElement(subnet, 'arc')
                SubElement(xml_arc, 'type').text = 'regular'
                SubElement(xml_arc, 'sourceId').text = 'P' + str(place_idx + 1)
                SubElement(xml_arc, 'destinationId').text = 'T' + str(transition_idx + 1)
                SubElement(xml_arc, 'multiplicity').text = "1"


"""
    Métodod para generar la figura de un tren.

    Un tren esta compuesto por una transicion y una plaza, y asi sucesivamente.
    Sólo se dibujan las transiciones y las plazas. Las conexiones quedan para el final.

    Un tren esta representado por una matriz identidad, no cuadrada.
    No es cuadrada porque tiene una transicion mas, es decir, una columna mas.
    Por eso lo primero que hacemos es agregar una transicion.
    Guardo el indice de cada una de las transiciones como un invariante.

    :param ip_matrix: matriz I+.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
"""
def draw_train(ip_matrix, axis_x, axis_y):
    global transition_index
    global subnet
    global invariants

    new_invariant = [transition_index]
    draw_transition(axis_x, axis_y)

    # Recorremos cada una de las plazas (filas)
    for place in range(len(ip_matrix)):
        # Luego agrego una plaza 75 pixeles debajo.
        axis_y = axis_y + 75
        draw_place("0", axis_x, axis_y)
        axis_y = axis_y + 75
        # Guardo el indice de cada una de las transiciones como un invariante
        new_invariant.append(transition_index)
        # Luego agrego una transicion
        draw_transition(axis_x, axis_y)

    invariants.append(new_invariant)


"""
    Método para dibujar una transición.

    :param axis_x: eje x donde se dibuja la transición en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la transición en el archivo '.pflow'.
"""
def draw_transition(axis_x, axis_y):
    global transition_index
    xml_transition = SubElement(subnet, 'transition')
    SubElement(xml_transition, 'id').text = 'T' + str(transition_index)
    SubElement(xml_transition, 'label').text = 'T' + str(transition_index)
    SubElement(xml_transition, 'x').text = str(axis_x)
    SubElement(xml_transition, 'y').text = str(axis_y)
    SubElement(xml_transition, 'timed').text = 'false'
    SubElement(xml_transition, 'rate').text = '1.0'
    SubElement(xml_transition, 'automatic').text = 'false'
    SubElement(xml_transition, 'informed').text = 'true'
    SubElement(xml_transition, 'enableWhenTrue').text = 'false'
    SubElement(xml_transition, 'guard').text = 'none'
    SubElement(xml_transition, 'stochasticProperties', {
        'distribution': 'Exponential',
        'labelVar1': 'Rate (λ)',
        'labelVar2': ' ',
        'var1': '1.0',
        'var2': '1.0'
    })
    # Sumo uno para la proxima transicion
    transition_index += 1


"""
    Método para dibujar una plaza.

    :param tokens: marcado de la plaza a dibujar.
    :param axis_x: eje x donde se dibuja la plaza en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la palza en el archivo '.pflow'.
"""
def draw_place(tokens, axis_x, axis_y):
    global place_index
    xml_place = SubElement(subnet, 'place')
    SubElement(xml_place, 'id').text = 'P' + str(place_index)
    SubElement(xml_place, 'label').text = 'P' + str(place_index)
    SubElement(xml_place, 'x').text = str(axis_x)
    SubElement(xml_place, 'y').text = str(axis_y)
    SubElement(xml_place, 'tokens').text = tokens
    SubElement(xml_place, 'isStatic').text = 'false'
    # Aumento el indice para la proxima plaza que se agregue
    place_index += 1


"""
    Método para dibujar recursos privados.
    Estos recursos no son mas que plazas (filas).

    :param ip_matrix: matriz I+.
    :param axis_x: eje x donde se dibuja el recurso privado en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja el recurso privado en el archivo '.pflow'.
"""
def draw_private_resources(ip_matrix, axis_x, axis_y):
    global subnet
    global place_index

    # Recorremos cada una de las plazas (filas)
    for place in range(len(ip_matrix)):
        # Agregamos una plaza con marcado 1
        draw_place("1", axis_x, axis_y)
        # La proxima plaza se agrega a 150 pixeles.
        axis_y = axis_y + 150



"""
    Método para generar recursos privados dentro de un tren.
    Crea una matriz de (reources x number_transitions), relacionada a I+.

    :param max_resources: cantidad maxima de recursos que se crean.
    :param number_transitions: cantidad de transiciones que tiene el circuito al que le agregamos los recursos.
    :return: retorna las matrices I+ e I- de los recursos privados generados.  
"""
def add_private_resources(max_resources, number_transitions):
    resources_plus_matrix = []
    resources_minus_matrix = []

    # Generamos un numero aleatorio de recursos menor a la cantidad total de recursos.
    resources = random.randrange(max_resources + 1)

    # Si no se generan nuevos recursos, no hace nada.
    if resources == 0:
        return resources_plus_matrix, resources_minus_matrix

    # Seleccionamos aleatoriamente las transiciones que tendran recursos.
    active_transitions = np.random.choice(number_transitions, resources, replace=False)

    # Si solo es una transicion y es la primera, no hago nada
    if len(active_transitions) == 1 & active_transitions[0] == 0:
        return resources_plus_matrix, resources_minus_matrix

    for transition in active_transitions:
        # No puede haber recursos en la primer transicion
        if transition == 0:
            continue
        new_resource = np.zeros(number_transitions)
        # Todas las transiciones son cero excepto la que recibe el token del recurso.
        new_resource[transition] = 1
        # Agregamos la plaza(fila) a la matriz relacionada con I+
        resources_plus_matrix.append(new_resource)
        # Para la matriz I- debemos desplazar a la izquierda los recursos.
        # Esto es porque siempre la transicion anterior es a la que se devuelve el recurso
        new_resource = np.roll(new_resource, -1)
        resources_minus_matrix.append(new_resource)

    return resources_plus_matrix, resources_minus_matrix


"""
    Método para generar una cantidad determinada de figuras de trenes.
    
    Cada tren tiene un numero aleatorio de transiciones y plazas.
    Cada tren tiene un numero aleatorio de recursos.
    Cada tren empieza y termina con una transicion.

    :param quantity: cantidad de trenes a generar.
    :param axis_x: eje x donde se dibuja el tren en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja el tren en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los trenes generados.
"""
def generate_trains(quantity, axis_x, axis_y):
    first_axis_y = axis_y
    trains = []

    for rand in np.random.randint(low=min_transitions_trains, high=max_transitions_trains, size=quantity):
        # Matriz I+. Como es un tren, tiene que tener una plaza menos que el total de transcisiones
        plus_matrix = np.identity(rand)
        plus_matrix = plus_matrix[:-1, :]
        # Matriz I-. En un tren es I+ desplazada a la derecha
        minus_matrix = np.roll(np.identity(rand), 1)
        # El -1 es para no tener en cuenta la plaza idle.
        minus_matrix = minus_matrix[:-1, :]
        minus_matrix[:, 0] = 0

        # Dibujo el tren generado antes de agregarle los recursos.
        draw_train(plus_matrix, axis_x, axis_y)
        # Me posiciono 100 pixeles a la derecha, y a la misma altura que la primera plaza del tren
        # para dibujar los recursos
        axis_x = axis_x + 100
        axis_y = -125

        # Puedo generar 1 recurso por cada plaza que hay en el circuito.
        new_res_plus_m, new_res_minus_m = add_private_resources(len(plus_matrix), len(plus_matrix[0]))

        # Agrego los recursos a las matrices I+ e I-
        if len(new_res_plus_m) > 0:
            # Dibujo los nuevos recursos y luego los agrego a la matriz del tren
            draw_private_resources(new_res_plus_m, axis_x, axis_y)
            plus_matrix = np.vstack([plus_matrix, new_res_plus_m])
        if len(new_res_minus_m) > 0:
            minus_matrix = np.vstack([minus_matrix, new_res_minus_m])

        trains.append([plus_matrix, minus_matrix])

        # Despues de dibujar un tren en pflow, me muevo 100 pixeles a la derecha y vuelvo al inicio
        axis_x = axis_x + 100
        axis_y = first_axis_y

    return trains


"""
    Método para generar una cantidad determinada de figuras de circuitos cerrados.
    
    Un circuito se compone de una plaza idle, que es la que tiene un marcado inicial aleatorio
    y de un tren de transiciones y plazas con recursos.
    Cada circuito tiene un numero aleatorio de recursos.

    :param quantity: cantidad de circuitos cerrados a generar.
    :param axis_x: eje x donde se dibuja el circuito en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja el circuito en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los circuitos generados.
"""
def generate_circuits(quantity, axis_x, axis_y):
    global max_tokens_idle

    first_axis_x = axis_x
    first_axis_y = axis_y
    circuits = []

    for index in range(quantity):
        axis_x_idle_place = axis_x - 100
        # Generamos un solo tren
        train = generate_trains(1, axis_x, axis_y)
        # Nos devuelve la I+ e I- del tren generado
        plus_matrix = train[0][0]
        minus_matrix = train[0][1]
        # Obtenemos el numero de transiciones del tren
        number_transitions = len(plus_matrix[0])

        # Plaza(fila) idle
        new_place_idle = np.zeros(number_transitions)
        # Seteamos un marcado aleatorio para la plaza idle
        # La plaza idle siempre se conecta con la ultima transicion de la matriz I+
        new_place_idle[number_transitions - 1] = 1

        # Los tokens no pueden ser menor a 1
        draw_place(str(random.randint(min_tokens_idle, max_tokens_idle)), axis_x_idle_place, axis_y)
        plus_matrix = np.vstack([plus_matrix, new_place_idle])
        # La plaza idle siempre se conecta con la primer transicion de la matriz I-
        new_place_idle = np.roll(new_place_idle, 1)
        minus_matrix = np.vstack([minus_matrix, new_place_idle])
        circuits.append([plus_matrix, minus_matrix])

        axis_x = axis_x + 300
        axis_y = first_axis_y

    return circuits


"""
    Método para generar una cantidad determinada de figuras de fork/join.
    
    Un fork es un proceso que se divide en dos y un join es donde vuelve a unirse.
    Este metodo genera una plaza inicial, de la cual salen dos procesos,
    y una plaza final, en la cual se vuelven a unir.

    :param quantity: cantidad de fork/join a generar.
    :param axis_x: eje x donde se dibuja el fork/join en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja el fork/join en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los fork/joins generados.
"""
def generate_forks_joins(quantity, axis_x, axis_y):
    first_axis_x = axis_x
    first_axis_y = axis_y
    forks = []

    for index in range(quantity):
        # Generamos dos trenes que luego unimos con una plaza inicial y otra final
        trains = generate_trains(2, axis_x, axis_y)
        train1_plus_matrix = trains[0][0]
        train1_minus_matrix = trains[0][1]
        train2_plus_matrix = trains[1][0]
        train2_minus_matrix = trains[1][1]
        train1_number_transitions = len(train1_plus_matrix[0])
        train2_number_transitions = len(train2_plus_matrix[0])

        # Maxima cantidad de transiciones para saber donde dibujar la plaza final
        max_number_transitions = 0
        if train1_number_transitions > train2_number_transitions:
            max_number_transitions = train1_number_transitions
        else:
            max_number_transitions = train2_number_transitions
        # Concatenamos la matrices de los dos trenes
        # Ej Train1 = [[1,0,0,0]       Train2 = [[1,0,0]
        #              [0,1,0,0]                 [0,1,0]
        #              [0,0,1,0]                 [0,0,1]]
        #              [0,0,0,1]]
        #    concat = [ [1,0,0,0,0,0,0]
        #               [0,1,0,0,0,0,0]
        #               [0,0,1,0,0,0,0]
        #               [0,0,0,1,0,0,0]
        #               [0,0,0,0,1,0,0]
        #               [0,0,0,0,0,1,0]
        #               [0,0,0,0,0,0,1]]
        plus_matrix = block_diag(train1_plus_matrix, train2_plus_matrix)
        minus_matrix = block_diag(train1_minus_matrix, train2_minus_matrix)

        number_transitions = len(plus_matrix[0])
        # Agrego plaza del comienzo de la bifurcation. (Horquilla o fork)
        # Esta plaza le da tokens a las transiciones de comienzo de cada uno de los trenes
        new_init_place = np.zeros(number_transitions)
        # Primero la agrego en I+, donde tiene todos ceros.
        plus_matrix = np.vstack([plus_matrix, new_init_place])
        # Luego la agrego en I-
        # La primer transicion del tren 1 siempre es la 0
        new_init_place[0] = 1
        # La primer transicion del tren 2 ahora esta despues de la ultima del tren 1
        new_init_place[train1_number_transitions] = 1
        minus_matrix = np.vstack([minus_matrix, new_init_place])
        # Dibujo la plaza
        draw_place("0", first_axis_x + 100, first_axis_y - 50)

        # Agrego plaza al final de la bifurcacion. (Y griega o join)
        # Esta plaza le quita tokens a las transiciones de finalizacion de cada uno de los trenes
        # Por eso se agrega en I+
        new_finish_place = np.zeros(number_transitions)
        # Primero la agrego en I-, donde tiene todos ceros
        minus_matrix = np.vstack([minus_matrix, new_finish_place])
        # La ultima transicion del primer tren
        new_finish_place[train1_number_transitions - 1] = 1
        # La ultima transicion del tren 2 siempre es la ultima de la nueva matriz
        new_finish_place[number_transitions - 1] = 1

        # Dibujo la plaza final
        draw_place("0", first_axis_x + 100, first_axis_y + (max_number_transitions * 100))
        plus_matrix = np.vstack([plus_matrix, new_finish_place])
        forks.append([plus_matrix, minus_matrix])

        first_axis_x = first_axis_x + 400
        axis_x = first_axis_x
        axis_y = first_axis_y

    return forks


"""
    Método para generar una cantidad determinada de figuras de fork/join con un tren inical.
    
    La figura base es igual a la del fork/join, salvo que en este caso se concatena un tren
    antes del fork/join. De esta forma el proceso inicia con un tren  que se bifurca y
    vuelve a unirse al final.

    :param quantity: cantidad de figuras a generar.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de las figuras generadas.
"""
def generate_fork_join_with_init_train(quantity, axis_x, axis_y):
    first_axis_x = axis_x
    first_axis_y = axis_y
    forks = []

    for index in range(quantity):
        train = generate_trains(1, axis_x, axis_y)

        fj = generate_forks_joins(1, axis_x + 200, axis_y)

        plus_matrix = block_diag(train[0][0], fj[0][0])
        minus_matrix = block_diag(train[0][1], fj[0][1])

        # Conecto el tren con el fork/join
        train_cols = np.shape(train[0][0])[1] - 1
        train_rows = np.shape(train[0][0])[0] - 1
        fj_rows = np.shape(fj[0][0])[0] - 1
        plus_matrix[train_rows + fj_rows][train_cols] = 1

        # Agrego transición final
        number_places = np.shape(plus_matrix)[0]
        finish_transition = np.zeros((number_places, 1))
        finish_transition[number_places - 1] = 1
        plus_matrix = np.hstack((plus_matrix, np.zeros((number_places, 1))))
        minus_matrix = np.hstack((minus_matrix, finish_transition))
        number_transitions = np.shape(plus_matrix)[1]
        draw_transition(axis_x + 300, axis_y + (number_transitions * 50))

        # Agrego plaza idle
        idle_place = np.zeros(number_transitions)
        idle_place[0] = 1
        minus_matrix = np.vstack([minus_matrix, idle_place])
        idle_place = np.roll(idle_place, -1)
        plus_matrix = np.vstack([plus_matrix, idle_place])
        draw_place(str(random.randint(min_tokens_idle, max_tokens_idle)), axis_x, axis_y - 100)

        forks.append([plus_matrix, minus_matrix])
        first_axis_x = first_axis_x + 600
        axis_x = first_axis_x
        axis_y = first_axis_y

    return forks


"""
    Método para generar una cantidad determinada de figuras de fork/join con un tren final.
    
    La figura base es igual a la del fork/join, salvo que en este caso se concatena un tren
    después del fork/join. De esta forma el proceso inicia con una bifurcación la cual se une,
    y finaliza con un tren.

    :param quantity: cantidad de figuras a generar.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de las figuras generadas.
"""
def generate_fork_join_with_final_train(quantity, axis_x, axis_y):
    first_axis_x = axis_x
    first_axis_y = axis_y
    forks = []

    for index in range(quantity):
        fj = generate_forks_joins(1, axis_x, axis_y)

        train = generate_trains(1, axis_x + 400, axis_y)

        plus_matrix = block_diag(fj[0][0], train[0][0])
        minus_matrix = block_diag(fj[0][1], train[0][1])

        # Conecto el fork/join con el tren
        fj_cols = np.shape(fj[0][0])[1] - 1
        fj_rows = np.shape(fj[0][0])[0] - 1
        minus_matrix[fj_rows][fj_cols + 1] = 1

        # Agrego transición inicial
        number_places = np.shape(plus_matrix)[0]
        init_transition = np.zeros((number_places, 1))
        init_transition[fj_rows - 1] = 1
        plus_matrix = np.hstack((plus_matrix, init_transition))
        minus_matrix = np.hstack((minus_matrix, np.zeros((number_places, 1))))
        draw_transition(axis_x + 100, axis_y - 200)

        # Agrego plaza idle
        number_transitions = np.shape(plus_matrix)[1]
        idle_place = np.zeros(number_transitions)
        idle_place[number_transitions - 1] = 1
        minus_matrix = np.vstack([minus_matrix, idle_place])
        idle_place = np.roll(idle_place, -1)
        plus_matrix = np.vstack([plus_matrix, idle_place])
        draw_place(str(random.randint(min_tokens_idle, max_tokens_idle)), axis_x, axis_y - 200)

        forks.append([plus_matrix, minus_matrix])
        first_axis_x = first_axis_x + 600
        axis_x = first_axis_x
        axis_y = first_axis_y

    return forks


"""
    Método para generar una cantidad determinada de figuras de fork/join con tren inicial y final.
    
    La figura base es igual a la del fork/join, salvo que en este caso se concatena un tren
    antes y después del fork/join.
    De esta forma el proceso inicia con un tren que se bifurca, vuelve a unirse y finaliza con un tren.

    :param quantity: cantidad de figuras a generar.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de las figuras generadas.
"""
def generate_fork_join_with_initial_and_final_trains(quantity, axis_x, axis_y):
    first_axis_x = axis_x
    first_axis_y = axis_y
    forks = []

    for index in range(quantity):
        init_train = generate_trains(1, axis_x, axis_y)
        
        fj = generate_forks_joins(1, axis_x + 200, axis_y)

        final_train = generate_trains(1, axis_x + 600, axis_y)

        plus_matrix = block_diag(init_train[0][0], fj[0][0], final_train[0][0])
        minus_matrix = block_diag(init_train[0][1], fj[0][1], final_train[0][1])

        # Conecto el fork/join con los trenes
        init_train_cols = np.shape(init_train[0][0])[1] - 1
        init_train_rows = np.shape(init_train[0][0])[0] - 1
        fj_cols = np.shape(fj[0][0])[1] - 1
        fj_rows = np.shape(fj[0][0])[0] - 1
        plus_matrix[init_train_rows + fj_rows][init_train_cols] = 1
        minus_matrix[init_train_rows + fj_rows + 1][init_train_cols + fj_cols + 2] = 1

        # Agrego plaza idle
        number_transitions = np.shape(plus_matrix)[1]
        new_place_idle = np.zeros(number_transitions)
        new_place_idle[number_transitions - 1] = 1
        draw_place(str(random.randint(min_tokens_idle, max_tokens_idle)), axis_x, axis_y - 100)
        plus_matrix = np.vstack([plus_matrix, new_place_idle])
        new_place_idle = np.roll(new_place_idle, 1)
        minus_matrix = np.vstack([minus_matrix, new_place_idle])

        forks.append([plus_matrix, minus_matrix])
        first_axis_x = first_axis_x + 800
        axis_x = first_axis_x
        axis_y = first_axis_y

    return forks


"""
    Método para generar una cantidad determinada de circuitos fork/join.
    
    A diferencia del método 'generate_forks_joins', donde se genera una plaza inicial,
    de la cual salen dos procesos, y una plaza final, en la cual se vuelven a unir,
    este método genera además una 'plaza idle' la cual se conecta a través de las
    correspondientes transiciones a la plaza inicial (fork) y a la final (join).

    :param quantity: cantidad de fork/join a generar.
    :param axis_x: eje x donde se dibuja el fork/join en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja el fork/join en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los circuitos fork/joins generados.
"""
def generate_circuits_fork_join(quantity, axis_x, axis_y):
    forks_joins = []
    first_axis_y = axis_y
    for index in range(quantity):
        # Generamos el fork/join
        fork_join = generate_forks_joins(1, axis_x, axis_y)
        plus_matrix = fork_join[0][0]
        minus_matrix = fork_join[0][1]

        number_transitions = len(plus_matrix[0])
        number_places = len(plus_matrix)

        # Agrego transicion inicial antes del fork. Esto modifica la anteultima plaza de la matriz I+ del fork_join
        init_transition = np.zeros((number_places, 1))
        init_transition[number_places - 2] = 1
        plus_matrix = np.hstack((plus_matrix, init_transition))
        plus_matrix = np.hstack((plus_matrix, np.zeros((number_places, 1))))
        # Dibujamos la primera transicion
        draw_transition(axis_x + 100, axis_y - 100)

        # Agrego transicion final despues del fork. Esto modifica la ultima plaza de la matriz I- del fork_join
        finish_transition = np.zeros((number_places, 1))
        finish_transition[number_places - 1] = 1
        minus_matrix = np.hstack((minus_matrix, np.zeros((number_places, 1))))
        minus_matrix = np.hstack((minus_matrix, finish_transition))
        # Dibujamos transicion al final
        draw_transition(axis_x + 100, axis_y + (number_transitions * 50) + 200)
        # Agrego plaza idle que conecta la transicion final con la inicial.
        number_transitions = len(plus_matrix[0])
        idle_place = np.zeros(number_transitions)
        idle_place[number_transitions - 1] = 1
        plus_matrix = np.vstack([plus_matrix, idle_place])
        idle_place = np.roll(idle_place, -1)
        minus_matrix = np.vstack([minus_matrix, idle_place])
        # Dibujamos la plaza idle
        draw_place(str(random.randint(min_tokens_idle, max_tokens_idle)), axis_x, axis_y - 100)
        # Nos movemos para no pisar la transicion en el dibujo
        forks_joins.append([plus_matrix, minus_matrix])
        axis_x = axis_x + 400
        axis_y = first_axis_y

    return forks_joins


"""
    Método para generar recursos compartidos.

    A diferencia del método 'add_private_resources', los recursos que genera este método
    son compartidos entre distintas figuras.
    Estos recursos se conectan a transiciones de distintas figuras, contrarios al flujo normal
    del proceso.

    Este método debe ser llamado luego de haber generado las distintas figuras para hacer las conexiones.

    :param number_transitions: número de transiciones en las matrices I+ e I-.
    :param min_quantity: mínimo de recursos compartidos a generar.
    :param max_quantity: máximo de recursos compartidos a generar.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los recursos compartidos generados.
"""
def generate_share_resources(number_transitions, min_quantity, max_quantity, axis_x, axis_y):
    global invariants

    # Se determina de manera aleatoria la cantidad de recursos compartidos dentro del rango especificado
    num_share_resources = np.random.randint(low=min_quantity, high=max_quantity+1)

    # Cada plaza nueva estara conectada a diferentes transiciones
    plus_matrix = np.empty((0, number_transitions), dtype=int)
    # Por cada recurso (plaza) nueva, selecciono un numero aleatorio de invariantes a los que se va a conectar
    for index in range(num_share_resources):
        new_share_resource = np.zeros(number_transitions)
        # Siempre selecciono 2 invariantes para que el recurso sea compartido
        for inv in random.sample(invariants, k=2):
            # De cada invariante selecciono una transición aleatoriamente, salvo la primera.
            random_transition = np.random.randint(inv[0], inv[-1])
            new_share_resource[random_transition] = 1

        # Una vez que selecciono una transicion de cada invariante, agrego la plaza a la matriz
        plus_matrix = np.vstack((plus_matrix, new_share_resource))
        # Dibujo la plaza
        draw_place("1", axis_x, axis_y)
        axis_x = axis_x + 100

    # Una vez que tengo la matriz I+, creo la matriz I-
    minus_matrix = np.roll(plus_matrix, -1)

    # TODO verificar esta seccion cuando se solucione el problema de recursos en fork join
    # index = 0
    # while index < (len(plus_matrix)):
    #     # Si no hay forks
    #     if len(np.where(np.sum(im, axis=1) > 1)[0]) < 1:
    #         break
    #     # Agrego recursos a los fork
    #     # Busco los fork de la red y los conecto a un recurso compartido

    #     for fork in np.where(np.sum(im, axis=1) > 1)[0]:
    #         if index < (len(plus_matrix)) and random.getrandbits(1):
    #             plus_matrix[index] = np.bitwise_or(im[fork].astype(int), plus_matrix[index].astype(int)).astype(float)
    #             minus_matrix[index] = np.bitwise_or(ip[fork].astype(int), minus_matrix[index].astype(int)).astype(float)
    #             index = index + 1

    #     # Agrego recursos a los join
    #     # Busco los join de la red y los conecto a un recurso compartido
    #     for join in (np.where(np.sum(ip, axis=1) > 1)[0]):
    #         if index < (len(plus_matrix)) and random.getrandbits(1):
    #             minus_matrix[index] = np.bitwise_or(ip[join].astype(int), minus_matrix[index].astype(int)).astype(float)
    #             plus_matrix[index] = np.bitwise_or(im[join].astype(int), plus_matrix[index].astype(int)).astype(float)
    #             index = index + 1


    return plus_matrix, minus_matrix


"""
    Método para generar recursos compartidos complejos.

    De manera similar al método anterior, 'generate_share_resources', estos recursos se conectan
    a transiciones de distintas figuras, contrarios al flujo normal del proceso, 
    pero tienen  la característica particular de estar conectados más de una vez a un invariante

    Este método debe ser llamado luego de haber generado las distintas figuras para hacer las conexiones.

    :param number_transitions: número de transiciones en las matrices I+ e I-.
    :param cmpx_resources_quantity: cantidad de recursos compartidos a generar.
    :param axis_x: eje x donde se dibuja la figura en el archivo '.pflow'.
    :param axis_y: eje y donde se dibuja la figura en el archivo '.pflow'.
    :return: retorna una lista donde cada elemento contiene las matrices I+ e I- 
                de los recursos compartidos generados.
"""
def generate_cmpx_resources(number_transitions, cmpx_resources_quantity, axis_x, axis_y):
    global invariants

    # Cada plaza nueva estara conectada a diferentes transiciones
    plus_matrix = np.empty((0, number_transitions), dtype=int)
    # Por cada recurso (plaza) nueva, selecciono un numero aleatorio de invariantes a los que se va a conectar
    for index in range(cmpx_resources_quantity):
        new_share_resource = np.zeros(number_transitions)
        # Siempre selecciono 3 o mas invariantes para que el recurso sea compartido
        invariants_share_resource = random.randint(3, len(invariants))
        for inv in random.sample(invariants, k=invariants_share_resource):
            # De cada invariante selecciono una transición aleatoriamente, salvo la primera.
            random_transition = np.random.randint(inv[0], inv[-1])
            new_share_resource[random_transition] = 1

        # Una vez que selecciono una transicion de cada invariante, agrego la plaza a la matriz
        plus_matrix = np.vstack((plus_matrix, new_share_resource))
        # Dibujo la plaza
        draw_place("1", axis_x, axis_y)
        axis_x = axis_x + 100

    # Una vez que tengo la matriz I+, creo la matriz I-
    minus_matrix = np.roll(plus_matrix, -1)

    return plus_matrix, minus_matrix


"""
    Método encargado de la generación del archivo '.pflow' con la cantidad de figuras especificadas
    en los distintos parámetros.

    :param path: directorio donde se creará el archivo '.pflow'.
    :param circuits_quantity: cantidad de figuras de circuitos a generar. Por defecto 1.
    :param cir_fj_w_init_train: cantidad de figuras fork/join con tren inicial a generar. Por defecto 1.
    :param cir_fj_w_final_train: cantidad de figuras fork/join con tren final a generar. Por defecto 1.
    :param cir_fj_w_init_final_train: cantidad de figuras fork/join con tren inicial y final a generar. Por defecto 1.
    :param cir_fork_join_quantity: cantidad de figuras fork/join a generar. Por defecto 1.
    :param cmpx_resources_quantity: cantidad de recursos compartidos complejos. Por defecto 0.
    :param min_share_resources: cantidad mínima de recursos compartidos simpes. Por defecto 2.
    :param max_share_resources: cantidad máxima de recursos compartidos simpes. Por defecto 5.
"""
def generate_petri_networks(path, circuits_quantity=1, cir_fj_w_init_train=1, cir_fj_w_final_train=1,
                            cir_fj_w_init_final_train=1, cir_fork_join_quantity=1, cmpx_resources_quantity=0,
                            min_share_resources=2, max_share_resources=5):
    global max_circuits
    global max_cir_forks_joins
    global global_axis_x
    global global_axis_y
    global ip
    global im
    first_global_axis_x = global_axis_x
    total_items = 0

    # Agrego circuitos
    new_circuits = generate_circuits(circuits_quantity, global_axis_x, global_axis_y)
    for cir_plus_matrix, cir_minus_matrix in new_circuits:
        ip = block_diag(ip, cir_plus_matrix)
        im = block_diag(im, cir_minus_matrix)
        total_items = total_items + 1

    # Cada circuito tiene un ancho maximo de 300 pixeles.
    global_axis_x = global_axis_x + (circuits_quantity * 300)

    # Agrego fork join con tren inicial
    fork_w_init_train = generate_fork_join_with_init_train(cir_fj_w_init_train, global_axis_x, global_axis_y)
    for init_fj_plus_matrix, init_fj_minus_matrix in fork_w_init_train:
        ip = block_diag(ip, init_fj_plus_matrix)
        im = block_diag(im, init_fj_minus_matrix)
        total_items = total_items + 1

    global_axis_x = global_axis_x + (cir_fj_w_init_train * 600)

    # Agrego fork join con tren final
    fork_w_final_train = generate_fork_join_with_final_train(cir_fj_w_final_train, global_axis_x, global_axis_y + 100)
    for final_fj_plus_matrix, final_fj_minus_matrix in fork_w_final_train:
        ip = block_diag(ip, final_fj_plus_matrix)
        im = block_diag(im, final_fj_minus_matrix)
        total_items = total_items + 1

    global_axis_x = global_axis_x + (cir_fj_w_final_train * 600)

    # Agrego fork join con trenes al inicio y al final
    fork_w_if_trains = generate_fork_join_with_initial_and_final_trains(cir_fj_w_init_final_train, global_axis_x,
                                                                        global_axis_y)
    for if_fj_plus_matrix, if_fj_minus_matrix in fork_w_if_trains:
        ip = block_diag(ip, if_fj_plus_matrix)
        im = block_diag(im, if_fj_minus_matrix)
        total_items = total_items + 1

    global_axis_x = global_axis_x + (cir_fj_w_init_final_train * 800)

    # Agrego circuitos con fork join
    new_cir_fork_join = generate_circuits_fork_join(cir_fork_join_quantity, global_axis_x, global_axis_y)

    for fj_plus_matrix, fj_minus_matrix in new_cir_fork_join:
        ip = block_diag(ip, fj_plus_matrix)
        im = block_diag(im, fj_minus_matrix)
        total_items = total_items + 1

    # Agrego recursos compartidos
    global_axis_x = first_global_axis_x
    global_axis_y = global_axis_y - 200
    number_transitions = np.shape(ip)[1]
    new_sr_ip, new_sr_im = generate_share_resources(number_transitions, min_share_resources, max_share_resources,
                                                    global_axis_x, global_axis_y)

    ip = np.vstack((ip, new_sr_ip))
    im = np.vstack((im, new_sr_im))

    # Agrego recursos compartidos complejos
    global_axis_y = global_axis_y - 100
    number_transitions = np.shape(ip)[1]
    cmpx_sr_ip, cmpx_sr_im = generate_cmpx_resources(number_transitions, cmpx_resources_quantity, global_axis_x, global_axis_y)
                                  
    ip = np.vstack((ip, cmpx_sr_ip))
    im = np.vstack((im, cmpx_sr_im))

    # Uno todas las cosas dibujadas
    draw_arcs(ip, im)
    ElementTree(document).write(path + "/net.pflow", xml_declaration=True, encoding='utf-8',
                                method="xml")

    reinit_global_vars()


"""
    Método para reiniciar las variables globales y el documento donde se escribe el archivo '.pflow'.
    
    Este método es necesario para la generación masiva de redes aleatorias.
"""
def reinit_global_vars():

    global global_axis_x
    global global_axis_y
    global place_index
    global transition_index
    global invariants
    global ip
    global im
    global document
    global subnet
    ip = np.empty((0, 0), dtype=int)
    im = np.empty((0, 0), dtype=int)

    document = Element('document')
    subnet = SubElement(document, 'subnet')
    SubElement(subnet, 'x').text = '0'
    SubElement(subnet, 'y').text = '0'

    global_axis_x = -200
    global_axis_y = -200
    place_index = 1
    transition_index = 1
    invariants = []



#TODO seccion para testing de las diferentes funcionalidades

# circuits_quantity = random.randint(1, max_circuits)
# cir_forks_joins_quantity = random.randint(1, max_cir_forks_joins)
# generate_petri_networks('.',circuits_quantity=1, cir_fj_w_init_train=1, cir_fj_w_final_train=0, cir_fj_w_init_final_train=0, cir_fork_join_quantity=0, cmpx_resources_quantity=6)
# print(invariants)

# item = generate_circuits_fork_join(1, global_axis_x, global_axis_y)
# draw_arcs(item[0][0], item[0][1])
# ElementTree(document).write(time.strftime("%Y%m%d-%H%M%S") + ".pflow", xml_declaration=True, encoding='utf-8',method="xml")
