/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import spock.lang.Unroll

class VariantAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    @Override
    String getTestConfiguration() { variantToTest }

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    boolean getPublishedModulesHaveAttributes() { gradleMetadataEnabled }

    String getVariantToTest() {
        if (gradleMetadataEnabled || useIvy()) {
            'customVariant'
        } else {
            'compile'
        }
    }

    void withDefaultVariantToTest() {
        repository {
            'org.test:moduleA:1.0'() {
                variant 'customVariant', [format: 'custom']
                dependsOn('org.test:moduleB:1.0')
            }
        }

        buildFile << """
            def testAttribute = Attribute.of("TEST_ATTRIBUTE", String)
            def formatAttribute = Attribute.of('format', String)

            configurations { $variantToTest { attributes { attribute(formatAttribute, 'custom') } } }
            
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    def "can add attributes"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            attributes {
                                attribute(formatAttribute, "custom")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant 'customVariant', [:]
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    def "can override attributes"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            attributes {
                                attribute(formatAttribute, "custom")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant('customVariant') {
                    if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                        artifact 'variant1'
                    }
                    attribute 'format', 'will be overriden'
                }
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                    expectGetVariantArtifacts('customVariant')
                } else {
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0") {
                        if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                            artifact group: 'org', module: 'moduleB', version: '1.0', classifier: 'variant1'
                            // for now we only check the selected variant for Gradle, but we should set the appropriate expectations for Ivy and Maven too
                            variant('customVariant', [format: 'custom', 'org.gradle.status': GradleMetadataResolveRunner.useIvy()?'integration':'release'])
                        }
                    }
                }
            }
        }
    }

    // This test documents the current behavior. It's not necessarily
    // what we want, but there doesn't seem to be a good use case for mutating
    // artifact attributes
    def "can specify an artifact attribute on a variant to mitigate missing withArtifacts rules"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            dependencies {
                artifactTypes {
                    jar {
                        // declares that the 'jar' artifact type wants a 'format' attribute with value 'custom'
                        // and this is missing from component and variant metatada
                        attributes.attribute(formatAttribute, 'custom')
                    }
                }
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            attributes {
                                // defines the 'format' attribute with value 'custom' on all variants
                                // which will be inherited by artifacts
                                attribute(formatAttribute, "custom")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant('customVariant') {
                    if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                        artifact 'variant1'
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                    expectGetVariantArtifacts('customVariant')
                } else {
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0") {
                        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                            artifact group: 'org', module: 'moduleB', version: '1.0', classifier: 'variant1'
                        }
                    }
                }
            }
        }
    }

    def "rule is applied only once"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            int cpt
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            attributes {
                                if (++cpt == 2) {
                                    throw new IllegalStateException("rule should only be applied once")
                                }
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'custom']
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    @Unroll
    def "can disambiguate variants to select #selectedVariant"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            configurations {
                ${variantToTest}.attributes.attribute(testAttribute, "select")
            }

            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$selectedVariant') { 
                            attributes {
                                attribute(testAttribute, "select")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant('customVariant1') {
                    attribute 'format', 'custom'
                    artifact 'variant1'
                }
                variant('customVariant2') {
                    attribute 'format', 'custom'
                    artifact 'variant2'
                }
            }
        }

        when:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            repositoryInteractions {
                'org.test:moduleA:1.0' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                'org.test:moduleB:1.0'() {
                    expectGetMetadata()
                    expectGetVariantArtifacts(selectedVariant)
                }
            }
        }

        then:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            succeeds 'checkDep'

            def variantToTest = variantToTest
            resolve.expectGraph {
                root(':', ':test:') {
                    module("org.test:moduleA:1.0:$variantToTest") {
                        module("org.test:moduleB:1.0") {
                            artifact(classifier: (selectedVariant - 'custom').toLowerCase())
                        }
                    }
                }
            }
        }

        where:
        selectedVariant << ['customVariant1', 'customVariant2']
    }

    @RequiredFeatures(
        // published attributes are only available in Gradle metadata
        @RequiredFeature(feature=GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
    )
    def "published variant metadata can be overwritten"() {
        given:
        repository {
            'org.test:module:1.0' {
                variant('customVariant1') {
                    attribute 'quality', 'canary'
                }
                variant('customVariant2') {
                    attribute 'quality', 'canary'
                }
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            
            configurations {
                ${variantToTest}.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') {
                        withVariant('customVariant2') {
                           attributes {
                              attribute quality, 'qa'
                           }
                        }
                    }
                }
                $variantToTest 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:module:1.0:customVariant2')
            }
        }
    }

}
