package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.NewFeature
import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*
import groovy.json.JsonSlurper

//@Ignore
class LTMChangePoolMemberStatusTests extends PluginTestHelper {

    static def projectName = 'TestProject: Change pool member status'
    static def configurationName = 'configuration1'
    static def bigIpClient

    static def TC = [
            C387326: [ids: 'C387326', description: 'Change pool member status to user-down'],
            C387327: [ids: 'C387327', description: 'Change pool member status to disabled'],
            C387328: [ids: 'C387328', description: 'Change pool member status to enabled'],
            C387329: [ids: 'C387329', description: 'empty config'],
            C387330: [ids: 'C387330', description: 'empty partition name'],
            C387331: [ids: 'C387331', description: 'empty pool name'],
            C387332: [ids: 'C387332', description: 'empty member name'],
            C387333: [ids: 'C387333', description: 'empty status'],
            C387364: [ids: 'C387364', description: 'empty Result Property Sheet'],
            C387365: [ids: 'C387365', description: 'wrong config'],
            C387366: [ids: 'C387366', description: 'wrong partition name'],
            C387367: [ids: 'C387367', description: 'wrong pool name'],
            C387368: [ids: 'C387368', description: 'wrong member name'],
            C387369: [ids: 'C387369', description: 'wrong status'],
    ]

    static def expectedSummaries = [
            default: 'Pool member Common~POOL~NAME has been changed',
            emptyConfig: 'No config name',
            emptyPartition: 'Required parameter "partition" is missing',
            emptyPoolName: 'Required parameter "pool_name" is missing',
            emptyName: 'Required parameter "name" is missing',
            emptyStatus: 'Required parameter "set_status" is missing',
            emptyProperty: 'Required parameter "resultPropertySheet" is missing',
            wrongConfig: 'Configuration "wrongConfig" does not exist',
            wrongPartition: 'The requested Pool Member (/wrongPartition/test_5679 /wrongPartition/10.200.1.137 80) was not found.',
            wrongPoolName: 'The requested Pool Member (/Common/wrongPool /Common/10.200.1.137 80) was not found.',
            wrongName: 'The requested Pool Member (/Common/test_5679 /Common/wrongName 80) was not found.',
            wrongStatus: 'Wrong value for set_status: wrongStatus',

    ]

    static def expectedLogs = [
            default : ["PATCH ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members",
            '{"session":"SESSION","name":"10.200.1.137:80","partition":"Common","pool_name":"test_5679","state":"STATE"}'],
    ]

    static def memberStatuses = [
            'force_off' : [state: 'user-down', session: 'user-disabled', sendState: 'user-down'],
            'disabled'  : [state: 'unchecked', session: 'user-disabled', sendState: 'user-up'],
            'enabled'   : [state: 'unchecked', session: 'user-enabled', sendState: 'user-up'],
    ]

    static def defaultPoolName = 'test_5679'
    static def defaultName = '10.200.1.137:80'
    static def defaultPartition = 'Common'

    static def testCaseHelper

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(changePoolMemberStatus)
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: createPoolMember, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: changePoolMemberStatus, params: changePoolMemberStatusParams]

    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

//    @Ignore
    @Sanity
    @Unroll
    def 'CreatePoolMember: Sanity #caseId.ids #caseId.description'() {
        bigIpClient.createBalancingPool(partition, poolName)

        def preconditionParams = [
                config             : configName,
                updateAction       : '0',
                name               : name,
                optionalParameters : '',
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)
        def preconditionalPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        preconditionalPoolMemberInfo.items[0].selfLink = preconditionalPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        if (status == 'enabled'){
            def parameters = [
                    config             : configName,
                    name               : name,
                    partition          : partition,
                    pool_name          : poolName,
                    set_status         : 'disabled',
                    resultPropertySheet: resultPropertySheet,
            ]
            runProcedure(projectName, changePoolMemberStatus, parameters)
            preconditionalPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
            preconditionalPoolMemberInfo.items[0].selfLink = preconditionalPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")
        }


        given: "Tests parameters"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                set_status         : status,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, changePoolMemberStatus, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$changePoolMemberStatus/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            for (log in expectedLog) {
                result.logs.contains(log
                        .replace('POOL', poolName)
                        .replace('SESSION', memberStatuses[status].session)
                        .replace('STATE', memberStatuses[status].sendState))
            }

            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            new JsonSlurper().parseText(outputParameters.poolMemberStatus) == poolMemberInfo.items[0]

            poolMemberInfo.items[0].state == memberStatuses[status].state
            poolMemberInfo.items[0].session == memberStatuses[status].session

            if (status == 'force_off') {
                preconditionalPoolMemberInfo.items[0].state != poolMemberInfo.items[0].state
            }

            preconditionalPoolMemberInfo.items[0].session != poolMemberInfo.items[0].session

            poolMemberInfo.items[0]
            poolMemberInfo.items[0].name == name
            poolMemberInfo.items[0].partition == partition
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            bigIpClient.deleteNode(partition, name)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | name        | poolName        | partition         | status      | resultPropertySheet       | expectedSummary           | expectedLog
        TC.C387326 | configurationName | defaultName | defaultPoolName | defaultPartition  | 'force_off' | '/myJob/poolMemberStatus' | expectedSummaries.default | expectedLogs.default
    }


//    @Ignore
    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)

        testCaseHelper.testCasePrecondition("Create pool member with name $name in pool $poolName")
        def preconditionParams = [
                config             : configName,
                updateAction       : '0',
                name               : name,
                optionalParameters : '',
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)
        def preconditionalPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        preconditionalPoolMemberInfo.items[0].selfLink = preconditionalPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        if (status == 'enabled'){
            def parameters = [
                    config             : configName,
                    name               : name,
                    partition          : partition,
                    pool_name          : poolName,
                    set_status         : 'disabled',
                    resultPropertySheet: resultPropertySheet,
            ]
            testCaseHelper.testCasePrecondition("Set pool member status to disabled")
            runProcedure(projectName, changePoolMemberStatus, parameters)
            preconditionalPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
            preconditionalPoolMemberInfo.items[0].selfLink = preconditionalPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")
        }


        given: "Tests parameters"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                set_status         : status,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $changePoolMemberStatus with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, changePoolMemberStatus, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$changePoolMemberStatus/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            for (log in expectedLog) {
                testCaseHelper.addExpectedResult("Job Logs: ${log.replace('POOL', poolName).replace('SESSION', memberStatuses[status].session).replace('STATE', memberStatuses[status].sendState)}")
                result.logs.contains(log
                        .replace('POOL', poolName)
                        .replace('SESSION', memberStatuses[status].session)
                        .replace('STATE', memberStatuses[status].sendState))
            }

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolMemberCreate: ${outputParameters.poolMemberCreate}")
            new JsonSlurper().parseText(outputParameters.poolMemberStatus) == poolMemberInfo.items[0]

            testCaseHelper.addExpectedResult("BigIp: Member status should be ${poolMemberInfo.items[0].state}")
            poolMemberInfo.items[0].state == memberStatuses[status].state
            poolMemberInfo.items[0].session == memberStatuses[status].session

            if (status == 'force_off') {
                testCaseHelper.addExpectedResult("BigIp: Member status should be changed from ${preconditionalPoolMemberInfo.items[0].state} to ${poolMemberInfo.items[0].state}")
                preconditionalPoolMemberInfo.items[0].state != poolMemberInfo.items[0].state
            }

            testCaseHelper.addExpectedResult("BigIp: Member session should be changed from ${preconditionalPoolMemberInfo.items[0].session} to ${poolMemberInfo.items[0].session}")
            preconditionalPoolMemberInfo.items[0].session != poolMemberInfo.items[0].session

            testCaseHelper.addExpectedResult("member pool json: ${poolMemberInfo.items[0]}")
            poolMemberInfo.items[0]
            poolMemberInfo.items[0].name == name
            poolMemberInfo.items[0].partition == partition
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            bigIpClient.deleteNode(partition, name)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | name        | poolName        | partition         | status      | resultPropertySheet       | expectedSummary           | expectedLog
        TC.C387326 | configurationName | defaultName | defaultPoolName | defaultPartition  | 'force_off' | '/myJob/poolMemberStatus' | expectedSummaries.default | expectedLogs.default
        TC.C387327 | configurationName | defaultName | defaultPoolName | defaultPartition  | 'disabled'  | '/myJob/poolMemberStatus' | expectedSummaries.default | expectedLogs.default
        TC.C387328 | configurationName | defaultName | defaultPoolName | defaultPartition  | 'enabled'   | '/myJob/poolMemberStatus' | expectedSummaries.default | expectedLogs.default
    }

//    @Ignore
    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Negative #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        def preconditionalPoolMemberInfo
        if (createPoolMembers) {
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $defaultPoolName")
            bigIpClient.createBalancingPool(defaultPartition, defaultPoolName)

            testCaseHelper.testCasePrecondition("Create pool member with name $defaultName in pool $defaultPoolName")
            def preconditionParams = [
                    config             : configurationName,
                    updateAction       : '0',
                    name               : defaultName,
                    optionalParameters : '',
                    partition          : defaultPartition,
                    pool_name          : defaultPoolName,
                    resultPropertySheet: resultPropertySheet,
            ]
            runProcedure(projectName, createPoolMember, preconditionParams)
            preconditionalPoolMemberInfo = bigIpClient.getPoolMemberInfo(defaultPoolName, defaultName)
        }

        given: "Tests parameters"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                set_status         : status,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $changePoolMemberStatus with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, changePoolMemberStatus, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$changePoolMemberStatus/summary", result.jobId)

        def poolMemberInfo
        if (createPoolMembers) {
            poolMemberInfo = bigIpClient.getPoolMemberInfo(defaultPoolName, defaultName)
        }

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog)

            if (createPoolMembers) {
                testCaseHelper.addExpectedResult("BigIp: pool Member Status and session shouldn't be changed")
                poolMemberInfo == preconditionalPoolMemberInfo
            }
        }

        cleanup:
        try {
            if (createPoolMembers) {
                bigIpClient.deleteBalancingPool(defaultPartition, defaultPoolName)
                bigIpClient.deleteNode(defaultPartition, defaultName)
            }
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | name              | poolName        | partition         | status        | resultPropertySheet       | expectedSummary                  | expectedLog                      | createPoolMembers
        TC.C387329 | ''                | defaultName       | defaultPoolName | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.emptyConfig    | expectedSummaries.emptyConfig    | false
        TC.C387330 | configurationName | defaultName       | defaultPoolName | ''                | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.emptyPartition | expectedSummaries.emptyPartition | false
        TC.C387331 | configurationName | defaultName       | ''              | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.emptyPoolName  | expectedSummaries.emptyPoolName  | false
        TC.C387332 | configurationName | ''                | defaultPoolName | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.emptyName      | expectedSummaries.emptyName      | false
        TC.C387333 | configurationName | defaultName       | defaultPoolName | defaultPartition  | ''            | '/myJob/poolMemberStatus' | expectedSummaries.emptyStatus    | expectedSummaries.emptyStatus    | false
        TC.C387364 | configurationName | defaultName       | defaultPoolName | defaultPartition  | 'force_off'   | ''                        | expectedSummaries.emptyProperty  | expectedSummaries.emptyProperty  | false

        TC.C387365 | 'wrongConfig'     | defaultName       | defaultPoolName | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.wrongConfig    | expectedSummaries.wrongConfig    | true
        TC.C387366 | configurationName | defaultName       | defaultPoolName | 'wrongPartition'  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.wrongPartition | expectedSummaries.wrongPartition | true
        TC.C387367 | configurationName | defaultName       | 'wrongPool'     | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.wrongPoolName  | expectedSummaries.wrongPoolName  | true
        TC.C387368 | configurationName | 'wrongName:80'    | defaultPoolName | defaultPartition  | 'force_off'   | '/myJob/poolMemberStatus' | expectedSummaries.wrongName      | expectedSummaries.wrongName      | true
        TC.C387369 | configurationName | defaultName       | defaultPoolName | defaultPartition  | 'wrongStatus' | '/myJob/poolMemberStatus' | expectedSummaries.wrongStatus    | expectedSummaries.wrongStatus    | true

    }

}