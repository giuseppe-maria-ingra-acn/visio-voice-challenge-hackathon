// Inserisce visiovoice.js nella pagina.
//
// Nessuna logica qui: una sola copia, servita da Spring. Una copia duplicata nella
// cartella dell'estensione andrebbe fuori sincrono nel giro di un'ora.
//
// Gira nel contesto della pagina, ed e' la ragione per cui visiovoice.js non usa
// nessuna API chrome.*: sarebbero indisponibili proprio qui.
(function () {
  var s = document.createElement('script');
  s.src = 'http://' + location.host + '/visiovoice.js';
  document.documentElement.appendChild(s);
})();
