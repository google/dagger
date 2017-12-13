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

import {nativeShadow} from './style-settings.js';
import StyleTransformer from './style-transformer.js';
import {getIsExtends} from './style-util.js';

export let flush = function() {};

/**
 * @param {HTMLElement} element
 * @return {!Array<string>}
 */
function getClasses(element) {
  let classes = [];
  if (element.classList) {
    classes = Array.from(element.classList);
  } else if (element instanceof window['SVGElement'] && element.hasAttribute('class')) {
    classes = element.getAttribute('class').split(/\s+/);
  }
  return classes;
}

/**
 * @param {HTMLElement} element
 * @return {string}
 */
function getCurrentScope(element) {
  let classes = getClasses(element);
  let idx = classes.indexOf(StyleTransformer.SCOPE_NAME);
  if (idx > -1) {
    return classes[idx + 1];
  }
  return '';
}

/**
 * @param {Array<MutationRecord|null>|null} mxns
 */
function handler(mxns) {
  for (let x=0; x < mxns.length; x++) {
    let mxn = mxns[x];
    if (mxn.target === document.documentElement ||
      mxn.target === document.head) {
      continue;
    }
    for (let i=0; i < mxn.addedNodes.length; i++) {
      let n = mxn.addedNodes[i];
      if (n.nodeType !== Node.ELEMENT_NODE) {
        continue;
      }
      n = /** @type {HTMLElement} */(n); // eslint-disable-line no-self-assign
      let root = n.getRootNode();
      let currentScope = getCurrentScope(n);
      // node was scoped, but now is in document
      if (currentScope && root === n.ownerDocument) {
        StyleTransformer.dom(n, currentScope, true);
      } else if (root.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
        let newScope;
        let host = /** @type {ShadowRoot} */(root).host;
        // element may no longer be in a shadowroot
        if (!host) {
          continue;
        }
        newScope = getIsExtends(host).is;
        if (currentScope === newScope) {
          // make sure all the subtree elements are scoped correctly
          let unscoped = window['ShadyDOM']['nativeMethods']['querySelectorAll'].call(
            n, `:not(.${StyleTransformer.SCOPE_NAME})`);
          for (let j = 0; j < unscoped.length; j++) {
            StyleTransformer.element(unscoped[j], currentScope);
          }
          continue;
        }
        if (currentScope) {
          StyleTransformer.dom(n, currentScope, true);
        }
        StyleTransformer.dom(n, newScope);
      }
    }
  }
}

if (!nativeShadow) {
  let observer = new MutationObserver(handler);
  let start = (node) => {
    observer.observe(node, {childList: true, subtree: true});
  }
  let nativeCustomElements = (window['customElements'] &&
    !window['customElements']['polyfillWrapFlushCallback']);
  // need to start immediately with native custom elements
  // TODO(dfreedm): with polyfilled HTMLImports and native custom elements
  // excessive mutations may be observed; this can be optimized via cooperation
  // with the HTMLImports polyfill.
  if (nativeCustomElements) {
    start(document);
  } else {
    let delayedStart = () => {
      start(document.body);
    }
    // use polyfill timing if it's available
    if (window['HTMLImports']) {
      window['HTMLImports']['whenReady'](delayedStart);
    // otherwise push beyond native imports being ready
    // which requires RAF + readystate interactive.
    } else {
      requestAnimationFrame(function() {
        if (document.readyState === 'loading') {
          let listener = function() {
            delayedStart();
            document.removeEventListener('readystatechange', listener);
          }
          document.addEventListener('readystatechange', listener);
        } else {
          delayedStart();
        }
      });
    }
  }

  flush = function() {
    handler(observer.takeRecords());
  }
}
