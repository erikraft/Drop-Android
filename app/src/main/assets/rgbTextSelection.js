(function () {
  'use strict';

  const root = document.documentElement;
  if (!root
      || document.querySelector('link[href*="text-selection.css"]')
      || document.querySelector('style[data-erikraft-rgb-selection]')) {
    return;
  }

  const style = document.createElement('style');
  style.setAttribute('data-erikraft-rgb-selection', '');
  style.textContent = `/* ErikrafT RGB Rainbow Text Selection: preserve native selection without outlines, borders, overlays, or shadows. */
@property --erikraft-selection-hue {
  syntax: "<number>";
  inherits: true;
  initial-value: 0;
}

:root {
  --erikraft-selection-hue: 0;
  animation: erikraft-selection-rainbow 3.6s linear infinite;
}

@keyframes erikraft-selection-rainbow {
  from { --erikraft-selection-hue: 0; }
  to { --erikraft-selection-hue: 360; }
}

::selection {
  color: #ffffff;
  background-color: hsla(var(--erikraft-selection-hue), 100%, 50%, 0.72);
}

::-moz-selection {
  color: #ffffff;
  background-color: hsla(var(--erikraft-selection-hue), 100%, 50%, 0.72);
}

@media (prefers-reduced-motion: reduce) {
  :root {
    animation: none;
    --erikraft-selection-hue: 330;
  }
}`;
  (document.head || root).appendChild(style);
})();
