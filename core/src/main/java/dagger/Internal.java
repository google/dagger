package dagger;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Target(METHOD) @Retention(RUNTIME)
public @interface Internal { }
