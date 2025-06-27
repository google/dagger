/*
 * Copyright (C) 2025 The Dagger Authors.
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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static androidx.room.compiler.codegen.compat.XConverters.toKotlinPoet;
import static com.google.common.collect.Streams.stream;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.xprocessing.XTypeNames.daggerProviderOf;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.CodeBlock;
import java.util.stream.Collector;

/** Convenience methods for creating {@link XCodeBlock}s. */
public final class XCodeBlocks {
  /**
   * Returns an {@link XCodeBlock} with the given java implementation and an empty kotlin
   * implementation.
   *
   * <p>This is a temporary stop gap to allow us to migrate from javapoet to xprocessing without
   * having to do a strict top-down approach.
   */
  public static XCodeBlock toXPoet(CodeBlock codeBlock) {
    return XConverters.toXPoet(codeBlock, com.squareup.kotlinpoet.CodeBlock.of(""));
  }

  /**
   * Joins {@link XCodeBlock} instances in a manner suitable for use as method parameters (or
   * arguments).
   */
  public static Collector<XCodeBlock, ?, XCodeBlock> toParametersCodeBlock() {
    // TODO(ronshapiro,jakew): consider adding zero-width spaces to help line breaking when the
    // formatter is off. If not, inline this
    return joining(", ");
  }

  /** Concatenates {@link XCodeBlock} instances separated by newlines for readability. */
  public static Collector<XCodeBlock, ?, XCodeBlock> toConcatenatedCodeBlock() {
    return joining("\n", "", "\n");
  }

  /** Returns a comma-separated version of {@code codeBlocks} as one unified {@link XCodeBlock}. */
  public static XCodeBlock makeParametersCodeBlock(Iterable<XCodeBlock> codeBlocks) {
    return stream(codeBlocks).collect(toParametersCodeBlock());
  }

  /**
   * Returns a comma-separated {@link XCodeBlock} using the name of every parameter in {@code
   * parameters}.
   */
  public static XCodeBlock parameterNames(Iterable<XParameterSpec> parameters) {
    // TODO(ronshapiro): Add DaggerStreams.stream(Iterable)
    return stream(parameters)
        .map(p -> XCodeBlock.of("%N", p.getName())) // SUPPRESS_GET_NAME_CHECK
        .collect(toParametersCodeBlock());
  }

  /**
   * Returns one unified {@link XCodeBlock} which joins each item in {@code codeBlocks} with a
   * newline.
   */
  public static XCodeBlock concat(Iterable<XCodeBlock> codeBlocks) {
    return stream(codeBlocks).collect(toConcatenatedCodeBlock());
  }

  /**
   * Returns an anonymous {@link javax.inject.Provider} class with the single {@link
   * javax.inject.Provider#get()} method that returns the given {@code expression}.
   */
  public static XCodeBlock anonymousProvider(XExpression expression) {
    return anonymousProvider(
        expression.type().asTypeName(), XCodeBlock.of("return %L;", expression.codeBlock()));
  }

  /**
   * Returns an anonymous {@link javax.inject.Provider} class with the single {@link
   * javax.inject.Provider#get()} method implemented by {@code body}.
   */
  public static XCodeBlock anonymousProvider(XTypeName providedType, XCodeBlock body) {
    return toXPoet(
        CodeBlock.of(
            "$L",
            anonymousClassBuilder("")
                .superclass(toJavaPoet(daggerProviderOf(providedType)))
                .addMethod(
                    methodBuilder("get")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(toJavaPoet(providedType))
                        .addCode(toJavaPoet(body))
                        .build())
                .build()));
  }

  /** Returns {@code expression} cast to a type. */
  public static XCodeBlock cast(XCodeBlock expression, XClassName castTo) {
    return XCodeBlock.ofCast(castTo, expression);
  }

  public static XCodeBlock type(XType type) {
    return XCodeBlock.of("%T", type.asTypeName());
  }

  public static XCodeBlock staticReferenceOf(XTypeElement typeElement) {
    if (typeElement.isKotlinObject() && !typeElement.isCompanionObject()) {
      // Call through the singleton instance.
      // See: https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#static-methods
      XCodeBlock.Builder builder = XCodeBlock.builder();
      toJavaPoet(builder).add("$T.INSTANCE", toJavaPoet(typeElement.asClassName()));
      toKotlinPoet(builder).add("%T", toKotlinPoet(typeElement.asClassName()));
      return builder.build();
    }
    return XCodeBlock.of("%T", typeElement.asClassName());
  }

  public static XCodeBlock stringLiteral(String toWrap) {
    return XCodeBlock.of("%S", toWrap);
  }

  public static XCodeBlock join(Iterable<XCodeBlock> codeBlocks, String separator) {
    return stream(codeBlocks).collect(joining(separator));
  }

  public static Collector<XCodeBlock, ?, XCodeBlock> joining(String separator) {
    return Collector.of(
        () -> new XCodeBlockJoiner(separator, XCodeBlock.builder()),
        XCodeBlockJoiner::add,
        XCodeBlockJoiner::merge,
        XCodeBlockJoiner::join);
  }

  public static Collector<XCodeBlock, ?, XCodeBlock> joining(
      String separator, String prefix, String suffix) {
    XCodeBlock.Builder builder = XCodeBlock.builder();
    if (prefix != null && !prefix.isEmpty()) {
      builder.add("%L", prefix);
    }
    return Collector.of(
        () -> new XCodeBlockJoiner(separator, builder),
        XCodeBlockJoiner::add,
        XCodeBlockJoiner::merge,
        joiner -> {
          if (suffix != null && !suffix.isEmpty()) {
            builder.add("%L", suffix);
          }
          return joiner.join();
        });
  }

  public static boolean isEmpty(XCodeBlock codeBlock) {
    // TODO(bcorso): Take into account kotlin code blocks.
    return toJavaPoet(codeBlock).isEmpty();
  }

  public static XCodeBlock ofJavaClassLiteral(XTypeName typeName) {
    XCodeBlock.Builder builder = XCodeBlock.builder();
    toJavaPoet(builder).add("$T.class", toJavaPoet(typeName));
    toKotlinPoet(builder).add("%T::class.java", toKotlinPoet(typeName));
    return builder.build();
  }

  public static XCodeBlock ofLocalVal(
      String name, XTypeName typeName, String format, Object... args) {
    return ofLocalVal(name, typeName, XCodeBlock.of(format, args));
  }

  public static XCodeBlock ofLocalVal(String name, XTypeName typeName, XCodeBlock initialization) {
    return XCodeBlock.builder()
        .addLocalVariable(name, typeName, /* isMutable= */ false, initialization)
        .build();
  }

  public static XCodeBlock ofLocalVar(
      String name, XTypeName typeName, String format, Object... args) {
    return ofLocalVar(name, typeName, XCodeBlock.of(format, args));
  }

  public static XCodeBlock ofLocalVar(String name, XTypeName typeName, XCodeBlock initialization) {
    return XCodeBlock.builder()
        .addLocalVariable(name, typeName, /* isMutable= */ true, initialization)
        .build();
  }

  private static final class XCodeBlockJoiner {
    private final String delimiter;
    private final XCodeBlock.Builder builder;
    private boolean first = true;

    XCodeBlockJoiner(String delimiter, XCodeBlock.Builder builder) {
      this.delimiter = delimiter;
      this.builder = builder;
    }

    @CanIgnoreReturnValue
    XCodeBlockJoiner add(XCodeBlock codeBlock) {
      if (!first) {
        if (!toKotlinPoet(codeBlock).isEmpty()) {
          toKotlinPoet(builder).add(delimiter);
        }
        if (!toJavaPoet(codeBlock).isEmpty()) {
          toJavaPoet(builder).add(delimiter);
        }
      }
      first = false;

      if (!toKotlinPoet(codeBlock).isEmpty()) {
        toKotlinPoet(builder).add(toKotlinPoet(codeBlock));
      }
      if (!toJavaPoet(codeBlock).isEmpty()) {
        toJavaPoet(builder).add(toJavaPoet(codeBlock));
      }
      return this;
    }

    @CanIgnoreReturnValue
    XCodeBlockJoiner merge(XCodeBlockJoiner other) {
      add(other.builder.build());
      return this;
    }

    XCodeBlock join() {
      return builder.build();
    }
  }

  private XCodeBlocks() {}
}
