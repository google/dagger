/*
 * Copyright (C) 2023 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilesTest {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    @Test
    fun testWalkInPlatformIndependentOrder() {
        val testDir = testProjectDir.root

        /**
         * testDir
         * ├── dir1
         * │   ├── file1
         * └── dir2
         * │   ├── dir3
         * │   │   └── file2
         * │   └── file3
         * └── file4
         */
        File(testDir, "dir2").mkdir()
        File(testDir, "dir2/dir3").mkdir()
        File(testDir, "dir2/dir3/file2").mkdir()
        File(testDir, "dir2/file3").createNewFile()
        File(testDir, "dir1").mkdir()
        File(testDir, "dir1/file1").createNewFile()
        File(testDir, "file4").createNewFile()
        val filesInOrder = testDir.walkInPlatformIndependentOrder().toList()

        val expected: List<File> = listOf(
            File(testDir, ""),
            File(testDir, "dir1"),
            File(testDir, "dir1/file1"),
            File(testDir, "dir2"),
            File(testDir, "dir2/dir3"),
            File(testDir, "dir2/dir3/file2"),
            File(testDir, "dir2/file3"),
            File(testDir, "file4"),
        )
        assertEquals(expected, filesInOrder)

        val filesNotInOrder = testDir.walkTopDown().toList()
        assertTrue(filesInOrder != filesNotInOrder)

        testDir.deleteRecursively()
    }
}
