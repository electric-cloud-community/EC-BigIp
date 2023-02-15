package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*

//@Ignore
@Stepwise
class LTMBalancingPoolMember extends PluginTestHelper {
    static final String projectName = "Spec Tests: balancing pool member"
    static final String configName = randomize("specs_config_" + projectName.replaceAll(' ', '_'))

    static final String procedureMemberCreate  = "LTM - Create or update pool member"
    static final String procedureMemberGet     = "LTM - Get pool member"
    static final String procedureMemberGetList = "LTM - Get member list"
    static final String procedureMemberManage  = "LTM - Create or update pool member"
    static final String procedureMemberStatus  = "LTM - Change pool member status"
    static final String procedureMemberDelete  = "LTM - Delete pool member"

    static BigIpClient helper

    static resultPropertyBase = '/myJob/'

    static partition = 'Common'
    static balancingPool = 'test_5678'
    static balancingPoolMemberAddress = '10.201.2.28'
    static balancingPoolMemberName = balancingPoolMemberAddress+':80'

    @Shared
    String caseId

    @Shared
    String status

    def doSetupSpec() {
        helper = getBigIpHelper()

        createConfiguration(configName)

        helper.createBalancingPool(partition, balancingPool)

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberCreate, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberGet, params: getPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberGetList, params: getMemberListParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberManage, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberStatus, params: changePoolMemberStatusParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureMemberDelete, params: deletePoolMemberParams]

    }

    def doCleanupSpec() {
        deleteConfiguration(PLUGIN_NAME, configName)
        conditionallyDeleteProject(projectName)

       try {
            helper.deleteBalancingPool(partition, balancingPool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }
    }

    @Sanity
    @Unroll
    def '#caseId. LTM - Create pool member'() {
        given:
        def procedureParams = [
            config             : configName,
            updateAction       : method,
            partition          : partition,
            pool_name          : balancingPool,
            name               : balancingPoolMemberName,
            optionalParameters : 'allowNat=no',
            resultPropertySheet: resultPropertyBase + 'poolMemberCreate',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberCreate, procedureParams)

        then:
        logger.debug('#021: '+getJobLink(result.jobId))
        logger.debug('#022: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#023: '+objectToJson(properties))
        assert properties['resultPropertySheet']

        where:
        caseId    | method  | logSummary
        'create'  | '0'     | "has been created"
        'nothing' | '0'     | "hasn't been touched"
        'update'  | 'PATCH' | "has been updated"
        'replace' | 'PUT'   | "has been replaced"
    }


    @Sanity
    @Unroll
    def 'LTM - Get pool member'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            pool_name          : balancingPool,
            name               : balancingPoolMemberName,
            resultPropertySheet: resultPropertyBase + 'poolMemberGet',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberGet, procedureParams)

        then:
        logger.debug('#024: '+getJobLink(result.jobId))
        logger.debug('#025: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#026: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }

    @Sanity
    @Unroll
    def 'LTM - Get member list'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            pool_name          : balancingPool,
            resultPropertySheet: resultPropertyBase + 'poolMemberGetList',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberGetList, procedureParams)

        then:
        logger.debug('#025: '+getJobLink(result.jobId))
        logger.debug('#026: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#027: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }

    @Sanity
    @Unroll
    def '#caseId. LTM - Manage pool member'() {
        given:
        def procedureParams = [
            config             : configName,
            updateAction       : method,
            partition          : partition,
            pool_name          : balancingPool,
            name               : balancingPoolMemberName,
            optionalParameters : 'allowNat=yes',
            resultPropertySheet: resultPropertyBase + 'poolMemberManage',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberManage, procedureParams)

        then:
        logger.debug('#027: '+getJobLink(result.jobId))
        logger.debug('#028: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#029: '+objectToJson(properties))
        assert properties['resultPropertySheet']

        where:
        caseId    | method  | logSummary
        'update'  | 'PATCH' | "has been updated"
        'replace' | 'PUT'   | "has been replaced"
    }

    @Sanity
    @Unroll
    def '#caseId. LTM - Change pool member status'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            pool_name          : balancingPool,
            name               : balancingPoolMemberName,
            set_status         : status,
            resultPropertySheet: resultPropertyBase + 'poolMemberStatus',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberStatus, procedureParams)

        then:
        logger.debug('#030: '+getJobLink(result.jobId))
        logger.debug('#031: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#032: '+objectToJson(properties))
        assert properties['resultPropertySheet']
        where:
        caseId | status
        '01'   | 'enabled'
        '02'   | 'disabled'
        '03'   | 'force_off'
    }

    @Sanity
    @Unroll
    def 'LTM - Delete balancing pool'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            pool_name          : balancingPool,
            name               : balancingPoolMemberName,
            resultPropertySheet: resultPropertyBase + 'poolMemberDelete',
        ]

        when:
        def result = runProcedure(projectName, procedureMemberDelete, procedureParams)

        then:
        logger.debug('#033: '+getJobLink(result.jobId))
        logger.debug('#034: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#035: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }
}
