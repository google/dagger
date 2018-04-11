/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Validates bindings that satisfy members-injecting entry point methods or requests for a {@link
 * dagger.MembersInjector}.
 */
final class MembersInjectionBindingValidation implements BindingGraphPlugin {

  private final DaggerTypes types;

  @Inject
  MembersInjectionBindingValidation(DaggerTypes types) {
    this.types = types;
  }

  @Override
  public String pluginName() {
    return "Dagger/MembersInjection";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    for (BindingNode bindingNode : bindingGraph.bindingNodes()) {
      membersInjectedType(bindingNode)
          .ifPresent(type -> validateMembersInjectionType(type, bindingNode, diagnosticReporter));
    }
  }

  /**
   * Returns the type whose members will be injected if the binding is a {@link
   * dagger.model.BindingKind#MEMBERS_INJECTION} or {@link
   * dagger.model.BindingKind#MEMBERS_INJECTOR} binding.
   */
  private Optional<TypeMirror> membersInjectedType(BindingNode bindingNode) {
    switch (bindingNode.binding().kind()) {
      case MEMBERS_INJECTION:
        return Optional.of(bindingNode.binding().key().type());

      case MEMBERS_INJECTOR:
        return Optional.of(types.unwrapType(bindingNode.binding().key().type()));

      default:
        return Optional.empty();
    }
  }

  /** Reports errors if a members injection binding is invalid. */
  private void validateMembersInjectionType(
      TypeMirror membersInjectedType,
      BindingNode bindingNode,
      DiagnosticReporter diagnosticReporter) {
    membersInjectedType.accept(
        new SimpleTypeVisitor8<Void, Void>() {
          @Override
          protected Void defaultAction(TypeMirror e, Void v) {
            // Only declared types can be members-injected.
            diagnosticReporter.reportBinding(
                ERROR, bindingNode, "Cannot inject members into %s", e);
            return null;
          }

          @Override
          public Void visitDeclared(DeclaredType type, Void v) {
            if (type.getTypeArguments().isEmpty()) {
              // If the type is the erasure of a generic type, that means the user referred to
              // Foo<T> as just 'Foo', which we don't allow.  (This is a judgement call; we
              // *could* allow it and instantiate the type bounds, but we don't.)
              if (!MoreElements.asType(type.asElement()).getTypeParameters().isEmpty()) {
                diagnosticReporter.reportBinding(
                    ERROR, bindingNode, "Cannot inject members into raw type %s", type);
              }
            } else {
              // If the type has arguments, validate that each type argument is declared.
              // Otherwise the type argument may be a wildcard (or other type), and we can't
              // resolve that to actual types.  For array type arguments, validate the type of
              // the array.
              for (TypeMirror arg : type.getTypeArguments()) {
                if (!arg.accept(DECLARED_OR_ARRAY, null)) {
                  diagnosticReporter.reportBinding(
                      ERROR,
                      bindingNode,
                      "Cannot inject members into types with unbounded type arguments: %s",
                      type);
                }
              }
            }
            return null;
          }
        },
        null);
  }

  // TODO(dpb): Can this be inverted so it explicitly rejects wildcards or type variables?
  // This logic is hard to describe.
  private static final TypeVisitor<Boolean, Void> DECLARED_OR_ARRAY =
      new SimpleTypeVisitor8<Boolean, Void>(false) {
        @Override
        public Boolean visitArray(ArrayType arrayType, Void p) {
          return arrayType
              .getComponentType()
              .accept(
                  new SimpleTypeVisitor8<Boolean, Void>(false) {
                    @Override
                    public Boolean visitDeclared(DeclaredType declaredType, Void p) {
                      for (TypeMirror arg : declaredType.getTypeArguments()) {
                        if (!arg.accept(this, null)) {
                          return false;
                        }
                      }
                      return true;
                    }

                    @Override
                    public Boolean visitArray(ArrayType arrayType, Void p) {
                      return arrayType.getComponentType().accept(this, null);
                    }

                    @Override
                    public Boolean visitPrimitive(PrimitiveType primitiveType, Void p) {
                      return true;
                    }
                  },
                  null);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return true;
        }
      };
}
