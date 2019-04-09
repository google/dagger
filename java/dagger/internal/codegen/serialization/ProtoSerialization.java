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

package dagger.internal.codegen.serialization;

import static com.google.common.io.BaseEncoding.base64;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.squareup.javapoet.CodeBlock;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/**
 * Serializes and deserializes {@link Message}s using {@link BaseEncoding#base64()} for use in
 * annotation values.
 */
public final class ProtoSerialization {
  /** Returns a {@link CodeBlock} of {@code message} serialized as a String. */
  public static CodeBlock toAnnotationValue(Message message) {
    return CodeBlock.of("$S", base64().encode(message.toByteArray()));
  }

  /**
   * Returns a {@link Message T} from the deserialized the String {@code value}.
   *
   * @throws IllegalArgumentException if {@code value} represents an {@link AnnotationValue} who's
   *     type is not {@link String}
   */
  public static <T extends Message> T fromAnnotationValue(
      AnnotationValue value, T defaultInstance) {
    byte[] bytes = base64().decode(value.accept(STRING_VALUE, null));
    Message message;
    try {
      message = defaultInstance.getParserForType().parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new InconsistentSerializedProtoException(e);
    }
    @SuppressWarnings("unchecked") // guaranteed by proto API
    T t = (T) message;
    return t;
  }

  private static final AnnotationValueVisitor<String, Void> STRING_VALUE =
      new SimpleAnnotationValueVisitor8<String, Void>() {
        @Override
        public String visitString(String s, Void ignored) {
          return s;
        }

        @Override
        protected String defaultAction(Object o, Void ignored) {
          throw new IllegalArgumentException(o + " is not a String");
        }
      };

  /**
   * An exception thrown when the proto that's serialized in a compiled subcomponent implementation
   * is from a different version than the current compiler's.
   */
  public static final class InconsistentSerializedProtoException extends RuntimeException {
    InconsistentSerializedProtoException(Throwable cause) {
      super(cause);
    }
  }
}
