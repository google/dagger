/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.AbstractProducer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor7;

import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.util.ElementFilter.constructorsIn;

/**
 * Represents a request for a key at an injection point. Parameters to {@link Inject} constructors
 * or {@link Provides} methods are examples of key requests.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): Set bindings and the permutations thereof need to be addressed
@AutoValue
abstract class DependencyRequest {
  static final Function<DependencyRequest, BindingKey> BINDING_KEY_FUNCTION =
      new Function<DependencyRequest, BindingKey>() {
        @Override public BindingKey apply(DependencyRequest request) {
          return request.bindingKey();
        }
      };

  enum Kind {
    /** A default request for an instance.  E.g.: {@code Blah} */
    INSTANCE,
    
    /** A request for a {@link Provider}.  E.g.: {@code Provider<Blah>} */
    PROVIDER(Provider.class),
    
    /** A request for a {@link Lazy}.  E.g.: {@code Lazy<Blah>} */
    LAZY(Lazy.class),
    
    /** A request for a {@link MembersInjector}.  E.g.: {@code MembersInjector<Blah>} */
    MEMBERS_INJECTOR(MembersInjector.class),
    
    /** A request for a {@link Producer}.  E.g.: {@code Producer<Blah>} */
    PRODUCER(Producer.class),
    
    /** A request for a {@link Produced}.  E.g.: {@code Produced<Blah>} */
    PRODUCED(Produced.class),
    
    /**
     * A request for a {@link ListenableFuture}.  E.g.: {@code ListenableFuture<Blah>}.
     * These can only be requested by component interfaces.
     */
    FUTURE,
    ;

    final Optional<Class<?>> frameworkClass;

    Kind(Class<?> frameworkClass) {
      this.frameworkClass = Optional.<Class<?>>of(frameworkClass);
    }

    Kind() {
      this.frameworkClass = Optional.absent();
    }
  }

  abstract Kind kind();
  abstract Key key();
  
  BindingKey bindingKey() {
    switch (kind()) {
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PRODUCER:
      case PRODUCED:
      case FUTURE:
        return BindingKey.create(BindingKey.Kind.CONTRIBUTION, key());
      case MEMBERS_INJECTOR:
        return BindingKey.create(BindingKey.Kind.MEMBERS_INJECTION, key());
      default:
        throw new AssertionError();
    }
  }

  abstract Element requestElement();

  /**
   * Returns the possibly resolved type that contained the requesting element. For members injection
   * requests, this is the type itself.
   */
  abstract DeclaredType enclosingType();

  /** Returns true if this request allows null objects. */
  abstract boolean isNullable();

  /**
   * An optional name for this request when it's referred to in generated code. If absent, it will
   * use a name derived from {@link #requestElement}.
   */
  abstract Optional<String> overriddenVariableName();

  /**
   * Factory for {@link DependencyRequest}s.
   *
   * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available,
   * which may mean that the type will be generated in a later round of processing.
   */
  static final class Factory {
    private final Elements elements;
    private final Key.Factory keyFactory;

    Factory(Elements elements, Key.Factory keyFactory) {
      this.elements = elements;
      this.keyFactory = keyFactory;
    }

    ImmutableSet<DependencyRequest> forRequiredResolvedVariables(DeclaredType container,
        List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
      checkState(resolvedTypes.size() == variables.size());
      ImmutableSet.Builder<DependencyRequest> builder = ImmutableSet.builder();
      for (int i = 0; i < variables.size(); i++) {
        builder.add(forRequiredResolvedVariable(container, variables.get(i), resolvedTypes.get(i)));
      }
      return builder.build();
    }

    ImmutableSet<DependencyRequest> forRequiredVariables(
        List<? extends VariableElement> variables) {
      return FluentIterable.from(variables)
          .transform(
              new Function<VariableElement, DependencyRequest>() {
                @Override
                public DependencyRequest apply(VariableElement input) {
                  return forRequiredVariable(input);
                }
              })
          .toSet();
    }

    /**
     * Creates a implicit {@link DependencyRequest} for {@code mapOfFactoryKey}, which will be used
     * to satisfy the {@code mapOfValueRequest}.
     * 
     * @param mapOfValueRequest a request for {@code Map<K, V>}
     * @param mapOfFactoryKey a key equivalent to {@code mapOfValueRequest}'s key, whose type is
     *     {@code Map<K, Provider<V>>} or {@code Map<K, Producer<V>>}
     */
    DependencyRequest forImplicitMapBinding(
        DependencyRequest mapOfValueRequest, Key mapOfFactoryKey) {
      checkNotNull(mapOfValueRequest);
      return new AutoValue_DependencyRequest(
          Kind.PROVIDER,
          mapOfFactoryKey,
          mapOfValueRequest.requestElement(),
          mapOfValueRequest.enclosingType(),
          false /* doesn't allow null */,
          Optional.<String>absent());
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      return forRequiredVariable(variableElement, Optional.<String>absent());
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement, Optional<String> name) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(
          variableElement, type, qualifier, getEnclosingType(variableElement), name);
    }

    DependencyRequest forRequiredResolvedVariable(DeclaredType container,
        VariableElement variableElement,
        TypeMirror resolvedType) {
      checkNotNull(variableElement);
      checkNotNull(resolvedType);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(
          variableElement, resolvedType, qualifier, container, Optional.<String>absent());
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod,
        ExecutableType provisionMethodType) {
      checkNotNull(provisionMethod);
      checkNotNull(provisionMethodType);
      checkArgument(
          provisionMethod.getParameters().isEmpty(),
          "Component provision methods must be empty: %s",
          provisionMethod);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(
          provisionMethod,
          provisionMethodType.getReturnType(),
          qualifier,
          getEnclosingType(provisionMethod),
          Optional.<String>absent());
    }

    DependencyRequest forComponentProductionMethod(ExecutableElement productionMethod,
        ExecutableType productionMethodType) {
      checkNotNull(productionMethod);
      checkNotNull(productionMethodType);
      checkArgument(productionMethod.getParameters().isEmpty(),
          "Component production methods must be empty: %s", productionMethod);
      TypeMirror type = productionMethodType.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(productionMethod);
      DeclaredType container = getEnclosingType(productionMethod);
      // Only a component production method can be a request for a ListenableFuture, so we
      // special-case it here.
      if (isTypeOf(ListenableFuture.class, type)) {
        return new AutoValue_DependencyRequest(
            Kind.FUTURE,
            keyFactory.forQualifiedType(
                qualifier, Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments())),
            productionMethod,
            container,
            false /* doesn't allow null */,
            Optional.<String>absent());
      } else {
        return newDependencyRequest(
            productionMethod, type, qualifier, container, Optional.<String>absent());
      }
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod,
        ExecutableType membersInjectionMethodType) {
      checkNotNull(membersInjectionMethod);
      checkNotNull(membersInjectionMethodType);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      TypeMirror returnType = membersInjectionMethodType.getReturnType();
      if (returnType.getKind().equals(DECLARED)
          && MoreTypes.isTypeOf(MembersInjector.class, returnType)) {
        return new AutoValue_DependencyRequest(
            Kind.MEMBERS_INJECTOR,
            keyFactory.forMembersInjectedType(
                Iterables.getOnlyElement(((DeclaredType) returnType).getTypeArguments())),
            membersInjectionMethod,
            getEnclosingType(membersInjectionMethod),
            false /* doesn't allow null */,
            Optional.<String>absent());
      } else {
        return new AutoValue_DependencyRequest(
            Kind.MEMBERS_INJECTOR,
            keyFactory.forMembersInjectedType(
                Iterables.getOnlyElement(membersInjectionMethodType.getParameterTypes())),
            membersInjectionMethod,
            getEnclosingType(membersInjectionMethod),
            false /* doesn't allow null */,
            Optional.<String>absent());
      }
    }

    DependencyRequest forMembersInjectedType(DeclaredType type) {
      return new AutoValue_DependencyRequest(
          Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(type),
          type.asElement(),
          type,
          false /* doesn't allow null */,
          Optional.<String>absent());
    }

    DependencyRequest forProductionComponentMonitorProvider() {
      TypeElement element = elements.getTypeElement(AbstractProducer.class.getCanonicalName());
      for (ExecutableElement constructor : constructorsIn(element.getEnclosedElements())) {
        if (constructor.getParameters().size() == 2) {
          // the 2-arg constructor has the appropriate dependency as its first arg
          return forRequiredVariable(constructor.getParameters().get(0), Optional.of("monitor"));
        }
      }
      throw new AssertionError("expected 2-arg constructor in AbstractProducer");
    }

    private DependencyRequest newDependencyRequest(
        Element requestElement,
        TypeMirror type,
        Optional<AnnotationMirror> qualifier,
        DeclaredType container,
        Optional<String> name) {
      KindAndType kindAndType = extractKindAndType(type);
      if (kindAndType.kind().equals(Kind.MEMBERS_INJECTOR)) {
        checkArgument(!qualifier.isPresent());
      }
      // Only instance types can be non-null -- all other requests are wrapped
      // inside something (e.g, Provider, Lazy, etc..).
      // TODO(sameb): should Produced/Producer always require non-nullable?
      boolean allowsNull = !kindAndType.kind().equals(Kind.INSTANCE)
          || ConfigurationAnnotations.getNullableType(requestElement).isPresent();
      return new AutoValue_DependencyRequest(
          kindAndType.kind(),
          keyFactory.forQualifiedType(qualifier, kindAndType.type()),
          requestElement,
          container,
          allowsNull,
          name);
    }

    @AutoValue
    static abstract class KindAndType {
      abstract Kind kind();
      abstract TypeMirror type();
    }

    /**
     * Extracts the dependency request type and kind from the type of a dependency request element.
     * For example, if a user requests {@code Provider<Foo>}, this will return
     * ({@link Kind#PROVIDER}, {@code Foo}).
     *
     * @throws TypeNotPresentException if {@code type}'s kind is {@link TypeKind#ERROR}, which may
     *     mean that the type will be generated in a later round of processing
     */
    static KindAndType extractKindAndType(TypeMirror type) {
      return type.accept(
          new SimpleTypeVisitor7<KindAndType, Void>() {
            @Override
            public KindAndType visitError(ErrorType errorType, Void p) {
              throw new TypeNotPresentException(errorType.toString(), null);
            }

            @Override
            public KindAndType visitExecutable(ExecutableType executableType, Void p) {
              return executableType.getReturnType().accept(this, null);
            }

            @Override
            public KindAndType visitDeclared(DeclaredType declaredType, Void p) {
              for (Kind kind : Kind.values()) {
                if (kind.frameworkClass.isPresent()
                    && isTypeOf(kind.frameworkClass.get(), declaredType)) {
                  return new AutoValue_DependencyRequest_Factory_KindAndType(
                      kind, Iterables.getOnlyElement(declaredType.getTypeArguments()));
                }
              }
              return defaultAction(declaredType, p);
            }

            @Override
            protected KindAndType defaultAction(TypeMirror otherType, Void p) {
              return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.INSTANCE, otherType);
            }
          },
          null);
    }

    static DeclaredType getEnclosingType(Element element) {
      while (!MoreElements.isType(element)) {
        element = element.getEnclosingElement();
      }
      return MoreTypes.asDeclared(element.asType());
    }
  }
}
