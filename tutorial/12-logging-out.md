---
layout: default
title: Logging out
---

Our ATM currently supports logging in users, but there's no way to log out.
Let's add support for that.

The logout command is simple. The body of `handleInput()` is just `return
input.isEmpty() ? Result.inputCompleted() : Result.invalid();`.

Configure `LogoutCommand` like we've done for the other commands in
`UserCommandsRouter`.

<section style="text-align: center" markdown="1">

[Previous](11-withdraw-command) · [Next](13-max-withdrawal-across-commands)

</section>
