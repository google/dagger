[![Build status](https://travis-ci.org/PolymerElements/iron-form-element-behavior.svg?branch=master)](https://travis-ci.org/PolymerElements/iron-form-element-behavior)

_[Demo and API docs](https://elements.polymer-project.org/elements/iron-form-element-behavior)_


## Polymer.IronFormElementBehavior

Polymer.IronFormElementBehavior enables a custom element to be included
in an `iron-form`.

### Changes in 2.0

If your form element is used with Polymer 2.0, it doesn't need to implement `Polymer.IronFormElementBehavior` as `iron-form#2.0.0` won't rely on it.
The events `iron-form-element-register` and `iron-form-element-unregister` are not fired on Polymer 2.0.
