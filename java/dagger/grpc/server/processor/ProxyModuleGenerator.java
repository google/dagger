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

import static com.google.auto.common.MoreElements.hasModifiers;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.fieldsIn;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.grpc.server.GrpcService;
import javax.lang.model.element.VariableElement;

/**
 * An object that generates the proxying service definition module for a {@link
 * GrpcService}-annotated service implementation.
 */
final class ProxyModuleGenerator extends SourceGenerator {

  private final GrpcServiceModel grpcServiceModel;

  ProxyModuleGenerator(GrpcServiceModel grpcServiceModel) {
    super(grpcServiceModel.packageName());
    this.grpcServiceModel = grpcServiceModel;
  }

  @Override
  protected TypeSpec createType() {
    return classBuilder(grpcServiceModel.proxyModuleName)
        .addModifiers(PUBLIC, FINAL)
        .addJavadoc(
            "Install this module in the {@link $T @Singleton} server component.\n",
            JavaxInject.singleton().type)
        .addAnnotation(grpcServiceModel.generatedAnnotation())
        .addAnnotation(Dagger.module())
        .addMethod(provideServiceDefinitionContribution())
        .addMethod(provideServiceDefinitionFactory())
        .build();
  }

  /**
   * Returns the {@link dagger.Provides @Provides} method for the proxying {@link
   * io.grpc.ServerServiceDefinition}.
   */
  private MethodSpec provideServiceDefinitionContribution() {
    MethodSpec.Builder method =
        methodBuilder("serviceDefinition")
            .addAnnotation(Dagger.provides())
            .addAnnotation(Dagger.intoSet())
            .addAnnotation(JavaxInject.singleton())
            .addModifiers(STATIC)
            .returns(IoGrpc.SERVER_SERVICE_DEFINITION)
            .addParameter(
                ParameterSpec.builder(
                        Dagger.GrpcServer.SERVICE_DEFINITION_FACTORY, "serviceDefinitionFactory")
                    .addAnnotation(grpcServiceModel.forGrpcService())
                    .build())
            .addCode(
                "return $T.builder($T.SERVICE_NAME)",
                IoGrpc.SERVER_SERVICE_DEFINITION,
                grpcServiceModel.grpcClass());
    for (VariableElement methodDescriptorField : methodDescriptorFields()) {
      method.addCode(
          ".addMethod($T.proxyMethod($T.$N, serviceDefinitionFactory))",
          Dagger.GrpcServer.PROXY_SERVER_CALL_HANDLER,
          grpcServiceModel.grpcClass(),
          methodDescriptorField.getSimpleName());
    }
    method.addCode(".build();");
    return method.build();
  }

  /**
   * Returns he {@link io.grpc.MethodDescriptor} {@code *_METHOD} fields on the class enclosing the
   * service interface.
   */
  private FluentIterable<VariableElement> methodDescriptorFields() {
    return FluentIterable.from(fieldsIn(grpcServiceModel.grpcClass().getEnclosedElements()))
        .filter(hasModifiers(PUBLIC, STATIC))
        .filter(
            new Predicate<VariableElement>() {
              @Override
              public boolean apply(VariableElement element) {
                TypeName typeName = TypeName.get(element.asType());
                return typeName instanceof ParameterizedTypeName
                    && ((ParameterizedTypeName) typeName).rawType.equals(IoGrpc.METHOD_DESCRIPTOR);
              }
            });
  }

  /**
   * Returns the {@link dagger.Provides @Provides} method for the {@link
   * dagger.grpc.server.ProxyServerCallHandler.ServiceDefinitionFactory} used by the proxy.
   */
  private MethodSpec provideServiceDefinitionFactory() {
    return methodBuilder("serviceDefinitionFactory")
        .addAnnotation(Dagger.provides())
        .addAnnotation(grpcServiceModel.forGrpcService())
        .addModifiers(STATIC)
        .returns(Dagger.GrpcServer.SERVICE_DEFINITION_FACTORY)
        .addParameter(grpcServiceModel.serviceDefinitionTypeFactoryName, "factory", FINAL)
        .addStatement("return $L", anonymousServiceDefinitionFactory())
        .build();
  }

  /**
   * Returns the anonymous inner class that implements the {@link
   * dagger.grpc.server.ProxyServerCallHandler.ServiceDefinitionFactory} used by the proxy.
   */
  private TypeSpec anonymousServiceDefinitionFactory() {
    return anonymousClassBuilder("")
        .addSuperinterface(Dagger.GrpcServer.SERVICE_DEFINITION_FACTORY)
        .addMethod(
            methodBuilder("getServiceDefinition")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(IoGrpc.SERVER_SERVICE_DEFINITION)
                .addParameter(IoGrpc.METADATA, "headers")
                .addStatement(
                    "return factory.grpcService(new $T(headers)).$N()",
                    Dagger.GrpcServer.GRPC_CALL_METADATA_MODULE,
                    grpcServiceModel.subcomponentServiceDefinitionMethodName())
                .build())
        .build();
  }
}
