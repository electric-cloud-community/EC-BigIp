import java.io.File

def procName = 'EditConfiguration'
procedure procName,
        description: 'Edits a plugin configuration', {
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
}
