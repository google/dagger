/**
 * @license
 * Copyright (c) 2014 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
 */

'use strict';

let customElements = window['customElements'];
let HTMLImports = window['HTMLImports'];
let Template = window['HTMLTemplateElement'];

// global for (1) existence means `WebComponentsReady` will file,
// (2) WebComponents.ready == true means event has fired.
window.WebComponents = window.WebComponents || {};

if (customElements && customElements['polyfillWrapFlushCallback']) {
  // Here we ensure that the public `HTMLImports.whenReady`
  // always comes *after* custom elements have upgraded.
  let flushCallback;
  let runAndClearCallback = function runAndClearCallback() {
    if (flushCallback) {
      // make sure to run the HTMLTemplateElement polyfill before custom elements upgrade
      if (Template.bootstrap) {
        Template.bootstrap(window.document);
      }
      let cb = flushCallback;
      flushCallback = null;
      cb();
      return true;
    }
  }
  let origWhenReady = HTMLImports['whenReady'];
  customElements['polyfillWrapFlushCallback'](function(cb) {
    flushCallback = cb;
    origWhenReady(runAndClearCallback);
  });

  HTMLImports['whenReady'] = function(cb) {
    origWhenReady(function() {
      // custom element code may add dynamic imports
      // to match processing of native custom elements before
      // domContentLoaded, we wait for these imports to resolve first.
      if (runAndClearCallback()) {
        HTMLImports['whenReady'](cb);
      } else {
        cb();
      }
    });
  }

}

HTMLImports['whenReady'](function() {
  requestAnimationFrame(function() {
    window.WebComponents.ready = true;
    document.dispatchEvent(new CustomEvent('WebComponentsReady', {bubbles: true}));
  });
});