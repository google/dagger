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

package dagger.testing.golden;

import androidx.room.compiler.processing.util.Source;
import com.google.common.io.Resources;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A test rule that manages golden files for tests. */
public final class GoldenFileRule implements TestRule {
  /** The generated import used in the golden files */
  private static final String GOLDEN_GENERATED_IMPORT =
      "import javax.annotation.processing.Generated;";

  /** The generated import used with the current jdk version */
  private static final String JDK_GENERATED_IMPORT =
      isBeforeJava9()
          ? "import javax.annotation.Generated;"
          : "import javax.annotation.processing.Generated;";

  private static boolean isBeforeJava9() {
    try {
      Class.forName("java.lang.Module");
      return false;
    } catch (ClassNotFoundException e) {
      return true;
    }
  }

  // Parameterized arguments in junit4 are added in brackets to the end of test methods, e.g.
  // `myTestMethod[testParam1=FOO,testParam2=BAR]`. This pattern captures theses into two separate
  // groups, `<GROUP1>[<GROUP2>]` to make it easier when generating the golden file name.
  private static final Pattern JUNIT_PARAMETERIZED_METHOD = Pattern.compile("(.*?)\\[(.*?)\\]");

  private Description description;

  @Override
  public Statement apply(Statement base, Description description) {
    this.description = description;
    return base;
  }

  /**
   * Returns the golden file as a {@link Source} containing the file's content.
   *
   * <p>If the golden file does not exist, the returned file object contains an error message
   * pointing to the location of the missing golden file. This can be used with scripting tools to
   * output the correct golden file in the proper location.
   */
  public Source goldenSource(String generatedFilePath) {
    // Note: we wrap the IOException in a RuntimeException so that this can be called from within
    // the lambda required by XProcessing's testing APIs. We could avoid this by calling this method
    // outside of the lambda, but that seems like an non-worthwile hit to readability.
    try {
      return Source.Companion.java(
          generatedFilePath, goldenFileContent(generatedFilePath.replace('/', '.')));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the golden file as a {@link JavaFileObject} containing the file's content.
   *
   * If the golden file does not exist, the returned file object contain an error message pointing
   * to the location of the missing golden file. This can be used with scripting tools to output
   * the correct golden file in the proper location.
   */
  public JavaFileObject goldenFile(String qualifiedName) throws IOException {
    return JavaFileObjects.forSourceLines(qualifiedName, goldenFileContent(qualifiedName));
  }

  /**
   * Returns the golden file content.
   *
   * If the golden file does not exist, the returned content contains an error message pointing
   * to the location of the missing golden file. This can be used with scripting tools to output
   * the correct golden file in the proper location.
   */
  public String goldenFileContent(String qualifiedName) throws IOException {
    String fileName = relativeGoldenFileName(description, qualifiedName);
    String resourceName = "goldens/" + fileName;
    URL url = description.getTestClass().getResource(resourceName);
    if (url == null) {
      url = description.getTestClass().getClassLoader().getResource(resourceName);
    }
    return url == null
        // If the golden file does not exist, create a fake file with a comment pointing to the
        // missing golden file. This is helpful for scripts that need to generate golden files from
        // the test failures.
        ? "// Error: Missing golden file for goldens/" + fileName
        // The goldens are generated using jdk 11, so we use this replacement to allow the
        // goldens to also work when compiling using jdk < 9.
        : Resources.toString(url, StandardCharsets.UTF_8)
            .replace(GOLDEN_GENERATED_IMPORT, JDK_GENERATED_IMPORT);
  }

  /** Returns the relative name for the golden file. */
  private static String relativeGoldenFileName(Description description, String qualifiedName) {
    // If this is a parameterized test, the parameters will be appended to the end of the method
    // name. We use a matcher to separate them out for the golden file name.
    Matcher matcher = JUNIT_PARAMETERIZED_METHOD.matcher(description.getMethodName());
    boolean isParameterized = matcher.find();
    String methodName = isParameterized ? matcher.group(1) : description.getMethodName();
    String fileName = isParameterized ? qualifiedName + "_" + matcher.group(2) : qualifiedName;
    return String.format(
        "%s/%s/%s",
        description.getTestClass().getSimpleName(),
        methodName,
        fileName);
  }
}
