# App Layout [![Build Status](https://travis-ci.org/PolymerElements/app-layout.svg?branch=master)](https://travis-ci.org/PolymerElements/app-layout) [![Published on webcomponents.org](https://img.shields.io/badge/webcomponents.org-published-blue.svg)](https://beta.webcomponents.org/element/PolymerElements/app-layout)

A collection of elements, along with guidelines and templates that can be used to structure your app’s layout.

<!---
```
<custom-element-demo height="368">
  <template>
    <script src="../webcomponentsjs/webcomponents-lite.js"></script>
    <link rel="import" href="app-drawer/app-drawer.html">
    <link rel="import" href="app-header/app-header.html">
    <link rel="import" href="app-toolbar/app-toolbar.html">
    <link rel="import" href="demo/sample-content.html">
    <link rel="import" href="../iron-flex-layout/iron-flex-layout.html">
    <link rel="import" href="../iron-icons/iron-icons.html">
    <link rel="import" href="../paper-icon-button/paper-icon-button.html">
    <link rel="import" href="../paper-progress/paper-progress.html">
    <custom-style>
      <style is="custom-style">
        html, body {
          margin: 0;
          font-family: 'Roboto', 'Noto', sans-serif;
          -webkit-font-smoothing: antialiased;
          background: #f1f1f1;
          max-height: 368px;
        }
        app-toolbar {
          background-color: #4285f4;
          color: #fff;
        }

        paper-icon-button {
          --paper-icon-button-ink-color: white;
        }

        paper-icon-button + [main-title] {
          margin-left: 24px;
        }
        paper-progress {
          display: block;
          width: 100%;
          --paper-progress-active-color: rgba(255, 255, 255, 0.5);
          --paper-progress-container-color: transparent;
        }
        app-header {
          @apply --layout-fixed-top;
          color: #fff;
          --app-header-background-rear-layer: {
            background-color: #ef6c00;
          };
        }
        app-drawer {
          --app-drawer-scrim-background: rgba(0, 0, 100, 0.8);
          --app-drawer-content-container: {
            background-color: #B0BEC5;
          }
        }
        sample-content {
          padding-top: 64px;
        }
      </style>
    </custom-style>
    <next-code-block></next-code-block>
  </template>
</custom-element-demo>
```
-->
```html
<app-header reveals>
  <app-toolbar>
    <paper-icon-button icon="menu" onclick="drawer.toggle()"></paper-icon-button>
    <div main-title>My app</div>
    <paper-icon-button icon="delete"></paper-icon-button>
    <paper-icon-button icon="search"></paper-icon-button>
    <paper-icon-button icon="close"></paper-icon-button>
    <paper-progress value="10" indeterminate bottom-item></paper-progress>
  </app-toolbar>
</app-header>
<app-drawer id="drawer" swipe-open></app-drawer>
<sample-content size="10"></sample-content>
```

## Install

```bash
$ bower install PolymerElements/app-layout --save
```

## Import

```html
<link rel="import" href="/bower_components/app-layout/app-layout.html">
```

## What is inside

### Elements

- [app-box](/app-box) - A container element that can have scroll effects - visual effects based on scroll position.

- [app-drawer](/app-drawer) - A navigation drawer that can slide in from the left or right.

- [app-drawer-layout](/app-drawer-layout) - A wrapper element that positions an app-drawer and other content.

- [app-grid](/app-grid) - A helper class useful for creating responsive, fluid grid layouts using custom properties.

- [app-header](/app-header) - A container element for app-toolbars at the top of the screen that can have scroll effects - visual effects based on scroll position.

- [app-header-layout](/app-header-layout) - A wrapper element that positions an app-header and other content.

- [app-toolbar](/app-toolbar) - A horizontal toolbar containing items that can be used for label, navigation, search and actions.

### Templates

The templates are a means to define, illustrate and share best practices in App Layout. Pick a template and customize it:

- **Getting started**
([Demo](https://polymerelements.github.io/app-layout/templates/getting-started) - [Source](/templates/getting-started))

- **Landing page**
([Demo](https://polymerelements.github.io/app-layout/templates/landing-page) - [Source](/templates/landing-page))

- **Publishing: Zuperkülblog**
([Demo](https://polymerelements.github.io/app-layout/templates/publishing) - [Source](/templates/publishing))

- **Shop: Shrine**
([Demo](https://polymerelements.github.io/app-layout/templates/shrine) - [Source](/templates/shrine))

- **Blog: Pesto**
([Demo](https://polymerelements.github.io/app-layout/templates/pesto) - [Source](/templates/pesto))

- **Scroll effects: Test drive**
([Demo](https://polymerelements.github.io/app-layout/templates/test-drive) - [Source](/templates/test-drive))

### Patterns

Sample code for various UI patterns:

- **Transform navigation:**
As more screen space is available, side navigation can transform into tabs.
([Demo](https://www.webcomponents.org/element/PolymerElements/app-layout/demo/patterns/transform-navigation/index.html) - [Source](/patterns/transform-navigation/x-app.html))

- **Expand Card:**
Content cards may expand to take up more horizontal space.
([Demo](https://www.webcomponents.org/element/PolymerElements/app-layout/demo/patterns/expand-card/index.html) - [Source](/patterns/expand-card/index.html))

- **Material Design Responsive Toolbar:**
Toolbar changes its height and padding to adapt mobile screen size.
([Demo](https://www.webcomponents.org/element/PolymerElements/app-layout/demo/patterns/md-responsive-toolbar/index.html) - [Source](/patterns/md-responsive-toolbar/index.html))

## Users

Here are some web apps built with App Layout:

- [Youtube Web](https://www.youtube.com/new)
- [Google I/O 2016](https://events.google.com/io2016/)
- [Polymer project site](https://www.polymer-project.org/summit)
- [Polymer summit](https://www.polymer-project.org/summit)
- [Shop](https://shop.polymer-project.org)
- [News](https://news.polymer-project.org)
- [webcomponents.org](https://www.webcomponents.org/)
- [Chrome Status](https://www.chromestatus.com/)
- [Project Fi](https://fi.google.com/about/)
- [NASA Open Source Software](https://code.nasa.gov/)

## Tools and References

- [Polymer App Toolbox](https://www.polymer-project.org/2.0/toolbox/)
- [Material Design Adaptive UI Pattern](https://www.google.com/design/spec/layout/adaptive-ui.html#adaptive-ui-patterns)

## Changes in App Layout 2.0

- Distribution is now done with slots, so things have changed because of that,

  ##### app-drawer-layout
  **1.x**
  ```
  <app-drawer-layout>
    <app-drawer>...</app-drawer>
    <div>content</div>
  </app-drawer-layout>
  ```
  **2.0**
  ```
  <app-drawer-layout>
    <app-drawer slot="drawer">...</app-drawer>
    <div>content</div>
  </app-drawer-layout>
  ```

  ##### app-header-layout
  **1.x**
  ```
  <app-header-layout>
    <app-header>...</app-header>
    <div>content</div>
  </app-header-layout>
  ```
  **2.0**
  ```
  <app-header-layout>
    <app-header slot="header">...</app-header>
    <div>content</div>
  </app-header-layout>
  ```

  ##### app-box
  **1.x**
  ```
  <app-box effects="...">
    <img background ...>
  </app-box>
  ```
  **2.0**
  ```
  <app-box effects="...">
    <img slot="background" ...>
  </app-box>
  ```

- In `app-drawer-layout`, the `drawer-toggle` element needs to be manually hidden
when `app-drawer-layout` is not in narrow layout. To add this, add the following CSS rule where
`app-drawer-layout` is used:

  ```css
  app-drawer-layout:not([narrow]) [drawer-toggle] {
    display: none;
  }
  ```
- In `app-drawer-layout`, if you specify a value for `--app-drawer-width`, that value must be
accessible by both `app-drawer` and `app-drawer-layout`. This can be done by defining the value
on the `:host` that contains <app-drawer-layout> (or `html` if outside a shadow root):

  ```css
  :host {
    --app-drawer-width: 300px;
  }
  ```
- `app-scrollpos-control` has been removed from App Layout in favor of using multiple scrolling regions to preserve the scroll position. In terms of UX, [`document.rootScroller`](https://github.com/bokand/NonDocumentRootScroller) is a new web platform API that will allow non-document scroll to hide the address bar on mobile.
