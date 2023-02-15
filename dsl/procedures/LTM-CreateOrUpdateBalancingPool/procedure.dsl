def procName = 'LTM - Create or update balancing pool'
def stepName = 'LTM - CreateOrUpdatePool'
procedure procName, description: 'Create or update balancing pool configuration', {
    formalOutputParameter 'poolCreateOrUpdate', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('create_or_update_balancing_pool');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
