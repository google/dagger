/*
 * Copyright (C) 2022 The Dagger Authors.
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

/** Stores the name and content of a file. */
public final class GradleFile {
  /** Creates a {@link GradleFile} with the given name and content */
  public static GradleFile create(String fileName, String... fileContent) {
    return new GradleFile(fileName, fileContent);
  }

  private final String fileName;
  private final String[] fileContent;

  GradleFile(String fileName, String... fileContent) {
    this.fileName = fileName;
    this.fileContent = fileContent;
  }

  String fileName() {
    return fileName;
  }

  String[] fileContent() {
    return fileContent;
  }
}
