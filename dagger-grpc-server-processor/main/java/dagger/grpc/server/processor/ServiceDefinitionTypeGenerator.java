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
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.TypeSpec;
import dagger.grpc.server.GrpcService;

/**
 * An object that generates the component supertype interface for a {@link GrpcService}-annotated
 * service implementation.
 */
final class ServiceDefinitionTypeGenerator extends SourceGenerator {

  private final GrpcServiceModel grpcServiceModel;

  ServiceDefinitionTypeGenerator(GrpcServiceModel grpcServiceModel) {
    super(grpcServiceModel.packageName());
    this.grpcServiceModel = grpcServiceModel;
  }

  @Override
  protected TypeSpec createType() {
    TypeSpec.Builder type =
        interfaceBuilder(grpcServiceModel.serviceDefinitionTypeName.simpleName())
            .addJavadoc("A component must implement this interface.\n")
            .addModifiers(PUBLIC);
    grpcServiceModel.generatedAnnotation().ifPresent(type::addAnnotation);
    type.addType(
        interfaceBuilder(grpcServiceModel.serviceDefinitionTypeFactoryName.simpleName())
            .addModifiers(PUBLIC, STATIC)
            .addMethod(
                methodBuilder("grpcService")
                    .addModifiers(PUBLIC, ABSTRACT)
                    .returns(grpcServiceModel.serviceDefinitionTypeName)
                    .addParameter(
                        Dagger.GrpcServer.GRPC_CALL_METADATA_MODULE, "grpcCallMetadataModule")
                    .build())
            .build());
    type.addMethod(
        methodBuilder(grpcServiceModel.subcomponentServiceDefinitionMethodName())
            .addModifiers(PUBLIC, ABSTRACT)
            .returns(IoGrpc.SERVER_SERVICE_DEFINITION)
            .addAnnotation(grpcServiceModel.forGrpcService())
            .build());
    return type.build();
  }
}
