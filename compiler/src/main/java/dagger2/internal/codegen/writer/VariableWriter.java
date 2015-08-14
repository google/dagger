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
package dagger2.internal.codegen.writer;

import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class VariableWriter extends Modifiable implements Writable, HasClassReferences {
  private final TypeName type;
  private final String name;

  VariableWriter(TypeName type, String name) {
    this.type = checkNotNull(type);
    this.name = checkNotNull(name);
  }

  public TypeName type() {
    return type;
  }

  public String name() {
    return name;
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    writeModifiers(appendable);
    type.write(appendable, context);
    return appendable.append(' ').append(name);
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return type.referencedClasses();
  }
}
