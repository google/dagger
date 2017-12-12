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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.grpc.server.GrpcService;

/**
 * An object that generates the unscoped-proxying service definition module for a {@link
 * GrpcService}-annotated service implementation.
 */
final class UnscopedGrpcServiceModuleGenerator extends SourceGenerator {

  private final GrpcServiceModel grpcServiceModel;

  UnscopedGrpcServiceModuleGenerator(GrpcServiceModel grpcServiceModel) {
    super(grpcServiceModel.packageName());
    this.grpcServiceModel = grpcServiceModel;
  }

  @Override
  protected TypeSpec createType() {
    ClassName unscopedComponentFactory =
        grpcServiceModel.unscopedServiceModuleName.nestedClass(
            grpcServiceModel.serviceImplementationClassName.simpleName() + "ComponentFactory");
    TypeSpec.Builder unscopedServiceModule =
        classBuilder(grpcServiceModel.unscopedServiceModuleName)
            .addJavadoc(
                "Install this module in the {@link $T @Singleton} server component\n",
                JavaxInject.singleton().type)
            .addJavadoc(
                "if it implements {@link $T}.\n", grpcServiceModel.serviceDefinitionTypeName);
    grpcServiceModel.generatedAnnotation().ifPresent(unscopedServiceModule::addAnnotation);
    return unscopedServiceModule
        .addAnnotation(
            Dagger.module(grpcServiceModel.proxyModuleName, grpcServiceModel.serviceModuleName))
        .addModifiers(PUBLIC, ABSTRACT)
        .addType(unscopedComponentFactory(unscopedComponentFactory.simpleName()))
        .addMethod(bindSubcomponentFactory(unscopedComponentFactory))
        .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
        .build();
  }
  
  /**
   * Returns the class that implements the component factory type by returning the singleton
   * component itself.
   */
  private TypeSpec unscopedComponentFactory(String simpleName) {
    return TypeSpec.classBuilder(simpleName)
        .addModifiers(STATIC, FINAL)
        .addSuperinterface(grpcServiceModel.serviceDefinitionTypeFactoryName)
        .addField(grpcServiceModel.serviceDefinitionTypeName, "component", PRIVATE, FINAL)
        .addMethod(
            MethodSpec.constructorBuilder()
                .addAnnotation(JavaxInject.inject())
                .addParameter(grpcServiceModel.serviceDefinitionTypeName, "component")
                .addStatement("this.component = component")
                .build())
        .addMethod(
            MethodSpec.methodBuilder("grpcService")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(grpcServiceModel.serviceDefinitionTypeName)
                .addParameter(Dagger.GrpcServer.GRPC_CALL_METADATA_MODULE, "grpcCallMetadataModule")
                .addStatement("return component")
                .build())
        .build();
  }

  /**
   * Returns the {@link dagger.Binds @Binds} method that binds the component factory type to the
   * {@linkplain #unscopedComponentFactory(String) unscoped component factory implementation class}.
   */
  private MethodSpec bindSubcomponentFactory(ClassName unscopedComponentFactory) {
    return MethodSpec.methodBuilder(
            UPPER_CAMEL.to(
                LOWER_CAMEL, grpcServiceModel.serviceDefinitionTypeFactoryName.simpleName()))
        .addAnnotation(Dagger.binds())
        .addModifiers(ABSTRACT)
        .returns(grpcServiceModel.serviceDefinitionTypeFactoryName)
        .addParameter(unscopedComponentFactory, "factory")
        .build();
  }
}
