## &lt;app-drawer&gt;

![app-drawer](http://app-layout-assets.appspot.com/assets/docs/app-drawer/drawer.gif)

app-drawer is a navigation drawer that can slide in from the left or right.

Example:

Align the drawer at the start, which is left in LTR layouts (default):

```html
<app-drawer opened></app-drawer>
```

Align the drawer at the end:

```html
<app-drawer align="end" opened></app-drawer>
```

To make the contents of the drawer scrollable, create a wrapper for the scroll
content, and apply height and overflow styles to it.

```html
<app-drawer>
  <div style="height: 100%; overflow: auto;"></div>
</app-drawer>
```

### Styling

Custom property                  | Description                            | Default
---------------------------------|----------------------------------------|--------------------
`--app-drawer-width`             | Width of the drawer                    | 256px
`--app-drawer-content-container` | Mixin for the drawer content container | {}
`--app-drawer-scrim-background`  | Background for the scrim               | rgba(0, 0, 0, 0.5)

**NOTE:** If you use <app-drawer> with <app-drawer-layout> and specify a value for
`--app-drawer-width`, that value must be accessible by both elements. This can be done by
defining the value on the `:host` that contains <app-drawer-layout> (or `html` if outside
a shadow root):

```css
:host {
  --app-drawer-width: 300px;
}
```
