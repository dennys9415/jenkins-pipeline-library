import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class TestTest extends BasePipelineTest {
    def test
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        test = loadScript("vars/test.groovy")
    }
    
    @Test
    void testUnitTestsJava() {
        def config = [
            language: 'java',
            testType: 'unit'
        ]
        
        test.call(config)
        
        assertJobStatusSuccess()
        assertTrue(helper.callStack.find { call ->
            call.methodName == 'sh' && call.argsToString().contains('test')
        } != null)
    }
    
    @Test
    void testIntegrationTests() {
        def config = [
            language: 'java',
            testType: 'integration'
        ]
        
        test.call(config)
        
        assertJobStatusSuccess()
    }
}