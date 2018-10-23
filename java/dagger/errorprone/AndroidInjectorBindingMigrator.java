/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.errorprone;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.ProvidesFix;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.Types;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** A refactoring to update AndroidInjector bindings to their new form. */
@BugPattern(
    name = "AndroidInjectorBindingMigrator",
    providesFix = ProvidesFix.REQUIRES_HUMAN_ATTENTION,
    summary = "A refactoring to update AndroidInjector bindings to their new form.",
    explanation =
        "dagger.android is migrating the mechanism used to bind AndroidInjectors. This refactoring "
            + "will migrate usages of the `dagger.android` class-based map keys to "
            + "`@dagger.multibindings.ClassKey` and also modify the return type of those binding "
            + "methods to AndroidInjector.Factory<?> (from AndroidInjector.Factory<? "
            + "extends Activity>).",
    severity = SUGGESTION)
public final class AndroidInjectorBindingMigrator extends BugChecker implements MethodTreeMatcher {
  private static final String ANDROID_INJECTOR_FACTORY = "dagger.android.AndroidInjector$Factory";
  private static final String CLASS_KEY = ClassKey.class.getName();
  private static final String ANDROID_INJECTION_KEY = "dagger.android.AndroidInjectionKey";

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    if (!hasAnnotation(tree, IntoMap.class, state)) {
      return Description.NO_MATCH;
    }

    Symbol androidInjectorFactory = state.getSymbolFromString(ANDROID_INJECTOR_FACTORY);

    if (androidInjectorFactory == null) {
      return Description.NO_MATCH;
    }

    SuggestedFix.Builder suggestedFix = SuggestedFix.builder();
    Types types = state.getTypes();

    if (!androidInjectorFactory.equals(getSymbol(tree.getReturnType()))) {
      return Description.NO_MATCH;
    }

    ClassType bindingType = (ClassType) getType(tree.getReturnType());
    if (bindingType.isParameterized()) {
      Type typeParameter = getOnlyElement(bindingType.getTypeArguments());
      if (typeParameter instanceof WildcardType
          && ((WildcardType) typeParameter).getExtendsBound() != null) {
        suggestedFix.replace(tree.getReturnType(), "AndroidInjector.Factory<?>");
      }
    }

    Type classKey = state.getTypeFromString(CLASS_KEY);
    Type androidInjectionKey = state.getTypeFromString(ANDROID_INJECTION_KEY);
    for (AnnotationTree annotationTree : tree.getModifiers().getAnnotations()) {
      Type annotationType = getType(annotationTree.getAnnotationType());

      if (hasAnnotation(annotationType.tsym, "dagger.MapKey", state)
          && !types.isSameType(annotationType, classKey)
          && !types.isSameType(annotationType, androidInjectionKey)) {
        suggestedFix.replace(annotationTree.getAnnotationType(), "ClassKey").addImport(CLASS_KEY);
      }
    }

    if (suggestedFix.isEmpty()) {
      return Description.NO_MATCH;
    }

    return buildDescription(tree).addFix(suggestedFix.build()).build();
  }
}
