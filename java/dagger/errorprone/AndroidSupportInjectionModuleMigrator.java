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

import static com.google.errorprone.BugPattern.SeverityLevel.SUGGESTION;

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.ProvidesFix;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.code.Symbol;

/** A refactoring to update AndroidInjector bindings to their new form. */
@BugPattern(
    name = "AndroidSupportInjectionModuleMigrator",
    providesFix = ProvidesFix.REQUIRES_HUMAN_ATTENTION,
    summary = "Inlines usages of AndroidSupportInjectionModule to AndroidInjectionModule",
    explanation =
        "AndroidSupportInjectionModule is now an empty module and acts as an alias for "
            + "AndroidInjectionModule. This migration rewrites usages of the former to the latter.",
    severity = SUGGESTION)
public final class AndroidSupportInjectionModuleMigrator extends BugChecker
    implements MemberSelectTreeMatcher {
  private static final Matcher<ExpressionTree> MODULE_CLASS_LITERAL =
      Matchers.classLiteral(
          (ExpressionTree expressionTree, VisitorState state) -> {
            Symbol symbol = ASTHelpers.getSymbol(expressionTree);
            if (symbol == null) {
              return false;
            }
            return symbol
                .getQualifiedName()
                .contentEquals("dagger.android.support.AndroidSupportInjectionModule");
          });

  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    if (MODULE_CLASS_LITERAL.matches(tree, state)) {
      return describeMatch(
          tree,
          SuggestedFix.builder()
              .replace(tree, "AndroidInjectionModule.class")
              .addImport("dagger.android.AndroidInjectionModule")
              .build());
    }
    return Description.NO_MATCH;
  }
}
