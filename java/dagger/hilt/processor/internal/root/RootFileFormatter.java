/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import com.squareup.javapoet.JavaFile;
import java.io.IOException;
import java.io.Writer;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * Typically we would just use {@code JavaFile#writeTo()} to write files. However, this formatter
 * exists to add new lines inbetween interfaces. This can be important for classes with many
 * interfaces (e.g. Dagger components) to avoid spamming the entire list of interfaces when
 * reporting errors to the user.
 *
 * <p>See b/33108646.
 */
final class RootFileFormatter {
  private static final Pattern CLASS_PATERN = Pattern.compile("(\\h*)(.*class.*implements)(.*\\{)");

  /** Formats the {@link JavaFile} java source file. */
  static void write(JavaFile javaFile, Filer filer) throws IOException {
    String fileName =
        javaFile.packageName.isEmpty()
            ? javaFile.typeSpec.name
            : javaFile.packageName + "." + javaFile.typeSpec.name;

    Element[] originatingElements = javaFile.typeSpec.originatingElements.toArray(new Element[0]);

    StringBuilder sb = new StringBuilder("");
    javaFile.writeTo(sb);
    String fileContent = formatInterfaces(sb.toString(), CLASS_PATERN);

    JavaFileObject filerSourceFile = filer.createSourceFile(fileName, originatingElements);
    try (Writer writer = filerSourceFile.openWriter()) {
      writer.write(fileContent);
    } catch (Exception e) {
      try {
        filerSourceFile.delete();
      } catch (Exception ignored) {
        // Nothing to do.
      }
      throw e;
    }
  }

  private static String formatInterfaces(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    StringBuffer sb = new StringBuffer(content.length());
    while (matcher.find()) {
      MatchResult result = matcher.toMatchResult();
      String spaces = result.group(1);
      String prefix = result.group(2);
      String interfaces = result.group(3);
      String formattedInterfaces = formatInterfaces(spaces, interfaces);
      matcher.appendReplacement(
          sb, Matcher.quoteReplacement(spaces + prefix + formattedInterfaces));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String formatInterfaces(String prefixSpaces, String interfaces) {
    StringBuilder sb = new StringBuilder(interfaces);
    String newLine = String.format("\n%s   ", prefixSpaces);

    // Add a line break after each interface so that there's only 1 interface per line.
    int i = 0;
    int bracketCount = 0;
    while (i >= 0 && i < sb.length()) {
      char c = sb.charAt(i++);
      if (c == '<') {
        bracketCount++;
      } else if (c == '>') {
        bracketCount--;
      } else if (c == ',' && bracketCount == 0) {
        sb.insert(i++, newLine);
      }
    }
    return sb.toString();
  }

  private RootFileFormatter() {}
}
