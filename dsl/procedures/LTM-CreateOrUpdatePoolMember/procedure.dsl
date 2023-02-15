def procName = 'LTM - Create or update pool member'
def stepName = 'LTM - CreateOrUpdatePoolMember'
procedure procName, description: 'Create or update the set of pool members that are associated with a load balancing pool', {
    formalOutputParameter 'poolMemberCreateOrUpdate', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('create_or_update_pool_member');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
