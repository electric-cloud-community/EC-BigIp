import java.io.File

def procName = 'TestConnection'
procedure procName, 
    description: 'Creates a plugin configuration',{

    property 'standardStepPicker', 'false'

    step 'TestConnection',
          command: new File(pluginDir, "dsl/procedures/$procName/steps/TestConnection.pl").text,
          errorHandling: 'abortProcedure',
          exclusiveMode: 'none',
          postProcessor: 'postp',
          releaseMode: 'none',
          shell: 'ec-perl',
          timeLimit: '30',
          timeLimitUnits: 'seconds'

    // add more steps here, e.g., 
    //step 'step2',
    //    command: new File(pluginDir, "dsl/procedures/$procName/steps/step2.groovy").text,
    //    shell: 'ec-groovy'
      
}
  
