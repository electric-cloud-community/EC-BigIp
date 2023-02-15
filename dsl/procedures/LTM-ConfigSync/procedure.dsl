def procName = 'LTM - Config sync'
def stepName = 'LTM - ConfigSync'
procedure procName, description: 'Synchronizes the local BIG-IP device to the device group', {
    formalOutputParameter 'configSync', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('config_sync');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
