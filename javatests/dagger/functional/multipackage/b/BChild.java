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

package dagger.functional.multipackage.b;

import dagger.functional.multipackage.a.AParent;
import dagger.functional.multipackage.a.APublicObject;
import javax.inject.Inject;

public class BChild extends AParent {

  @Inject BPackagePrivateObject aChildField;

  private APublicObject aChildMethod;

  @Inject
  protected void aChildMethod(APublicObject aChildMethod) {
    this.aChildMethod = aChildMethod;
  }

  @SuppressWarnings("OverridesJavaxInjectableMethod")
  @Override
  protected void aParentMethod(APublicObject aParentMethod) {
    super.aParentMethod(aParentMethod);
  }

  public BPackagePrivateObject aChildField() {
    return aChildField;
  }

  public APublicObject aChildMethod() {
    return aChildMethod;
  }
}
