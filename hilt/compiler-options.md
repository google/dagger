---
layout: default
title: Compiler Options
---

## Turning off the `@InstallIn` check {#disable-install-in-check}

By default, Hilt checks `@Module` classes for the `@InstallIn` annotation and
raises an error if it is missing. This is because if someone accidentally
forgets to put `@InstallIn` on a module, it could be very hard to debug that
Hilt isn't picking it up.

This check can sometimes be overly broad though, especially if in the middle of
a migration. To turn off this check, this flag can be used:

`-Adagger.hilt.disableModulesHaveInstallInCheck=true`.

Alternatively, the check can be disabled at the individual module level by
annotating the module with
[`@DisableInstallInCheck`](https://dagger.dev/api/latest/dagger/hilt/migration/DisableInstallInCheck.html).
