def procName = 'LTM - Change pool member status'
def stepName = 'LTM - ChangePoolMembersStatus'
procedure procName, description: 'Change pool member status', {
    formalOutputParameter 'poolMemberStatus', {
        description = '''
'''
    }

    step stepName,
        command: """
\$[/myProject/scripts/preamble]
use EC::BigIp::Handler;
EC::BigIp::Handler->new->run_step('change_pool_member_status');
""",
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes'

}
