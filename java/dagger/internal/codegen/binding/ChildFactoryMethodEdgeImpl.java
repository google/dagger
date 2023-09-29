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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.ElementFormatter.elementToString;

import androidx.room.compiler.processing.XMethodElement;
import dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.internal.codegen.model.DaggerExecutableElement;

/** An implementation of {@link ChildFactoryMethodEdge}. */
public final class ChildFactoryMethodEdgeImpl implements ChildFactoryMethodEdge {

  private final DaggerExecutableElement factoryMethod;

  ChildFactoryMethodEdgeImpl(XMethodElement factoryMethod) {
    this.factoryMethod = DaggerExecutableElement.from(factoryMethod);
  }

  @Override
  public DaggerExecutableElement factoryMethod() {
    return factoryMethod;
  }

  @Override
  public String toString() {
    return elementToString(factoryMethod.xprocessing());
  }
}
