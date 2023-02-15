def procName = 'LTM - Get balancing pool'
def stepName = 'LTM - GetPool'
procedure procName, description: 'Get pool configuration', {
    formalOutputParameter 'poolGet', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('get_balancing_pool');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
