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
package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.Util.isValidJavaIdentifier;
import static javax.lang.model.element.NestingKind.MEMBER;
import static javax.lang.model.element.NestingKind.TOP_LEVEL;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Represents a fully-qualified class name for {@link NestingKind#TOP_LEVEL} and
 * {@link NestingKind#MEMBER} classes.
 *
 * @since 2.0
 */
@AutoValue
abstract class ClassName implements Comparable<ClassName> {
  private String fullyQualifiedName = null;

  String fullyQualifiedName() {
    if (fullyQualifiedName == null) {
      StringBuilder builder = new StringBuilder(packageName());
      if (builder.length() > 0) {
        builder.append('.');
      }
      for (String enclosingSimpleName : enclosingSimpleNames()) {
        builder.append(enclosingSimpleName).append('.');
      }
      fullyQualifiedName = builder.append(simpleName()).toString();
    }
    return fullyQualifiedName;
  }

  String classFileName() {
    StringBuilder builder = new StringBuilder();
    Joiner.on('$').appendTo(builder, enclosingSimpleNames());
    if (!enclosingSimpleNames().isEmpty()) {
      builder.append('$');
    }
    return builder.append(simpleName()).toString();
  }

  abstract String packageName();
  /* From top to bottom.  E.g.: this field will contian ["A", "B"] for pgk.A.B.C */
  abstract ImmutableList<String> enclosingSimpleNames();
  abstract String simpleName();


  String suggestedVariableName() {
    return CharMatcher.is('$').removeFrom(
        CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, simpleName()));
  }

  ClassName nameOfTopLevelClass() {
    Iterator<String> enclosingIterator = enclosingSimpleNames().iterator();
    return enclosingIterator.hasNext()
        ? new AutoValue_ClassName(packageName(), ImmutableList.<String>of(),
            enclosingIterator.next())
        : this;
  }

  ClassName memberClassNamed(String memberClassName) {
    checkNotNull(memberClassName);
    checkArgument(isValidJavaIdentifier(memberClassName));
    checkArgument(Ascii.isUpperCase(memberClassName.charAt(0)));
    return new AutoValue_ClassName(packageName(),
        new ImmutableList.Builder<String>()
            .addAll(enclosingSimpleNames())
            .add(simpleName())
            .build(),
        memberClassName);
  }

  ClassName peerNamed(String peerClassName) {
    checkNotNull(peerClassName);
    checkArgument(isValidJavaIdentifier(peerClassName));
    checkArgument(Ascii.isUpperCase(peerClassName.charAt(0)));
    return new AutoValue_ClassName(packageName(), enclosingSimpleNames(), peerClassName);
  }

  private static final ImmutableSet<NestingKind> ACCEPTABLE_NESTING_KINDS =
      Sets.immutableEnumSet(TOP_LEVEL, MEMBER);

  static ClassName fromTypeElement(TypeElement element) {
    checkNotNull(element);
    checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
    String simpleName = element.getSimpleName().toString();
    List<String> enclosingNames = new ArrayList<String>();
    Element current = element.getEnclosingElement();
    while (current.getKind().isClass() || current.getKind().isInterface()) {
      checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
      enclosingNames.add(current.getSimpleName().toString());
      current = element.getEnclosingElement();
    }
    PackageElement packageElement = Util.getPackage(current);
    Collections.reverse(enclosingNames);
    return new AutoValue_ClassName(packageElement.getQualifiedName().toString(),
        ImmutableList.copyOf(enclosingNames), simpleName);
  }

  static ClassName fromClass(Class<?> clazz) {
    checkNotNull(clazz);
    List<String> enclosingNames = new ArrayList<String>();
    Class<?> current = clazz.getEnclosingClass();
    while (current != null) {
      enclosingNames.add(current.getSimpleName());
      current = clazz.getEnclosingClass();
    }
    Collections.reverse(enclosingNames);
    return create(clazz.getPackage().getName(), enclosingNames, clazz.getSimpleName());
  }

  /**
   * Returns a new {@link ClassName} instance for the given fully-qualified class name string. This
   * method assumes that the input is ASCII and follows typical Java style (lower-case package
   * names, upper-camel-case class names) and may produce incorrect results or throw
   * {@link IllegalArgumentException} otherwise. For that reason, {@link #fromClass(Class)} and
   * {@link #fromClass(Class)} should be preferred as they can correctly create {@link ClassName}
   * instances without such restrictions.
   */
  static ClassName bestGuessFromString(String classNameString) {
    checkNotNull(classNameString);
    List<String> parts = Splitter.on('.').splitToList(classNameString);
    int firstClassPartIndex = -1;
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      checkArgument(isValidJavaIdentifier(part));
      char firstChar = part.charAt(0);
      if (Ascii.isLowerCase(firstChar)) {
        // looks like a package part
        if (firstClassPartIndex >= 0) {
          throw new IllegalArgumentException("couldn't make a guess for " + classNameString);
        }
      } else if (Ascii.isUpperCase(firstChar)) {
        // looks like a class part
        if (firstClassPartIndex < 0) {
          firstClassPartIndex = i;
        }
      } else {
        throw new IllegalArgumentException("couldn't make a guess for " + classNameString);
      }
    }
    int lastIndex = parts.size() - 1;
    return new AutoValue_ClassName(
        Joiner.on('.').join(parts.subList(0, firstClassPartIndex)),
        firstClassPartIndex == lastIndex
            ? ImmutableList.<String>of()
            : ImmutableList.copyOf(parts.subList(firstClassPartIndex, lastIndex)),
        parts.get(lastIndex));
  }

  static ClassName create(String packageName,
      List<String> enclosingSimpleNames, String simpleName) {
    return new AutoValue_ClassName(packageName, ImmutableList.copyOf(enclosingSimpleNames),
        simpleName);
  }

  static ClassName create(String packageName, String simpleName) {
    return new AutoValue_ClassName(packageName, ImmutableList.<String>of(), simpleName);
  }

  @Override
  public String toString() {
    return fullyQualifiedName();
  }

  @Override
  public int compareTo(ClassName o) {
    return fullyQualifiedName().compareTo(o.fullyQualifiedName());
  }
}
