/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.file

import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

// cannot use org.gradle.util.Resources here because the files it returns have already been canonicalized
@Requires(TestPrecondition.SYMLINKS)
class FileCollectionSymlinkTest extends AbstractProjectBuilderSpec {
    TestFile baseDir
    TestFile file
    TestFile symlink
    TestFile symlinked

    def setup() {
        baseDir = temporaryFolder.file('files')
        file = baseDir.file('file')
        file.text = 'regular file'
        symlinked = baseDir.file('symlinked')
        symlinked.text = 'target of symlink'
        symlink = baseDir.file('symlink')
        symlink.createLink(symlinked)
    }

    @Unroll("#desc can handle symlinks")
    def "file collection can handle symlinks"() {
        def fileCollection = this."${method}"()

        expect:
        fileCollection.contains(file)
        fileCollection.contains(symlink)
        fileCollection.contains(symlinked)
        fileCollection.files == [file, symlink, symlinked] as Set

        (fileCollection - project.files(symlink)).files == [file, symlinked] as Set

        where:
        desc                 | method
        "project.files()"    | "files"
        "project.fileTree()" | "fileTree"
    }

    def files() {
        project.files(file, symlink, symlinked)
    }

    def fileTree() {
        project.fileTree(baseDir)
    }
}
