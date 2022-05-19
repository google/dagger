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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XAnnotationValue;
import com.google.common.base.Equivalence;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XAnnotationValue} helper methods. */
public final class XAnnotationValues {
  private enum Kind {
    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT,
    LONG,
    SHORT,
    STRING,
    TYPE,
    ENUM_CONSTANT,
    ANNOTATION,
    ARRAY,
  };

  public static boolean hasBooleanValue(XAnnotationValue value) {
    return getKind(value) == Kind.BOOLEAN;
  }

  public static boolean hasByteValue(XAnnotationValue value) {
    return getKind(value) == Kind.BYTE;
  }

  public static boolean hasCharValue(XAnnotationValue value) {
    return getKind(value) == Kind.CHAR;
  }

  public static boolean hasDoubleValue(XAnnotationValue value) {
    return getKind(value) == Kind.DOUBLE;
  }

  public static boolean hasFloatValue(XAnnotationValue value) {
    return getKind(value) == Kind.FLOAT;
  }

  public static boolean hasIntValue(XAnnotationValue value) {
    return getKind(value) == Kind.INT;
  }

  public static boolean hasLongValue(XAnnotationValue value) {
    return getKind(value) == Kind.LONG;
  }

  public static boolean hasShortValue(XAnnotationValue value) {
    return getKind(value) == Kind.SHORT;
  }

  public static boolean hasStringValue(XAnnotationValue value) {
    return getKind(value) == Kind.STRING;
  }

  public static boolean hasTypeValue(XAnnotationValue value) {
    return getKind(value) == Kind.TYPE;
  }

  public static boolean hasEnumValue(XAnnotationValue value) {
    return getKind(value) == Kind.ENUM_CONSTANT;
  }

  public static boolean hasAnnotationValue(XAnnotationValue value) {
    return getKind(value) == Kind.ANNOTATION;
  }

  public static boolean hasArrayValue(XAnnotationValue value) {
    return getKind(value) == Kind.ARRAY;
  }

  private static Kind getKind(XAnnotationValue value) {
    return KindVisitor.INSTANCE.visit(toJavac(value));
  }

  private static final class KindVisitor extends SimpleAnnotationValueVisitor8<Kind, Void> {
    private static final KindVisitor INSTANCE = new KindVisitor();

    @Override
    public Kind visitBoolean(boolean b, Void p) {
      return Kind.BOOLEAN;
    }

    @Override
    public Kind visitByte(byte b, Void p) {
      return Kind.BYTE;
    }

    @Override
    public Kind visitChar(char c, Void p) {
      return Kind.CHAR;
    }

    @Override
    public Kind visitDouble(double d, Void p) {
      return Kind.DOUBLE;
    }

    @Override
    public Kind visitFloat(float f, Void p) {
      return Kind.FLOAT;
    }

    @Override
    public Kind visitInt(int i, Void p) {
      return Kind.INT;
    }

    @Override
    public Kind visitLong(long i, Void p) {
      return Kind.LONG;
    }

    @Override
    public Kind visitShort(short s, Void p) {
      return Kind.SHORT;
    }

    @Override
    public Kind visitString(String s, Void p) {
      return Kind.STRING;
    }

    @Override
    public Kind visitType(TypeMirror t, Void p) {
      return Kind.TYPE;
    }

    @Override
    public Kind visitEnumConstant(VariableElement e, Void p) {
      return Kind.ENUM_CONSTANT;
    }

    @Override
    public Kind visitAnnotation(AnnotationMirror a, Void p) {
      return Kind.ANNOTATION;
    }

    @Override
    public Kind visitArray(List<? extends AnnotationValue> vals, Void p) {
      return Kind.ARRAY;
    }
  }

  private static final Equivalence<XAnnotationValue> XANNOTATION_VALUE_EQUIVALENCE =
      new Equivalence<XAnnotationValue>() {
        @Override
        protected boolean doEquivalent(XAnnotationValue left, XAnnotationValue right) {
          if (hasAnnotationValue(left)) {
            return hasAnnotationValue(right)
                && XAnnotations.equivalence().equivalent(left.asAnnotation(), right.asAnnotation());
          } else if (hasArrayValue(left)) {
            return hasArrayValue(right)
                && XAnnotationValues.equivalence()
                    .pairwise()
                    .equivalent(left.asAnnotationValueList(), right.asAnnotationValueList());
          } else if (hasTypeValue(left)) {
            return hasTypeValue(right)
                && XTypes.equivalence().equivalent(left.asType(), right.asType());
          }
          return left.getValue().equals(right.getValue());
        }

        @Override
        protected int doHash(XAnnotationValue value) {
          if (hasAnnotationValue(value)) {
            return XAnnotations.equivalence().hash(value.asAnnotation());
          } else if (hasArrayValue(value)) {
            return XAnnotationValues.equivalence().pairwise().hash(value.asAnnotationValueList());
          } else if (hasTypeValue(value)) {
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

  private XAnnotationValues() {}
}
