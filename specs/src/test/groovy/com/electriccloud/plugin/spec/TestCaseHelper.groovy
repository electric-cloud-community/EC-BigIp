package com.electriccloud.plugin.spec
import groovy.xml.*

class TestCaseHelper {

    def builder
    def testCases
    def testCaseTemplate
    def procedureName

    TestCaseHelper(procedureName){
        this.builder = new StreamingMarkupBuilder()
        builder.encoding = 'UTF-8'
        this.procedureName = procedureName
        this.testCases = []
    }

    def createTestCases(){
        this.testCaseTemplate = this.builder.bind {
            mkp.xmlDeclaration()
            sections {
                section {
                    name this.procedureName
                    cases {
                        this.testCases.each { testCase ->
                            'case' {
                                id testCase.id
                                title testCase.title
                                template 'Test Case (Steps)'
                                type 'Regression'
                                priority 'Medium'
                                estimate ''
                                references ''
                                custom {
                                    automation_type {
                                        id '0'
                                        value 'None'
                                    }
                                    automated 'false'
                                    preconds testCase.precondition
                                    steps_separated {
                                        step {
                                            index '1'
                                            content testCase.content
                                            expected testCase.expected
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        def xmlText = XmlUtil.serialize(this.testCaseTemplate)
        def xmlFileName = "testCases ${this.procedureName}.xml"
        if (System.getenv("CREATE_TESTS_CASES")) {
            def file = new File(xmlFileName)
            file.write xmlText
        }
    }

    def createNewTestCase(def id, def title){
        this.testCases += [id: id, title: title, expected: "", precondition: ""]
    }

    def testCasePrecondition(def precondition){
        this.testCases[-1].precondition += precondition + '\n'
    }

    def addStepContent(def stepDescription, def procedureParameters){
        def stepText = stepDescription + "\n"
        procedureParameters.each { parameterName, parameterValue ->
            stepText += "||${parameterName} |${parameterValue}\n"
        }
        this.testCases[-1].content = stepText
    }

    def addExpectedResult(def expectedResult){
        this.testCases[-1].expected += expectedResult + '\n\n'
    }


}
