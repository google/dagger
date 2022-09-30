/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import java.util.ServiceLoader;
import javax.annotation.processing.ProcessingEnvironment;

/** A class that loads services for the {@link ComponentProcessor}. */
final class ServiceLoaders {

  private ServiceLoaders() {}

  /**
   * Returns the loaded services for the given class.
   *
   * <p>Note: This should only be called in Javac. This method will throw if called in KSP.
   */
  static <T> ImmutableSet<T> loadServices(XProcessingEnv processingEnv, Class<T> clazz) {
    return ImmutableSet.copyOf(ServiceLoader.load(clazz, classLoaderFor(processingEnv, clazz)));
  }

  private static ClassLoader classLoaderFor(XProcessingEnv processingEnv, Class<?> clazz) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return javaClassLoader(toJavac(processingEnv), clazz);
      case KSP:
        return clazz.getClassLoader();
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  private static ClassLoader javaClassLoader(
      ProcessingEnvironment javacProcessingEnv, Class<?> clazz) {
    return clazz.getClassLoader();
  }
}
