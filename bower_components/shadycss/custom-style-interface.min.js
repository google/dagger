(function(){/*

Copyright (c) 2017 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
Code distributed by Google as part of the polymer project is also
subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
*/
'use strict';var c=!(window.ShadyDOM&&window.ShadyDOM.inUse),f;function g(a){f=a&&a.shimcssproperties?!1:c||!(navigator.userAgent.match(/AppleWebKit\/601|Edge\/15/)||!window.CSS||!CSS.supports||!CSS.supports("box-shadow","0 0 0 var(--foo)"))}window.ShadyCSS&&void 0!==window.ShadyCSS.nativeCss?f=window.ShadyCSS.nativeCss:window.ShadyCSS?(g(window.ShadyCSS),window.ShadyCSS=void 0):g(window.WebComponents&&window.WebComponents.flags);var h=f;function k(a,b){for(var d in b)null===d?a.style.removeProperty(d):a.style.setProperty(d,b[d])};var l=null,m=window.HTMLImports&&window.HTMLImports.whenReady||null,n;function p(){var a=q;requestAnimationFrame(function(){m?m(a):(l||(l=new Promise(function(a){n=a}),"complete"===document.readyState?n():document.addEventListener("readystatechange",function(){"complete"===document.readyState&&n()})),l.then(function(){a&&a()}))})};var r=null,q=null;function t(){this.customStyles=[];this.enqueued=!1}function u(a){!a.enqueued&&q&&(a.enqueued=!0,p())}t.prototype.c=function(a){a.__seenByShadyCSS||(a.__seenByShadyCSS=!0,this.customStyles.push(a),u(this))};t.prototype.b=function(a){if(a.__shadyCSSCachedStyle)return a.__shadyCSSCachedStyle;var b;a.getStyle?b=a.getStyle():b=a;return b};
t.prototype.a=function(){for(var a=this.customStyles,b=0;b<a.length;b++){var d=a[b];if(!d.__shadyCSSCachedStyle){var e=this.b(d);e&&(e=e.__appliedElement||e,r&&r(e),d.__shadyCSSCachedStyle=e)}}return a};t.prototype.addCustomStyle=t.prototype.c;t.prototype.getStyleForCustomStyle=t.prototype.b;t.prototype.processStyles=t.prototype.a;
Object.defineProperties(t.prototype,{transformCallback:{get:function(){return r},set:function(a){r=a}},validateCallback:{get:function(){return q},set:function(a){var b=!1;q||(b=!0);q=a;b&&u(this)}}});var v=new t;window.ShadyCSS||(window.ShadyCSS={prepareTemplate:function(){},styleSubtree:function(a,b){v.a();k(a,b)},styleElement:function(){v.a()},styleDocument:function(a){v.a();k(document.body,a)},getComputedStyleValue:function(a,b){return(a=window.getComputedStyle(a).getPropertyValue(b))?a.trim():""},nativeCss:h,nativeShadow:c});window.ShadyCSS.CustomStyleInterface=v;}).call(this);

//# sourceMappingURL=custom-style-interface.min.js.map
