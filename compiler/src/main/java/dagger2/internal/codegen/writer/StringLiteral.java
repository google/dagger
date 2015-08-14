/*
 * Copyright (C) 2014 Square, Inc.
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
package dagger2.internal.codegen.writer;

import java.util.Formatter;

/**
 * Represents a string literal as found in Java source code.
 */
public final class StringLiteral {
  /** Returns a new {@link StringLiteral} instance for the intended value of the literal. */
  public static StringLiteral forValue(String value) {
    return new StringLiteral(value, stringLiteral(value));
  }

  /** Returns the string literal representing {@code data}, including wrapping quotes. */
  private static String stringLiteral(String value) {
    StringBuilder result = new StringBuilder();
    result.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          result.append("\\\"");
          break;
        case '\\':
          result.append("\\\\");
          break;
        case '\b':
          result.append("\\b");
          break;
        case '\t':
          result.append("\\t");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\f':
          result.append("\\f");
          break;
        case '\r':
          result.append("\\r");
          break;
        default:
          if (Character.isISOControl(c)) {
            new Formatter(result).format("\\u%04x", (int) c);
          } else {
            result.append(c);
          }
      }
    }
    result.append('"');
    return result.toString();
  }

  private final String value;
  private final String literal;

  private StringLiteral(String value, String literal) {
    this.value = value;
    this.literal = literal;
  }

  public String value() {
    return value;
  }

  public String literal() {
    return literal;
  }

  @Override
  public String toString() {
    return literal;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof StringLiteral) {
      return this.value.equals(((StringLiteral) obj).value);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
