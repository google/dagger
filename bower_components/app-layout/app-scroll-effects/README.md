# Scroll effects

`Polymer.AppScrollEffectsBehavior` provides an interface that allows an element to use scrolls effects.

### Importing the app-layout effects

app-layout provides a set of scroll effects that can be used by explicitly importing `app-scroll-effects.html`:

```html
<link rel="import" href="/bower_components/app-layout/app-scroll-effects/app-scroll-effects.html">
```

The scroll effects can also be used by individually importing `app-layout/app-scroll-effects/effects/[effectName].html`.
For example:

```html
<link rel="import" href="/bower_components/app-layout/app-scroll-effects/effects/waterfall.html">
```

### Consuming effects

Effects can be consumed via the `effects` property. For example:

```html
<app-header effects="waterfall"></app-header>
```

### Creating scroll effects

You may want to create a custom scroll effect if you need to modify the CSS of an element
based on the scroll position.

A scroll effect definition is an object with `setUp()`, `tearDown()` and `run()` functions.

To register the effect, you can use `Polymer.AppLayout.registerEffect(effectName, effectDef)`
For example, let's define an effect that resizes the header's logo:

```js
Polymer.AppLayout.registerEffect('resizable-logo', {
  setUp: function(config) {
    // the effect's config is passed to the setUp.
    this._fxResizeLogo = { logo: Polymer.dom(this).querySelector('[logo]') };
  },

  run: function(progress) {
     // the progress of the effect
     this.transform('scale3d(' + progress + ', '+ progress +', 1)',  this._fxResizeLogo.logo);
  },

  tearDown: function() {
     // clean up and reset of states
     delete this._fxResizeLogo;
  }
});
```
Now, you can consume the effect:

```html
<app-header id="appHeader" effects="resizable-logo">
  <img logo src="logo.svg">
</app-header>
```

### Imperative API

```js
var logoEffect = appHeader.createEffect('resizable-logo', effectConfig);
// run the effect: logoEffect.run(progress);
// tear down the effect: logoEffect.tearDown();
```

### Configuring effects

For effects installed via the `effects` property, their configuration can be set
via the `effectsConfig` property. For example:

```html
<app-header effects="waterfall"
  effects-config='{"waterfall": {"startsAt": 0, "endsAt": 0.5}}'>
</app-header>
```

All effects have a `startsAt` and `endsAt` config property. They specify at what
point the effect should start and end. This value goes from 0 to 1 inclusive.
