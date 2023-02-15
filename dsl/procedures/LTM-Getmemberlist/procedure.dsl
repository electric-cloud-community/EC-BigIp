def procName = 'LTM - Get member list'
def stepName = 'LTM - GetMemberList'
procedure procName, description: 'Get all pool members that make up a load balancing pool', {
    formalOutputParameter 'poolMemberGetList', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('get_pool_member_list');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
