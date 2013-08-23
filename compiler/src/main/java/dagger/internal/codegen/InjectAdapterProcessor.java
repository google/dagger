/*
 * Copyright (C) 2012 Square, Inc.
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
package dagger.internal.codegen;

import com.squareup.javawriter.JavaWriter;
import dagger.MembersInjector;
import dagger.internal.Binding;
import dagger.internal.Linker;
import dagger.internal.StaticInjection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static dagger.internal.Keys.isPlatformType;
import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.codegen.Util.adapterName;
import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.getNoArgsConstructor;
import static dagger.internal.codegen.Util.getPackage;
import static dagger.internal.codegen.Util.isCallableConstructor;
import static dagger.internal.codegen.Util.rawTypeToString;
import static dagger.internal.codegen.Util.typeToString;
import static dagger.internal.loaders.GeneratedAdapters.INJECT_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.STATIC_INJECTION_SUFFIX;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an implementation of {@link Binding} that injects the
 * {@literal @}{@code Inject}-annotated members of a class.
 */
@SupportedAnnotationTypes("javax.inject.Inject")
public final class InjectAdapterProcessor extends AbstractProcessor {
  private final Set<String> remainingTypeNames = new LinkedHashSet<String>();

  public static final String PARENT_ADAPTER_INFIX = "$$ParentAdapter$$";

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    remainingTypeNames.addAll(findInjectedClassNames(env));
    for (Iterator<String> i = remainingTypeNames.iterator(); i.hasNext();) {
      InjectedClass injectedClass = createInjectedClass(i.next());
      // Verify that we have access to all types to be injected on this pass.
      boolean missingDependentClasses =
          !allTypesExist(injectedClass.fields)
          || (injectedClass.constructor != null && !allTypesExist(injectedClass.constructor
              .getParameters()))
          || !allTypesExist(injectedClass.staticFields);
      if (!missingDependentClasses) {
        try {
          generateInjectionsForClass(injectedClass);
        } catch (IOException e) {
          error("Code gen failed: " + e, injectedClass.type);
        }
        i.remove();
      }
    }
    if (env.processingOver() && !remainingTypeNames.isEmpty()) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Could not find injection type required by " + remainingTypeNames);
    }
    return false;
  }

  private void generateInjectionsForClass(InjectedClass injectedClass) throws IOException {
    if (injectedClass.constructor != null || !injectedClass.fields.isEmpty()) {
      generateInjectAdapter(injectedClass.type, injectedClass.constructor, injectedClass.fields);
    }
    if (!injectedClass.staticFields.isEmpty()) {
      generateStaticInjection(injectedClass.type, injectedClass.staticFields);
    }
  }

  /**
   * Return true if all element types are currently available in this code
   * generation pass. Unavailable types will be of kind {@link TypeKind#ERROR}.
   */
  private boolean allTypesExist(Collection<? extends Element> elements) {
    for (Element element : elements) {
      if (element.asType().getKind() == TypeKind.ERROR) {
        return false;
      }
    }
    return true;
  }

  private Set<String> findInjectedClassNames(RoundEnvironment env) {
    // First gather the set of classes that have @Inject-annotated members.
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      if (!validateInjectable(element)) {
        continue;
      }
      injectedTypeNames.add(rawTypeToString(element.getEnclosingElement().asType(), '.'));
    }
    return injectedTypeNames;
  }

  private boolean validateInjectable(Element injectable) {
    Element injectableType = injectable.getEnclosingElement();

    if (injectable.getKind() == ElementKind.CLASS) {
      error("@Inject is not valid on a class: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.METHOD) {
      error("Method injection is not supported: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(FINAL)) {
      error("Can't inject a final field: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(PRIVATE)) {
      error("Can't inject a private field: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.CONSTRUCTOR
        && injectable.getModifiers().contains(PRIVATE)) {
      error("Can't inject a private constructor: " + elementToString(injectable), injectable);
      return false;
    }

    ElementKind elementKind = injectableType.getEnclosingElement().getKind();
    boolean isClassOrInterface = elementKind.isClass() || elementKind.isInterface();
    boolean isStatic = injectableType.getModifiers().contains(STATIC);

    if (isClassOrInterface && !isStatic) {
      error("Can't inject a non-static inner class: " + elementToString(injectable),
          injectableType);
      return false;
    }

    return true;
  }

  /**
   * @param injectedClassName the name of a class with an @Inject-annotated member.
   */
  private InjectedClass createInjectedClass(String injectedClassName) {
    TypeElement type = processingEnv.getElementUtils().getTypeElement(injectedClassName);
    boolean isAbstract = type.getModifiers().contains(ABSTRACT);
    List<Element> staticFields = new ArrayList<Element>();
    ExecutableElement constructor = null;
    List<Element> fields = new ArrayList<Element>();
    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) == null) {
        continue;
      }

      switch (member.getKind()) {
        case FIELD:
          if (member.getModifiers().contains(STATIC)) {
            staticFields.add(member);
          } else {
            fields.add(member);
          }
          break;
        case CONSTRUCTOR:
          if (constructor != null) {
            // TODO(tbroyer): pass annotation information
            error("Too many injectable constructors on " + type.getQualifiedName(), member);
          } else if (isAbstract) {
            // TODO(tbroyer): pass annotation information
            error("Abstract class " + type.getQualifiedName()
                + " must not have an @Inject-annotated constructor.", member);
          }
          constructor = (ExecutableElement) member;
          break;
        default:
          // TODO(tbroyer): pass annotation information
          error("Cannot inject " + elementToString(member), member);
          break;
      }
    }

    if (constructor == null && !isAbstract) {
      constructor = getNoArgsConstructor(type);
      if (constructor != null && !isCallableConstructor(constructor)) {
        constructor = null;
      }
    }

    return new InjectedClass(type, staticFields, constructor, fields);
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private void generateInjectAdapter(TypeElement type, ExecutableElement constructor,
      List<Element> fields) throws IOException {
    String packageName = getPackage(type).getQualifiedName().toString();
    String strippedTypeName =
        strippedTypeName(type.getQualifiedName().toString(), packageName);
    TypeMirror supertype = getNextMemberInjectedAncestor(type);
    String adapterName = adapterName(type, INJECT_ADAPTER_SUFFIX);
    JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    boolean isAbstract = type.getModifiers().contains(ABSTRACT);
    boolean injectMembers = !fields.isEmpty() || supertype != null;
    boolean disambiguateFields = !fields.isEmpty()
        && (constructor != null)
        && !constructor.getParameters().isEmpty();
    boolean dependent = injectMembers
        || ((constructor != null) && !constructor.getParameters().isEmpty());

    writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(packageName);
    writer.emitImports(findImports(dependent, injectMembers, constructor != null));
    writer.emitEmptyLine();
    writer.emitJavadoc(bindingTypeDocs(strippedTypeName, isAbstract, injectMembers, dependent));
    writer.beginType(adapterName, "class", EnumSet.of(PUBLIC, FINAL),
        JavaWriter.type(Binding.class, strippedTypeName),
        implementedInterfaces(strippedTypeName, injectMembers, constructor != null));
    writeMemberBindingsFields(writer, fields, disambiguateFields);
    if (constructor != null) {
      writeParameterBindingsFields(writer, constructor, disambiguateFields);
     }
    if (supertype != null) {
      writeSupertypeInjectorField(writer, type, supertype);
    }
    writer.emitEmptyLine();
    writeInjectAdapterConstructor(writer, constructor, type, strippedTypeName, adapterName);
    if (dependent) {
      writeAttachMethod(writer, constructor, fields, disambiguateFields, strippedTypeName,
          supertype, true);
      writeGetDependenciesMethod(writer, constructor, fields, disambiguateFields, supertype, true);
    }
    if (constructor != null) {
      writeGetMethod(writer, constructor, disambiguateFields, injectMembers, strippedTypeName);
    }
    if (injectMembers) {
      writeMembersInjectMethod(writer, fields, disambiguateFields, strippedTypeName, supertype);
    }
    writer.endType();
    writer.close();
    if (supertype != null) {
      generateParentBindings(type,
          ((TypeElement) processingEnv.getTypeUtils().asElement(supertype)));
    }
  }

  private void generateParentBindings(TypeElement originChild, TypeElement ancestor)
      throws IOException {
    List<Element> ancestorFields = createInjectedClass(ancestor.toString()).fields;
    TypeMirror nextAncestor = getNextMemberInjectedAncestor(ancestor);
    TypeElement nextAncestorElement =
        (nextAncestor != null) ? (TypeElement) processingEnv.getTypeUtils().asElement(nextAncestor)
            : null;
    String ancestorPackageName = getPackage(ancestor).getQualifiedName().toString();
    String strippedAncestorType =
        strippedTypeName(ancestor.getQualifiedName().toString(), ancestorPackageName);
    String adapterName = parentAdapterName(originChild, ancestor);
    JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(adapterName, ancestor);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(ancestorPackageName);
    writer.emitImports(MembersInjector.class.getCanonicalName(), Binding.class.getCanonicalName());
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.PARENT_ADAPTER_TYPE);
    writer.beginType(adapterName, "class", EnumSet.of(PUBLIC, FINAL), null,
        JavaWriter.type(MembersInjector.class, strippedAncestorType));
    writeMemberBindingsFields(writer, ancestorFields, false);
    if (nextAncestor != null) {
      writeSupertypeInjectorField(writer, originChild, nextAncestor); // next injectable ancestor
    }
    writer.emitEmptyLine();
    writeAttachMethod(writer, null, ancestorFields, false, strippedAncestorType, nextAncestor,
        false);
    writeGetDependenciesMethod(writer, null, ancestorFields, false, nextAncestor, false);
    writeMembersInjectMethod(writer, ancestorFields, false, strippedAncestorType, nextAncestor);
    writer.endType();
    writer.close();
    if (nextAncestor != null) {
      generateParentBindings(originChild, nextAncestorElement);
    }
  }

  /**
   * Returns the closest ancestor that has members injected or {@code null}
   * if the class has no ancestors with injected members.
   */
  private TypeMirror getNextMemberInjectedAncestor(TypeElement type) {
    TypeMirror nextAncestor = type.getSuperclass();
    TypeElement nextAncestorElement =
        (TypeElement) processingEnv.getTypeUtils().asElement(nextAncestor);
    if (isPlatformType(nextAncestor.toString())) {
      return null;
    }
    if (!createInjectedClass(nextAncestorElement.toString()).fields.isEmpty()) {
      return nextAncestor;
    }
    return getNextMemberInjectedAncestor(nextAncestorElement);
  }

  /**
   * Write a companion class for {@code type} that extends {@link StaticInjection}.
   */
  private void generateStaticInjection(TypeElement type, List<Element> fields) throws IOException {
    String typeName = type.getQualifiedName().toString();
    String adapterName = adapterName(type, STATIC_INJECTION_SUFFIX);
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(getPackage(type).getQualifiedName().toString());
    writer.emitImports(Arrays.asList(
        StaticInjection.class.getName(),
        Binding.class.getName(),
        Linker.class.getName()));
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.STATIC_INJECTION_TYPE, type.getSimpleName());
    writer.beginType(
        adapterName, "class", EnumSet.of(PUBLIC, FINAL), StaticInjection.class.getSimpleName());
    writeMemberBindingsFields(writer, fields, false);
    writer.emitEmptyLine();
    writeAttachMethod(writer, null, fields, false, typeName, null, true);
    writeStaticInjectMethod(writer, fields, typeName);
    writer.endType();
    writer.close();
  }

  private void writeMemberBindingsFields(
      JavaWriter writer, List<Element> fields, boolean disambiguateFields) throws IOException {
    for (Element field : fields) {
      writer.emitField(JavaWriter.type(Binding.class, typeToString(field.asType())),
          fieldName(disambiguateFields, field), EnumSet.of(PRIVATE));
    }
  }

  private void writeParameterBindingsFields(
      JavaWriter writer, ExecutableElement constructor, boolean disambiguateFields)
      throws IOException {
    for (VariableElement parameter : constructor.getParameters()) {
      writer.emitField(JavaWriter.type(Binding.class,
          typeToString(parameter.asType())),
          parameterName(disambiguateFields, parameter), EnumSet.of(PRIVATE));
    }
  }

  private void writeSupertypeInjectorField(
      JavaWriter writer, TypeElement type, TypeMirror nextAncestor) throws IOException {
    TypeElement supertypeElement =
        ((TypeElement) processingEnv.getTypeUtils().asElement(nextAncestor));
    String adapterName = parentAdapterName(type, supertypeElement);
    writer.emitField(
        adapterName, "nextInjectableAncestor", EnumSet.of(PRIVATE), "new " + adapterName + "()");
  }

  private void writeInjectAdapterConstructor(JavaWriter writer, ExecutableElement constructor,
      TypeElement type, String strippedTypeName, String adapterName) throws IOException {
    writer.beginMethod(null, adapterName, EnumSet.of(PUBLIC));
    String key = (constructor != null)
        ? JavaWriter.stringLiteral(GeneratorKeys.get(type.asType()))
        : null;
    String membersKey = JavaWriter.stringLiteral(GeneratorKeys.rawMembersKey(type.asType()));
    boolean singleton = type.getAnnotation(Singleton.class) != null;
    writer.emitStatement("super(%s, %s, %s, %s.class)",
        key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"), strippedTypeName);
    writer.endMethod();
    writer.emitEmptyLine();
  }

  /**
   * Writes the {@code attach()} method for the generated adapters. The {@code supertype} provided
   * is the next injectable ancestor.
   */
  private void writeAttachMethod(JavaWriter writer, ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, String typeName, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    writer.emitJavadoc(AdapterJavadocs.ATTACH_METHOD);
    if (extendsBinding) {
      writer.emitAnnotation(Override.class);
    }
    writer.emitAnnotation(SuppressWarnings.class, JavaWriter.stringLiteral("unchecked"));
    writer.beginMethod(
        "void", "attach", EnumSet.of(PUBLIC), Linker.class.getCanonicalName(), "linker");
    if (supertype != null) {
      writer.emitStatement("nextInjectableAncestor.attach(linker)");
    }
    if (constructor != null) {
      for (VariableElement parameter : constructor.getParameters()) {
        writer.emitStatement(
            "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
            parameterName(disambiguateFields, parameter),
            writer.compressType(JavaWriter.type(Binding.class, typeToString(parameter.asType()))),
            JavaWriter.stringLiteral(GeneratorKeys.get(parameter)), typeName);
      }
    }
    for (Element field : fields) {
      writer.emitStatement(
          "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
          fieldName(disambiguateFields, field),
          writer.compressType(JavaWriter.type(Binding.class, typeToString(field.asType()))),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) field)), typeName);
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

  /**
   * Writes the {@code getDependencies()} method for the generated adapters. The {@code supertype}
   * provided is the next injectable ancestor.
   */
  private void writeGetDependenciesMethod(JavaWriter writer, ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
    if (extendsBinding) {
      writer.emitAnnotation(Override.class);
    }
    String setOfBindings = JavaWriter.type(Set.class, "Binding<?>");
    writer.beginMethod("void", "getDependencies", EnumSet.of(PUBLIC), setOfBindings, "getBindings",
        setOfBindings, "injectMembersBindings");
    if (constructor != null) {
      for (Element parameter : constructor.getParameters()) {
        writer.emitStatement("getBindings.add(%s)", parameterName(disambiguateFields, parameter));
      }
    }
    for (Element field : fields) {
      writer.emitStatement("injectMembersBindings.add(%s)", fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      writer.emitStatement("nextInjectableAncestor.getDependencies(null, injectMembersBindings)");
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private void writeGetMethod(JavaWriter writer, ExecutableElement constructor,
      boolean disambiguateFields, boolean injectMembers, String strippedTypeName)
      throws IOException {
    writer.emitJavadoc(AdapterJavadocs.GET_METHOD, strippedTypeName);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(strippedTypeName, "get", EnumSet.of(PUBLIC));
    StringBuilder newInstance = new StringBuilder();
    newInstance.append(strippedTypeName).append(" result = new ");
    newInstance.append(strippedTypeName).append('(');
    boolean first = true;
    for (VariableElement parameter : constructor.getParameters()) {
      if (!first) newInstance.append(", ");
      else first = false;
      newInstance.append(parameterName(disambiguateFields, parameter)).append(".get()");
    }
    newInstance.append(')');
    writer.emitStatement(newInstance.toString());
    if (injectMembers) {
      writer.emitStatement("injectMembers(result)");
    }
    writer.emitStatement("return result");
    writer.endMethod();
    writer.emitEmptyLine();
  }

  /**
   * Writes the {@code injectMembers()} method for the generated adapters. The {@code supertype}
   * provided is the next injectable ancestor.
   */
  private void writeMembersInjectMethod(JavaWriter writer, List<Element> fields,
      boolean disambiguateFields, String strippedTypeName, TypeMirror supertype)
      throws IOException {
    writer.emitJavadoc(AdapterJavadocs.MEMBERS_INJECT_METHOD, strippedTypeName);
    writer.emitAnnotation(Override.class);
    writer.beginMethod("void", "injectMembers", EnumSet.of(PUBLIC), strippedTypeName, "object");
    for (Element field : fields) {
      writer.emitStatement("object.%s = %s.get()",
          field.getSimpleName(),
          fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      writer.emitStatement("nextInjectableAncestor.injectMembers(object)");
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private void writeStaticInjectMethod(JavaWriter writer, List<Element> fields, String typeName)
      throws IOException {
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.STATIC_INJECT_METHOD);
    writer.emitAnnotation(Override.class);
    writer.beginMethod("void", "inject", EnumSet.of(PUBLIC));
    for (Element field : fields) {
      writer.emitStatement("%s.%s = %s.get()",
          writer.compressType(typeName),
          field.getSimpleName().toString(),
          fieldName(false, field));
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private Set<String> findImports(boolean dependent, boolean injectMembers, boolean isProvider) {
    Set<String> imports = new LinkedHashSet<String>();
    imports.add(Binding.class.getCanonicalName());
    if (dependent) {
      imports.add(Linker.class.getCanonicalName());
      imports.add(Set.class.getCanonicalName());
    }
    if (injectMembers) imports.add(MembersInjector.class.getCanonicalName());
    if (isProvider) imports.add(Provider.class.getCanonicalName());
    return imports;
  }

  private String[] implementedInterfaces(
      String strippedTypeName, boolean hasFields, boolean isProvider) {
    List<String> interfaces = new ArrayList<String>();
    if (isProvider) {
      interfaces.add(JavaWriter.type(Provider.class, strippedTypeName));
    }
    if (hasFields) {
      interfaces.add(JavaWriter.type(MembersInjector.class, strippedTypeName));
    }
    return interfaces.toArray(new String[interfaces.size()]);
  }

  private String strippedTypeName(String type, String packageName) {
    return type.substring(packageName.isEmpty() ? 0 : packageName.length() + 1);
  }

  private String fieldName(boolean disambiguateFields, Element field) {
    return (disambiguateFields ? "field_" : "") + field.getSimpleName().toString();
  }

  private String parameterName(boolean disambiguateFields, Element parameter) {
    return (disambiguateFields ? "parameter_" : "") + parameter.getSimpleName().toString();
  }

  private String parentAdapterName(TypeElement originChild, TypeElement ancestor) {
    StringBuilder result = new StringBuilder();
    String ancestorPackageName = getPackage(ancestor).getQualifiedName().toString();
    String childPackageName = getPackage(originChild).getQualifiedName().toString();
    String childName = strippedTypeName(originChild.getQualifiedName().toString(), childPackageName)
        .replace('.', '$');
    String ancestorName = strippedTypeName(
        ancestor.getQualifiedName().toString(), ancestorPackageName).replace('.', '$');
    if (!ancestorPackageName.isEmpty()) {
      result.append(ancestorPackageName);
      result.append('.');
    }
    result.append(ancestorName);
    result.append(PARENT_ADAPTER_INFIX);
    if (!childPackageName.isEmpty()) {
      result.append(childPackageName.replace('.', '_'));
      result.append('_');
    }
    result.append(childName);
    return result.toString();
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  static class InjectedClass {
    final TypeElement type;
    final List<Element> staticFields;
    final ExecutableElement constructor;
    final List<Element> fields;

    InjectedClass(TypeElement type, List<Element> staticFields, ExecutableElement constructor,
        List<Element> fields) {
      this.type = type;
      this.staticFields = staticFields;
      this.constructor = constructor;
      this.fields = fields;
    }
  }

}
