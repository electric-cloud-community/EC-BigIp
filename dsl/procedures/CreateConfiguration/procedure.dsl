import java.io.File

def procName = 'CreateConfiguration'
procedure procName,
        description: 'Creates a plugin configuration', {
    formalParameter(formalParameterName: 'credential', type: 'credential', required: '1')
    timeLimit = '5'
    timeLimitUnits = 'minutes'

    step( 'TestConnection',
          command: new File(pluginDir, "dsl/procedures/TestConnection/steps/TestConnection.pl").text,
          errorHandling: 'abortProcedure',
          exclusiveMode: 'none',
          postProcessor: '$[/myProject/postpLoader]',
          condition: '$[/javascript myJob.attemptConnection == "true" || myJob.attemptConnection == "1"]',
          releaseMode: 'none',
          shell: 'ec-perl',
          timeLimit: '30',
          timeLimitUnits: 'seconds') {
        attachParameter(formalParameterName: 'credential')
    }   

    step 'createConfiguration',
            command: new File(pluginDir, "dsl/procedures/$procName/steps/createConfiguration.pl").text,
            errorHandling: 'failProcedure',
            exclusiveMode: 'none',
            postProcessor: '$[/myProject/postpLoader]',
            releaseMode: 'none',
            shell: 'ec-perl',
            timeLimitUnits: 'minutes'

    step( 'createAndAttachCredential',
        command: new File(pluginDir, "dsl/procedures/$procName/steps/createAndAttachCredential.pl").text,
        errorHandling: 'failProcedure',
        exclusiveMode: 'none',
        releaseMode: 'none',
        shell: 'ec-perl',
        timeLimitUnits: 'minutes') {
        attachParameter(formalParameterName: 'credential')
    }

}
