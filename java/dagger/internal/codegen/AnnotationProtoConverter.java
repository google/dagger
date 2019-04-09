/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.transformValues;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static javax.lang.model.util.ElementFilter.fieldsIn;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.serialization.AnnotationProto;
import dagger.internal.codegen.serialization.AnnotationValueProto;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/** Converts {@link AnnotationMirror}s to {@link AnnotationProto}s and vice-versa. */
final class AnnotationProtoConverter {
  private final TypeProtoConverter typeProtoConverter;

  @Inject
  AnnotationProtoConverter(TypeProtoConverter typeProtoConverter) {
    this.typeProtoConverter = typeProtoConverter;
  }

  /** Translates an {@link AnnotationMirror} to a proto representation. */
  static AnnotationProto toProto(AnnotationMirror annotationMirror) {
    AnnotationProto.Builder builder = AnnotationProto.newBuilder();
    builder.setAnnotationType(TypeProtoConverter.toProto(annotationMirror.getAnnotationType()));
    getAnnotationValuesWithDefaults(annotationMirror)
        .forEach(
            (attribute, value) ->
                builder.putValues(
                    attribute.getSimpleName().toString(), annotationValueProto(value)));
    return builder.build();
  }

  /** Creates an {@link AnnotationMirror} from its proto representation. */
  AnnotationMirror fromProto(AnnotationProto annotation) {
    return SimpleAnnotationMirror.of(
        MoreTypes.asTypeElement(typeProtoConverter.fromProto(annotation.getAnnotationType())),
        transformValues(annotation.getValuesMap(), AnnotationValueFromProto::new));
  }

  private static final AnnotationValueVisitor<
          AnnotationValueProto.Builder, AnnotationValueProto.Builder>
      ANNOTATION_VALUE_TO_PROTO =
          new SimpleAnnotationValueVisitor8<
              AnnotationValueProto.Builder, AnnotationValueProto.Builder>() {
            @Override
            public AnnotationValueProto.Builder visitAnnotation(
                AnnotationMirror nestedAnnotation, AnnotationValueProto.Builder builder) {
              return builder
                  .setNestedAnnotation(toProto(nestedAnnotation))
                  .setKind(AnnotationValueProto.Kind.ANNOTATION);
            }

            @Override
            public AnnotationValueProto.Builder visitBoolean(
                boolean b, AnnotationValueProto.Builder builder) {
              return builder.setBooleanValue(b).setKind(AnnotationValueProto.Kind.BOOLEAN);
            }

            @Override
            public AnnotationValueProto.Builder visitChar(
                char c, AnnotationValueProto.Builder builder) {
              return builder
                  .setStringValue(String.valueOf(c))
                  .setKind(AnnotationValueProto.Kind.CHAR);
            }

            @Override
            public AnnotationValueProto.Builder visitByte(
                byte b, AnnotationValueProto.Builder builder) {
              return builder.setIntValue(b).setKind(AnnotationValueProto.Kind.BYTE);
            }

            @Override
            public AnnotationValueProto.Builder visitShort(
                short s, AnnotationValueProto.Builder builder) {
              return builder.setIntValue(s).setKind(AnnotationValueProto.Kind.SHORT);
            }

            @Override
            public AnnotationValueProto.Builder visitInt(
                int i, AnnotationValueProto.Builder builder) {
              return builder.setIntValue(i).setKind(AnnotationValueProto.Kind.INT);
            }

            @Override
            public AnnotationValueProto.Builder visitFloat(
                float f, AnnotationValueProto.Builder builder) {
              return builder.setFloatValue(f).setKind(AnnotationValueProto.Kind.FLOAT);
            }

            @Override
            public AnnotationValueProto.Builder visitLong(
                long l, AnnotationValueProto.Builder builder) {
              return builder.setLongValue(l).setKind(AnnotationValueProto.Kind.LONG);
            }

            @Override
            public AnnotationValueProto.Builder visitDouble(
                double d, AnnotationValueProto.Builder builder) {
              return builder.setDoubleValue(d).setKind(AnnotationValueProto.Kind.DOUBLE);
            }

            @Override
            public AnnotationValueProto.Builder visitString(
                String s, AnnotationValueProto.Builder builder) {
              return builder.setStringValue(s).setKind(AnnotationValueProto.Kind.STRING);
            }

            @Override
            public AnnotationValueProto.Builder visitType(
                TypeMirror t, AnnotationValueProto.Builder builder) {
              return builder
                  .setClassLiteral(TypeProtoConverter.toProto(t))
                  .setKind(AnnotationValueProto.Kind.CLASS_LITERAL);
            }

            @Override
            public AnnotationValueProto.Builder visitEnumConstant(
                VariableElement c, AnnotationValueProto.Builder builder) {
              return builder
                  .setEnumType(TypeProtoConverter.toProto(c.asType()))
                  .setEnumName(c.getSimpleName().toString())
                  .setKind(AnnotationValueProto.Kind.ENUM);
            }

            @Override
            public AnnotationValueProto.Builder visitArray(
                List<? extends AnnotationValue> values, AnnotationValueProto.Builder builder) {
              values.forEach(value -> builder.addArrayValues(annotationValueProto(value)));
              return builder.setKind(AnnotationValueProto.Kind.ARRAY);
            }

            @Override
            public AnnotationValueProto.Builder visitUnknown(
                AnnotationValue av, AnnotationValueProto.Builder builder) {
              throw new UnsupportedOperationException(av.toString());
            }
          };

  /** Translates an {@link AnnotationValue} to a proto representation. */
  private static AnnotationValueProto annotationValueProto(AnnotationValue annotationValue) {
    return annotationValue
        .accept(ANNOTATION_VALUE_TO_PROTO, AnnotationValueProto.newBuilder())
        .build();
  }

  private class AnnotationValueFromProto implements AnnotationValue {
    private final AnnotationValueProto proto;

    AnnotationValueFromProto(AnnotationValueProto proto) {
      this.proto = proto;
    }

    @Override
    public Object getValue() {
      switch (proto.getKind()) {
        case BOOLEAN:
          return proto.getBooleanValue();
        case BYTE:
          return (byte) proto.getIntValue();
        case SHORT:
          return (short) proto.getIntValue();
        case CHAR:
          return getCharValue();
        case INT:
          return proto.getIntValue();
        case FLOAT:
          return proto.getFloatValue();
        case LONG:
          return proto.getLongValue();
        case DOUBLE:
          return proto.getDoubleValue();
        case STRING:
          return proto.getStringValue();
        case CLASS_LITERAL:
          return typeProtoConverter.fromProto(proto.getClassLiteral());
        case ENUM:
          return getEnumConstant();
        case ANNOTATION:
          return fromProto(proto.getNestedAnnotation());
        case ARRAY:
          return getArrayValues();
        case UNKNOWN:
        case UNRECOGNIZED:
          // fall through
      }
      throw new AssertionError(proto);
    }

    @Override
    public <R, P> R accept(AnnotationValueVisitor<R, P> visitor, P passedValue) {
      switch (proto.getKind()) {
        case BOOLEAN:
          return visitor.visitBoolean(proto.getBooleanValue(), passedValue);
        case BYTE:
          return visitor.visitByte((byte) proto.getIntValue(), passedValue);
        case SHORT:
          return visitor.visitShort((short) proto.getIntValue(), passedValue);
        case CHAR:
          return visitor.visitChar(getCharValue(), passedValue);
        case INT:
          return visitor.visitInt(proto.getIntValue(), passedValue);
        case FLOAT:
          return visitor.visitFloat(proto.getFloatValue(), passedValue);
        case LONG:
          return visitor.visitLong(proto.getLongValue(), passedValue);
        case DOUBLE:
          return visitor.visitDouble(proto.getDoubleValue(), passedValue);
        case STRING:
          return visitor.visitString((String) getValue(), passedValue);
        case CLASS_LITERAL:
          return visitor.visitType((TypeMirror) getValue(), passedValue);
        case ENUM:
          return visitor.visitEnumConstant((VariableElement) getValue(), passedValue);
        case ANNOTATION:
          return visitor.visitAnnotation((AnnotationMirror) getValue(), passedValue);
        case ARRAY:
          return visitor.visitArray(getArrayValues(), passedValue);
        case UNKNOWN:
        case UNRECOGNIZED:
          // fall through
      }
      throw new AssertionError(proto);
    }

    private char getCharValue() {
      checkState(proto.getKind().equals(AnnotationValueProto.Kind.CHAR));
      return proto.getStringValue().charAt(0);
    }

    private VariableElement getEnumConstant() {
      checkState(proto.getKind().equals(AnnotationValueProto.Kind.ENUM));
      TypeMirror enumType = typeProtoConverter.fromProto(proto.getEnumType());
      return fieldsIn(MoreTypes.asTypeElement(enumType).getEnclosedElements()).stream()
          .filter(value -> value.getSimpleName().contentEquals(proto.getEnumName()))
          .findFirst()
          .get();
    }

    private ImmutableList<AnnotationValue> getArrayValues() {
      checkState(proto.getKind().equals(AnnotationValueProto.Kind.ARRAY));
      return proto.getArrayValuesList().stream()
          .map(AnnotationValueFromProto::new)
          .collect(toImmutableList());
    }
  }
}
