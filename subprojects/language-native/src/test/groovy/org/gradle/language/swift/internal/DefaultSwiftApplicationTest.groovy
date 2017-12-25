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

package org.gradle.language.swift.internal

import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultSwiftApplicationTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    def app = new DefaultSwiftApplication("main", project.objects, project, project.configurations)

    def "can create executable binary"() {
        def targetPlatform = Stub(SwiftPlatform)
        def toolChain = Stub(NativeToolChainInternal)
        def platformToolProvider = Stub(PlatformToolProvider)

        expect:
        def binary = app.addExecutable("debug", true, false, true, targetPlatform, toolChain, platformToolProvider)
        binary.name == "mainDebug"
        binary.debuggable
        !binary.optimized
        binary.testable
        binary.targetPlatform == targetPlatform
        binary.toolChain == toolChain
        binary.platformToolProvider == platformToolProvider

        app.binaries.realizeNow()
        app.binaries.get() == [binary] as Set
    }

    def "throws exception when development binary is not available"() {
        given:
        app.binaries.realizeNow()

        when:
        app.developmentBinary.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No value has been specified for this provider."
    }
}
