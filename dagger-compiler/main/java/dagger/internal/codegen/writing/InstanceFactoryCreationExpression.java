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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.room.compiler.codegen.XCodeBlock;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.function.Supplier;

/**
 * A {@link FrameworkInstanceCreationExpression} that creates an {@link InstanceFactory} for an
 * instance.
 */
final class InstanceFactoryCreationExpression implements FrameworkInstanceCreationExpression {

  private final boolean nullable;
  private final Supplier<XCodeBlock> instanceExpression;

  InstanceFactoryCreationExpression(Supplier<XCodeBlock> instanceExpression) {
    this(false, instanceExpression);
  }

  InstanceFactoryCreationExpression(boolean nullable, Supplier<XCodeBlock> instanceExpression) {
    this.nullable = nullable;
    this.instanceExpression = checkNotNull(instanceExpression);
  }

  @Override
  public XCodeBlock creationExpression() {
    return XCodeBlock.of(
        "%T.%L(%L)",
        XTypeNames.INSTANCE_FACTORY,
        nullable ? "createNullable" : "create",
        instanceExpression.get());
  }
}
