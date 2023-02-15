package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.NewFeature
import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*
import groovy.json.JsonSlurper

//@Ignore
class LTMGetMembersListTests extends PluginTestHelper {

    static def projectName = 'TestProject: GetListMember'
    static def configurationName = 'configuration1'
    static def bigIpClient


    static def TC = [
            C387381: [ids: 'C387381', description: 'Get Member List - empty pool'],
            C387382: [ids: 'C387382', description: 'Get Member List - pool contains one member'],
            C387383: [ids: 'C387383', description: 'Get Member List - pool contains some members'],
            C387404: [ids: 'C387404', description: 'Get Member List - pool contains a lot of members'],
            C387398: [ids: 'C387398', description: 'Get Member List - there some pools, pool contains some members'],

            C387416: [ids: 'C387416', description: 'empty config'],
            C387417: [ids: 'C387417', description: 'empty partition name'],
            C387418: [ids: 'C387418', description: 'empty pool name'],
            C387419: [ids: 'C387419', description: 'empty property'],
            C387420: [ids: 'C387420', description: 'wrong config'],
            C387421: [ids: 'C387421', description: 'wrong partition name'],
            C387422: [ids: 'C387422', description: 'wrong pool name'],

    ]

    static def expectedSummaries = [
            default       : 'Pool member list has been gotten',
            emptyConfig : 'No config name\n\n\n',
            emptyPartitionNname : 'Required parameter "partition" is missing\n\n',
            emptyPoolName : 'Required parameter "pool_name" is missing\n\n',
            emptyProperty : 'Required parameter "resultPropertySheet" is missing\n\n',
            wrongConfig : 'Configuration "wrong" does not exist\n\n',
            wrongPartitionName : 'The requested Pool (/wrong/test_5679) was not found.\n',
            wrongPoolName : 'The requested Pool (/Common/wrong) was not found.\n',
    ]

    static def expectedErrors = [
            wrongPartitionName: '{"code":404,"message":"01020036:3: The requested Pool (/wrong/test_5679) was not found.","errorStack":[],"apiError":3}',
            wrongPoolName: '{"code":404,"message":"01020036:3: The requested Pool (/Common/wrong) was not found.","errorStack":[],"apiError":3}',
    ]

    static def expectedLogs = [
            default : "GET ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members",
    ]

    static def defaultPoolName = 'test_5679'
    static def defaultPartition = 'Common'

    static def testCaseHelper

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(getMemberList)
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: createPoolMember, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: getMemberList, params: getMemberListParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

    @Sanity
    @Unroll
    def 'Get member list: Sanity #caseId.ids #caseId.description'() {
        bigIpClient.createBalancingPool(partition, poolName)
        if (countOfPools > 1){
            for (def i=1; i<countOfPools; i++) {
                bigIpClient.createBalancingPool(partition, poolName+i.toString())
            }
        }

        if (countOfPoolMembers) {
            for (def i = 0; i < countOfPoolMembers; i++) {
                def preconditionParams = [
                        config             : configName,
                        updateAction       : '0',
                        name               : "10.200.1.2${i.toString()}:80",
                        optionalParameters : '',
                        partition          : partition,
                        pool_name          : poolName,
                        resultPropertySheet: resultPropertySheet,
                ]
                runProcedure(projectName, createPoolMember, preconditionParams)
            }
        }

        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, getMemberList, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getMemberList/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName)
        for (def i=0; i<countOfPoolMembers; i++) {
            poolMemberInfo.items[i].selfLink = poolMemberInfo.items[i].selfLink.replace(poolName, "~$partition~$poolName")
        }
        poolMemberInfo.selfLink = poolMemberInfo.selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary == expectedSummary

            result.logs.contains(expectedLog
                    .replace('POOL', poolName))

            new JsonSlurper().parseText(outputParameters.poolMemberGetList) == poolMemberInfo

            new JsonSlurper().parseText(propertyName) == poolMemberInfo
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            if (countOfPools > 1) {
                for (def i = 1; i < countOfPools; i++) {
                    bigIpClient.deleteBalancingPool(partition, poolName + i.toString())
                }
            }

            for (def i = 0; i < countOfPoolMembers; i++) {
                bigIpClient.deleteNode(partition, "10.200.1.2${i.toString()}:80")
            }

        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | poolName        | partition         | resultPropertySheet       | expectedSummary           | expectedLog            | countOfPoolMembers | countOfPools
        TC.C387381 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 0                  | 1
        TC.C387398 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 2                  | 2
    }


    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get member list: Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)
        if (countOfPools > 1){
            for (def i=1; i<countOfPools; i++) {
                testCaseHelper.testCasePrecondition("Create additional Balancing Pool with name ${poolName+i.toString()}")
                bigIpClient.createBalancingPool(partition, poolName+i.toString())
            }
        }

        if (countOfPoolMembers) {
            testCaseHelper.testCasePrecondition("Add $countOfPoolMembers pool members into balancing pool")
            for (def i = 0; i < countOfPoolMembers; i++) {
                def preconditionParams = [
                        config             : configName,
                        updateAction       : '0',
                        name               : "10.200.1.2${i.toString()}:80",
                        optionalParameters : '',
                        partition          : partition,
                        pool_name          : poolName,
                        resultPropertySheet: resultPropertySheet,
                ]
                runProcedure(projectName, createPoolMember, preconditionParams)
            }
        }

        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $getMemberList with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, getMemberList, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getMemberList/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName)
        for (def i=0; i<countOfPoolMembers; i++) {
            poolMemberInfo.items[i].selfLink = poolMemberInfo.items[i].selfLink.replace(poolName, "~$partition~$poolName")
        }
        poolMemberInfo.selfLink = poolMemberInfo.selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: $expectedSummary")
            jobSummary == expectedSummary

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('POOL', poolName))

            testCaseHelper.addExpectedResult("Job OutputParameters: poolMemberGetList should contain actual info about member list: ${outputParameters.poolMemberGetList} in pool $poolName")
            new JsonSlurper().parseText(outputParameters.poolMemberGetList) == poolMemberInfo

            testCaseHelper.addExpectedResult("Job Properties: $resultPropertySheet should contain actual info about member list: $propertyName in pool $poolName")
            new JsonSlurper().parseText(propertyName) == poolMemberInfo
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            if (countOfPools > 1) {
                for (def i = 1; i < countOfPools; i++) {
                    bigIpClient.deleteBalancingPool(partition, poolName + i.toString())
                }
            }

            for (def i = 0; i < countOfPoolMembers; i++) {
                bigIpClient.deleteNode(partition, "10.200.1.2${i.toString()}:80")
            }

        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | poolName        | partition         | resultPropertySheet       | expectedSummary           | expectedLog            | countOfPoolMembers | countOfPools
        TC.C387381 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 0                  | 1
        TC.C387382 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 1                  | 1
        TC.C387383 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 2                  | 1
        TC.C387404 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 25                 | 1
        TC.C387398 | configurationName | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.default | expectedLogs.default   | 2                  | 2
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get member list: Negative #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        if (createPool) {
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $defaultPoolName")
            bigIpClient.createBalancingPool(defaultPartition, defaultPoolName)
        }

        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $getMemberList with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, getMemberList, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getMemberList/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def propertyName = ''
        if (resultPropertySheet) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: $expectedSummary")
            jobSummary == expectedSummary

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog)

            if (error){
//                testCaseHelper.addExpectedResult("Job OutputParameters: poolMemberGetList should contain actual error: $error")
//                outputParameters.poolMemberGetList == error
                assert outputParameters.poolMemberGetList == null

//                testCaseHelper.addExpectedResult("Job property: poolMemberGetList should contain actual error: $error")
//                propertyName  == error
                assert propertyName  == null
            }
            else {
                !outputParameters.poolMemberGetList
                !propertyName
            }
        }

        cleanup:
        try {
            if (createPool) {
                bigIpClient.deleteBalancingPool(defaultPartition, defaultPoolName)
            }

        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | poolName        | partition         | resultPropertySheet       | expectedSummary                        | expectedLog                            | error                             | createPool
        TC.C387416 | ''                | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.emptyConfig          | expectedSummaries.emptyConfig          | ''                                | false
        TC.C387417 | configurationName | defaultPoolName | ''                | '/myJob/poolMemberCreate' | expectedSummaries.emptyPartitionNname  | expectedSummaries.emptyPartitionNname  | ''                                | false
        TC.C387418 | configurationName | ''              | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.emptyPoolName        | expectedSummaries.emptyPoolName        | ''                                | false
        TC.C387419 | configurationName | defaultPoolName | defaultPartition  | ''                        | expectedSummaries.emptyProperty        | expectedSummaries.emptyProperty        | ''                                | false
        TC.C387420 | 'wrong'           | defaultPoolName | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.wrongConfig          | expectedSummaries.wrongConfig          | ''                                | true
        TC.C387421 | configurationName | defaultPoolName | 'wrong'           | '/myJob/poolMemberCreate' | expectedSummaries.wrongPartitionName   | expectedErrors.wrongPartitionName      | expectedErrors.wrongPartitionName | true
        TC.C387422 | configurationName | 'wrong'         | defaultPartition  | '/myJob/poolMemberCreate' | expectedSummaries.wrongPoolName        | expectedErrors.wrongPoolName           | expectedErrors.wrongPoolName      | true
    }

}