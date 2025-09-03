import xmltodict, json

with open("./net.pflow", 'r') as myfile:
    obj = xmltodict.parse(myfile.read())
print(json.dumps(obj))