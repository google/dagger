/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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
package dagger.internal.loaders;


/**
 * A single point for API used in common by Adapters and Adapter generators
 */
public final class GeneratedAdapters {
  private static final String SEPARATOR = "$$";
  public static final String INJECT_ADAPTER_SUFFIX = SEPARATOR + "InjectAdapter";
  public static final String MODULE_ADAPTER_SUFFIX = SEPARATOR + "ModuleAdapter";
  public static final String STATIC_INJECTION_SUFFIX = SEPARATOR + "StaticInjection";

  private GeneratedAdapters() { }
}
