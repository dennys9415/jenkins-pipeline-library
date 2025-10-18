import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class BuildTest extends BasePipelineTest {
    def build
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        build = loadScript("vars/build.groovy")
    }
    
    @Test
    void testBuildJavaMaven() {
        def config = [
            language: 'java',
            buildTool: 'maven',
            jdkVersion: '11'
        ]
        
        build.call(config)
        
        assertJobStatusSuccess()
        assertTrue(helper.callStack.find { call ->
            call.methodName == 'sh' && call.argsToString().contains('mvn')
        } != null)
    }
    
    @Test
    void testBuildNodeJS() {
        def config = [
            language: 'nodejs',
            nodeVersion: '18'
        ]
        
        build.call(config)
        
        assertJobStatusSuccess()
        assertTrue(helper.callStack.find { call ->
            call.methodName == 'sh' && call.argsToString().contains('npm')
        } != null)
    }
    
    @Test
    void testBuildPython() {
        def config = [
            language: 'python',
            pythonVersion: '3.9'
        ]
        
        build.call(config)
        
        assertJobStatusSuccess()
        assertTrue(helper.callStack.find { call ->
            call.methodName == 'sh' && call.argsToString().contains('python')
        } != null)
    }
}