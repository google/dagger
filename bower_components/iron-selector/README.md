[![Build status](https://travis-ci.org/PolymerElements/iron-selector.svg?branch=master)](https://travis-ci.org/PolymerElements/iron-selector)

_[Demo and API docs](https://elements.polymer-project.org/elements/iron-selector)_


## &lt;iron-selector&gt;

  `iron-selector` is an element which can be used to manage a list of elements
  that can be selected.  Tapping on the item will make the item selected.  The `selected` indicates
  which item is being selected.  The default is to use the index of the item.

  Example:

```html
  <iron-selector selected="0">
    <div>Item 1</div>
    <div>Item 2</div>
    <div>Item 3</div>
  </iron-selector>
```

  If you want to use the attribute value of an element for `selected` instead of the index,
  set `attrForSelected` to the name of the attribute.  For example, if you want to select item by
  `name`, set `attrForSelected` to `name`.

  Example:

```html
  <iron-selector attr-for-selected="name" selected="foo">
    <div name="foo">Foo</div>
    <div name="bar">Bar</div>
    <div name="zot">Zot</div>
  </iron-selector>
```

  You can specify a default fallback with `fallbackSelection` in case the `selected` attribute does
  not match the `attrForSelected` attribute of any elements.

  Example:

```html
    <iron-selector attr-for-selected="name" selected="non-existing"
                   fallback-selection="default">
      <div name="foo">Foo</div>
      <div name="bar">Bar</div>
      <div name="default">Default</div>
    </iron-selector>
```

  Note: When the selector is multi, the selection will set to `fallbackSelection` iff
  the number of matching elements is zero.

  `iron-selector` is not styled. Use the `iron-selected` CSS class to style the selected element.

  Example:

```html
  <style>
    .iron-selected {
      background: #eee;
    }
  </style>

  ...

  <iron-selector selected="0">
    <div>Item 1</div>
    <div>Item 2</div>
    <div>Item 3</div>
  </iron-selector>
```

### Notable breaking changes between 1.x and 2.x (hybrid):

#### IronSelectableBehavior

- IronSelectableBehavior no longer updates its list of items synchronously
  when it is connected to avoid triggering a situation introduced in the
  Custom Elements v1 spec that might cause custom element reactions to be
  called later than expected.

  If you are using an element with IronSelectableBehavior and ...
  1. are reading or writing properties of the element that depend on its
     items (`items`, `selectedItems`, etc.)
  1. are performing these accesses after the element is created or connected
    (attached) either **synchronously** or **after a timeout**

  ... you should wait for the element to dispatch an `iron-items-changed`
  event instead.
- `Polymer.dom.flush()` no longer triggers the observer used by
  IronSelectableBehavior to watch for changes to its items. You can call
  `forceSynchronousItemUpdate` instead or, preferably, listen for the
  `iron-items-changed` event.

#### IronMultiSelectableBehavior

- All breaking changes to IronSelectableBehavior listed above apply to
  IronMultiSelectableBehavior.
- `selectedValues` and `selectedItems` now have empty arrays as default
  values. This may cause bindings or observers of these properties to
  trigger at start up when they previously had not.
