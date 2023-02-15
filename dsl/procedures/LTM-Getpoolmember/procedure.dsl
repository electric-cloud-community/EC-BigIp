def procName = 'LTM - Get pool member'
def stepName = 'LTM - GetPoolMember'
procedure procName, description: 'Get a specified pool member from a load balancing pool', {
    formalOutputParameter 'poolMemberGet', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('get_pool_member');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
