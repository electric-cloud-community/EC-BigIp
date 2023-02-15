def procName = 'LTM - Get pool list'
def stepName = 'LTM - GetPoolList'
procedure procName, description: 'Get balancing pool list', {
    formalOutputParameter 'poolGetList', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('get_balancing_pool_list');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
