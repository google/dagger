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
import static androidx.room.compiler.codegen.compat.XConverters.toXPoet;
import static com.google.common.base.Preconditions.checkState;

import androidx.room.compiler.codegen.VisibilityModifier;
import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.kotlinpoet.KModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Utility methods for building {@link XTypeSpec}s. */
public final class XTypeSpecs {
  public static Builder classBuilder(String name) {
    return new Builder(Builder.Kind.CLASS).name(name);
  }

  public static Builder classBuilder(XClassName className) {
    return new Builder(Builder.Kind.CLASS).name(className.getSimpleName());
  }

  public static Builder interfaceBuilder(String name) {
    return new Builder(Builder.Kind.INTERFACE).name(name);
  }

  public static Builder interfaceBuilder(XClassName className) {
    return new Builder(Builder.Kind.INTERFACE).name(className.getSimpleName());
  }

  public static Builder objectBuilder(String name) {
    return new Builder(Builder.Kind.OBJECT).name(name);
  }

  public static Builder anonymousClassBuilder() {
    return new Builder(Builder.Kind.ANONYMOUS_CLASS);
  }

  public static Builder annotationBuilder(String name) {
    return new Builder(Builder.Kind.ANNOTATION).name(name);
  }

  public static XTypeSpec.Builder toBuilder(XTypeSpec typeSpec) {
    return toXPoet(
        toJavaPoet(typeSpec).toBuilder(),
        toKotlinPoet(typeSpec).toBuilder());
  }

  /** Builds an {@link XTypeSpec} in a way that is more similar to the JavaPoet API. */
  public static class Builder {
    private static enum Kind {
      CLASS,
      INTERFACE,
      ANNOTATION,
      OBJECT,
      ANONYMOUS_CLASS
    }

    private final Kind kind;
    private String name;
    private boolean isOpen;
    private boolean isStatic;
    private boolean isAbstract;
    private VisibilityModifier visibility = null;
    private XElement originatingElement;
    private final Set<String> alwaysQualifyNames = new LinkedHashSet<>();
    private final List<XCodeBlock> javadocs = new ArrayList<>();
    private XTypeName superclass;
    private final List<XTypeName> superinterfaces = new ArrayList<>();
    private final List<XTypeName> typeVariableNames = new ArrayList<>();
    private final List<XAnnotationSpec> annotations = new ArrayList<>();
    private final List<XTypeSpec> types = new ArrayList<>();
    private final List<XPropertySpec> properties = new ArrayList<>();
    private final List<XFunSpec> functions = new ArrayList<>();

    private Builder(Kind kind) {
      this.kind = kind;
    }

    @CanIgnoreReturnValue
    private Builder name(String name) {
      this.name = name;
      return this;
    }

    /** Sets the static modifier on the type. */
    @CanIgnoreReturnValue
    public Builder isStatic(boolean isStatic) {
      this.isStatic = isStatic;
      return this;
    }

    /** Sets the final/open modifier on the type. */
    @CanIgnoreReturnValue
    public Builder isOpen(boolean isOpen) {
      this.isOpen = isOpen;
      return this;
    }

    /** Sets the abstract modifier on the type. */
    @CanIgnoreReturnValue
    public Builder isAbstract(boolean isAbstract) {
      this.isAbstract = isAbstract;
      return this;
    }

    /** Sets the visibility of the type. */
    @CanIgnoreReturnValue
    public Builder visibility(VisibilityModifier visibility) {
      this.visibility = visibility;
      return this;
    }

    /** Sets the originating element of the type. */
    @CanIgnoreReturnValue
    public Builder addJavadoc(String format, Object... args) {
      javadocs.add(XCodeBlock.of(format, args));
      return this;
    }

    /** Sets the originating element of the type. */
    @CanIgnoreReturnValue
    public Builder addOriginatingElement(XElement originatingElement) {
      this.originatingElement = originatingElement;
      return this;
    }

    /** Sets the super class/interface of the type and handles nested class name clashes. */
    @CanIgnoreReturnValue
    public Builder superType(XTypeElement superType) {
      if (superType.isClass()) {
        return avoidClashesWithNestedClasses(superType).superclass(superType.asClassName());
      } else if (superType.isInterface()) {
        return avoidClashesWithNestedClasses(superType).addSuperinterface(superType.asClassName());
      }
      throw new AssertionError(superType + " is neither a class nor an interface.");
    }

    /**
     * Configures the given {@link XTypeSpec.Builder} so that it fully qualifies all classes nested
     * in the given {@link XTypeElement} and all classes nested within any super type of the given
     * {@link XTypeElement}.
     */
    @CanIgnoreReturnValue
    public Builder avoidClashesWithNestedClasses(XTypeElement typeElement) {
      typeElement.getEnclosedTypeElements().stream()
          .map(XElements::getSimpleName)
          .forEach(alwaysQualifyNames::add);

      typeElement.getType().getSuperTypes().stream()
          .filter(XTypes::isDeclared)
          .map(XType::getTypeElement)
          .forEach(this::avoidClashesWithNestedClasses);

      return this;
    }

    /** Adds the name to the set of always-qualified names to avoid clashes. */
    @CanIgnoreReturnValue
    public Builder alwaysQualify(String name) {
      alwaysQualifyNames.add(name);
      return this;
    }

    /** Sets the super class of the type. */
    @CanIgnoreReturnValue
    public Builder superclass(XTypeName superclass) {
      this.superclass = superclass;
      return this;
    }

    /** Adds the super interfaces to the type. */
    @CanIgnoreReturnValue
    public Builder addSuperinterfaces(Collection<XTypeName> superinterfaces) {
      superinterfaces.forEach(this::addSuperinterface);
      return this;
    }

    /** Adds the super interface to the type. */
    @CanIgnoreReturnValue
    public Builder addSuperinterface(XTypeName superInterface) {
      this.superinterfaces.add(superInterface);
      return this;
    }

    /** Sets the modifiers of the type. */
    @CanIgnoreReturnValue
    public Builder addModifiers(Modifier... modifiers) {
      for (Modifier modifier : modifiers) {
        switch (modifier) {
          case PUBLIC:
            visibility(VisibilityModifier.PUBLIC);
            break;
          case PRIVATE:
            visibility(VisibilityModifier.PRIVATE);
            break;
          case PROTECTED:
            visibility(VisibilityModifier.PROTECTED);
            break;
          case ABSTRACT:
            isOpen(true);
            isAbstract(true);
            break;
          case STATIC:
            isStatic(true);
            break;
          case FINAL:
            isOpen(false);
            break;
          default:
            throw new AssertionError("Unexpected modifier: " + modifier);
        }
      }
      return this;
    }

    /** Adds the given type variables to the type. */
    @CanIgnoreReturnValue
    public Builder addTypeVariables(Collection<? extends XType> typeVariables) {
      typeVariables.forEach(this::addTypeVariable);
      return this;
    }

    /** Adds the given type variable names to the type. */
    @CanIgnoreReturnValue
    public Builder addTypeVariableNames(Collection<XTypeName> typeVariableNames) {
      typeVariableNames.forEach(this::addTypeVariable);
      return this;
    }

    /** Adds the given type variable to the type. */
    @CanIgnoreReturnValue
    public Builder addTypeVariable(XType type) {
      return addTypeVariable(type.asTypeName());
    }

    /** Adds the given type variable name to the type. */
    @CanIgnoreReturnValue
    public Builder addTypeVariable(XTypeName typeName) {
      typeVariableNames.add(typeName);
      return this;
    }

    /** Adds the given annotations to the type. */
    @CanIgnoreReturnValue
    public Builder addAnnotations(Collection<XAnnotationSpec> annotations) {
      annotations.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation names to the type. */
    @CanIgnoreReturnValue
    public Builder addAnnotationNames(Collection<XClassName> annotationNames) {
      annotationNames.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation to the type. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XAnnotationSpec annotation) {
      annotations.add(annotation);
      return this;
    }

    /** Adds the given annotation name to the type. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XClassName annotationName) {
      return addAnnotation(XAnnotationSpec.of(annotationName));
    }

    /** Adds the given types to the type. */
    @CanIgnoreReturnValue
    public Builder addTypes(Collection<XTypeSpec> types) {
      types.forEach(this::addType);
      return this;
    }

    /** Adds the given annotation name to the type. */
    @CanIgnoreReturnValue
    public Builder addType(XTypeSpec type) {
      types.add(type);
      return this;
    }

    /** Adds the given properties to the type. */
    @CanIgnoreReturnValue
    public Builder addProperties(Collection<XPropertySpec> properties) {
      properties.forEach(this::addProperty);
      return this;
    }

    /** Adds the given property to the type. */
    @CanIgnoreReturnValue
    public Builder addProperty(XPropertySpec property) {
      properties.add(property);
      return this;
    }

    /** Adds the given functions to the type. */
    @CanIgnoreReturnValue
    public Builder addFunctions(Collection<XFunSpec> functions) {
      functions.forEach(this::addFunction);
      return this;
    }

    /** Adds the given function to the type. */
    @CanIgnoreReturnValue
    public Builder addFunction(XFunSpec function) {
      functions.add(function);
      return this;
    }

    /** Builds the type and returns an {@link XTypeSpec}. */
    public XTypeSpec build() {
      XTypeSpec.Builder builder;
      switch (kind) {
        case CLASS:
          builder = XTypeSpec.classBuilder(name, isOpen);
          break;
        case INTERFACE:
          // TODO(bcorso): Add support for interfaces in XPoet.
          builder = toXPoet(
              com.squareup.javapoet.TypeSpec.interfaceBuilder(name),
              com.squareup.kotlinpoet.TypeSpec.interfaceBuilder(name));
          if (isOpen) {
            toKotlinPoet(builder).addModifiers(KModifier.OPEN);
          } else {
            toJavaPoet(builder).addModifiers(Modifier.FINAL);
          }
          break;
        case ANNOTATION:
          // TODO(bcorso): Add support for annotations in XPoet.
          builder = toXPoet(
              com.squareup.javapoet.TypeSpec.annotationBuilder(name),
              com.squareup.kotlinpoet.TypeSpec.annotationBuilder(name));
          break;
        case OBJECT:
          builder = XTypeSpec.objectBuilder(name);
          break;
        case ANONYMOUS_CLASS:
          checkState(name == null);
          builder = XTypeSpec.anonymousClassBuilder("");
          break;
        default:
          throw new AssertionError();
      }

      if (originatingElement != null) {
        builder.addOriginatingElement(originatingElement);
      }

      if (isStatic) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addModifiers(Modifier.STATIC);
      }

      if (isAbstract) {
        builder.addAbstractModifier();
      }

      if (visibility != null) {
        builder.setVisibility(visibility);
      }

      for (String name : alwaysQualifyNames) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).alwaysQualify(name);
      }

      for (XCodeBlock javadoc : javadocs) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addJavadoc(toJavaPoet(javadoc));
      }

      if (superclass != null) {
        builder.superclass(superclass);
      }

      superinterfaces.forEach(builder::addSuperinterface);
      builder.addTypeVariables(typeVariableNames);
      annotations.forEach(builder::addAnnotation);
      types.forEach(builder::addType);
      properties.forEach(builder::addProperty);
      functions.forEach(builder::addFunction);

      return builder.build();
    }
  }

  private XTypeSpecs() {}
}
