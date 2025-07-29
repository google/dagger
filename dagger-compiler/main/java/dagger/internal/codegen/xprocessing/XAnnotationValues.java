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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.lang.Character.isISOControl;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XAnnotationValue;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.CodeBlock;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XAnnotationValue} helper methods. */
public final class XAnnotationValues {
  private static final Equivalence<XAnnotationValue> XANNOTATION_VALUE_EQUIVALENCE =
      new Equivalence<XAnnotationValue>() {
        @Override
        protected boolean doEquivalent(XAnnotationValue left, XAnnotationValue right) {
          if (left.hasAnnotationValue()) {
            return right.hasAnnotationValue()
                && XAnnotations.equivalence().equivalent(left.asAnnotation(), right.asAnnotation());
          } else if (left.hasListValue()) {
            return right.hasListValue()
                && XAnnotationValues.equivalence()
                    .pairwise()
                    .equivalent(left.asAnnotationValueList(), right.asAnnotationValueList());
          } else if (left.hasTypeValue()) {
            return right.hasTypeValue()
                && XTypes.equivalence().equivalent(left.asType(), right.asType());
          }
          return left.getValue().equals(right.getValue());
        }

        @Override
        protected int doHash(XAnnotationValue value) {
          if (value.hasAnnotationValue()) {
            return XAnnotations.equivalence().hash(value.asAnnotation());
          } else if (value.hasListValue()) {
            return XAnnotationValues.equivalence().pairwise().hash(value.asAnnotationValueList());
          } else if (value.hasTypeValue()) {
            return XTypes.equivalence().hash(value.asType());
          }
          return value.getValue().hashCode();
        }

        @Override
        public String toString() {
          return "XAnnotationValues.equivalence()";
        }
      };

  /** Returns an {@link Equivalence} for {@link XAnnotationValue}. */
  public static Equivalence<XAnnotationValue> equivalence() {
    return XANNOTATION_VALUE_EQUIVALENCE;
  }

  public static String getKindName(XAnnotationValue value) {
    if (value.hasAnnotationListValue()) {
      return "ANNOTATION_ARRAY";
    } else if (value.hasAnnotationValue()) {
      return "ANNOTATION";
    } else if (value.hasEnumListValue()) {
      return "ENUM_ARRAY";
    } else if (value.hasEnumValue()) {
      return "ENUM";
    } else if (value.hasTypeListValue()) {
      return "TYPE_ARRAY";
    } else if (value.hasTypeValue()) {
      return "TYPE";
    } else if (value.hasBooleanListValue()) {
      return "BOOLEAN_ARRAY";
    } else if (value.hasBooleanValue()) {
      return "BOOLEAN";
    } else if (value.hasByteListValue()) {
      return "BYTE_ARRAY";
    } else if (value.hasByteValue()) {
      return "BYTE";
    } else if (value.hasCharListValue()) {
      return "CHAR_ARRAY";
    } else if (value.hasCharValue()) {
      return "CHAR";
    } else if (value.hasDoubleListValue()) {
      return "DOUBLE_ARRAY";
    } else if (value.hasDoubleValue()) {
      return "DOUBLE";
    } else if (value.hasFloatListValue()) {
      return "FLOAT_ARRAY";
    } else if (value.hasFloatValue()) {
      return "FLOAT";
    } else if (value.hasIntListValue()) {
      return "INT_ARRAY";
    } else if (value.hasIntValue()) {
      return "INT";
    } else if (value.hasLongListValue()) {
      return "LONG_ARRAY";
    } else if (value.hasLongValue()) {
      return "LONG";
    } else if (value.hasShortListValue()) {
      return "SHORT_ARRAY";
    } else if (value.hasShortValue()) {
      return "SHORT";
    } else if (value.hasStringListValue()) {
      return "STRING_ARRAY";
    } else if (value.hasStringValue()) {
      return "STRING";
    } else {
      return value.hasListValue() ? "UNKNOWN_ARRAY" : "UNKNOWN";
    }
  }

  public static String toStableString(XAnnotationValue value) {
    try {
      // TODO(b/251786719): XProcessing handles error values differently in KSP and Javac. In Javac
      // an exception is thrown for type "<error>", but in KSP the value is just null. We work
      // around this here and try to give the same string regardless of the backend.
      if (value.getValue() == null) {
        return "<error>";
      }
      if (value.hasListValue()) {
        // TODO(b/241834848): After this is fixed, consider skipping the braces for single values.
        return value.asAnnotationValueList().stream()
            .map(v -> toStableString(v))
            .collect(joining(", ", "{", "}"));
      } else if (value.hasAnnotationValue()) {
        return XAnnotations.toStableString(value.asAnnotation());
      } else if (value.hasEnumValue()) {
        return getSimpleName(value.asEnum());
      } else if (value.hasTypeValue()) {
        return value.asType().getTypeElement().getQualifiedName();
      } else if (value.hasStringValue()) {
        return CodeBlock.of("$S", value.asString()).toString();
      } else if (value.hasCharValue()) {
        return characterLiteralWithSingleQuotes(value.asChar());
      } else {
        return value.getValue().toString();
      }
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  public static String characterLiteralWithSingleQuotes(char c) {
    return "'" + characterLiteralWithoutSingleQuotes(c) + "'";
  }

  // TODO(bcorso): Replace with javapoet when fixed: https://github.com/square/javapoet/issues/698.
  private static String characterLiteralWithoutSingleQuotes(char c) {
    // see https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6
    switch (c) {
      case '\b': // backspace (BS)
        return "\\b";
      case '\t': // horizontal tab (HT)
        return "\\t";
      case '\n': // linefeed (LF)
        return "\\n";
      case '\f': // form feed (FF)
        return "\\f";
      case '\r': // carriage return (CR)
        return "\\r";
      case '\"': // double quote (")
        return "\"";
      case '\'': // single quote (')
        return "\\'";
      case '\\': // backslash (\)
        return "\\\\";
      default:
        return isISOControl(c) ? String.format("\\u%04x", (int) c) : Character.toString(c);
    }
  }

  private XAnnotationValues() {}
}
