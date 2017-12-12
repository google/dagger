/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static dagger.internal.codegen.KytheFormatting.formatKey;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.base.NodeKind;
import com.google.devtools.kythe.analyzers.java.Plugin.KytheGraph;
import com.google.devtools.kythe.proto.Storage.VName;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import dagger.model.Key;
import java.util.EnumMap;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

// TODO(ronshapiro): move this to a dagger.kythe package once Key is a public API
/** Factory for {@link VName}s of Dagger {@link Key}s. */
final class KeyVNameFactory {
  private final KytheGraph kytheGraph;
  private final KytheEntrySets entrySets;
  private final FactEmitter emitter;
  private final EnumMap<TypeKind, VName> typeKindVNames = new EnumMap<>(TypeKind.class);

  // TODO(ronshapiro): use @Inject
  KeyVNameFactory(KytheGraph kytheGraph, KytheEntrySets entrySets, FactEmitter emitter) {
    this.kytheGraph = kytheGraph;
    this.entrySets = entrySets;
    this.emitter = emitter;
  }

  VName vname(Key key) {
    return key.type().accept(new TypeExtractor(), key);
  }

  private VName forTypeKind(TypeKind typeKind) {
    return typeKindVNames.computeIfAbsent(
        typeKind, kind -> entrySets.getBuiltinVName(kind.toString().toLowerCase()));
  }

  private class TypeExtractor extends SimpleTypeVisitor8<VName, Key> {
    @Override
    public VName visitPrimitive(PrimitiveType primitiveType, Key key) {
      return forTypeKind(primitiveType.getKind());
    }

    @Override
    public VName visitArray(ArrayType arrayType, Key key) {
      return entrySets
          .newTApplyAndEmit(
              forTypeKind(TypeKind.ARRAY),
              ImmutableList.of(arrayType.getComponentType().accept(this, key)))
          .getVName();
    }

    @Override
    public VName visitDeclared(DeclaredType declaredType, Key key) {
      ClassSymbol classSymbol = (ClassSymbol) MoreTypes.asTypeElement(declaredType);
      // TODO(user,ronshapiro): is this correct? Will this be the VName for the Symbol but not
      // the *type*? Also, this seems to return null for all boxed primitive types if boxed
      // primitive is not mentioned in the compilation (i.e. if you @Provides int and then request
      // int from a binding, but never java.lang.Integer) - we have to synthesize those
      VName rawType = kytheGraph.getNode(classSymbol).get().getVName();
      if (classSymbol.getTypeParameters().isEmpty()) {
        return rawType;
      }
      ImmutableList.Builder<VName> typeArguments = ImmutableList.builder();
      for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
        typeArguments.add(typeArgument.accept(this, key));
      }
      return entrySets.newTApplyAndEmit(rawType, typeArguments.build()).getVName();
    }

    private int wildcardCounter = 0;

    @Override
    public VName visitWildcard(WildcardType wildcardType, Key key) {
      EntrySet wildcardNode =
          entrySets
              .newNode(NodeKind.ABS_VAR)
              // wildcards should be unique among other keys with similar wildcards, but similar
              // against equal keys
              .addSignatureSalt(formatKey(key) + wildcardCounter++)
              .build();
      wildcardNode.emit(emitter);
      return wildcardNode.getVName();
    }

    @Override
    public VName visitTypeVariable(TypeVariable typeVariable, Key key) {
      throw new IllegalArgumentException(
          "Type Variables are not allowed in resolved bindings, found in key: " + key);
    }
  }
}
