def procName = 'LTM - Delete balancing pool'
def stepName = 'LTM - DeletePool'
procedure procName, description: 'Delete balancing pool configuration', {
    formalOutputParameter 'poolDelete', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('delete_balancing_pool');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
