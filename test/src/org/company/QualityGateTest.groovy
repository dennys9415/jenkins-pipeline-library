package org.company

import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class QualityGateTest extends BasePipelineTest {
    def qualityGate
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        qualityGate = new QualityGate(binding)
    }
    
    @Test
    void testQualityGateCreation() {
        assertNotNull(qualityGate)
    }
    
    @Test
    void testValidateCodeQuality() {
        def config = [
            language: 'java',
            coverageThreshold: 80
        ]
        
        // Mock file exists
        helper.registerAllowedMethod('fileExists', [String.class], { true })
        
        // Mock sh commands
        helper.registerAllowedMethod('sh', [Map.class], { 
            return '100' 
        })
        
        def result = qualityGate.validateCodeQuality(config)
        assertTrue(result)
    }
}