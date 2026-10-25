(function () {
  'use strict';

  const root = document.documentElement;
  if (!root || document.querySelector('link[href*="text-selection.css"]') || document.querySelector('style[data-erikraft-rgb-selection]')) {
    return;
  }

  const style = document.createElement('style');
  style.setAttribute('data-erikraft-rgb-selection', '');
  style.textContent = "/* ErikrafT RGB Rainbow Text Selection: preserve native selection without outlines, borders, overlays, or shadows. */\n@property --erikraft-selection-hue {\n  syntax: \"<number>\";\n  inherits: true;\n  initial-value: 0;\n}\n\n:root {\n  --erikraft-selection-hue: 0;\n  animation: erikraft-selection-rainbow 3.6s linear infinite;\n}\n\n@keyframes erikraft-selection-rainbow {\n  from { --erikraft-selection-hue: 0; }\n  to { --erikraft-selection-hue: 360; }\n}\n\n::selection {\n  color: #ffffff;\n  background-color: hsla(var(--erikraft-selection-hue), 100%, 50%, 0.72);\n}\n\n::-moz-selection {\n  color: #ffffff;\n  background-color: hsla(var(--erikraft-selection-hue), 100%, 50%, 0.72);\n}\n\n@media (prefers-reduced-motion: reduce) {\n  :root {\n    animation: none;\n    --erikraft-selection-hue: 330;\n  }\n}";
  (document.head || root).appendChild(style);
})();
