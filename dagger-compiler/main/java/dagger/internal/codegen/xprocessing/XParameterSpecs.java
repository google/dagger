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
import static dagger.internal.codegen.xprocessing.NullableTypeNames.asNullableTypeName;

import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.kotlinpoet.KModifier;
import dagger.internal.codegen.compileroption.CompilerOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.Modifier;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XParameterSpec} helper methods. */
public final class XParameterSpecs {

  /**
   * Creates a {@link XParameterSpec} from the given {@code property} with the same name, type, and
   * annotations.
   */
  public static XParameterSpec from(XPropertySpec property) {
    XParameterSpec.Builder builder =
        of(property.getName(), property.getType()).toBuilder(); // SUPPRESS_GET_NAME_CHECK
    // Copy the annotations from the property to the parameter.
    toJavaPoet(builder).annotations.addAll(toJavaPoet(property).annotations);
    toKotlinPoet(builder).addAnnotations(toKotlinPoet(property).getAnnotations());
    return builder.build();
  }

  /**
   * Creates a {@link XParameterSpec} from the given {@code parameter} with the same name, type,
   * and nullable annotations.
   */
  public static XParameterSpec from(XExecutableParameterElement parameter) {
    return builder(parameter.getJvmName(), parameter.getType().asTypeName()).build();
  }

  /**
   * Creates a {@link XParameterSpec} from the given {@code parameter} and {@code parameterType}
   * with the same name and nullable annotations as the {@code parameter}, and the same type as
   * {@code parameterType}.
   */
  public static XParameterSpec from(
      XExecutableParameterElement parameter, XType parameterType, CompilerOptions compilerOptions) {
    return of(parameter.getJvmName(), parameterType, Nullability.of(parameter), compilerOptions);
  }

  /**
   * Creates a {@link XParameterSpec} with the given {@code name} and {@code typeName} and adds the
   * type-use nullability annotations to the type and the non-type-use annotations to the parameter.
   *
   * @deprecated Use {@link #of(String, XType, Nullability, CompilerOptions)}.
   */
  @Deprecated
  public static XParameterSpec of(
      String name, XTypeName typeName, Nullability nullability, CompilerOptions compilerOptions) {
    return builder(name, typeName, nullability, compilerOptions).build();
  }

  public static XParameterSpec of(
      String name, XType type, Nullability nullability, CompilerOptions compilerOptions) {
    return builder(name, type, nullability, compilerOptions).build();
  }

  /** Creates a {@link XParameterSpec} with the given {@code name} and {@code typeName}. */
  public static XParameterSpec of(String name, XTypeName typeName) {
    return builder(name, typeName).build();
  }

  /**
   * Creates a {@link Builder} with the given {@code name} and {@code typeName} and adds the
   * type-use nullability annotations to the type and the non-type-use annotations to the parameter.
   *
   * @deprecated Use {@link #builder(String, XType, Nullability, CompilerOptions)}.
   */
  @Deprecated
  public static Builder builder(
      String name, XTypeName typeName, Nullability nullability, CompilerOptions compilerOptions) {
    return new Builder(name, asNullableTypeName(typeName, nullability, compilerOptions))
        .addAnnotationNames(nullability.nonTypeUseNullableAnnotations());
  }

  public static Builder builder(
      String name, XType type, Nullability nullability, CompilerOptions compilerOptions) {
    return builder(name, asNullableTypeName(type, compilerOptions))
        .addAnnotationNames(nullability.nonTypeUseNullableAnnotations());
  }

  /** Creates a {@link Builder} with the given {@code name} and {@code typeName}. */
  public static Builder builder(String name, XTypeName typeName) {
    return new Builder(name, typeName);
  }

  /** Builds an {@link XParameterSpec} in a way that is more similar to the JavaPoet API. */
  public static class Builder {
    private final String name;
    private final XTypeName typeName;
    private Boolean isFinal = null;
    private final List<XAnnotationSpec> annotations = new ArrayList<>();

    Builder(String name, XTypeName typeName) {
      this.name = name;
      this.typeName = typeName;
    }

    /** Sets the modifiers of the method. */
    @CanIgnoreReturnValue
    public Builder addModifiers(Collection<Modifier> modifiers) {
      return addModifiers(modifiers.toArray(new Modifier[0]));
    }

    /** Sets the modifiers of the method. */
    @CanIgnoreReturnValue
    public Builder addModifiers(Modifier... modifiers) {
      for (Modifier modifier : modifiers) {
        switch (modifier) {
          case FINAL:
            isFinal = true;
            break;
          default:
            throw new AssertionError("Unexpected modifier: " + modifier);
        }
      }
      return this;
    }

    /** Adds the given annotations to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotations(Collection<XAnnotationSpec> annotations) {
      annotations.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation names to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotationNames(Collection<XClassName> annotationNames) {
      annotationNames.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation name to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XClassName annotationName) {
      return addAnnotation(XAnnotationSpec.of(annotationName));
    }

    /** Adds the given annotation to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XAnnotationSpec annotation) {
      annotations.add(annotation);
      return this;
    }

    /** Builds the parameter and returns an {@link XParameterSpec}. */
    public XParameterSpec build() {
      XParameterSpec.Builder builder =
          XParameterSpec.builder(name, typeName, /* addJavaNullabilityAnnotation= */ false);

      // XPoet makes all parameters final by default for both JavaPoet and KotlinPoet so rather than
      // adding the final modifier if it is final, we need to remove the modifier if it's not final.
      if (isFinal == null) {
        // If the final modifier isn't set explicitly, then default to false for JavaPoet but leave
        // it as final for KotlinPoet since that's the default behavior in Kotlin.
        toJavaPoet(builder).modifiers.remove(Modifier.FINAL);
      } else if (!isFinal) {
        toJavaPoet(builder).modifiers.remove(Modifier.FINAL);
        toKotlinPoet(builder).getModifiers().remove(KModifier.FINAL);
      }

      return builder.addAnnotations(annotations).build();
    }
  }

  private XParameterSpecs() {}
}
