package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*

//@Ignore
@Stepwise
class LTMBalancingPool extends PluginTestHelper {
    static final String projectName = "Spec Tests: balancing pool"
    static final String configName = randomize("specs_config_" + projectName.replaceAll(' ', '_'))

    static final String procedureCreate  = "LTM - Create or update balancing pool"
    static final String procedureGet     = "LTM - Get balancing pool"
    static final String procedureGetList = "LTM - Get pool list"
    static final String procedureManage  = "LTM - Create or update balancing pool"
    static final String procedureDelete  = "LTM - Delete balancing pool"

    static BigIpClient helper

    static resultPropertyBase = '/myJob/'

    static partition = 'Common'
    static balancingPool = 'test_1234'

    @Shared
    String caseId

    def doSetupSpec() {
        helper = getBigIpHelper()

        createConfiguration(configName)

        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureCreate, params: createBalancingPoolParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureGet, params: getBalancingPoolParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureGetList, params: getPoolListParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureManage, params: createBalancingPoolParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureDelete, params: deleteBalancingPoolParams]

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
    def '#caseId. LTM - Create balancing pool'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            name               : balancingPool,
            updateAction       : method,
            optionalParameters : 'loadBalancingMode=round-robin',
            resultPropertySheet: resultPropertyBase + 'poolCreate',
        ]

        when:
        def result = runProcedure(projectName, procedureCreate, procedureParams)

        then:
        logger.debug('#001: '+getJobLink(result.jobId))
        logger.debug('#002: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#003: '+objectToJson(properties))
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
    def 'LTM - Get balancing pool'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            name               : balancingPool,
            resultPropertySheet: resultPropertyBase + 'poolGet',
        ]

        when:
        def result = runProcedure(projectName, procedureGet, procedureParams)

        then:
        logger.debug('#004: '+getJobLink(result.jobId))
        logger.debug('#005: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#006: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }

    @Sanity
    @Unroll
    def 'LTM - Get pool list'() {
        given:
        def procedureParams = [
            config             : configName,
            resultPropertySheet: resultPropertyBase + 'poolGetList',
        ]

        when:
        def result = runProcedure(projectName, procedureGetList, procedureParams)

        then:
        logger.debug('#005: '+getJobLink(result.jobId))
        logger.debug('#006: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#007: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }

    @Sanity
    @Unroll
    def '#caseId. LTM - Manage balancing pool'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            name               : balancingPool,
            updateAction       : method,
            optionalParameters : 'loadBalancingMode=least-connections-member',
            resultPropertySheet: resultPropertyBase + 'poolManage',
        ]

        when:
        def result = runProcedure(projectName, procedureManage, procedureParams)

        then:
        logger.debug('#007: '+getJobLink(result.jobId))
        logger.debug('#008: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#009: '+objectToJson(properties))
        assert properties['resultPropertySheet']

        where:
        caseId    | method  | logSummary
        'update'  | 'PATCH' | "has been updated"
        'replace' | 'PUT'   | "has been replaced"
    }

    @Sanity
    @Unroll
    def 'LTM - Delete balancing pool'() {
        given:
        def procedureParams = [
            config             : configName,
            partition          : partition,
            name               : balancingPool,
            resultPropertySheet: resultPropertyBase + 'poolDelete',
        ]

        when:
        def result = runProcedure(projectName, procedureDelete, procedureParams)

        then:
        logger.debug('#009: '+getJobLink(result.jobId))
        logger.debug('#010: '+result)
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#011: '+objectToJson(properties))
        assert properties['resultPropertySheet']
    }
}
