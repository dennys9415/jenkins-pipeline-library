package org.company

import com.lesfurets.jenkins.unit.*
import static groovy.test.GroovyAssert.*
import org.junit.Before
import org.junit.Test

class PipelineBuilderTest extends BasePipelineTest {
    def builder
    
    @Override
    @Before
    void setUp() {
        super.setUp()
        builder = new PipelineBuilder(binding)
    }
    
    @Test
    void testPipelineBuilderCreation() {
        assertNotNull(builder)
    }
    
    @Test
    void testForLanguage() {
        def result = builder.forLanguage('java')
        assertSame(builder, result)
    }
    
    @Test
    void testWithBuildTool() {
        def result = builder.withBuildTool('maven')
        assertSame(builder, result)
    }
    
    @Test
    void testBuildMethod() {
        builder.forLanguage('java')
              .withBuildTool('maven')
        
        def pipeline = builder.build()
        assertNotNull(pipeline)
    }
}