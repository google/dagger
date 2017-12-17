## &lt;app-drawer-layout&gt;

app-drawer-layout is a wrapper element that positions an app-drawer and other content. When
the viewport width is smaller than `responsiveWidth`, this element changes to narrow layout.
In narrow layout, the drawer will be stacked on top of the main content. The drawer will slide
in/out to hide/reveal the main content.


By default the drawer is aligned to the start, which is left in LTR layouts:

```html
<app-drawer-layout>
  <app-drawer slot="drawer">
    drawer content
  </app-drawer>
  <div>
    main content
  </div>
</app-drawer-layout>
```

Align the drawer at the end:

```html
<app-drawer-layout>
  <app-drawer slot="drawer" align="end">
     drawer content
  </app-drawer>
  <div>
    main content
  </div>
</app-drawer-layout>
```

With an app-header-layout:

```html
<app-drawer-layout>
  <app-drawer slot="drawer">
    drawer-content
  </app-drawer>
  <app-header-layout>
    <app-header slot="header">
      <app-toolbar>
        <div main-title>App name</div>
      </app-toolbar>
    </app-header>

    main content

  </app-header-layout>
</app-drawer-layout>
```

Add the `drawer-toggle` attribute to elements inside `app-drawer-layout` that toggle the drawer on click events:

```html
<app-drawer-layout>
  <app-drawer slot="drawer">
    drawer-content
  </app-drawer>
  <app-header-layout>
    <app-header slot="header">
      <app-toolbar>
        <paper-icon-button icon="menu" drawer-toggle></paper-icon-button>
        <div main-title>App name</div>
      </app-toolbar>
    </app-header>

    main content

  </app-header-layout>
</app-drawer-layout>
```

**NOTE:** With app-layout 2.0, the `drawer-toggle` element needs to be manually hidden
when app-drawer-layout is not in narrow layout. To add this, add the following CSS rule where
app-drawer-layout is used:

```css
app-drawer-layout:not([narrow]) [drawer-toggle] {
  display: none;
}
```

Add the `fullbleed` attribute to app-drawer-layout to make it fit the size of its container:

```html
<app-drawer-layout fullbleed>
  <app-drawer slot="drawer">
     drawer content
  </app-drawer>
  <div>
    main content
  </div>
</app-drawer-layout>
```

### Styling

Custom property                          | Description                          | Default
-----------------------------------------|--------------------------------------|---------
`--app-drawer-width`                     | Width of the drawer                  | 256px
`--app-drawer-layout-content-transition` | Transition for the content container | none

**NOTE:** If you use <app-drawer> with <app-drawer-layout> and specify a value for
`--app-drawer-width`, that value must be accessible by both elements. This can be done by
defining the value on the `:host` that contains <app-drawer-layout> (or `html` if outside
a shadow root):

```css
:host {
  --app-drawer-width: 300px;
}
```
