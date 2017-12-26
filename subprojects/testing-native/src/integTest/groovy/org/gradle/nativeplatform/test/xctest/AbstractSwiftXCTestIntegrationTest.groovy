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

package org.gradle.nativeplatform.test.xctest

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.language.swift.AbstractSwiftComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT])
abstract class AbstractSwiftXCTestIntegrationTest extends AbstractSwiftComponentIntegrationTest {
    def setup() {
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'xcTest')
    }

    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        given:
        def fixture = getPassingTestFixture()
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        fixture.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", expectedLifecycleTasks)
        fixture.assertTestCasesRan(testExecutionResult)

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", taskToAssembleComponentUnderTest, ":assemble"]
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        given:
        def fixture = getPassingTestFixture()
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        fixture.writeToProject(testDirectory)

        succeeds("test")

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest, ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
    }

    @Override
    protected String getAllBinariesOfMainComponentBuildScript() {
        return "xctest.binaries.get()"
    }

    protected abstract String[] getTaskToAssembleComponentUnderTest()

    protected abstract String[] getTasksToCompileComponentUnderTest()

    protected abstract XCTestSourceElement getPassingTestFixture()
}
