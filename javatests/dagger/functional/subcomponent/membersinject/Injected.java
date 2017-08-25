package dagger.functional.subcomponent.membersinject;

import java.util.Set;

import javax.inject.Inject;

class Injected {
    @Inject Set<String> strings;
}
