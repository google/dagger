## &lt;app-grid&gt;

app-grid is a helper class useful for creating responsive, fluid grid layouts using custom properties.
Because custom properties can be defined inside a `@media` rule, you can customize the grid layout
for different responsive breakpoints.

Example:

Import `app-grid-style.html` and include `app-grid-style` in the style of an element's definition.
Then, add the class `app-grid` to a container such as `ul` or `div`:

```html
<template>
  <style include="app-grid-style">

    :host {
      --app-grid-columns: 3;
      --app-grid-item-height: 100px;
    }

    ul {
      padding: 0;
      list-style: none;
    }

    .item {
      background-color: white;
    }

    @media (max-width: 640px) {
      :host {
        --app-grid-columns: 1;
      }
    }

  </style>
  <ul class="app-grid">
    <li class="item">1</li>
    <li class="item">2</li>
    <li class="item">3</li>
  </ul>
</template>
```

In this example, the grid  will take 3 columns per row and only 1 column if the viewport width is
smaller than 640px.

### Expandible items

In many cases, it's useful to expand an item more than 1 column. To achieve this type of layout,
you can specify the number of columns the item should expand to by setting the custom property
`--app-grid-expandible-item-columns`. To indicate which item should expand, apply the mixin
`--app-grid-expandible-item` to a rule with a selector to the item. For example:

```html
<template>
  <style include="app-grid-style">
    :host {
      --app-grid-columns: 3;
      --app-grid-item-height: 100px;
      --app-grid-expandible-item-columns: 3;
    }

    ul {
      padding: 0;
      list-style: none;
    }

    /* Only the first item should expand */
    .item:first-child {
      @apply --app-grid-expandible-item;
    }
  </style>
</template>
```

### Preserving the aspect ratio

When the size of a grid item should preserve the aspect ratio, you can add the `has-aspect-ratio`
attribute to the element with the class `app-grid`. Now, every item element becomes a wrapper around
the item content. For example:

```html
<template>
  <style include="app-grid-style">

    :host {
      --app-grid-columns: 3;
      /* 50% the width of the item is equivalent to 2:1 aspect ratio*/
      --app-grid-item-height: 50%;
    }

    ul {
      padding: 0;
      list-style: none;
    }

    .item {
      background-color: white;
    }

  </style>
  <ul class="app-grid" has-aspect-ratio>
    <li class="item">
      <div>item 1</div>
    </li>
    <li class="item">
      <div>item 2</div>
    </li>
    <li class="item">
      <div>item 3</div>
    </li>
  </ul>
</template>
```

### Styling

Custom property                               | Description                                                | Default
----------------------------------------------|------------------------------------------------------------|------------------
`--app-grid-columns`                          | The number of columns per row.                             | 1
`--app-grid-gutter`                           | The space between two items.                               | 0px
`--app-grid-item-height`                      | The height of the items.                                   | auto
`--app-grid-expandible-item-columns`          | The number of columns an expandible item should expand to. | 1

### CSS grid layout

[CSS grid layout](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Grid_Layout) is a new layout system for CSS that lets you create complex grids. It's richer than app-grid in many cases, but it's only supported in a [few browsers](http://caniuse.com/#search=css%20grid%20layout).
