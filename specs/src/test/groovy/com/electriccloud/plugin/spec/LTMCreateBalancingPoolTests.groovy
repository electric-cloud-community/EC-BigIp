package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import groovy.json.JsonSlurper
import spock.lang.*

//@Ignore
class LTMCreateBalancingPoolTests extends PluginTestHelper{

    @Shared
    def procedureName = "LTM - Create or update balancing pool",
        projectName = "TestProject: Create or update balancing pool",
        optionalParam = "description=test description;allowNat=no;allowSnat=no;ignorePersistedWeight=enabled",
        bigIpClient,
        testCaseHelper,

        TC = [
                C387067:  [id: 'C387067', description: 'Only required - Update Action = \'Do Nothing\' for new balancing pool'],
                C387068:  [id: 'C387068', description: 'All - Update Action = \'Do Nothing\' for exist balancing pool'],
                C387069:  [id: 'C387069', description: 'Only required - Update Action = \'Selective Update\' for new balancing pool'],
                C387071:  [id: 'C387071', description: 'All - Update Action = \'Selective Update\' for exist balancing pool'],
                C387073:  [id: 'C387073', description: 'Only required - Update Action = \'Remove and Create\' for new balancing pool'],
                C387074:  [id: 'C387074', description: 'All - Update Action = \'Remove and Create\' for exist balancing pool'],
                C387823:  [id: 'C387823', description: 'All - Update Action = \'Throw exception\' for new balancing pool'],

                //deprecated
                /*C387134:  [id: 'C387134', description: 'Run with empty Result Format'],
                C387135:  [id: 'C387135', description: 'Run with invalid Result Format'],*/

                C387435: [id: 'C387435', description: 'Update(default) pool with optionalParameters'],
                C387437: [id: 'C387437', description: 'Remove and Create existing pool with optionalParameters'],
                C387441: [id: 'C387441', description: 'Remove and Create existing pool without new data'],
                //Negative TC
                C387075:  [id: 'C387075', description: 'Run with empty Configuration Name'],
                C387683:  [id: 'C387683', description: 'Run with "Throw exception" for existed pool'],
                C387076:  [id: 'C387076', description: 'Run with empty Update Action'],
                C387077:  [id: 'C387077', description: 'Run with empty Partition'],
                C387078:  [id: 'C387078', description: 'Run with empty Pool Name'],
                C387079:  [id: 'C387079', description: 'Run with empty Result Property Sheet'],
                C387081:  [id: 'C387081', description: 'Run with non-exist Configuration Name'],
                //deprecated
                //C387082:  [id: 'C387082', description: 'Run with invalid method'],
                C387083:  [id: 'C387083', description: 'Run with non-exist Partitition'],
                C387084:  [id: 'C387084', description: 'Run with non-exist Result Property Sheet'],

                //deprecated
                /*C387438: [id: 'C387438', description: 'Remove and Create for nonexistent pool with only required parameters'],
                C387439: [id: 'C387439', description: 'Remove and Create for nonexistent pool with optionalParameters']*/
        ],
        expectedSummaries = [
                created           : 'Balancing pool PART~POOL has been created',
                updated           : 'Balancing pool PART~POOL has been updated',
                replaced          : 'Balancing pool PART~POOL has been replaced',
                didntTouch        : 'Balancing pool PART~POOL hasn\'t been touched',

                emptyConfig       : 'No config name',
                errorUpdate       : 'Balancing pool PART~POOL has been found',
                emptyMethod       : 'Required parameter "updateAction" is missing',
                emptyPartition    : 'Required parameter "partition" is missing',
                emptyPoolName     : 'Required parameter "name" is missing',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "nonExistent" does not exist',
                //deprecated
                //wrongMethod       : 'Wrong method value: wrong',
                nonexisPool       : 'one or more properties must be specified',
                nonexistPartition : 'The requested folder (/PART) was not found.',
                wrongResult       : 'Unrecognized path element in \'/invalid/invalid\': \'invalid\'',
                //deprecated
                //nonexisPoolName   : 'The requested Pool (/PART/POOL) was not found.'

        ],

        expectedLogs = [
                created:  "POST ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool",
                updated : "PATCH ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL",
                replaced : "PUT ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL",
                doNothing: "GET ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL"
        ],

        outputErrors = [
                partition: '{"code":400,"message":"The requested folder (/PART) was not found.","errorStack":[],"apiError":26214401}',
                //deprecated
                //poolName: '{"code":404,"message":"01020036:3: The requested Pool (/PART/POOL) was not found.","errorStack":[],"apiError":3}'
        ]


    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(procedureName)
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: createBalancingPoolParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)
    }

    @Sanity
    @Unroll
    def 'Create balancing pool : Sanity Positive  #caseId.id #caseId.description'(){

        if(namePool == 'existPoolName'){
            bigIpClient.createBalancingPool(partition, namePool)
        }

        given: "Tests parameters for procedure LTM - Create balancing pool"
        def procedureParams = [
                config             : configName,
                updateAction       : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet
        ]

        when: "LTM - Create balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolInfo = bigIpClient.getBalancingPoolInfo(partition, namePool)

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            new JsonSlurper().parseText(propertyName) == poolInfo

            new JsonSlurper().parseText(outputParameters.poolCreateOrUpdate) == poolInfo

            if (optionalParameters && ( method!= '0')) {
                for (param in optionalParameters.split(";")) {
                    poolInfo[param.split("=")[0]] == param.split("=")[1]
                }
            }

            poolInfo
            poolInfo.name == namePool
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName  | partition | method  | namePool       | optionalParameters |resultPropertySheet  | expectedSummary              | expectedLog
        TC.C387067 | CONFIG_NAME | 'Common'  | '0'     | 'testPoolName' | ''                 | '/myJob/poolCreate' | expectedSummaries.created    | expectedLogs.created
        TC.C387071 | CONFIG_NAME | 'Common'  | 'PATCH' | 'existPoolName'| optionalParam      | '/myJob/poolCreate' | expectedSummaries.updated    | expectedLogs.updated
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Create balancing pool: Positive  #caseId.id #caseId.description'(){
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        if(namePool == 'existPoolName'){
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
            bigIpClient.createBalancingPool(partition, namePool)
        }

        given: "Tests parameters for procedure LTM - Create balancing pool"
        def procedureParams = [
                config             : configName,
                updateAction       : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "LTM - Create balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolInfo = bigIpClient.getBalancingPoolInfo(partition, namePool)

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool)}")
            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolInfo

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolManage: ${outputParameters.poolCreateOrUpdate}")
            new JsonSlurper().parseText(outputParameters.poolCreateOrUpdate) == poolInfo

            if (optionalParameters && ( method!= '0')) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: balancing pool $namePool should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolInfo[param.split("=")[0]] == param.split("=")[1]
                }
            }

            testCaseHelper.addExpectedResult("balancing pool json: ${poolInfo}")
            poolInfo
            poolInfo.name == namePool
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName  | partition | method  | namePool       | optionalParameters |resultPropertySheet  | expectedSummary              | expectedLog
        TC.C387067 | CONFIG_NAME | 'Common'  | '0'     | 'testPoolName' | ''                 | '/myJob/poolCreate' | expectedSummaries.created    | expectedLogs.created
        TC.C387068 | CONFIG_NAME | 'Common'  | '0'     | 'existPoolName'| optionalParam      | '/myJob/poolCreate' | expectedSummaries.didntTouch | expectedLogs.doNothing
        TC.C387069 | CONFIG_NAME | 'Common'  | 'PATCH' | 'testPoolName' | ''                 | '/myJob/poolCreate' | expectedSummaries.created    | expectedLogs.created
        TC.C387071 | CONFIG_NAME | 'Common'  | 'PATCH' | 'existPoolName'| optionalParam      | '/myJob/poolCreate' | expectedSummaries.updated    | expectedLogs.updated
        TC.C387073 | CONFIG_NAME | 'Common'  | 'PUT'   | 'testPoolName' | ''                 | '/myJob/poolCreate' | expectedSummaries.created    | expectedLogs.created
        TC.C387074 | CONFIG_NAME | 'Common'  | 'PUT'   | 'existPoolName'| optionalParam      | '/myJob/poolCreate' | expectedSummaries.replaced   | expectedLogs.replaced
        TC.C387823 | CONFIG_NAME | 'Common'  | 'ERROR' | 'testPoolName' | optionalParam      | '/myJob/poolCreate' | expectedSummaries.created    | expectedLogs.created
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Update balancing pool: Positive  #caseId.id #caseId.description'(){
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
        bigIpClient.createBalancingPool(partition, namePool)

        given: "Tests parameters for procedure LTM - Create balancing pool"
        def procedureParams = [
                config             : configName,
                updateAction       : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "LTM - Create balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolInfo = bigIpClient.getBalancingPoolInfo(partition, namePool)

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool)}")
            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolInfo

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolManage: ${outputParameters.poolCreateOrUpdate}")
            new JsonSlurper().parseText(outputParameters.poolCreateOrUpdate) == poolInfo

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: balancing pool $namePool should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolInfo[param.split("=")[0]] == param.split("=")[1]
                }
            }

            testCaseHelper.addExpectedResult("balancing pool json: ${poolInfo}")
            poolInfo
            poolInfo.name == namePool
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName  | partition | method  | namePool        | optionalParameters | resultPropertySheet | expectedSummary            | expectedLog
        TC.C387435 | CONFIG_NAME | 'Common'  | 'PATCH' | 'testPoolName'  | optionalParam      | '/myJob/poolManage' | expectedSummaries.updated  | expectedLogs.updated
        TC.C387437 | CONFIG_NAME | 'Common'  | 'PUT'   | 'testPoolName'  | optionalParam      | '/myJob/poolManage' | expectedSummaries.replaced | expectedLogs.replaced
        TC.C387441 | CONFIG_NAME | 'Common'  | 'PUT'   | 'testPoolName'  | ''                 | '/myJob/poolManage' | expectedSummaries.replaced | expectedLogs.replaced
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Create balancing pool: Negative #caseId.id #caseId.description'(){
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        if(namePool == 'existPoolName'){
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
            bigIpClient.createBalancingPool(partition, namePool)
        }

        given: "Tests parameters for procedure LTM - Create balancing pool"
        def procedureParams = [
                config             : configName,
                updateAction       : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "LTM - Create balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def propertyName
        if (resultPropertySheet && (resultPropertySheet != '/invalid')) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART', partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART', partition).replace('POOL', namePool)}")
            result.logs.contains(expectedLog
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            if (error){
//                testCaseHelper.addExpectedResult("Output parameter should contains error message: ${error.replace('PART', partition).replace('POOL', namePool)}")
//                outputParameters.poolCreateOrUpdate == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
                assert outputParameters.poolCreateOrUpdate == null

//                testCaseHelper.addExpectedResult("property $propertyName should contains error message: ${error.replace('PART', partition).replace('POOL', namePool)}")
//                propertyName == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
                assert propertyName == null
            }
            else {
                testCaseHelper.addExpectedResult("Procedure shouldn't have outputParameters")
                if (resultPropertySheet) {
                    testCaseHelper.addExpectedResult("Procedure shouldn't have property: ${resultPropertySheet.split("/")[2]}")
                }
                if(namePool != 'existPoolName'){
                    assert !outputParameters
                    assert !propertyName
                }
            }
        }

        cleanup:
        if(namePool == 'existPoolName' || namePool == 'testPoolName7'){
            try {
                bigIpClient.deleteBalancingPool(partition, namePool)
            } catch (Throwable e) {
                logger.debug(e.message)
            }
        }

        where:
        caseId     | configName  | partition | method    | namePool        | optionalParameters        | resultPropertySheet | expectedSummary                     | expectedLog                         | error
        TC.C387075 | ''          | 'Common'  | '0'       | 'testPoolName1' | ''                        | '/myJob/poolCreate' | expectedSummaries.emptyConfig       | expectedSummaries.emptyConfig       | ''
        TC.C387076 | CONFIG_NAME | 'Common'  | ''        | 'testPoolName0' | ''                        | '/myJob/poolCreate' | expectedSummaries.emptyMethod       | expectedSummaries.emptyMethod       | ''
        TC.C387077 | CONFIG_NAME | ''        | '0'       | 'testPoolName2' | ''                        | '/myJob/poolCreate' | expectedSummaries.emptyPartition    | expectedSummaries.emptyPartition    | ''
        TC.C387078 | CONFIG_NAME | 'Common'  | '0'       | ''              | ''                        | '/myJob/poolCreate' | expectedSummaries.emptyPoolName     | expectedSummaries.emptyPoolName     | ''
        TC.C387079 | CONFIG_NAME | 'Common'  | '0'       | 'testPoolName3' | ''                        | ''                  | expectedSummaries.emptyResult       | expectedSummaries.emptyResult       | ''
        TC.C387081 |'nonExistent'| 'Common'  | '0'       | 'testPoolName4' | ''                        | '/myJob/poolCreate' | expectedSummaries.nonexisConfig     | expectedSummaries.nonexisConfig     | ''
        //TC.C387082 | CONFIG_NAME | 'Common'  | 'wrong'   | 'testPoolName8' | ''                        | '/myJob/poolCreate' | expectedSummaries.wrongMethod       | expectedSummaries.wrongMethod       | ''
        TC.C387083 | CONFIG_NAME | 'invalid' | '0'       | 'testPoolName6' | ''                        | '/myJob/poolCreate' | expectedSummaries.nonexistPartition | expectedLogs.created                | outputErrors.partition
        TC.C387084 | CONFIG_NAME | 'Common'  | '0'       | 'testPoolName7' | ''                        | '/invalid/invalid'  | expectedSummaries.wrongResult       | expectedSummaries.wrongResult       | ''
        /*TC.C387438 | CONFIG_NAME | 'Common'  | 'PUT'     | 'NewPoolName'   | ''                        | '/myJob/poolManage' | expectedSummaries.nonexisPoolName   | expectedLogs.doNothing              | outputErrors.poolName
        TC.C387439 | CONFIG_NAME | 'Common'  | 'PUT'     | 'NewPoolName'   | 'allowNat=no;allowSnat=no'| '/myJob/poolManage' | expectedSummaries.nonexisPoolName   | expectedLogs.doNothing              | outputErrors.poolName*/
        TC.C387683 | CONFIG_NAME | 'Common'  | 'ERROR'   | 'existPoolName' | ''                        | '/myJob/poolCreate' | expectedSummaries.errorUpdate       | expectedSummaries.errorUpdate       | ''
    }

}