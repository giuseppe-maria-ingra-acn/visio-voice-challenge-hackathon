/*
 * VisioVoice — il livello accessibile che ripara la pagina del servizio dall'interno.
 *
 * Tre sole responsabilita': LEGGE il DOM, CHIAMA il backend, INIETTA il risultato.
 * Nessuna logica di dominio qui dentro: niente validazione di IBAN, niente composizione
 * di frasi di contenuto, niente importi. Quella parte sta in Java, e' testata e ha il gate
 * di provenance sopra. Una seconda implementazione in JavaScript sarebbe una seconda
 * verita', non verificata da nulla.
 *
 * Ogni intervento e' ADDITIVO o IN-PLACE, mai sostitutivo: Marco compila il form del sito
 * e il protocollo che sente e' la risposta del sito al suo invio. Se sostituissimo un
 * controllo della pagina, quel protocollo tornerebbe a essere un numero inventato da noi.
 *
 * Una logica, due veicoli, senza una riga di differenza:
 *   - <script src="/visiovoice.js"> incluso dalla pagina (percorso della demo);
 *   - content script MV3 (extension/loader.js inserisce questo stesso file nella pagina).
 * Per questo NON esiste nessun riferimento alle API dell'estensione (il namespace che il
 * browser espone solo ai content script): nel secondo veicolo
 * questo codice gira nel contesto della pagina, dove quelle API non esistono. Le chiamate
 * all'API sono same-origin, quindi non servono.
 *
 * Su NVDA non c'e' niente da integrare: un <table> vero, una <label for> e una regione
 * aria-live SONO l'integrazione. Funzionano anche con JAWS, VoiceOver e Narrator.
 */
(function () {
  'use strict';

  // ────────────────────────────────────────────────────────────────────────────
  // 0. Una sola istanza
  // La pagina include lo script E l'estensione puo' inserirlo di nuovo: due copie
  // significherebbero quattro regioni live e due annunci per ogni evento.
  // ────────────────────────────────────────────────────────────────────────────
  if (window.__visiovoiceAttivo) { return; }
  window.__visiovoiceAttivo = true;

  var VERSIONE = '0.1.0';
  var API = '/api';

  /*
   * La pagina COME IL SERVIZIO L'HA CONSEGNATA, catturata prima di qualunque iniezione.
   * E' questo che viene mandato a /api/perceive: se spedissimo il DOM riparato, la
   * percezione non vedrebbe piu' le barriere e il racconto descriverebbe il nostro lavoro
   * invece della pagina.
   */
  var HTML_SORGENTE = document.documentElement.outerHTML;

  /*
   * COPIE DI SERVIZIO di it.visiovoice.model.Provenance (italianLabel(), spokenDisclaimer()).
   * La fonte di verita' resta il Java. Qui servono solo quando non c'e' un segmentId da
   * interrogare: appena /api/provenance/... risponde, queste voci vengono sovrascritte con
   * le sue (vedi memorizzaEtichetta).
   */
  var ETICHETTA_PROVENIENZA = {
    SOURCE_VERBATIM: 'letto dalla pagina',
    AI_REPHRASED: "riformulato dall'AI, verificato sulla fonte",
    AI_INFERRED: "dedotto dall'AI, da verificare",
    HUMAN_REVIEWED: 'verificato da una persona'
  };
  var AVVERTENZA_DEDOTTO = "Attenzione: questa parte l'ho dedotta io, conviene verificarla.";

  // Scorciatoie a un tasto: Marco ascolta a 380 parole al minuto, i menu lo rallentano.
  var COMANDI = [
    ['D', 'descrivi questa schermata'],
    ['N', 'passo successivo'],
    ['B', 'passo precedente'],
    ['R', "ripeti l'ultima frase"],
    ['P', 'da dove viene questa informazione'],
    ['?', 'questo elenco dei comandi']
  ];

  // alt che non trasmettono niente. Il giudizio vero e' VisualAsset.altIsUseless() in Java:
  // questa e' l'euristica locale usata finche' la percezione non risponde.
  var ALT_MUTI = ['', 'immagine', 'image', 'img', 'grafico', 'graph', 'chart', 'foto',
                  'photo', 'icona', 'icon', 'logo', 'tabella', 'spacer', 'banner'];

  var stato = {
    voceAttiva: true,
    sessionId: null,
    ultimaFrase: '',
    ultimaOrigine: null,
    ultimoScript: null,
    livelloChiesto: null,
    etichettaGiaDetta: {},
    ultimoAllarme: '',
    ultimoAllarmeQuando: 0,
    controlloDatiAvvisato: false
  };

  var regioneCalma = null;     // aria-live="polite"  : narrazione, avanzamento, conferme
  var regioneUrgente = null;   // role="alert"        : errori
  var pannello = null;         // specchio visivo, aria-hidden: serve al pubblico, non a Marco

  // ────────────────────────────────────────────────────────────────────────────
  // 1. Utilita' DOM — createElement e textContent, mai HTML costruito come stringa
  // Il backend compone frasi a partire dal contenuto della pagina, e quel contenuto non lo
  // controlliamo: assemblare HTML a stringa significherebbe eseguire cio' che ci arriva.
  // ────────────────────────────────────────────────────────────────────────────

  function nuovo(tag, classe, testo) {
    var n = document.createElement(tag);
    if (classe) { n.className = classe; }
    if (testo !== undefined && testo !== null) { n.textContent = String(testo); }
    return n;
  }

  function testoDi(nodo) {
    return nodo && nodo.textContent ? nodo.textContent.replace(/\s+/g, ' ').trim() : '';
  }

  function visibile(nodo) {
    if (!nodo || !nodo.getClientRects) { return false; }
    return !!(nodo.offsetWidth || nodo.offsetHeight || nodo.getClientRects().length);
  }

  function nostro(nodo) {
    return !!(nodo && nodo.closest && nodo.closest('[data-vv]'));
  }

  function plurale(n, singolare, plurale) {
    return n + ' ' + (n === 1 ? singolare : plurale);
  }

  function etichettaProvenienza(livello) {
    return ETICHETTA_PROVENIENZA[livello] || 'origine non dichiarata';
  }

  function memorizzaEtichetta(livello, etichetta) {
    if (livello && etichetta) { ETICHETTA_PROVENIENZA[livello] = etichetta; }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 2. Le due regioni live, iniettate una volta sola
  // Distinte perche' un errore urgente non deve restare in coda dietro una descrizione
  // lunga. Si aggiorna il textContent del nodo che esiste: ricrearlo non produce annuncio.
  // ────────────────────────────────────────────────────────────────────────────

  function preparaInterfaccia() {
    var foglio = document.createElement('link');
    foglio.rel = 'stylesheet';
    foglio.href = '/visiovoice.css';
    foglio.setAttribute('data-vv', 'css');
    document.head.appendChild(foglio);

    // Le regole indispensabili anche se il foglio non arriva: senza .vv-solo-lettore il
    // testo destinato alla sintesi diventa un blocco visibile in mezzo alla pagina.
    var stile = nuovo('style');
    stile.setAttribute('data-vv', 'css-minimo');
    stile.textContent = '.vv-solo-lettore{position:absolute!important;width:1px;height:1px;'
      + 'margin:-1px;padding:0;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;border:0}';
    document.head.appendChild(stile);

    var radice = nuovo('div', 'vv-radice');
    radice.setAttribute('data-vv', 'radice');

    preparaVoce();
    regioneCalma = nuovo('div', 'vv-solo-lettore');
    regioneCalma.id = 'vv-annuncio';
    regioneCalma.setAttribute('role', 'status');
    regioneCalma.setAttribute('aria-live', 'polite');
    regioneCalma.setAttribute('aria-atomic', 'true');

    regioneUrgente = nuovo('div', 'vv-solo-lettore');
    regioneUrgente.id = 'vv-allerta';
    regioneUrgente.setAttribute('role', 'alert');
    regioneUrgente.setAttribute('aria-live', 'assertive');
    regioneUrgente.setAttribute('aria-atomic', 'true');

    radice.appendChild(regioneCalma);
    radice.appendChild(regioneUrgente);

    // Specchio visivo: tutto aria-hidden, cosi' il lettore di schermo non lo legge due volte.
    pannello = nuovo('div', 'vv-pannello');
    pannello.setAttribute('aria-hidden', 'true');
    pannello.appendChild(nuovo('p', 'vv-pannello-titolo', 'VisioVoice ' + VERSIONE + ' — premi ? per i comandi'));
    pannello.appendChild(nuovo('p', 'vv-pannello-frase', ''));
    radice.appendChild(pannello);

    document.body.appendChild(radice);
  }

  function scrivi(regione, frase, origine) {
    if (!regione || !frase) { return; }
    stato.ultimaFrase = frase;
    stato.ultimaOrigine = origine || { locale: "Questa frase e' dell'interfaccia VisioVoice: "
      + "non viene dal servizio." };
    if (pannello) {
      var riga = pannello.querySelector('.vv-pannello-frase');
      if (riga) { riga.textContent = frase; }
    }
    // Il passaggio per la stringa vuota fa ri-annunciare anche un testo identico al
    // precedente (serve al comando "ripeti"), senza sostituire il nodo.
    regione.textContent = '';
    var token = (regione.__vvToken || 0) + 1;
    regione.__vvToken = token;
    window.setTimeout(function () {
      if (regione.__vvToken === token) { regione.textContent = frase; }
    }, 60);
  }

  // ── Voce ──────────────────────────────────────────────────────────────────
  // Le regioni aria-live sono il canale vero: uno screen reader le legge, ed e' quello
  // che Marco usa davvero perche' e' configurato come vuole lui. Ma su una macchina
  // senza screen reader restano MUTE, e chi guarda la demo non sente niente.
  // Questa sintesi e' quindi un extra, non il canale principale, e si puo' spegnere.

  var vocePronta = false;
  var voceItaliana = null;

  function preparaVoce() {
    if (!('speechSynthesis' in window)) { return; }
    var scegli = function () {
      var voci = window.speechSynthesis.getVoices() || [];
      for (var i = 0; i < voci.length; i++) {
        if (voci[i].lang && voci[i].lang.toLowerCase().indexOf('it') === 0) {
          voceItaliana = voci[i];
          break;
        }
      }
      vocePronta = true;
    };
    scegli();
    // Su Chrome l'elenco delle voci arriva in ritardo: senza questo si parla in inglese.
    window.speechSynthesis.onvoiceschanged = scegli;
  }

  function pronuncia(frase, urgente) {
    if (!stato.voceAttiva || !('speechSynthesis' in window) || !frase) { return; }
    if (urgente) { window.speechSynthesis.cancel(); }
    var u = new window.SpeechSynthesisUtterance(frase);
    u.lang = 'it-IT';
    if (voceItaliana) { u.voice = voceItaliana; }
    u.rate = 1.15;   // Marco ascolta molto piu' veloce; per chi guarda resta comprensibile
    window.speechSynthesis.speak(u);
  }

  function zittisci() {
    if ('speechSynthesis' in window) { window.speechSynthesis.cancel(); }
  }

  function annuncia(frase, origine) {
    scrivi(regioneCalma, frase, origine);
    pronuncia(frase, false);
  }

  function allarme(frase, origine) {
    scrivi(regioneUrgente, frase, origine);
    pronuncia(frase, true);
  }

  function allarmeUnaVolta(frase, origine) {
    var adesso = Date.now();
    if (frase === stato.ultimoAllarme && adesso - stato.ultimoAllarmeQuando < 1500) { return; }
    stato.ultimoAllarme = frase;
    stato.ultimoAllarmeQuando = adesso;
    allarme(frase, origine);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 3. Rete — same-origin in entrambi i veicoli
  // Ogni risposta porta con se' una frase pronunciabile: quando il backend ne manda una
  // (ErrorResponse.spokenMessage) si usa la sua, che e' stata scritta e rivista in Java.
  // ────────────────────────────────────────────────────────────────────────────

  function chiediApi(metodo, percorso, corpo) {
    var opzioni = { method: metodo, headers: { Accept: 'application/json' } };
    if (corpo !== undefined) {
      opzioni.headers['Content-Type'] = 'application/json';
      opzioni.body = JSON.stringify(corpo);
    }
    return fetch(API + percorso, opzioni).then(function (risposta) {
      return risposta.text().then(function (grezzo) {
        var dati = null;
        try { dati = grezzo ? JSON.parse(grezzo) : null; } catch (e) { dati = null; }
        var frase = '';
        if (dati && typeof dati.spokenMessage === 'string' && dati.spokenMessage) {
          frase = dati.spokenMessage;
        } else if (!risposta.ok) {
          frase = 'Il servizio ha risposto con un errore ' + risposta.status
            + ' senza spiegarlo a parole. Preferisco dirtelo che inventare una risposta.';
        }
        return { ok: risposta.ok, stato: risposta.status, dati: dati, frase: frase };
      });
    }).catch(function () {
      return { ok: false, stato: 0, dati: null,
        frase: 'Non riesco a contattare il servizio VisioVoice. '
          + 'La pagina resta quella del servizio: continuo a leggerti solo cio' + "' "
          + 'che trovo nel suo contenuto.' };
    });
  }

  function apriSessione() {
    var salvata = null;
    try { salvata = window.sessionStorage.getItem('vv-sessione'); } catch (e) { salvata = null; }
    var riusa = salvata
      ? chiediApi('GET', '/session/' + encodeURIComponent(salvata)).then(function (r) {
          return r.ok ? salvata : null;
        })
      : Promise.resolve(null);
    return riusa.then(function (id) {
      if (id) { return id; }
      return chiediApi('POST', '/session', { scenarioId: null, level: 'STANDARD' })
        .then(function (r) {
          if (r.ok && r.dati && r.dati.sessionId) {
            try { window.sessionStorage.setItem('vv-sessione', r.dati.sessionId); } catch (e) { /* ok */ }
            return r.dati.sessionId;
          }
          return null;
        });
    });
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 4. Lettura del DOM
  // ────────────────────────────────────────────────────────────────────────────

  function altMuto(immagine) {
    if (!immagine.hasAttribute('alt')) { return true; }
    var alt = (immagine.getAttribute('alt') || '').trim().toLowerCase();
    if (ALT_MUTI.indexOf(alt) >= 0) { return true; }
    // Un alt di due parole generiche ("tabella importi") non trasmette nessun dato:
    // descrive il contenitore, non il contenuto.
    return alt.split(/\s+/).length <= 2 && /tabella|grafico|immagine|figura|schema/.test(alt);
  }

  function immaginiMute() {
    var risultato = [];
    var tutte = document.querySelectorAll('img');
    for (var i = 0; i < tutte.length; i++) {
      var immagine = tutte[i];
      if (nostro(immagine)) { continue; }
      if (immagine.getAttribute('aria-hidden') === 'true') { continue; }
      if (altMuto(immagine)) { risultato.push(immagine); }
    }
    return risultato;
  }

  function indicatoreDiPasso(immagine) {
    return /step|passo|indicator|progress|wizard/i.test(immagine.getAttribute('src') || '');
  }

  function nomeAccessibileDi(campo) {
    if (!campo) { return ''; }
    var aria = (campo.getAttribute('aria-label') || '').trim();
    if (aria) { return aria; }
    var riferimento = campo.getAttribute('aria-labelledby');
    if (riferimento) {
      var pezzi = [];
      riferimento.split(/\s+/).forEach(function (id) {
        var nodo = document.getElementById(id);
        if (nodo) { pezzi.push(testoDi(nodo)); }
      });
      if (pezzi.join(' ').trim()) { return pezzi.join(' ').trim(); }
    }
    if (campo.labels && campo.labels.length) {
      var dalleLabel = [];
      for (var i = 0; i < campo.labels.length; i++) { dalleLabel.push(testoDi(campo.labels[i])); }
      if (dalleLabel.join(' ').trim()) { return dalleLabel.join(' ').trim(); }
    }
    var titolo = (campo.getAttribute('title') || '').trim();
    if (titolo) { return titolo; }
    return '';
  }

  function campiDaNominare() {
    var risultato = [];
    var tutti = document.querySelectorAll('input, select, textarea');
    for (var i = 0; i < tutti.length; i++) {
      var campo = tutti[i];
      if (nostro(campo)) { continue; }
      var tipo = (campo.getAttribute('type') || '').toLowerCase();
      if (tipo === 'hidden' || tipo === 'submit' || tipo === 'button' || tipo === 'reset') { continue; }
      if (!visibile(campo)) { continue; }
      // I campi che abbiamo gia' nominato noi restano in lista: il nome di ripiego preso
      // dagli attributi va sostituito da quello del servizio appena il servizio risponde.
      if (nomeAccessibileDi(campo) && campo.getAttribute('data-vv-riparato') !== 'b2') { continue; }
      risultato.push(campo);
    }
    return risultato;
  }

  /*
   * Il nome di ripiego viene dall'attributo name (o id) della pagina, ripulito solo
   * meccanicamente: "cf_richiedente" -> "cf richiedente". Nessuna traduzione, nessuna
   * interpretazione: quello che la pagina scrive, non quello che immaginiamo significhi.
   */
  function nomeDagliAttributi(campo) {
    var grezzo = (campo.getAttribute('name') || campo.id || '').trim();
    if (!grezzo) { return ''; }
    return grezzo.replace(/[_\-.]+/g, ' ').replace(/\s+/g, ' ').trim();
  }

  function passoDalContenuto() {
    var fonti = [document.title];
    var intestazioni = document.querySelectorAll('h1, h2, h3');
    for (var i = 0; i < intestazioni.length; i++) {
      if (!nostro(intestazioni[i])) { fonti.push(testoDi(intestazioni[i])); }
    }
    for (var j = 0; j < fonti.length; j++) {
      var trovato = /passo\s+(\d+)\s*(?:di|su|\/)\s*(\d+)/i.exec(fonti[j] || '');
      if (trovato) { return { passo: Number(trovato[1]), totale: Number(trovato[2]) }; }
    }
    for (var k = 0; k < fonti.length; k++) {
      var solo = /passo\s+(\d+)/i.exec(fonti[k] || '');
      if (solo) { return { passo: Number(solo[1]), totale: 0 }; }
    }
    return null;
  }

  function descrizioneLocale() {
    var intestazioni = document.querySelectorAll('h1, h2, h3, h4, h5, h6').length;
    var campi = campiVisibili().length;
    var tabelle = document.querySelectorAll('table:not([data-vv])').length;
    var mute = immaginiMute().length;
    var frase = 'Titolo della pagina: ' + (document.title || 'senza titolo') + '. Leggo '
      + plurale(intestazioni, 'intestazione', 'intestazioni') + ', '
      + plurale(campi, 'campo da compilare', 'campi da compilare') + ', '
      + plurale(tabelle, 'tabella', 'tabelle');
    if (mute) { frase += ' e ' + plurale(mute, 'immagine senza descrizione', 'immagini senza descrizione'); }
    return frase + '.';
  }

  function campiVisibili() {
    var risultato = [];
    var tutti = document.querySelectorAll('input, select, textarea');
    for (var i = 0; i < tutti.length; i++) {
      var campo = tutti[i];
      var tipo = (campo.getAttribute('type') || '').toLowerCase();
      if (nostro(campo) || tipo === 'hidden' || !visibile(campo)) { continue; }
      risultato.push(campo);
    }
    return risultato;
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 5. b1 — dati solo in immagine
  // Intervento giusto: inserire ACCANTO all'immagine un <table> vero con <th scope>,
  // coi dati che arrivano dall'API, e mettere aria-hidden sull'immagine.
  // Una tabella raccontata a parole si puo' ascoltare ma non consultare.
  // ────────────────────────────────────────────────────────────────────────────

  function sembraTabella(oggetto) {
    if (!oggetto || typeof oggetto !== 'object') { return false; }
    if (!Array.isArray(oggetto.headers) || !Array.isArray(oggetto.rows)) { return false; }
    if (!oggetto.headers.length || !oggetto.rows.length) { return false; }
    return oggetto.rows.every(function (riga) { return Array.isArray(riga); });
  }

  /*
   * Cerca strutture di tipo AccessibleTable dovunque siano nella risposta, senza dipendere
   * dal punto esatto in cui il backend le espone (oggi PerceiveResponse non ha un campo
   * dedicato: quando l'avra', questa funzione le trova senza modifiche qui).
   */
  function tabelleNella(risposta) {
    var trovate = [];
    function scendi(nodo, profondita) {
      if (!nodo || typeof nodo !== 'object' || profondita > 8) { return; }
      if (Array.isArray(nodo)) {
        for (var i = 0; i < nodo.length; i++) { scendi(nodo[i], profondita + 1); }
        return;
      }
      if (sembraTabella(nodo)) { trovate.push(nodo); }
      var chiavi = Object.keys(nodo);
      for (var k = 0; k < chiavi.length; k++) { scendi(nodo[chiavi[k]], profondita + 1); }
    }
    scendi(risposta, 0);
    return trovate;
  }

  function costruisciTabella(dati) {
    var blocco = nuovo('div', 'vv-blocco');
    blocco.setAttribute('data-vv', 'b1');

    var tabella = nuovo('table', 'vv-tabella');
    var didascalia = nuovo('caption');
    didascalia.textContent = (dati.caption || 'Dati contenuti nell’immagine')
      + ' — tabella aggiunta da VisioVoice, ' + etichettaProvenienza(dati.provenance) + '.';
    tabella.appendChild(didascalia);

    var testa = nuovo('thead');
    var rigaTesta = nuovo('tr');
    dati.headers.forEach(function (intestazione) {
      var cella = nuovo('th', null, intestazione);
      cella.setAttribute('scope', 'col');
      rigaTesta.appendChild(cella);
    });
    testa.appendChild(rigaTesta);
    tabella.appendChild(testa);

    var corpo = nuovo('tbody');
    dati.rows.forEach(function (riga) {
      var tr = nuovo('tr');
      riga.forEach(function (valore, colonna) {
        // Prima colonna come <th scope="row">: e' l'intestazione di riga, ed e' cio' che
        // permette a NVDA di dire "fascia ISEE ... importo ..." spostandosi per colonne.
        var cella = nuovo(colonna === 0 ? 'th' : 'td', null, valore);
        if (colonna === 0) { cella.setAttribute('scope', 'row'); }
        tr.appendChild(cella);
      });
      corpo.appendChild(tr);
    });
    tabella.appendChild(corpo);
    blocco.appendChild(tabella);
    return blocco;
  }

  function iniettaTabelle(tabelle) {
    var bersagli = immaginiMute().filter(function (immagine) { return !indicatoreDiPasso(immagine); });
    if (!bersagli.length) { return false; }
    var immagine = bersagli[0];
    if (immagine.getAttribute('data-vv-riparato') === 'b1') { return false; }

    var dopo = immagine;
    var frasi = [];
    tabelle.forEach(function (dati) {
      var blocco = costruisciTabella(dati);
      if (dopo.parentNode) { dopo.parentNode.insertBefore(blocco, dopo.nextSibling); }
      dopo = blocco;
      frasi.push((dati.caption || 'tabella') + ', '
        + plurale(dati.rows.length, 'riga', 'righe') + ', '
        + plurale(dati.headers.length, 'colonna', 'colonne'));
      if (dati.provenance === 'AI_INFERRED') { frasi.push(AVVERTENZA_DEDOTTO); }
    });

    // Solo ora l'immagine si puo' togliere dall'albero di accessibilita': l'alternativa
    // vera esiste. Nasconderla prima avrebbe cancellato anche l'unico indizio che qui
    // c'erano dei dati.
    immagine.setAttribute('aria-hidden', 'true');
    immagine.setAttribute('data-vv-riparato', 'b1');
    var avviso = document.querySelector('[data-vv="b1-mancante"]');
    if (avviso && avviso.parentNode) { avviso.parentNode.removeChild(avviso); }

    annuncia('Ho messo accanto all’immagine una tabella vera, navigabile per righe e '
      + 'colonne: ' + frasi.join('. ') + '.', { locale: 'Tabella costruita con i dati '
      + 'restituiti dal servizio, non letti dall’immagine.' });
    return true;
  }

  function segnalaDatiMancanti(motivo) {
    var bersagli = immaginiMute().filter(function (immagine) { return !indicatoreDiPasso(immagine); });
    if (!bersagli.length) { return; }
    var immagine = bersagli[0];
    if (immagine.getAttribute('data-vv-riparato') === 'b1') { return; }
    if (document.querySelector('[data-vv="b1-mancante"]')) { return; }

    var frase = 'VisioVoice: questa immagine contiene dati che non sono nel testo della pagina. '
      + 'Il servizio non me li ha forniti, e non li invento.' + (motivo ? ' ' + motivo : '');
    var avviso = nuovo('p', 'vv-avviso', frase);
    avviso.setAttribute('data-vv', 'b1-mancante');
    if (immagine.parentNode) { immagine.parentNode.insertBefore(avviso, immagine.nextSibling); }
    // L'immagine resta nell'albero di accessibilita': senza alternativa, nasconderla
    // trasformerebbe una barriera in un silenzio.
    annuncia(frase, { locale: 'Constatazione dell’interfaccia: nessun dato ricevuto per '
      + 'questa immagine.' });
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 6. b2 — input senza nome accessibile
  // Intervento giusto: dare un nome all'input ESISTENTE. Un input nuovo romperebbe il
  // submit della pagina, e con esso l'unica cosa che rende onesto questo prodotto.
  // ────────────────────────────────────────────────────────────────────────────

  function riparaCampiSenzaNome(campiApi) {
    var perId = {};
    (campiApi || []).forEach(function (campo) {
      if (campo && campo.id) { perId[campo.id] = campo; }
    });

    campiDaNominare().forEach(function (campo) {
      var chiave = campo.id || campo.getAttribute('name') || '';
      var dalServizio = perId[chiave] || null;
      var nome = dalServizio && dalServizio.label ? dalServizio.label : nomeDagliAttributi(campo);
      if (!nome) { return; }
      var provenienza = dalServizio && dalServizio.labelProvenance
        ? dalServizio.labelProvenance
        : 'AI_INFERRED';

      if (campo.id) {
        // Una <label for> vera: la vede il lettore di schermo, si vede sullo schermo, ed e'
        // cliccabile. Non tocca l'input, gli si affianca.
        var esistente = document.querySelector('label[data-vv="b2"][for="' + campo.id + '"]');
        var etichetta = esistente || nuovo('label', 'vv-etichetta');
        etichetta.setAttribute('data-vv', 'b2');
        etichetta.setAttribute('for', campo.id);
        etichetta.textContent = nome;
        if (!esistente && campo.parentNode) { campo.parentNode.insertBefore(etichetta, campo); }
      } else {
        campo.setAttribute('aria-label', nome);
      }
      campo.setAttribute('data-vv-riparato', 'b2');

      if (stato.etichettaGiaDetta[chiave] === nome) { return; }
      stato.etichettaGiaDetta[chiave] = nome;
      var spiegazione = dalServizio && dalServizio.label
        ? 'Il nome viene dalla scheda del servizio.'
        : "Il nome l'ho ricavato dall'attributo che la pagina usa per questo campo.";
      var frase = 'Questo modulo aveva un campo senza nome: adesso si chiama "' + nome + '". '
        + spiegazione;
      if (provenienza === 'AI_INFERRED') { frase += ' ' + AVVERTENZA_DEDOTTO; }
      annuncia(frase, { locale: spiegazione });
    });
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 7. b3 — avanzamento solo visuale
  // Intervento giusto: inserire l'avanzamento come testo e nascondere l'immagine al
  // lettore di schermo. Non riscrivere l'intestazione della pagina.
  // ────────────────────────────────────────────────────────────────────────────

  function riparaAvanzamento(schermo) {
    var indicatori = immaginiMute().filter(indicatoreDiPasso);
    var esistente = document.querySelector('[data-vv="b3"]');
    if (!indicatori.length && !esistente) { return; }

    var frase = null;
    var origine = null;
    if (schermo && schermo.stepIndex > 0 && schermo.stepCount > 0) {
      frase = 'Avanzamento: passo ' + schermo.stepIndex + ' di ' + schermo.stepCount + '.';
      origine = { locale: 'Passo e totale forniti dal servizio.' };
    } else {
      var dalTesto = passoDalContenuto();
      if (dalTesto && dalTesto.totale) {
        frase = 'Avanzamento: passo ' + dalTesto.passo + ' di ' + dalTesto.totale + '.';
        origine = { locale: 'Passo e totale letti nel testo della pagina.' };
      } else if (dalTesto) {
        frase = 'Avanzamento: passo ' + dalTesto.passo
          + '. Il numero totale dei passi non e’ dichiarato in questa pagina.';
        origine = { locale: 'Passo letto nel testo della pagina; il totale non c’e’.' };
      } else {
        frase = 'Avanzamento: questa pagina non dichiara a che passo sei.';
        origine = { locale: 'Constatazione dell’interfaccia.' };
      }
    }

    var testo = esistente || nuovo('p', 'vv-avanzamento');
    testo.setAttribute('data-vv', 'b3');
    if (testo.textContent === frase) { return; }
    testo.textContent = frase;

    if (!esistente && indicatori.length && indicatori[0].parentNode) {
      indicatori[0].parentNode.insertBefore(testo, indicatori[0].nextSibling);
    }
    indicatori.forEach(function (immagine) {
      immagine.setAttribute('aria-hidden', 'true');
      immagine.setAttribute('data-vv-riparato', 'b3');
    });
    annuncia(frase, origine);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 8. b4 — calendario inaccessibile
  // Due interventi, entrambi additivi: i giorni diventano raggiungibili da tastiera e con
  // un nome, e accanto compare un campo data testuale. Per Marco digitare 15/03/1985 e'
  // piu' rapido che attraversare una griglia di 36 caselle.
  // Il valore finisce nell'input della PAGINA: il suo submit resta intatto.
  // ────────────────────────────────────────────────────────────────────────────

  function griglieDiPulsantiMuti() {
    var conteggio = new Map();
    var pulsanti = document.querySelectorAll('[role="button"]');
    for (var i = 0; i < pulsanti.length; i++) {
      var pulsante = pulsanti[i];
      if (nostro(pulsante) || nomeAccessibileDi(pulsante)) { continue; }
      var padre = pulsante.parentElement;
      if (!padre) { continue; }
      conteggio.set(padre, (conteggio.get(padre) || 0) + 1);
    }
    var griglie = [];
    conteggio.forEach(function (quanti, padre) {
      if (quanti >= 10) { griglie.push(padre); }   // una griglia di giorni, non tre pulsanti
    });
    return griglie;
  }

  function celleConTesto(griglia) {
    var celle = [];
    var figli = griglia.querySelectorAll('[role="button"]');
    for (var i = 0; i < figli.length; i++) {
      if (testoDi(figli[i])) { celle.push(figli[i]); }
    }
    return celle;
  }

  function contestoDellaGriglia(griglia) {
    var contenitore = griglia.parentElement;
    if (!contenitore) { return ''; }
    var figli = contenitore.children;
    for (var i = 0; i < figli.length; i++) {
      var figlio = figli[i];
      if (figlio === griglia || nostro(figlio)) { continue; }
      var testo = testoDi(figlio);
      // L'intestazione del mese: testo breve, una riga, non la fila dei nomi dei giorni.
      if (testo && testo.length <= 40 && figlio.children.length === 0) { return testo; }
    }
    return '';
  }

  function portatoreDelValore(griglia) {
    var nodo = griglia;
    for (var salti = 0; salti < 5 && nodo; salti++) {
      var nascosto = nodo.querySelector('input[type="hidden"]');
      if (nascosto && !nostro(nascosto)) { return nascosto; }
      nodo = nodo.parentElement;
    }
    return null;
  }

  function riparaCalendario() {
    griglieDiPulsantiMuti().forEach(function (griglia) {
      if (griglia.getAttribute('data-vv-riparato') === 'b4') { return; }
      griglia.setAttribute('data-vv-riparato', 'b4');

      var contesto = contestoDellaGriglia(griglia);
      var celle = celleConTesto(griglia);

      // Le caselle vuote sparivano come "pulsante" senza nome: fuori dall'albero.
      var tutti = griglia.querySelectorAll('[role="button"]');
      for (var i = 0; i < tutti.length; i++) {
        if (!testoDi(tutti[i])) {
          tutti[i].setAttribute('aria-hidden', 'true');
          tutti[i].removeAttribute('role');
        }
      }

      // In-place: nome accessibile dal testo che la casella mostra gia', piu' il contesto
      // che la pagina scrive sopra la griglia. Nessun testo inventato.
      celle.forEach(function (cella) {
        cella.setAttribute('aria-label', testoDi(cella) + (contesto ? ' ' + contesto : ''));
        cella.setAttribute('tabindex', '0');
      });
      if (contesto) {
        griglia.setAttribute('role', 'group');
        griglia.setAttribute('aria-label', 'Calendario ' + contesto);
      }

      griglia.addEventListener('keydown', function (evento) {
        var attiva = document.activeElement;
        var posizione = celle.indexOf(attiva);
        if (posizione < 0) { return; }
        var salto = 0;
        if (evento.key === 'Enter' || evento.key === ' ' || evento.key === 'Spacebar') {
          evento.preventDefault();
          attiva.click();   // l'handler della pagina, non il nostro
          var portatore = portatoreDelValore(griglia);
          annuncia('Selezionato: ' + testoDi(attiva) + (contesto ? ' ' + contesto : '')
            + (portatore && portatore.value ? '. Il campo della pagina adesso vale '
              + portatore.value + '.' : '.'),
            { locale: 'Valore letto dal campo della pagina dopo la selezione.' });
          return;
        }
        if (evento.key === 'ArrowRight') { salto = 1; }
        else if (evento.key === 'ArrowLeft') { salto = -1; }
        else if (evento.key === 'ArrowDown') { salto = 7; }
        else if (evento.key === 'ArrowUp') { salto = -7; }
        else if (evento.key === 'Home') { salto = -posizione; }
        else if (evento.key === 'End') { salto = celle.length - 1 - posizione; }
        else { return; }
        var nuovaPosizione = Math.min(celle.length - 1, Math.max(0, posizione + salto));
        evento.preventDefault();
        celle[nuovaPosizione].focus();
      });

      aggiungiCampoData(griglia, contesto);
    });
  }

  function aggiungiCampoData(griglia, contesto) {
    var portatore = portatoreDelValore(griglia);
    var contenitore = griglia.parentElement || griglia;
    if (contenitore.parentNode === null) { return; }

    var blocco = nuovo('div', 'vv-blocco');
    blocco.setAttribute('data-vv', 'b4');

    var idCampo = 'vv-data-testo';
    var etichetta = nuovo('label', 'vv-etichetta',
      'In alternativa al calendario, scrivi la data in cifre: giorno barra mese barra anno');
    etichetta.setAttribute('for', idCampo);

    // Nessun attributo name: se la pagina inviasse il form nativamente, questo campo non
    // entrerebbe nei dati. Il valore vero resta quello del campo della pagina.
    var campo = nuovo('input');
    campo.type = 'text';
    campo.id = idCampo;
    campo.className = 'vv-campo';
    campo.setAttribute('inputmode', 'numeric');
    campo.setAttribute('autocomplete', 'bday');
    if (portatore && portatore.value) { campo.value = portatore.value; }

    var conferma = nuovo('button', 'vv-pulsante', 'Metti questa data nel campo della pagina');
    conferma.type = 'button';

    var esito = nuovo('p', 'vv-esito', '');
    esito.id = 'vv-data-esito';
    campo.setAttribute('aria-describedby', esito.id);

    conferma.addEventListener('click', function () { confermaData(campo, portatore, esito); });
    campo.addEventListener('keydown', function (evento) {
      if (evento.key === 'Enter') {
        evento.preventDefault();
        confermaData(campo, portatore, esito);
      }
    });

    blocco.appendChild(etichetta);
    blocco.appendChild(campo);
    blocco.appendChild(conferma);
    blocco.appendChild(esito);
    contenitore.parentNode.insertBefore(blocco, contenitore.nextSibling);

    annuncia('Accanto al calendario ho aggiunto un campo in cui scrivere la data in cifre'
      + (contesto ? ', per il periodo ' + contesto : '')
      + '. Il valore finisce nel campo della pagina, non in una nostra copia.',
      { locale: 'Aggiunta dell’interfaccia VisioVoice.' });
  }

  function confermaData(campo, portatore, esito) {
    var valore = campo.value.trim();
    if (!valore) {
      allarme('Il campo della data e’ vuoto: scrivi la data in cifre, giorno barra mese '
        + 'barra anno.', { locale: 'Controllo dell’interfaccia.' });
      return;
    }
    var idCampoPagina = portatore ? (portatore.getAttribute('name') || portatore.id || '') : '';
    chiediApi('POST', '/guide/validate', {
      sessionId: stato.sessionId, fieldId: idCampoPagina, value: valore
    }).then(function (risposta) {
      var esitoOk = !!(risposta.ok && risposta.dati && risposta.dati.valid);
      var frase;
      if (risposta.ok && risposta.dati && typeof risposta.dati.spokenMessage === 'string') {
        frase = risposta.dati.spokenMessage;
      } else {
        frase = 'Il controllo della data non e’ disponibile su questo server, quindi non '
          + 'te lo confermo. ' + (risposta.frase || '');
      }
      if (esitoOk || !risposta.ok) {
        // Anche senza verifica il valore va nel campo della pagina: e' cio' che l'utente ha
        // scritto, e la pagina resta la fonte di verita'. Ma glielo diciamo.
        var daScrivere = esitoOk && risposta.dati.normalizedValue
          ? risposta.dati.normalizedValue
          : valore;
        if (portatore) {
          portatore.value = daScrivere;
          portatore.dispatchEvent(new Event('input', { bubbles: true }));
          portatore.dispatchEvent(new Event('change', { bubbles: true }));
        }
        frase += ' Ho scritto ' + daScrivere + ' nel campo della pagina.';
        esito.textContent = frase;
        annuncia(frase, { locale: 'Messaggio del servizio di controllo dei dati.' });
      } else {
        esito.textContent = frase;
        allarme(frase, { locale: 'Messaggio del servizio di controllo dei dati.' });
      }
    });
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 9. b5 — errore non annunciato
  // Intervento giusto: osservare il DOM e annunciare l'errore in una regione role="alert".
  // Non intercettare il submit: il submit e' della pagina.
  // ────────────────────────────────────────────────────────────────────────────

  /*
   * Scrittura idempotente: setAttribute produce una mutazione anche quando riscrive lo
   * stesso valore, e l'osservatore del DOM reagirebbe alla nostra stessa scrittura.
   */
  function segnaValidita(campo, valido) {
    var atteso = valido ? 'false' : 'true';
    if (campo.getAttribute('aria-invalid') !== atteso) {
      campo.setAttribute('aria-invalid', atteso);
    }
  }

  function sembraNodoDiErrore(nodo) {
    if (!nodo || nodo.nodeType !== 1 || nostro(nodo)) { return false; }
    var firma = (nodo.id || '') + ' ' + (typeof nodo.className === 'string' ? nodo.className : '');
    return /errore|error|alert|avviso|warning|messaggio|invalid/i.test(firma);
  }

  /*
   * Il campo a cui si riferisce un messaggio di errore: si sale di un livello alla volta
   * finche' non si trova un campo vero. Si parte dal padre e non dal nodo stesso perche' il
   * messaggio e' spesso un <div>, e closest('div') restituirebbe il messaggio stesso — che
   * di campi non ne contiene nessuno.
   */
  function campoVicino(nodo) {
    var contenitore = nodo.parentElement;
    for (var salti = 0; salti < 4 && contenitore; salti++) {
      var campi = contenitore.querySelectorAll('input, select, textarea');
      for (var i = 0; i < campi.length; i++) {
        var tipo = (campi[i].getAttribute('type') || '').toLowerCase();
        if (!nostro(campi[i]) && tipo !== 'hidden') { return campi[i]; }
      }
      contenitore = contenitore.parentElement;
    }
    return null;
  }

  function annunciaErrore(nodo) {
    if (!visibile(nodo)) { return; }
    var testo = testoDi(nodo);
    if (!testo) { return; }
    var campo = campoVicino(nodo);
    var etichetta = campo ? nomeAccessibileDi(campo) : '';
    if (campo) {
      segnaValidita(campo, false);
      if (nodo.id) {
        var descritto = (campo.getAttribute('aria-describedby') || '').split(/\s+/)
          .filter(function (x) { return x; });
        if (descritto.indexOf(nodo.id) < 0) {
          descritto.push(nodo.id);
          campo.setAttribute('aria-describedby', descritto.join(' '));
        }
      }
    }
    allarmeUnaVolta('Errore' + (etichetta ? ' nel campo ' + etichetta : '') + ': ' + testo,
      { locale: 'Testo dell’errore letto dalla pagina, cosi’ com’e’ scritto.' });
  }

  function controllaSegnaleVisivo(campo) {
    if (!campo || nostro(campo)) { return; }
    var bordo = campo.style ? (campo.style.borderColor || '') : '';
    if (!bordo) {
      segnaValidita(campo, true);
      return;
    }
    segnaValidita(campo, false);
    var contenitore = campo.closest('.form-group, fieldset, form, div');
    var testo = '';
    if (contenitore) {
      var candidati = contenitore.querySelectorAll('*');
      for (var i = 0; i < candidati.length; i++) {
        if (sembraNodoDiErrore(candidati[i]) && visibile(candidati[i]) && testoDi(candidati[i])) {
          testo = testoDi(candidati[i]);
          break;
        }
      }
    }
    var etichetta = nomeAccessibileDi(campo);
    if (testo) {
      allarmeUnaVolta('Errore' + (etichetta ? ' nel campo ' + etichetta : '') + ': ' + testo,
        { locale: 'Testo dell’errore letto dalla pagina.' });
    } else {
      allarmeUnaVolta('Il campo' + (etichetta ? ' ' + etichetta : '')
        + ' e’ segnalato come non valido, ma la pagina lo dice solo con il colore: '
        + 'non c’e’ nessun testo che spieghi perche’.',
        { locale: 'Constatazione dell’interfaccia: segnale visivo senza testo.' });
    }
  }

  function osservaGliErrori() {
    var osservatore = new MutationObserver(function (mutazioni) {
      mutazioni.forEach(function (mutazione) {
        var bersaglio = mutazione.target;
        if (!bersaglio || bersaglio.nodeType !== 1) {
          bersaglio = bersaglio && bersaglio.parentElement;
          if (!bersaglio) { return; }
        }
        if (nostro(bersaglio)) { return; }
        if (mutazione.type === 'attributes' && /^(input|select|textarea)$/i.test(bersaglio.tagName)) {
          controllaSegnaleVisivo(bersaglio);
        }
        if (sembraNodoDiErrore(bersaglio)) { annunciaErrore(bersaglio); }
        if (mutazione.type === 'childList') {
          for (var i = 0; i < mutazione.addedNodes.length; i++) {
            var aggiunto = mutazione.addedNodes[i];
            if (aggiunto.nodeType === 1 && sembraNodoDiErrore(aggiunto)) { annunciaErrore(aggiunto); }
          }
        }
      });
    });
    /*
     * aria-invalid NON e' fra gli attributi osservati, ed e' deliberato: lo scriviamo noi.
     * Osservarlo significherebbe reagire alla nostra stessa scrittura, e siccome
     * setAttribute produce una mutazione anche quando il valore non cambia, l'osservatore
     * si richiamerebbe all'infinito bloccando la pagina. Osserviamo i segnali della
     * PAGINA (style, class, hidden, testo), non i nostri.
     */
    osservatore.observe(document.body, {
      subtree: true, childList: true, characterData: true,
      attributes: true, attributeFilter: ['style', 'class', 'hidden']
    });
  }

  /*
   * Controllo a campo lasciato: Marco sente il motivo PRIMA di premere Avanti, non dopo.
   * Il giudizio e' del backend (validatori normati, messaggi HUMAN_REVIEWED): qui non c'e'
   * nessuna regola su IBAN o codici fiscali.
   */
  function controllaQuandoEsceDalCampo() {
    document.addEventListener('change', function (evento) {
      var campo = evento.target;
      if (!campo || nostro(campo)) { return; }
      if (!/^(input|select|textarea)$/i.test(campo.tagName || '')) { return; }
      var tipo = (campo.getAttribute('type') || '').toLowerCase();
      if (tipo === 'hidden' || tipo === 'checkbox' || tipo === 'radio') { return; }
      var idCampo = campo.getAttribute('name') || campo.id || '';
      var valore = (campo.value || '').trim();
      if (!idCampo || !valore) { return; }

      chiediApi('POST', '/guide/validate', {
        sessionId: stato.sessionId, fieldId: idCampo, value: valore
      }).then(function (risposta) {
        if (risposta.ok && risposta.dati && typeof risposta.dati.spokenMessage === 'string') {
          var frase = risposta.dati.spokenMessage;
          if (risposta.dati.valid === false) {
            segnaValidita(campo, false);
            allarme(frase, { locale: 'Messaggio del servizio di controllo dei dati.' });
          } else {
            segnaValidita(campo, true);
            annuncia(frase, { locale: 'Messaggio del servizio di controllo dei dati.' });
          }
          return;
        }
        if (risposta.stato === 404) { return; }   // campo che il servizio non conosce: silenzio
        if (!stato.controlloDatiAvvisato) {
          stato.controlloDatiAvvisato = true;
          annuncia('Il controllo automatico dei dati non risponde su questo server: quello che '
            + 'scrivi non viene verificato. ' + (risposta.frase || ''),
            { locale: 'Constatazione dell’interfaccia sullo stato del servizio.' });
        }
      });
    }, true);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 10. Il racconto: leggo il DOM, chiamo il backend, inietto
  // ────────────────────────────────────────────────────────────────────────────

  function segmentoDaInterrogare(script) {
    if (!script || !script.segments || !script.segments.length) { return null; }
    var ordine = ['AI_INFERRED', 'AI_REPHRASED', 'SOURCE_VERBATIM', 'HUMAN_REVIEWED'];
    for (var i = 0; i < ordine.length; i++) {
      for (var j = 0; j < script.segments.length; j++) {
        if (script.segments[j].provenance === ordine[i]) { return script.segments[j].id; }
      }
    }
    return script.segments[0].id;
  }

  function annunciaScript(script) {
    if (!script || !script.segments || !script.segments.length) { return false; }
    stato.ultimoScript = script;
    var frasi = [];
    var dedotti = 0;
    script.segments.forEach(function (segmento) {
      if (segmento && segmento.text) { frasi.push(segmento.text); }
      if (segmento && segmento.provenance === 'AI_INFERRED') { dedotti++; }
    });
    if (dedotti > 0) { frasi.push(AVVERTENZA_DEDOTTO); }
    annuncia(frasi.join(' '), { segmentId: segmentoDaInterrogare(script) });
    return true;
  }

  function racconta(livello) {
    stato.livelloChiesto = livello;
    return chiediApi('POST', '/perceive', {
      sessionId: stato.sessionId, url: window.location.href,
      html: HTML_SORGENTE, level: livello
    }).then(function (risposta) {
      if (!risposta.ok) {
        segnalaDatiMancanti(risposta.frase);
        annuncia(risposta.frase + ' Intanto ti leggo la struttura che trovo io nella pagina. '
          + descrizioneLocale(),
          { locale: 'Struttura contata nel DOM della pagina dall’interfaccia.' });
        return null;
      }
      var schermo = risposta.dati ? risposta.dati.screen : null;
      riparaAvanzamento(schermo);
      riparaCampiSenzaNome(schermo ? schermo.fields : null);
      var tabelle = tabelleNella(risposta.dati);
      if (tabelle.length) {
        iniettaTabelle(tabelle);
      } else {
        segnalaDatiMancanti('Il servizio ha letto la pagina ma non mi ha passato i dati '
          + 'dell’immagine.');
      }
      if (!annunciaScript(risposta.dati ? risposta.dati.script : null)) {
        annuncia('Il racconto parlato non e’ disponibile su questo server. '
          + descrizioneLocale(),
          { locale: 'Struttura contata nel DOM della pagina dall’interfaccia.' });
      }
      return risposta.dati;
    });
  }

  function raccontaProvenienza() {
    var origine = stato.ultimaOrigine;
    if (origine && origine.segmentId) {
      chiediApi('GET', '/provenance/' + encodeURIComponent(origine.segmentId))
        .then(function (risposta) {
          if (risposta.ok && risposta.dati) {
            memorizzaEtichetta(risposta.dati.provenance, risposta.dati.italianLabel);
            annuncia(risposta.dati.spokenExplanation || ('Questa frase e’ '
              + etichettaProvenienza(risposta.dati.provenance) + '.'), origine);
            return;
          }
          annuncia(risposta.frase || 'Non riesco a risalire all’origine di questa frase.',
            origine);
        });
      return;
    }
    annuncia((origine && origine.locale)
      ? origine.locale
      : 'Non ho ancora detto niente di cui possa dichiarare l’origine.', origine);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 11. Navigazione: i controlli della pagina, azionati da noi
  // ────────────────────────────────────────────────────────────────────────────

  function trovaControllo(regola) {
    var candidati = document.querySelectorAll('button, a[href], input[type="submit"]');
    for (var i = 0; i < candidati.length; i++) {
      var candidato = candidati[i];
      if (nostro(candidato) || !visibile(candidato)) { continue; }
      var testo = testoDi(candidato) || candidato.getAttribute('value') || '';
      if (regola.test(testo)) { return candidato; }
    }
    return null;
  }

  function vai(regola, descrizione) {
    var controllo = trovaControllo(regola);
    if (!controllo) {
      annuncia('Da questa pagina non trovo un controllo per ' + descrizione + '.',
        { locale: 'Constatazione dell’interfaccia sui controlli della pagina.' });
      return;
    }
    annuncia(descrizione + ': ' + (testoDi(controllo) || 'controllo della pagina') + '.',
      { locale: 'Testo del controllo, letto dalla pagina.' });
    controllo.click();   // il comportamento della pagina, non una nostra imitazione
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 12. Scorciatoie a un tasto, disattivate quando il focus e' in un campo
  // ────────────────────────────────────────────────────────────────────────────

  function scrivendoInUnCampo() {
    var attivo = document.activeElement;
    if (!attivo) { return false; }
    if (attivo.isContentEditable) { return true; }
    return /^(input|textarea|select)$/i.test(attivo.tagName || '');
  }

  function elencoComandi() {
    var voci = COMANDI.map(function (voce) { return 'tasto ' + voce[0] + ': ' + voce[1]; });
    return 'Comandi di VisioVoice, un tasto ciascuno, attivi quando il focus non e’ in un '
      + 'campo. ' + voci.join('. ') + '. I controlli della pagina restano raggiungibili con '
      + 'Tab, Invio e Spazio.';
  }

  function gestisciTasto(evento) {
    if (evento.ctrlKey || evento.altKey || evento.metaKey) { return; }
    if (scrivendoInUnCampo()) { return; }   // altrimenti Marco non riesce a scrivere "d"
    var tasto = (evento.key || '').toLowerCase();
    if (tasto === '?') {
      evento.preventDefault();
      annuncia(elencoComandi(), { locale: 'Elenco dei comandi dell’interfaccia.' });
      return;
    }
    if (tasto === 'd') {
      evento.preventDefault();
      annuncia('Leggo la schermata.', { locale: 'Conferma dell’interfaccia.' });
      racconta(stato.livelloChiesto === 'STANDARD' ? 'FULL' : 'STANDARD');
      return;
    }
    if (tasto === 'n') { evento.preventDefault(); vai(/avanti|continua|prosegui|presenta|invia/i, 'Passo successivo'); return; }
    if (tasto === 'b') { evento.preventDefault(); vai(/indietro|precedente|torna/i, 'Passo precedente'); return; }
    if (tasto === 'r') {
      evento.preventDefault();
      if (stato.ultimaFrase) { annuncia(stato.ultimaFrase, stato.ultimaOrigine); }
      else { annuncia('Non ho ancora detto niente da ripetere.', null); }
      return;
    }
    if (tasto === 'p') { evento.preventDefault(); raccontaProvenienza(); }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // 13. Avvio
  // Prima le riparazioni che non hanno bisogno della rete: se il backend non risponde, la
  // pagina resta comunque piu' accessibile di come l'abbiamo trovata.
  // ────────────────────────────────────────────────────────────────────────────

  function avvia() {
    preparaInterfaccia();
    riparaAvanzamento(null);
    riparaCampiSenzaNome(null);
    riparaCalendario();
    osservaGliErrori();
    controllaQuandoEsceDalCampo();
    document.addEventListener('keydown', gestisciTasto, true);

    annuncia('VisioVoice e’ attivo su questa pagina. Premi il tasto punto di domanda per '
      + 'l’elenco dei comandi.', { locale: 'Messaggio di avvio dell’interfaccia.' });

    apriSessione().then(function (id) {
      stato.sessionId = id;
      // BRIEF all'avvio: tre segmenti, tre secondi, e Marco sa se e' nel posto giusto.
      // Il tasto D chiede il livello successivo.
      return racconta('BRIEF');
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', avvia);
  } else {
    avvia();
  }
})();
