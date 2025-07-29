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

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.WildcardTypeName.subtypeOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.grpc.server.GrpcService;
import java.util.List;

/**
 * An object that generates the non-proxying service definition module for a {@link
 * GrpcService}-annotated service implementation.
 */
final class GrpcServiceModuleGenerator extends SourceGenerator {

  private static final TypeName LIST_OF_INTERCEPTORS = ParameterizedTypeName.get(
      ClassName.get(List.class), subtypeOf(IoGrpc.SERVER_INTERCEPTOR));
  
  private final GrpcServiceModel grpcServiceModel;

  GrpcServiceModuleGenerator(GrpcServiceModel grpcServiceModel) {
    super(grpcServiceModel.packageName());
    this.grpcServiceModel = grpcServiceModel;
  }

  @Override
  protected TypeSpec createType() {
    TypeSpec.Builder serviceModule =
        classBuilder(grpcServiceModel.serviceModuleName)
            .addJavadoc(
                "Install this module in the {@link $T @Singleton} server component\n",
                JavaxInject.singleton().type)
            .addJavadoc(
                "or in the subcomponent that implements {@link $T}.\n",
                grpcServiceModel.serviceDefinitionTypeName);
    grpcServiceModel.generatedAnnotation().ifPresent(serviceModule::addAnnotation);
    return serviceModule
        .addAnnotation(Dagger.module())
        .addModifiers(PUBLIC, FINAL)
        .addMethod(provideServiceDefinition())
        .build();
  }

  /**
   * Returns the {@link dagger.Provides @Provides} method for the {@link
   * io.grpc.ServerServiceDefinition} for the service.
   */
  private MethodSpec provideServiceDefinition() {
    return methodBuilder("serviceDefinition")
        .addAnnotation(Dagger.provides())
        .addAnnotation(grpcServiceModel.forGrpcService())
        .addModifiers(STATIC)
        .returns(IoGrpc.SERVER_SERVICE_DEFINITION)
        .addParameter(grpcServiceModel.serviceImplementationClassName, "implementation")
        .addParameter(
            ParameterSpec.builder(LIST_OF_INTERCEPTORS, "interceptors")
                .addAnnotation(grpcServiceModel.forGrpcService())
                .build())
        .addStatement(
            "$T serviceDefinition = implementation.bindService()", IoGrpc.SERVER_SERVICE_DEFINITION)
        .addStatement(
            "return $T.intercept(serviceDefinition, interceptors)", IoGrpc.SERVER_INTERCEPTORS)
        .build();
  }
}
