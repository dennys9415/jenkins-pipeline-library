package org.company

import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class SecurityScannerTest extends BasePipelineTest {
    def securityScanner
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        securityScanner = new SecurityScanner(binding)
    }
    
    @Test
    void testSecurityScannerCreation() {
        assertNotNull(securityScanner)
    }
    
    @Test
    void testScanDependenciesJava() {
        def config = [
            language: 'java',
            failOnVulnerabilities: false
        ]
        
        // Mock file exists and readJSON
        helper.registerAllowedMethod('fileExists', [String.class], { true })
        helper.registerAllowedMethod('readJSON', [Map.class], { 
            return [dependencies: []] 
        })
        
        def vulnerabilities = securityScanner.scanDependencies(config)
        assertNotNull(vulnerabilities)
        assertEquals(0, vulnerabilities.size())
    }
}