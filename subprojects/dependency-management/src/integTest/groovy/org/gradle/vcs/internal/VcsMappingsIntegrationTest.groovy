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

package org.gradle.vcs.internal

import org.gradle.vcs.internal.spec.DirectoryRepositorySpec

class VcsMappingsIntegrationTest extends AbstractVcsIntegrationTest {
    def setup() {
        settingsFile << """
            import ${DirectoryRepositorySpec.canonicalName}
        """
    }

    def "can define and use source repositories"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def 'emits sensible error when bad code is in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule('rule') { details ->
                        foo()
                    }
                }
            }
        """
        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasFileName("Settings file '$settingsFile.path'")
        failure.assertHasLineNumber(7)
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not find method foo()")
    }

    def 'emits sensible error when bad vcs url in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(GitVersionControlSpec) {
                            url = 'https://bad.invalid'
                        }
                    }
                }
            }
        """

        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not list available versions for 'Git Repository at https://bad.invalid'.")
    }

    def "can define and use source repositories with all {}"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule("rule") { details ->
                        if (details.requested.group == "org.test") {
                            from vcs(DirectoryRepositorySpec) {
                                sourceDir = file("dep")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def "can define unused vcs mappings"() {
        settingsFile << """
            // include the missing dep as a composite
            includeBuild 'dep'
            
            sourceControl {
                vcsMappings {
                    withModule("unused:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    addRule("rule") { details ->
                        if (details instanceof ModuleVersionSelector && details.requested.group == "unused") {
                            from vcs(DirectoryRepositorySpec) {
                                sourceDir = file("does-not-exist")
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoNotCheckedOut()
        assertRepoNotCheckedOut("does-not-exist")
    }

    def "last vcs mapping rule wins"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("does-not-exist")
                        }
                    }
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
        assertRepoNotCheckedOut("does-not-exist")
    }

    def 'missing settings has clear error'() {
        depProject.settingsFile.delete()

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        assertRepoCheckedOut()
        failureCauseContains("Included build from '")
        failureCauseContains("' must contain a settings file.")
    }

    def 'can build from sub-directory of repository'() {
        file('repoRoot').mkdir()
        file('dep').renameTo(file('repoRoot/dep'))
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file('repoRoot')
                            rootDir = 'dep'
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut('repoRoot')
    }

    def 'fails with a reasonable message if rootDir is invalid'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file('dep')
                            rootDir = null
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        result.error.contains("rootDir should be non-null")
    }

    def 'can provide a settings.gradle for a source dependency that does not have one'() {
        depProject.settingsFile.delete()

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                            settingsFile = resources.text.fromString '''
                                rootProject.name = "dep"
                                println "User-provided settings.gradle"
                            '''
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        result.assertOutputContains("User-provided settings.gradle")
        assertRepoCheckedOut()
    }

    def 'can provide a settings.gradle that overrides a source dependency settings.gradle'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                            settingsFile = resources.text.fromString '''
                                rootProject.name = "dep"
                                println "User-provided settings.gradle"
                            '''
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        result.assertOutputContains("User-provided settings.gradle")
        assertRepoCheckedOut()
    }

    def 'can provide build configuration from settings.gradle'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                            settingsFile = resources.text.fromFile("dep-settings.gradle")
                        }
                    }
                }
            }
        """
        file("dep-settings.gradle") << """
            rootProject.name = "dep"
            gradle.allprojects { project ->
                println "Can configure " + project.getIdentityPath() + " from settings"
            }
        """
        expect:
        succeeds("assemble")
        result.assertOutputContains("Can configure :dep from settings")
        assertRepoCheckedOut()
    }

    def 'can add external plugins to build configuration from settings.gradle'() {
        buildTestFixture.withBuildInSubDir().singleProjectBuild("external-plugin") {
            buildFile << """
                apply plugin: 'java-gradle-plugin'
                apply plugin: 'groovy'
            """
            file("src/main/groovy/MyPlugin.groovy") << """
                import org.gradle.api.*
                
                class MyPlugin implements Plugin<Project> {
                    void apply(Project project) {
                        println "External plugin message"
                    }
                }
            """
            file("src/main/resources/META-INF/gradle-plugins/external-plugin.properties").text = "implementation-class=MyPlugin"
        }

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("dep")
                            settingsFile = resources.text.fromFile("dep-settings.gradle")
                        }
                    }
                }
            }
            includeBuild "external-plugin"
        """

        // Source "repository" does not have a settings or build file
        depProject.settingsFile.delete()
        depProject.buildFile.delete()
        // TODO: We should be able to supply this through the settings file?
        depProject.buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'external-plugin'
                
                group = 'org.test'
                version = '1.0'
            }
        """
        file("dep-settings.gradle") << """
            rootProject.name = "dep"
            gradle.projectsLoaded { gradle ->
                gradle.rootProject.buildscript {
                    dependencies {
                        classpath 'org.test:external-plugin:1.0'
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        result.assertOutputContains("External plugin message")
        assertRepoCheckedOut()
    }

    void assertRepoCheckedOut(String repoName="dep") {
        def checkout = checkoutDir(repoName, "fixed", "directory-repo:${file(repoName).absolutePath}")
        checkout.file("checkedout").assertIsFile()
    }

    void assertRepoNotCheckedOut(String repoName="dep") {
        def checkout = checkoutDir(repoName, "fixed", "directory-repo:${file(repoName).absolutePath}")
        checkout.file("checkedout").assertDoesNotExist()
    }
}
