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

package dagger.functional.aot;

import dagger.Subcomponent;
import javax.inject.Inject;

/**
 * This class demonstrates a regression where a missing binding method was generated in a leaf
 * component and then satisfied in an ancestor with a generated instance binding. If the ancestor's
 * generated instance method had the same name as the formerly-missing binding method, Dagger would
 * generate code without a proper {@code DaggerOuter.this} reference:
 *
 * <pre>{@code
 * public class DaggerAncestor implements Ancestor {
 *   protected abstract Ancestor getAncestor();
 *
 *   protected abstract class LeafImpl extends DaggerLeaf {
 *     {@literal @Override}
 *     protected final Ancestor getAncestor() {
 *       return getAncestor();
 *       //     ^ should be DaggerAncestor.this.getAncestor()
 *     }
 *   }
 * }
 * }</pre>
 */
final class MissingBindingReplacedWithGeneratedInstance {
  @Subcomponent
  interface Leaf {
    DependsOnGeneratedInstance dependsOnGeneratedInstance();
  }

  static class DependsOnGeneratedInstance {
    @Inject DependsOnGeneratedInstance(Ancestor generatedInstance) {}
  }

  @Subcomponent
  interface Ancestor {
    Leaf child();
  }
}
