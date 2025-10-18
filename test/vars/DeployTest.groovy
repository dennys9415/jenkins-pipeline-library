import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class DeployTest extends BasePipelineTest {
    def deploy
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        deploy = loadScript("vars/deploy.groovy")
    }
    
    @Test
    void testDeployToDev() {
        def config = [
            environment: 'dev',
            platform: 'kubernetes'
        ]
        
        deploy.call(config)
        
        assertJobStatusSuccess()
        assertTrue(helper.callStack.find { call ->
            call.methodName == 'echo' && call.argsToString().contains('dev')
        } != null)
    }
    
    @Test
    void testRollingDeployment() {
        def config = [
            environment: 'staging',
            deploymentStrategy: 'rolling'
        ]
        
        deploy.call(config)
        
        assertJobStatusSuccess()
    }
}