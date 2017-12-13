## &lt;app-header&gt;

![app-header](http://app-layout-assets.appspot.com/assets/docs/app-header/header.gif)

app-header is container element for app-toolbars at the top of the screen that can have scroll
effects. By default, an app-header moves away from the viewport when scrolling down and
if using `reveals`, the header slides back when scrolling back up. For example:

```html
<app-header reveals>
  <app-toolbar>
    <div main-title>App name</div>
  </app-toolbar>
</app-header>
```

app-header can also condense when scrolling down. To achieve this behavior, the header
must have a larger height than the `sticky` element in the light DOM. For example:

```html
<app-header style="height: 96px;" condenses fixed>
  <app-toolbar style="height: 64px;">
    <div main-title>App name</div>
  </app-toolbar>
</app-header>
```

In this case the header is initially `96px` tall, and it shrinks to `64px` when scrolling down.
That is what is meant by "condensing".

### Sticky element

The element that is positioned fixed to top of the header's `scrollTarget` when a threshold
is reached, similar to `position: sticky` in CSS. This element **must** be an immediate
child of app-header. By default, the `sticky` element is the first `app-toolbar that
is an immediate child of app-header.

```html
<app-header condenses>
  <app-toolbar> Sticky element </app-toolbar>
</app-header>
```

#### Customizing the sticky element

```html
<app-header condenses>
  <app-toolbar></app-toolbar>
  <app-toolbar sticky> Sticky element </app-toolbar>
</app-header>
```

### Scroll target

The app-header's `scrollTarget` property allows to customize the scrollable element to which
the header responds when the user scrolls. By default, app-header uses the document as
the scroll target, but you can customize this property by setting the id of the element, e.g.

```html
<div id="scrollingRegion" style="overflow-y: auto;">
  <app-header scroll-target="scrollingRegion">
  </app-header>
</div>
```

In this case, the `scrollTarget` property points to the outer div element. Alternatively,
you can set this property programmatically:

```js
appHeader.scrollTarget = document.querySelector("#scrollingRegion");
```

## Backgrounds
app-header has two background layers that can be used for styling when the header is condensed
or when the scrollable element is scrolled to the top.

## Scroll effects

Scroll effects are _optional_ visual effects applied in app-header based on scroll position. For example,
The [Material Design scrolling techniques](https://www.google.com/design/spec/patterns/scrolling-techniques.html)
recommends effects that can be installed via the `effects` property. e.g.

```html
<app-header effects="waterfall">
  <app-toolbar>App name</app-toolbar>
</app-header>
```

#### Importing the effects

To use the scroll effects, you must explicitly import them in addition to `app-header`:

```html
<link rel="import" href="/bower_components/app-layout/app-scroll-effects/app-scroll-effects.html">
```

#### List of effects

* **blend-background**
Fades in/out two background elements by applying CSS opacity based on scroll position.
You can use this effect to smoothly change the background color or image of the header.
For example, using the mixin `--app-header-background-rear-layer` lets you assign a different
background when the header is condensed:

```css
app-header {
  background-color: red;
  --app-header-background-rear-layer: {
    /* The header is blue when condensed */
    background-color: blue;
  };
}
```

* **fade-background**
Upon scrolling past a threshold, this effect will trigger an opacity transition to
fade in/out the backgrounds. Compared to the `blend-background` effect,
this effect doesn't interpolate the opacity based on scroll position.

* **parallax-background**
A simple parallax effect that vertically translates the backgrounds based on a fraction
of the scroll position. For example:

```css
app-header {
  --app-header-background-front-layer: {
    background-image: url(...);
  };
}
```
```html
<app-header style="height: 300px;" effects="parallax-background">
  <app-toolbar>App name</app-toolbar>
</app-header>
```

The fraction determines how far the background moves relative to the scroll position.
This value can be assigned via the `scalar` config value and it is typically a value
between 0 and 1 inclusive. If `scalar=0`, the background doesn't move away from the header.

* **resize-title**
Progressively interpolates the size of the title from the element with the `main-title` attribute
to the element with the `condensed-title` attribute as the header condenses. For example:

```html
<app-header condenses reveals effects="resize-title">
  <app-toolbar>
      <h4 condensed-title>App name</h4>
  </app-toolbar>
  <app-toolbar>
      <h1 main-title>App name</h1>
  </app-toolbar>
</app-header>
```

* **resize-snapped-title**
Upon scrolling past a threshold, this effect fades in/out the titles using opacity transitions.
Similarly to `resize-title`, the `main-title` and `condensed-title` elements must be placed in the
light DOM.

* **waterfall**
Toggles the shadow property in app-header to create a sense of depth (as recommended in the
MD spec) between the header and the underneath content. You can change the shadow by
customizing the `--app-header-shadow` mixin. For example:

```css
app-header {
  --app-header-shadow: {
    box-shadow: inset 0px 5px 2px -3px rgba(0, 0, 0, 0.2);
  };
}
```

```html
<app-header condenses reveals effects="waterfall">
  <app-toolbar>
      <h1 main-title>App name</h1>
  </app-toolbar>
</app-header>
```

* **material**
Installs the waterfall, resize-title, blend-background and parallax-background effects.

### Content attributes

Attribute | Description         | Default
----------|---------------------|----------------------------------------
`sticky` | Element that remains at the top when the header condenses. | The first app-toolbar in the light DOM.


## Styling

Mixin | Description | Default
------|-------------|----------
`--app-header-background-front-layer` | Applies to the front layer of the background. | {}
`--app-header-background-rear-layer` | Applies to the rear layer of the background. | {}
`--app-header-shadow` | Applies to the shadow. | {}
