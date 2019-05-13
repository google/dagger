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

import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import dagger.grpc.server.GrpcService;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

/**
 * Generates code from types annotated with {@link GrpcService @GrpcService}.
 *
 * @see <a href="https://dagger.dev/grpc">https://dagger.dev/grpc</a>
 */
@AutoService(Processor.class)
public class GrpcServiceProcessor extends BasicAnnotationProcessor implements ProcessingStep {

  @Override
  protected ImmutableList<GrpcServiceProcessor> initSteps() {
    return ImmutableList.of(this);
  }

  @Override
  public ImmutableSet<Class<GrpcService>> annotations() {
    return ImmutableSet.of(GrpcService.class);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> deferredElements = ImmutableSet.builder();
    for (TypeElement element : typesIn(elementsByAnnotation.get(GrpcService.class))) {
      try {
        GrpcServiceModel grpcServiceModel = new GrpcServiceModel(processingEnv, element);
        if (grpcServiceModel.validate()) {
          write(new ServiceDefinitionTypeGenerator(grpcServiceModel), element);
          write(new ProxyModuleGenerator(grpcServiceModel), element);
          write(new GrpcServiceModuleGenerator(grpcServiceModel), element);
          write(new UnscopedGrpcServiceModuleGenerator(grpcServiceModel), element);
        }
      } catch (TypeNotPresentException e) {
        deferredElements.add(element);
      }
    }
    return deferredElements.build();
  }

  private void write(SourceGenerator grpcServiceTypeWriter, final TypeElement element) {
    JavaFile javaFile = grpcServiceTypeWriter.javaFile();
    ClassName outputClassName = ClassName.get(javaFile.packageName, javaFile.typeSpec.name);
    try {
      javaFile.writeTo(new FormattingFiler(processingEnv.getFiler()));
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Kind.ERROR, String.format("Error writing %s: %s", outputClassName, e), element);
    }
  }
}
