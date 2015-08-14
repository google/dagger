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
package dagger2.internal.codegen;

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger2.MapKey;
import dagger2.internal.codegen.writer.ClassName;
import dagger2.internal.codegen.writer.JavaWriter;
import dagger2.internal.codegen.writer.MethodWriter;
import dagger2.internal.codegen.writer.TypeWriter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates implementations to create {@link MapKey} instances
 *
 * @author Chenying Hou
 * @since 2.0
 */
final class MapKeyGenerator extends SourceFileGenerator<Element> {
  MapKeyGenerator(Filer filer) {
    super(filer);
  }

  @Override
  ClassName nameGeneratedType(Element e) {
    return Util.getMapKeyCreatorClassName((TypeElement) e);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(Element e) {
    return ImmutableSet.of(e);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(Element e) {
    return Optional.of(e);
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, Element e) {
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());
    TypeWriter mapKeyWriter = writer.addClass(generatedTypeName.simpleName());
    mapKeyWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    mapKeyWriter.addModifiers(PUBLIC);

    //create map key create method, which will return an instance of map key
    MethodWriter getMethodWriter = mapKeyWriter.addMethod(e.asType(), "create");
    //get parameter list of create method
    List<? extends Element> enclosingElements = e.getEnclosedElements();
    List<String> paraList = new ArrayList<String>();

    //Using AutoAnnotation to generate mapkey creator files later
    getMethodWriter.annotate(AutoAnnotation.class);
    getMethodWriter.addModifiers(PUBLIC, STATIC);

    for (Element element : enclosingElements) {
      if (element instanceof ExecutableElement) {
        ExecutableElement executableElement = (ExecutableElement) element;
        Name parameterName = executableElement.getSimpleName();
        getMethodWriter.addParameter(
            (TypeElement) ((DeclaredType) (executableElement.getReturnType())).asElement(),
            parameterName.toString());
        paraList.add(parameterName.toString());
      } else {
        throw new IllegalStateException();
      }
    }

    getMethodWriter.body().addSnippet(
        "return new AutoAnnotation_" + generatedTypeName.simpleName() + "_create(%s);",
        Joiner.on(", ").join(paraList));

    return ImmutableSet.of(writer);
  }
}
