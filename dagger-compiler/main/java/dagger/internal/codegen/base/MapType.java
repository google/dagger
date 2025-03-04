/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;

/** Information about a {@link java.util.Map} type. */
public final class MapType {
  /**
   * Returns a {@link MapType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link java.util.Map} type
   */
  public static MapType from(Key key) {
    return from(key.type().xprocessing());
  }

  /**
   * Returns a {@link MapType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link java.util.Map} type
   */
  public static MapType from(XType type) {
    checkArgument(isMap(type), "%s is not a Map", type);
    return new MapType(type);
  }

  // TODO(b/28555349): support PROVIDER_OF_LAZY here too
  // TODO(b/376124787): We could consolidate this with a similar list in FrameworkTypes
  // if we had a better way to go from RequestKind to framework class name or vice versa
  /** The valid framework request kinds allowed on a multibinding map value. */
  private static final ImmutableSet<RequestKind> VALID_FRAMEWORK_REQUEST_KINDS =
      ImmutableSet.of(RequestKind.PROVIDER, RequestKind.PRODUCER, RequestKind.PRODUCED);

  private final XType type;

  private MapType(XType type) {
    this.type = type;
  }

  /** The map type itself. */
  XTypeName typeName() {
    return type.asTypeName();
  }

  /** {@code true} if the map type is the raw {@link java.util.Map} type. */
  public boolean isRawType() {
    return XTypes.isRawParameterizedType(type);
  }

  /**
   * The map key type.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  public XType keyType() {
    checkState(!isRawType());
    return type.getTypeArguments().get(0);
  }

  /**
   * The map value type.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  public XType valueType() {
    checkState(!isRawType());
    return type.getTypeArguments().get(1);
  }

  /** Returns {@code true} if the raw type of {@link #valueType()} is {@code className}. */
  public boolean valuesAreTypeOf(XClassName className) {
    return !isRawType() && isTypeOf(valueType(), className);
  }

  /** Returns {@code true} if the raw type of {@link #valueType()} is a framework type. */
  public boolean valuesAreFrameworkType() {
    return valueRequestKind() != RequestKind.INSTANCE;
  }

  /** Returns {@code true} if the raw type of {@link #valueType()} is a provider type.*/
  public boolean valuesAreProvider() {
    return valuesAreTypeOf(XTypeNames.PROVIDER) || valuesAreTypeOf(XTypeNames.JAKARTA_PROVIDER);
  }

  /**
   * Returns the map's {@link #valueType()} without any wrapping framework type, if one exists.
   *
   * <p>In particular, this method returns {@code V} for all of the following map types:
   * {@code Map<K,V>}, {@code Map<K,Provider<V>>}, {@code Map<K,Producer<V>>}, and
   * {@code Map<K,Produced<V>>}.
   *
   * <p>Note that we don't consider {@code Lazy} a framework type for this particular case, so this
   * method will return {@code Lazy<V>} for {@code Map<K,Lazy<V>>}.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  public XType unwrappedFrameworkValueType() {
    return valuesAreFrameworkType() ? unwrapType(valueType()) : valueType();
  }

  /**
   * Returns the {@link RequestKind} of the {@link #valueType()}.
   *
   * @throws IllegalArgumentException if {@link #isRawType()} is true.
   */
  public RequestKind valueRequestKind() {
    checkArgument(!isRawType());
    RequestKind requestKind = RequestKinds.getRequestKind(valueType());
    if (VALID_FRAMEWORK_REQUEST_KINDS.contains(requestKind)) {
      return requestKind;
    } else if (requestKind == RequestKind.PROVIDER_OF_LAZY) {
      // This is kind of a weird case. We don't support Map<K, Lazy<V>>, so we also don't support
      // Map<K, Provider<Lazy<V>>> directly. However, if the user bound that themselves, we don't
      // want that to get confused as a normal instance request, so return PROVIDER here.
      return RequestKind.PROVIDER;
    } else {
      // Not all RequestKinds are supported, so if there's a map value that matches an unsupported
      // RequestKind, just treat it like it is a normal instance request.
      return RequestKind.INSTANCE;
    }
  }

  /** Returns {@code true} if {@code type} is a {@link java.util.Map} type. */
  public static boolean isMap(XType type) {
    // In general, Dagger ignores mutability so check for both kotlin.collection.(Map|MutableMap).
    return XTypes.isTypeOf(type, XTypeName.MAP) || XTypes.isTypeOf(type, XTypeName.MUTABLE_MAP);
  }

  /** Returns {@code true} if {@code key.type()} is a {@link java.util.Map} type. */
  public static boolean isMap(Key key) {
    return isMap(key.type().xprocessing());
  }

  /** Returns {@code true} if the given type is a {@code Map<K, Provider<V>>}. */
  public static boolean isMapOfProvider(XType keyType) {
    if (MapType.isMap(keyType)) {
      return MapType.from(keyType).valuesAreProvider();
    }
    return false;
  }
}
