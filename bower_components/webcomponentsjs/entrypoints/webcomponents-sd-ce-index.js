/**
@license
Copyright (c) 2017 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
Code distributed by Google as part of the polymer project is also
subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
*/
'use strict';

/*
 * Polyfills loaded: Custom Elements, Shady DOM/Shady CSS
 * Used in: Safari 9, Firefox, Edge
 */

import '../node_modules/@webcomponents/shadydom/src/shadydom.js';
import '../node_modules/@webcomponents/custom-elements/src/custom-elements.js';
import '../node_modules/@webcomponents/shadycss/entrypoints/scoping-shim.js';

let document = window.document;
// global for (1) existence means `WebComponentsReady` will file,
// (2) WebComponents.ready == true means event has fired.
window.WebComponents = window.WebComponents || {};

function fire() {
  requestAnimationFrame(() => {
    window.WebComponents.ready = true;
    window.document.dispatchEvent(new CustomEvent('WebComponentsReady', { bubbles: true }));
  })
}

function wait() {
  fire();
  document.removeEventListener('readystatechange', wait);
}

if (document.readyState !== 'loading') {
  fire();
} else {
  document.addEventListener('readystatechange', wait);
}