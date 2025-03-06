/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.grpc.server.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

/**
 * An object that generates one top-level type.
 */
abstract class SourceGenerator {

  private final String packageName;

  protected SourceGenerator(String packageName) {
    this.packageName = packageName;
  }

  public JavaFile javaFile() {
    return JavaFile.builder(packageName, createType()).build();
  }

  /**
   * Creates the type to write.
   */
  protected abstract TypeSpec createType();

  /** Class names and annotation specs for types in the {@link dagger} package. */
  protected static final class Dagger {
    private Dagger() {}

    static AnnotationSpec binds() {
      return AnnotationSpec.builder(ClassName.get("dagger", "Binds")).build();
    }

    static AnnotationSpec intoSet() {
      return AnnotationSpec.builder(ClassName.get("dagger.multibindings", "IntoSet")).build();
    }

    static AnnotationSpec provides() {
      return AnnotationSpec.builder(ClassName.get("dagger", "Provides")).build();
    }

    /** A {@code @dagger.Module} annotation that includes the given module classes. */
    static AnnotationSpec module(ClassName... includedModules) {
      AnnotationSpec.Builder module = AnnotationSpec.builder(ClassName.get("dagger", "Module"));
      for (ClassName includedModule : includedModules) {
        module.addMember("includes", "$T.class", includedModule);
      }
      return module.build();
    }

    /** Class names and annotation specs for types in the {@link dagger.grpc} package. */
    protected static final class GrpcServer {
      private GrpcServer() {}

      static final ClassName PROXY_SERVER_CALL_HANDLER =
          ClassName.get("dagger.grpc.server", "ProxyServerCallHandler");

      static final ClassName GRPC_CALL_METADATA_MODULE =
          ClassName.get("dagger.grpc.server", "GrpcCallMetadataModule");

      static final ClassName SERVICE_DEFINITION_FACTORY =
          PROXY_SERVER_CALL_HANDLER.nestedClass("ServiceDefinitionFactory");
    }
  }

  /** Class names and annotation specs for types in the {@link io.grpc} package. */
  protected static final class IoGrpc {
    private IoGrpc() {}

    static final ClassName BINDABLE_SERVICE = ClassName.get("io.grpc", "BindableService");
    static final ClassName METADATA = ClassName.get("io.grpc", "Metadata");
    static final ClassName METHOD_DESCRIPTOR = ClassName.get("io.grpc", "MethodDescriptor");
    static final ClassName SERVER_INTERCEPTOR =
        ClassName.get("io.grpc", "ServerInterceptor");
    static final ClassName SERVER_INTERCEPTORS =
        ClassName.get("io.grpc", "ServerInterceptors");
    static final ClassName SERVER_SERVICE_DEFINITION =
        ClassName.get("io.grpc", "ServerServiceDefinition");
  }

  /** Class names and annotation specs for types in the {@link javax.inject} package. */
  protected static final class JavaxInject {
    private JavaxInject() {}

    static AnnotationSpec inject() {
      return AnnotationSpec.builder(ClassName.get("javax.inject", "Inject")).build();
    }

    static AnnotationSpec singleton() {
      return AnnotationSpec.builder(ClassName.get("javax.inject", "Singleton")).build();
    }
  }
}
