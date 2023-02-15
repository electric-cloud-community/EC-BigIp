def procName = 'LTM - Delete pool member'
def stepName = 'LTM - DeletePoolMember'
procedure procName, description: 'Delete a specified pool member from a load balancing pool', {
    formalOutputParameter 'poolMemberDelete', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('delete_pool_member');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
