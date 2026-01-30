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

import static androidx.room3.compiler.codegen.compat.XConverters.toJavaPoet;
import static androidx.room3.compiler.codegen.compat.XConverters.toKotlinPoet;
import static androidx.room3.compiler.codegen.compat.XConverters.toXPoet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import androidx.room3.compiler.codegen.XAnnotationSpec;
import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.codegen.XCodeBlock;
import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.processing.JavaPoetExtKt;
import androidx.room3.compiler.processing.XAnnotation;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.AnnotationSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Static factories to create {@link AnnotationSpec}s. */
public final class XAnnotationSpecs {
  public static XAnnotationSpec of(XClassName className) {
    return builder(className).build();
  }

  public static XAnnotationSpec of(XAnnotation annotation) {
    return of(annotation, /* includeDefaultValues= */ false);
  }

  public static XAnnotationSpec of(XAnnotation annotation, boolean includeDefaultValues) {
    return toXPoet(
        JavaPoetExtKt.toAnnotationSpec(annotation, includeDefaultValues),
        // TODO(b/411661393): Add support for annotation values. For now, the KotlinPoet
        // implementation only copies the class name and ignores the annotation values.
        com.squareup.kotlinpoet.AnnotationSpec
            .builder(toKotlinPoet(XAnnotations.asClassName(annotation)))
            .build());
  }

  /** Values for an {@link SuppressWarnings} annotation. */
  public enum Suppression {
    RAWTYPES("rawtypes"),
    UNCHECKED("unchecked", "UNCHECKED_CAST"),
    FUTURE_RETURN_VALUE_IGNORED("FutureReturnValueIgnored"),
    KOTLIN_INTERNAL("KotlinInternal"),
    CAST("cast", "USELESS_CAST"),
    DEPRECATION("deprecation", "DEPRECATION"),
    UNINITIALIZED("nullness:initialization.field.uninitialized");

    private final String javaName;
    private final Optional<String> kotlinName;

    Suppression(String javaName) {
      this(javaName, Optional.empty());
    }

    Suppression(String javaName, String kotlinName) {
      this(javaName, Optional.of(kotlinName));
    }

    private Suppression(String javaName, Optional<String> kotlinName) {
      this.javaName = javaName;
      this.kotlinName = kotlinName;
    }
  }

  /** Creates an {@link XAnnotationSpec} for {@link SuppressWarnings}. */
  public static XAnnotationSpec suppressWarnings(Suppression first, Suppression... rest) {
    return suppressWarnings(ImmutableSet.copyOf(Lists.asList(first, rest)));
  }

  /** Creates an {@link XAnnotationSpec} for {@link SuppressWarnings}. */
  public static XAnnotationSpec suppressWarnings(ImmutableSet<Suppression> suppressions) {
    checkArgument(!suppressions.isEmpty());

    // Kotlin and Java have different member names for the suppression annotation so create two
    // separate builders and combine them after.
    XAnnotationSpecs.Builder javaBuilder = XAnnotationSpecs.builder(XTypeName.SUPPRESS);
    XAnnotationSpecs.Builder kotlinBuilder = XAnnotationSpecs.builder(XTypeName.SUPPRESS);
    for (Suppression suppression : suppressions) {
      javaBuilder.addArrayMember("value", "%S", suppression.javaName);
      if (suppression == Suppression.KOTLIN_INTERNAL) {
        javaBuilder.addArrayMember("value", "%S", "KotlinInternalInJava");
      }
      suppression.kotlinName.ifPresent(name -> kotlinBuilder.addArrayMember("names", "%S", name));
    }

    return toXPoet(toJavaPoet(javaBuilder.build()), toKotlinPoet(kotlinBuilder.build()));
  }

  public static Builder builder(XClassName className) {
    return new Builder(className);
  }

  /** Builds an {@link XAnnotationSpec} in a way that is more similar to the JavaPoet API. */
  public static final class Builder {
    private final XClassName className;
    private final Set<String> nonArrayMembers = new HashSet<>();
    private final ListMultimap<String, XCodeBlock> members = LinkedListMultimap.create();

    Builder(XClassName className) {
      this.className = className;
    }

    /** Adds the given member to the annotation. */
    @CanIgnoreReturnValue
    public Builder addMember(String name, String format, Object... args) {
      return addMember(name, XCodeBlock.of(format, args));
    }

    /** Adds the given annotation names to the method. */
    @CanIgnoreReturnValue
    public Builder addMember(String name, XCodeBlock value) {
      checkState(nonArrayMembers.add(name));
      checkState(!members.containsKey(name));
      members.put(name, value);
      return this;
    }

    /** Adds the given member to the annotation. */
    @CanIgnoreReturnValue
    public Builder addArrayMember(String name, String format, Object... args) {
      return addArrayMember(name, XCodeBlock.of(format, args));
    }

    /** Adds the given annotation names to the method. */
    @CanIgnoreReturnValue
    public Builder addArrayMember(String name, XCodeBlock value) {
      checkState(!nonArrayMembers.contains(name));
      members.put(name, value);
      return this;
    }

    /** Builds the parameter and returns an {@link XParameterSpec}. */
    public XAnnotationSpec build() {
      XAnnotationSpec.Builder builder = XAnnotationSpec.builder(className);

      // JavaPoet supports array-typed annotation values natively so just add all members normally.
      for (String name : members.keySet()) {
        for (XCodeBlock value : members.get(name)) {
          toJavaPoet(builder).addMember(name, toJavaPoet(value));
        }
      }

      // KotlinPoet does not support array-typed annotation values, so roll our own.
      for (String name : members.keySet()) {
        List<XCodeBlock> values = members.get(name);
        if (nonArrayMembers.contains(name)) {
          toKotlinPoet(builder).addMember("%L = %L", name, toKotlinPoet(values.get(0)));
        } else if (values.size() == 1) {
          toKotlinPoet(builder).addMember("%L = [%L]", name, toKotlinPoet(values.get(0)));
        } else {
          toKotlinPoet(builder)
              .addMember("%L = [\n⇥⇥%L⇤⇤\n]", name, toKotlinPoet(formattedList(values)));
        }
      }
      return builder.build();
    }

    private XCodeBlock formattedList(List<XCodeBlock> values) {
      XCodeBlock.Builder builder = XCodeBlock.builder();
      for (int i = 0; i < values.size(); i++) {
        builder.add(values.get(i));
        if (values.size() > 1 && i < values.size() - 1) {
          builder.add(",\n");
        }
      }
      return builder.build();
    }
  }

  private XAnnotationSpecs() {}
}
