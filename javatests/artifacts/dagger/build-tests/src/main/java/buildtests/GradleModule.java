/*
 * Copyright (C) 2021 The Dagger Authors.
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

package buildtests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/** Used to create files for a Gradle module in a particular directory. */
public final class GradleModule {
  public static GradleModule create(File moduleDir) {
    return new GradleModule(moduleDir);
  }

  public static GradleModule create(File projectDir, String moduleName) {
    return new GradleModule(new File(projectDir, moduleName));
  }

  private final File moduleDir;
  private final File moduleSrcDir;

  private GradleModule(File moduleDir) {
    this.moduleDir = moduleDir;
    this.moduleSrcDir = new File(moduleDir, "src/main/java/");
  }

  public GradleModule addBuildFile(String... content) throws IOException {
    writeFile(createFile(moduleDir, "build.gradle"), content);
    return this;
  }

  public GradleModule addSettingsFile(String... content) throws IOException {
    writeFile(createFile(moduleDir, "settings.gradle"), content);
    return this;
  }

  public GradleModule addFile(GradleFile gradleFile) throws IOException {
    return addFile(gradleFile.fileName(), gradleFile.fileContent());
  }

  public GradleModule addFile(String fileName, String... content) throws IOException {
    writeFile(createFile(moduleDir, fileName), content);
    return this;
  }

  public GradleModule addSrcFile(GradleFile gradleFile) throws IOException {
    return addSrcFile(gradleFile.fileName(), gradleFile.fileContent());
  }

  public GradleModule addSrcFile(String fileName, String... content) throws IOException {
    writeFile(createFile(moduleSrcDir, fileName), content);
    return this;
  }

  private static File createFile(File dir, String fileName) {
    File file = new File(dir, fileName);
    file.getParentFile().mkdirs();
    return file;
  }

  private static void writeFile(File destination, String... content) throws IOException {
    BufferedWriter output = null;
    try {
      output = new BufferedWriter(new FileWriter(destination));
      for (String line : content) {
        output.write(line + "\n");
      }
    } finally {
      if (output != null) {
        output.close();
      }
    }
  }
}
