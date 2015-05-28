package test.subcomponent.repeat;

import dagger.Component;

import java.util.Set;

@Component(modules = RepeatedModule.class)
interface ParentComponent {
  String getString();
  Set<String> getMultiboundStrings();
  OnlyUsedInParent getOnlyUsedInParent();

  ChildComponent newChildComponent();
}
