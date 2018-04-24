---
layout: default
title: ELI5: Dagger
---

Q: [ELI5 on what Dagger is](reddit)?

A: Instead of taking the toys you want yourself, ask Mommy/Daddy for your toys
and they'll give them to you. Sometimes you ask for a new Teddy Bear multiple
times, but Mommy and Daddy give you the same Teddy Bear because you don't know
the difference between an old one and a new one.

Some play rooms only have certain toys. Before you're ready to play, Mommy and
Daddy need to bring all of the toys to the room. If you ask for a toy that isn't
in the room, Mommy and Daddy won't compile your application.

- toys == bindings == `@Inject` contructors/`@Provides` methods
- Mommy/Daddy/Dagger == `@Component`s
- TeddyBear == a scoped object, i.e. there exists only one
- some rooms only have some toys = `@Component`s need to declare what `@Module`s
  they include

[reddit]: https://www.reddit.com/r/androiddev/comments/7vfenw/were_on_the_team_that_builds_dagger_at_google_ask/dts9ki4/?st=jejbnpc2&sh=43ba2000
