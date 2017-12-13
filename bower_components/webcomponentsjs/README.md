webcomponents.js (v1 spec polyfills)
================

[![Build Status](https://travis-ci.org/webcomponents/webcomponentsjs.svg?branch=master)](https://travis-ci.org/webcomponents/webcomponentsjs)

> **Note**. For polyfills that work with the older Custom Elements and Shadow DOM v0 specs, see the [v0 branch](https://github.com/webcomponents/webcomponentsjs/tree/v0).

A suite of polyfills supporting the [Web Components](http://webcomponents.org) specs:

- **Custom Elements v1**: allows authors to define their own custom tags ([spec](https://w3c.github.io/webcomponents/spec/custom/), [tutorial](https://developers.google.com/web/fundamentals/getting-started/primers/customelements)).
- **HTML Imports**: a way to include and reuse HTML documents via other HTML documents ([spec](https://w3c.github.io/webcomponents/spec/imports/), [tutorial](https://www.html5rocks.com/en/tutorials/webcomponents/imports/)).
- **Shadow DOM v1**: provides encapsulation by hiding DOM subtrees under shadow roots ([spec](https://w3c.github.io/webcomponents/spec/shadow/), [tutorial](https://developers.google.com/web/fundamentals/getting-started/primers/shadowdom)).

For browsers that need it, there are also some minor polyfills included:
- [`HTMLTemplateElement`](https://github.com/webcomponents/template)
- [`Promise`](https://github.com/stefanpenner/es6-promise)
- `Event`, `CustomEvent`, `MouseEvent` constructors and `Object.assign`, `Array.from` (see [webcomponents-platform](https://github.com/webcomponents/webcomponents-platform))

## How to use

The polyfills are built (concatenated & minified) into several bundles that target
different browsers and spec readiness:

- `webcomponents-hi.js` -- HTML Imports (needed by Safari Tech Preview)
- `webcomponents-hi-ce.js` -- HTML Imports and Custom Elements v1 (needed by Safari 10)
- `webcomponents-hi-sd-ce.js` -- HTML Imports, Custom Elements v1 and Shady DOM/CSS (needed by Safari 9, Firefox, Edge)
- `webcomponents-sd-ce.js` -- Custom Elements and Shady DOM/CSS (no HTML Imports)
- `webcomponents-lite.js` -- all of the polyfills: HTML Imports, Custom Elements, Shady DOM/CSS and generic platform polyfills (such as ES6 Promise, Constructable events, etc.) (needed by Internet Explorer 11), and Template (needed by IE 11 and Edge)

If you are only targeting a specific browser, you can just use the bundle that's
needed by it; alternatively, if your server is capable of serving different assets based on user agent, you can send the polyfill bundle that's necessary for the browser making that request.

## `webcomponents-loader.js`
Alternatively, this repo also comes with `webcomponents-loader.js`, a client-side
loader that dynamically loads the minimum polyfill bundle, using feature detection.
Note that because the bundle will be loaded asynchronously, you should wait for the `WebComponentsReady` before you can safely assume that all the polyfills have
loaded and are ready to be used (i.e. if you want to dynamically load other custom
elements, etc.).

Additionally, you can check if `window.WebComponents` exists to know if the `WebComponentsReady` event will fire, and you can check if `window.WebComponents.ready` is true to check if the `WebComponentsReady` event has already fired.

Here's an example:

```html
<!-- Load polyfills; note that "loader" will load these async -->
<script src="bower_components/webcomponentsjs/webcomponents-loader.js"></script>

<!-- Load a custom element definition via HTMLImports -->
<link rel="import" href="my-element.html">

<!-- Use the custom element -->
<my-element></my-element>

<!-- Interact with the upgraded element -->
<script>
  window.addEventListener('WebComponentsReady', function() {
    // At this point we are guaranteed that all required polyfills have loaded,
    // all HTML imports have loaded, and all defined custom elements have upgraded
    let MyElement = customElements.get('my-element');
    let element = document.querySelector('my-element');
    console.assert(element instanceof MyElement);  // 👍
    element.someAPI(); // 👍
  });
</script>
```

## `custom-elements-es5-adapter.js`
According to the spec, Custom Elements must be ES6 classes (https://html.spec.whatwg.org/multipage/scripting.html#custom-element-conformance). Since most projects need to support a wide range of browsers that don't necessary support ES6, it may make sense to compile your project to ES5. However, ES5-style custom element classes will **not** work with native Custom Elements because ES5-style classes cannot properly extend ES6 classes, like `HTMLElement`.

To work around this, load `custom-elements-es5-adapter.js` before declaring new Custom Elements.

**The adapter must NOT be compiled.**

```html
<!-- Load Custom Elements es5 adapter -->
<script src="bower_components/webcomponentsjs/custom-elements-es5-adapter.js"></script>
<!-- Load polyfills; note that "loader" will load these async -->
<script src="bower_components/webcomponentsjs/webcomponents-loader.js"></script>
<!-- Load the es5 compiled custom element definition -->
<link rel="import" href="my-es5-element.html">

<!-- Use the custom element -->
<my-es5-element></my-es5-element>
```

## Browser Support

The polyfills are intended to work in the latest versions of evergreen browsers. See below
for our complete browser support matrix:

| Polyfill   | IE11+ | Chrome* | Firefox* | Safari 9+* | Chrome Android* | Mobile Safari* |
| ---------- |:-----:|:-------:|:--------:|:----------:|:---------------:|:--------------:|
| Custom Elements | ✓ | ✓ | ✓ | ✓ | ✓| ✓ |
| HTML Imports |  ✓ | ✓ | ✓ | ✓| ✓| ✓ |
| Shady CSS/DOM |  ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

\*Indicates the current version of the browser

The polyfills may work in older browsers, however require additional polyfills (such as classList, or other [platform](https://github.com/webcomponents/webcomponents-platform)
polyfills) to be used. We cannot guarantee support for browsers outside of our compatibility matrix.


### Manually Building

If you wish to build the bundles yourself, you'll need `node` and `npm` on your system:

 * install [node.js](http://nodejs.org/) using the instructions on their website
 * use `npm` to install [gulp.js](http://gulpjs.com/): `npm install -g gulp`

Now you are ready to build the polyfills with:

    # install dependencies
    npm install
    bower install
    # build
    gulp

The builds will be placed into the root directory.

## Contribute

See the [contributing guide](CONTRIBUTING.md)

## License

Everything in this repository is BSD style license unless otherwise specified.

Copyright (c) 2015 The Polymer Authors. All rights reserved.

## Helper utilities

### `WebComponentsReady`

Under native HTML Imports, `<script>` tags in the main document block the loading of such imports. This is to ensure the imports have loaded and any registered elements in them have been upgraded.

The `webcomponents-lite.js` polyfill (as well as the smaller bundles and the loader) parse element definitions and handle their upgrade asynchronously. If prematurely fetching the element from the DOM before it has an opportunity to upgrade, you'll be working with an `HTMLUnknownElement`.

For these situations, you can use the `WebComponentsReady` event as a signal before interacting with the element. The criteria for this event to fire is all Custom Elements with definitions registered by the time HTML Imports available at load time have loaded have upgraded.

```js
window.addEventListener('WebComponentsReady', function(e) {
  // imports are loaded and elements have been registered
  console.log('Components are ready');
});
```

## Known Issues

  * [ShadowDOM CSS is not encapsulated out of the box](#shadycss)
  * [Custom element's constructor property is unreliable](#constructor)
  * [Contenteditable elements do not trigger MutationObserver](#contentedit)
  * [ShadyCSS: :host(.zot:not(.bar:nth-child(2))) doesn't work](#nestedparens)
  
### ShadowDOM CSS is not encapsulated out of the box <a id="shadycss"></a>
The ShadowDOM polyfill is not able to encapsulate CSS in ShadowDOM out of the box. You need to use specific code from the ShadyCSS library, included with the polyfill. See [ShadyCSS instructions](https://github.com/webcomponents/shadycss).

### Custom element's constructor property is unreliable <a id="constructor"></a>
See [#215](https://github.com/webcomponents/webcomponentsjs/issues/215) for background.

In Safari and IE, instances of Custom Elements have a `constructor` property of `HTMLUnknownElementConstructor` and `HTMLUnknownElement`, respectively. It's unsafe to rely on this property for checking element types.

It's worth noting that `customElement.__proto__.__proto__.constructor` is `HTMLElementPrototype` and that the prototype chain isn't modified by the polyfills(onto `ElementPrototype`, etc.)

### Contenteditable elements do not trigger MutationObserver <a id="contentedit"></a>
Using the MutationObserver polyfill, it isn't possible to monitor mutations of an element marked `contenteditable`.
See [the mailing list](https://groups.google.com/forum/#!msg/polymer-dev/LHdtRVXXVsA/v1sGoiTYWUkJ)

### ShadyCSS: :host(.zot:not(.bar:nth-child(2))) doesn't work <a id="nestedparens"></a>
ShadyCSS `:host()` rules can only have (at most) 1-level of nested parentheses in its argument selector under ShadyCSS. For example, `:host(.zot)` and `:host(.zot:not(.bar))` both work, but `:host(.zot:not(.bar:nth-child(2)))` does not.
