package dsl

def projName = args.projectName
def procName = args.procedureName
def params = args.params
def resName = args.resName

project projName, {
    procedure procName, {
        resourceName = resName

        step procName, {
            description = ''
            subprocedure = procName
            subproject = '/plugins/EC-BigIp/project'

            params.each { name, defValue ->
                actualParameter name, '$[' + name + ']'
            }
        }

        params.each {name, defValue ->
            formalParameter name, defaultValue: defValue, {
                type = 'textarea'
            }
        }
    }
}
