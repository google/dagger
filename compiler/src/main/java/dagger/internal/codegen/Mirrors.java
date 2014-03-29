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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.EXECUTABLE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.WILDCARD;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Utilities related to {@link TypeMirror} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class Mirrors {
  private static final Equivalence<TypeMirror> TYPE_EQUIVALENCE = new Equivalence<TypeMirror>() {
    @Override
    protected boolean doEquivalent(TypeMirror a, TypeMirror b) {
      return Mirrors.equal(a, b);
    }

    @Override
    protected int doHash(TypeMirror t) {
      return Mirrors.hash(t);
    }
  };

  static Equivalence<TypeMirror> equivalence() {
    return TYPE_EQUIVALENCE;
  }

  private static final TypeVisitor<Boolean, TypeMirror> EQUAL_VISITOR =
      new SimpleTypeVisitor6<Boolean, TypeMirror>() {
        @Override
        protected Boolean defaultAction(TypeMirror a, TypeMirror b) {
          return a.getKind().equals(b.getKind());
        }

        @Override
        public Boolean visitArray(ArrayType a, TypeMirror m) {
          if (m.getKind().equals(ARRAY)) {
            ArrayType b = (ArrayType) m;
            return equal(a.getComponentType(), b.getComponentType());
          }
          return false;
        }

        @Override
        public Boolean visitDeclared(DeclaredType a, TypeMirror m) {
          if (m.getKind().equals(DECLARED)) {
            DeclaredType b = (DeclaredType) m;
            return a.asElement().equals(b.asElement())
                && equal(a.getEnclosingType(), a.getEnclosingType())
                && equalLists(a.getTypeArguments(), b.getTypeArguments());

          }
          return false;
        }

        @Override
        public Boolean visitError(ErrorType a, TypeMirror m) {
          return a.equals(m);
        }

        @Override
        public Boolean visitExecutable(ExecutableType a, TypeMirror m) {
          if (m.getKind().equals(EXECUTABLE)) {
            ExecutableType b = (ExecutableType) m;
            return equalLists(a.getParameterTypes(), b.getParameterTypes())
                && equal(a.getReturnType(), b.getReturnType())
                && equalLists(a.getThrownTypes(), b.getThrownTypes())
                && equalLists(a.getTypeVariables(), b.getTypeVariables());
          }
          return false;
        }

        @Override
        public Boolean visitTypeVariable(TypeVariable a, TypeMirror m) {
          if (m.getKind().equals(TYPEVAR)) {
            TypeVariable b = (TypeVariable) m;
            return equal(a.getUpperBound(), b.getUpperBound())
                && equal(a.getLowerBound(), b.getLowerBound());
          }
          return false;
        }

        @Override
        public Boolean visitWildcard(WildcardType a, TypeMirror m) {
          if (m.getKind().equals(WILDCARD)) {
            WildcardType b = (WildcardType) m;
            return equal(a.getExtendsBound(), b.getExtendsBound())
                && equal(a.getSuperBound(), b.getSuperBound());
          }
          return false;
        }

        @Override
        public Boolean visitUnknown(TypeMirror a, TypeMirror p) {
          throw new UnsupportedOperationException();
        }
      };

  static boolean equal(TypeMirror a, TypeMirror b) {
    return (a == b) || (a != null && b != null && a.accept(EQUAL_VISITOR, b));
  }

  private static boolean equalLists(List<? extends TypeMirror> a, List<? extends TypeMirror> b) {
    int size = a.size();
    if (size != b.size()) {
      return false;
    }
    // Use iterators in case the Lists aren't RandomAccess
    Iterator<? extends TypeMirror> aIterator = a.iterator();
    Iterator<? extends TypeMirror> bIterator = b.iterator();
    while (aIterator.hasNext()) {
      if (!bIterator.hasNext()) {
        return false;
      }
      TypeMirror nextMirrorA = aIterator.next();
      TypeMirror nextMirrorB = bIterator.next();
      if (!equal(nextMirrorA, nextMirrorB)) {
        return false;
      }
    }
    return !aIterator.hasNext();
  }

  private static final int HASH_SEED = 17;
  private static final int HASH_MULTIPLIER = 31;

  private static final TypeVisitor<Integer, Void> HASH_VISITOR =
      new SimpleTypeVisitor6<Integer, Void>() {
          int hashKind(int seed, TypeMirror t) {
            int result = seed * HASH_MULTIPLIER;
            result += t.getKind().hashCode();
            return result;
          }

          @Override
          protected Integer defaultAction(TypeMirror e, Void p) {
            return hashKind(HASH_SEED, e);
          }

          @Override
          public Integer visitArray(ArrayType t, Void v) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getComponentType().accept(this, null);
            return result;
          }

          @Override
          public Integer visitDeclared(DeclaredType t, Void v) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.asElement().hashCode();
            result *= HASH_MULTIPLIER;
            result += t.getEnclosingType().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeArguments());
            return result;
          }

          @Override
          public Integer visitExecutable(ExecutableType t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getParameterTypes());
            result *= HASH_MULTIPLIER;
            result += t.getReturnType().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += hashList(t.getThrownTypes());
            result *= HASH_MULTIPLIER;
            result += hashList(t.getTypeVariables());
            return result;
          }

          @Override
          public Integer visitTypeVariable(TypeVariable t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += t.getLowerBound().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += t.getUpperBound().accept(this, null);
            return result;
          }

          @Override
          public Integer visitWildcard(WildcardType t, Void p) {
            int result = hashKind(HASH_SEED, t);
            result *= HASH_MULTIPLIER;
            result += (t.getExtendsBound() == null) ? 0 : t.getExtendsBound().accept(this, null);
            result *= HASH_MULTIPLIER;
            result += (t.getSuperBound() == null) ? 0 : t.getSuperBound().accept(this, null);
            return result;
          }

          @Override
          public Integer visitUnknown(TypeMirror t, Void p) {
            throw new UnsupportedOperationException();
          }
      };

  static int hashList(List<? extends TypeMirror> mirrors) {
    int result = HASH_SEED;
    for (TypeMirror mirror : mirrors) {
      result *= HASH_MULTIPLIER;
      result += hash(mirror);
    }
    return result;
  }

  static int hash(TypeMirror mirror) {
    return mirror == null ? 0 : mirror.accept(HASH_VISITOR, null);
  }

  /**
   * Returns the set of {@linkplain TypeElement types} that are referenced by the given
   * {@link TypeMirror}.
   */
  static ImmutableSet<TypeElement> referencedTypes(TypeMirror type) {
    checkNotNull(type);
    ImmutableSet.Builder<TypeElement> elements = ImmutableSet.builder();
    type.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<TypeElement>>() {
      @Override
      public Void visitArray(ArrayType t, Builder<TypeElement> p) {
        t.getComponentType().accept(this, p);
        return null;
      }

      @Override
      public Void visitDeclared(DeclaredType t, Builder<TypeElement> p) {
        p.add(ElementUtil.asTypeElement(t.asElement()));
        for (TypeMirror typeArgument : t.getTypeArguments()) {
          typeArgument.accept(this, p);
        }
        return null;
      }

      @Override
      public Void visitTypeVariable(TypeVariable t, Builder<TypeElement> p) {
        t.getLowerBound().accept(this, p);
        t.getUpperBound().accept(this, p);
        return null;
      }

      @Override
      public Void visitWildcard(WildcardType t, Builder<TypeElement> p) {
        TypeMirror extendsBound = t.getExtendsBound();
        if (extendsBound != null) {
          extendsBound.accept(this, p);
        }
        TypeMirror superBound = t.getSuperBound();
        if (superBound != null) {
          superBound.accept(this, p);
        }
        return null;
      }
    }, elements);
    return elements.build();
  }

  private Mirrors() {}
}
