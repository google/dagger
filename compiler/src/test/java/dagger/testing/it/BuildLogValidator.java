/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2012 Square, Inc.
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
package dagger.testing.it;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class BuildLogValidator {

  /**
   * Processes a log file, ensuring it has all the provided strings within it.
   *
   * @param buildLogfile a log file to be searched
   * @param expectedStrings the strings that must be present in the log file for it to be valid
   */
  public void assertHasText(File buildLogfile, String ... expectedStrings) throws Throwable {
    String buildOutput = getBuildOutput(buildLogfile);

    StringBuilder sb = new StringBuilder("Build output did not contain expected error text:");
    boolean missing = false;

    for (String expected : expectedStrings) {
      if (!buildOutput.contains(expected)) {
        missing = true;
        sb.append("\n    \"").append(expected).append("\"");
      }
    }
    if (missing) {
      appendBuildStatus(sb, buildOutput);
      throw new Exception(sb.toString());
    }
  }

  /**
   * Processes a log file, ensuring it does not contain any of the provided strings within it.
   *
   * @param buildLogfile a log file to be searched
   * @param unexpectedStrings the strings that must not be present in the log file for it to be
   *        valid
   */
  public void assertDoesNotHaveText(File buildLogfile, String... unexpectedStrings)
      throws Throwable {
    String buildOutput = getBuildOutput(buildLogfile);

    StringBuilder sb = new StringBuilder("Build output contained unexpected text:");
    boolean found = false;

    for (String unexpected : unexpectedStrings) {
      if (buildOutput.contains(unexpected)) {
        found = true;
        sb.append("\n    \"").append(unexpected).append("\"");
      }
    }
    if (found) {
      appendBuildStatus(sb, buildOutput);
      throw new Exception(sb.toString());
    }
  }

  private String getBuildOutput(File buildLogfile) throws Throwable {
    String buildOutput;
    FileInputStream stream = new FileInputStream(buildLogfile);
    try {
      FileChannel fc = stream.getChannel();
      MappedByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      buildOutput = Charset.defaultCharset().decode(buf).toString();
    } finally {
      stream.close();
    }
    if (buildOutput == null) {
      throw new Exception("Could not read build output");
    }
    return buildOutput;
  }

  private void appendBuildStatus(StringBuilder sb, String buildOutput) {
    sb.append("\n\nBuild Output:\n\n");
    boolean containsError = false;
    for(String line : buildOutput.split("\n")) {
      if (line.contains("[ERROR]")) {
        containsError = true;
        sb.append("\n        ").append(line);
      }
    }
    if (!containsError) {
      sb.append("\nTEST BUILD SUCCEEDED.\n");
    }
  }

}
