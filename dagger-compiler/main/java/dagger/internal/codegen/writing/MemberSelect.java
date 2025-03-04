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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.room.compiler.codegen.XClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;

/**
 * Represents a {@link com.sun.source.tree.MemberSelectTree} as a {@link CodeBlock}.
 */
abstract class MemberSelect {

  /**
   * Returns a {@link MemberSelect} that accesses the field given by {@code fieldName} owned by
   * {@code owningClass}. In this context "local" refers to the fact that the field is owned by the
   * type (or an enclosing type) from which the code block will be used. The returned {@link
   * MemberSelect} will not be valid for accessing the field from a different class (regardless of
   * accessibility).
   */
  static MemberSelect localField(ShardImplementation owningShard, String fieldName) {
    return new LocalField(owningShard, fieldName);
  }

  private static final class LocalField extends MemberSelect {
    final ShardImplementation owningShard;
    final String fieldName;

    LocalField(ShardImplementation owningShard, String fieldName) {
      super(owningShard.name(), false);
      this.owningShard = owningShard;
      this.fieldName = checkNotNull(fieldName);
    }

    @Override
    CodeBlock getExpressionFor(XClassName usingClass) {
      return owningClass().equals(usingClass)
          ? CodeBlock.of("$N", fieldName)
          : CodeBlock.of("$L.$N", owningShard.shardFieldReference(), fieldName);
    }
  }

  private final XClassName owningClass;
  private final boolean staticMember;

  MemberSelect(XClassName owningClass, boolean staticMemeber) {
    this.owningClass = owningClass;
    this.staticMember = staticMemeber;
  }

  /** Returns the class that owns the member being selected. */
  XClassName owningClass() {
    return owningClass;
  }

  /**
   * Returns true if the member being selected is static and does not require an instance of
   * {@link #owningClass()}.
   */
  boolean staticMember() {
    return staticMember;
  }

  /**
   * Returns a {@link CodeBlock} suitable for accessing the member from the given {@code
   * usingClass}.
   */
  abstract CodeBlock getExpressionFor(XClassName usingClass);
}
