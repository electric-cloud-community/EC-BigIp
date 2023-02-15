package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.NewFeature
import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*
import groovy.json.JsonSlurper

//@Ignore
class LTMGetPoolMemberTests extends PluginTestHelper {

    static def projectName = 'TestProject: GetPoolMember'
    static def configurationName = 'configuration1'
    static def bigIpClient


    static def TC = [
            C387442: [ids: 'C387442', description: 'Get Pool Member - one member in Pool'],
            C387443: [ids: 'C387443', description: 'Get Pool Member - some members in Pool'],
            C387444: [ids: 'C387444', description: 'Get Pool Member - some Pool exist'],
            C387445: [ids: 'C387445', description: 'empty config'],
            C387446: [ids: 'C387446', description: 'empty partition name'],
            C387447: [ids: 'C387447', description: 'empty pool name'],
            C387479: [ids: 'C387479', description: 'empty name'],
            C387480: [ids: 'C387480', description: 'empty property'],
            C387481: [ids: 'C387481', description: 'wrong config'],
            C387482: [ids: 'C387482', description: 'wrong partition name'],
            C387483: [ids: 'C387483', description: 'wrong pool name'],
            C387484: [ids: 'C387484', description: 'wrong name'],
            C387485: [ids: 'C387485', description: 'wrong property'],
    ]

    static def expectedSummaries = [
            default : "Pool member Common~POOL~NAME has been gotten",
            emptyConfig : 'No config name\n\n\n',
            emptyPartitionName : 'Required parameter "partition" is missing\n\n',
            emptyName : 'Required parameter "name" is missing\n\n',
            emptyPoolName : 'Required parameter "pool_name" is missing\n\n',
            emptyProperty : 'Required parameter "resultPropertySheet" is missing\n\n',
            wrongConfig : 'Configuration "wrong" does not exist\n\n',
            wrongPartitionName : 'The requested Pool (/wrong/test_5679) was not found.\n',
            wrongPoolName : 'The requested Pool (/Common/wrong) was not found.\n',
            wrongPoolName2 : "The requested Pool (/Common/test_5679) was not found.\n",
            wrongName: 'Object not found - /Common/wrong:80\n',
            wrongProperty: "Unrecognized path element in '/invalid/invalid': 'invalid'",
    ]

    static def expectedErrors = [
            wrongPartitionName: '{"code":404,"message":"01020036:3: The requested Pool (/wrong/test_5679) was not found.","errorStack":[],"apiError":3}',
            wrongPoolName: '{"code":404,"message":"01020036:3: The requested Pool (/Common/wrong) was not found.","errorStack":[],"apiError":3}',
            wrongName: '{"code":404,"message":"Object not found - /Common/wrong:80","errorStack":[],"apiError":1}',
    ]

    static def expectedLogs = [
            default : "GET ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members/~Common~NAME",
    ]

    static def defaultPoolName = 'test_5679'
    static def defaultName = '10.200.1.137:80'
    static def defaultPartition = 'Common'
    static def testCaseHelper

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(getPoolMember)
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: createPoolMember, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: getPoolMember, params: getPoolMemberParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

    @Sanity
    @Unroll
    def 'Get pool member: Sanity #caseId.ids #caseId.description'() {
        bigIpClient.createBalancingPool(partition, poolName)
        if (countOfPools > 1){
            for (def i=1; i<countOfPools; i++) {
                bigIpClient.createBalancingPool(partition, poolName+i.toString())
            }
        }

        for (def i = 0; i < countOfPoolMembers; i++) {
            def preconditionParams = [
                    config             : configName,
                    updateAction       : '0',
                    name               : i == 0 ? name : "10.200.1.2${i.toString()}:80",
                    optionalParameters : '',
                    partition          : partition,
                    pool_name          : poolName,
                    resultPropertySheet: resultPropertySheet,
            ]
            runProcedure(projectName, createPoolMember, preconditionParams)
        }


        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        when: "Run procedure"
        def result = runProcedure(projectName, getPoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, partition, name)

        poolMemberInfo.selfLink = poolMemberInfo.selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary == expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name)

            result.logs.contains(expectedLog
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            new JsonSlurper().parseText(outputParameters.poolMemberGet) == poolMemberInfo

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
                bigIpClient.deleteNode(partition, i == 0 ? name : "10.200.1.2${i.toString()}:80")
            }


        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | name        | poolName        | partition         | resultPropertySheet       | expectedSummary           | expectedLog            | countOfPoolMembers | countOfPools
        TC.C387442 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.default | expectedLogs.default   | 1                  | 1
        TC.C387444 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.default | expectedLogs.default   | 3                  | 2
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get pool member: Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)
        if (countOfPools > 1){
            for (def i=1; i<countOfPools; i++) {
                testCaseHelper.testCasePrecondition("Create additional Balancing Pool with name ${poolName+i.toString()}")
                bigIpClient.createBalancingPool(partition, poolName+i.toString())
            }
        }


        testCaseHelper.testCasePrecondition("Add $countOfPoolMembers pool members into balancing pool")
        for (def i = 0; i < countOfPoolMembers; i++) {
            def preconditionParams = [
                    config             : configName,
                    updateAction       : '0',
                    name               : i == 0 ? name : "10.200.1.2${i.toString()}:80",
                    optionalParameters : '',
                    partition          : partition,
                    pool_name          : poolName,
                    resultPropertySheet: resultPropertySheet,
            ]
            runProcedure(projectName, createPoolMember, preconditionParams)
        }


        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $getPoolMember with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, getPoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, partition, name)
        poolMemberInfo.selfLink = poolMemberInfo.selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary == expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name)

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('POOL', poolName).replace('NAME', name)}")
            result.logs.contains(expectedLog
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job OutputParameters: poolMemberGet should contain actual info about pool member: ${outputParameters.poolMemberGet}")
            new JsonSlurper().parseText(outputParameters.poolMemberGet) == poolMemberInfo

            testCaseHelper.addExpectedResult("Job Properties: $resultPropertySheet should contain actual info about pool member: $propertyName")
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
                bigIpClient.deleteNode(partition, i == 0 ? name : "10.200.1.2${i.toString()}:80")
            }


        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | name        | poolName        | partition         | resultPropertySheet       | expectedSummary           | expectedLog            | countOfPoolMembers | countOfPools
        TC.C387442 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.default | expectedLogs.default   | 1                  | 1
        TC.C387443 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.default | expectedLogs.default   | 3                  | 1
        TC.C387444 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.default | expectedLogs.default   | 3                  | 2
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get pool member: Negative #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        if (createPool) {
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $defaultPoolName")
            bigIpClient.createBalancingPool(defaultPartition, defaultPoolName)
        }


        given: "Tests parameters for procedure Get member list"
        def parameters = [
                config             : configName,
                name               : name,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $getPoolMember with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, getPoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$getPoolMember/summary", result.jobId)

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
            if (caseId != TC.C387485) {
                jobSummary == expectedSummary
            }
            else {
                jobSummary.contains(expectedSummary)
            }

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
//            result.logs.contains(expectedLog)

            if (error){
//                testCaseHelper.addExpectedResult("Job OutputParameters: poolMemberGetList should contain actual error: $error")
//                outputParameters.poolMemberGet == error
                assert outputParameters.poolMemberGet == null

//                testCaseHelper.addExpectedResult("Job property: poolMemberGetList should contain actual error: $error")
//                propertyName  == error
                assert propertyName  == null
            }
            else {
                testCaseHelper.addExpectedResult("Job OutputParameters: procedure shouldn't have output parameter poolMemberGet")
                !outputParameters.poolMemberGet
                testCaseHelper.addExpectedResult("Job property: procedure shouldn't have output parameter poolMemberGet")
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
        caseId     | configName        | name        | poolName        | partition         | resultPropertySheet       | expectedSummary                      | expectedLog                          | error                              | createPool
        TC.C387445 | ''                | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.emptyConfig        | expectedSummaries.emptyConfig        | ''                                 | false
        TC.C387446 | configurationName | defaultName | defaultPoolName | ''                | '/myJob/poolMemberGet'    | expectedSummaries.emptyPartitionName | expectedSummaries.emptyPartitionName | ''                                 | false
        TC.C387447 | configurationName | defaultName | ''              | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.emptyPoolName      | expectedSummaries.emptyPoolName      | ''                                 | false
        TC.C387479 | configurationName | ''          | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.emptyName          | expectedSummaries.emptyName          | ''                                 | false
        TC.C387480 | configurationName | defaultName | defaultPoolName | defaultPartition  | ''                        | expectedSummaries.emptyProperty      | expectedSummaries.emptyProperty      | ''                                 | false
        TC.C387481 | 'wrong'           | defaultName | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.wrongConfig        | expectedSummaries.wrongConfig        | ''                                 | false
        TC.C387482 | configurationName | defaultName | defaultPoolName | 'wrong'           | '/myJob/poolMemberGet'    | expectedSummaries.wrongPartitionName | expectedErrors.wrongPartitionName    | expectedErrors.wrongPartitionName  | true
        TC.C387483 | configurationName | defaultName | 'wrong'         | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.wrongPoolName      | expectedErrors.wrongPoolName         | expectedErrors.wrongPoolName       | true
        TC.C387484 | configurationName | 'wrong:80'  | defaultPoolName | defaultPartition  | '/myJob/poolMemberGet'    | expectedSummaries.wrongName          | expectedErrors.wrongName             | expectedErrors.wrongName           | true
//        TC.C387485 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/invalid/invalid'        | expectedSummaries.wrongProperty      | expectedSummaries.wrongProperty      | ''                                 | false
        TC.C387485 | configurationName | defaultName | defaultPoolName | defaultPartition  | '/invalid/invalid'        | expectedSummaries.wrongPoolName2     | expectedSummaries.wrongProperty      | ''                                 | false

    }

}