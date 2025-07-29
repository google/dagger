/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import com.squareup.javapoet.ClassName;
import java.util.function.Function;

/**
 * Utility class for getting the generated component name.
 *
 * <p>This should not be used externally.
 */
public final class ComponentNames {

  /**
   * Returns an instance of {@link ComponentNames} that will base all component names off of the
   * given root.
   */
  public static ComponentNames withoutRenaming() {
    return new ComponentNames(Function.identity());
  }

  /**
   * Returns an instance of {@link ComponentNames} that will base all component names off of the
   * given root after mapping it with {@code rootRenamer}.
   */
  public static ComponentNames withRenaming(Function<ClassName, ClassName> rootRenamer) {
    return new ComponentNames(rootRenamer);
  }

  private final Function<ClassName, ClassName> rootRenamer;

  private ComponentNames(Function<ClassName, ClassName> rootRenamer) {
    this.rootRenamer = rootRenamer;
  }

  public ClassName generatedComponentTreeDeps(ClassName root) {
    return Processors.append(
        Processors.getEnclosedClassName(rootRenamer.apply(root)), "_ComponentTreeDeps");
  }

  /** Returns the name of the generated component wrapper. */
  public ClassName generatedComponentsWrapper(ClassName root) {
    return Processors.append(
        Processors.getEnclosedClassName(rootRenamer.apply(root)), "_HiltComponents");
  }

  /** Returns the name of the generated component. */
  public ClassName generatedComponent(ClassName root, ClassName component) {
    return generatedComponentsWrapper(root).nestedClass(componentName(component));
  }

  /**
   * Returns the shortened component name by replacing the ending "Component" with "C" if it exists.
   *
   * <p>This is a hack because nested subcomponents in Dagger generate extremely long class names
   * that hit the 256 character limit.
   */
  // TODO(bcorso): See if this issue can be fixed in Dagger, e.g. by using static subcomponents.
  private static String componentName(ClassName component) {
    // TODO(bcorso): How do we want to handle collisions across packages? Currently, we only handle
    // collisions across enclosing elements since namespacing by package would likely lead to too
    // long of class names.
    // Note: This uses regex matching so we only match if the name ends in "Component"
    return Processors.getEnclosedName(component).replaceAll("Component$", "C");
  }

}
