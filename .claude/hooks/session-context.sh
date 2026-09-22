#!/usr/bin/env bash
# SessionStart - inietta persona e stato reale del progetto nel contesto.
#
# Perche': dopo un /clear, un compact o l'avvio di una nuova sessione, l'agente riparte
# senza sapere a che punto siamo, e in una sessione di lavoro serrata quel disorientamento
# costa piu' di ogni altra cosa. Lo stato lo LEGGIAMO dal filesystem invece di fidarci
# della memoria: se un file non c'e', quella fase non e' fatta, qualunque cosa dica il log.
#
# Nota di portabilita': il testo passa a Python via stdin, non via file temporaneo.
# Git Bash e Python-per-Windows non condividono la nozione di "/tmp" (per Python e' C:\tmp,
# che non esiste), quindi qualunque path scambiato fra i due e' una trappola silenziosa.

set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0

mark() { [ -e "$1" ] && echo "  [fatto]     $2" || echo "  [da fare]   $2"; }

emit_context() {
  echo "== VisioVoice - stato del progetto =="
  echo
  echo "PERSONA: Marco Ferrari, 41 anni, cieco dalla nascita. NVDA + display braille,"
  echo "sola tastiera, competenza informatica alta. Deve inviare la domanda di Assegno"
  echo "Unico all'INPS. Si blocca su: (1) gli importi sono dentro un'immagine, (2) il form"
  echo "ha l'IBAN senza label, l'avanzamento come immagine, gli errori solo col colore."
  echo "Scheda completa: docs/PERSONA.md"
  echo
  echo "INVARIANTE: ogni frase che l'utente ascolta porta la sua provenance, e ogni numero"
  echo "pronunciato deve essere ritrovabile nella fonte. Spec: docs/PROVENANCE-SPEC.md"
  echo
  echo "AVANZAMENTO (letto dal filesystem, non dalla memoria):"
  mark "src/main/resources/scenarios"           "fase 0 - scenario e prove (scenario-researcher)"
  mark "src/main/java/it/visiovoice/model"      "fase 1 - contratti condivisi (contract-architect)"
  mark "src/main/java/it/visiovoice/perception" "fase 2 - percezione (perception-engineer)"
  mark "src/main/java/it/visiovoice/narration"  "fase 2 - narrazione (narration-engineer)"
  mark "src/main/java/it/visiovoice/procedure"  "fase 2 - procedura (procedure-engineer)"
  mark "src/main/resources/static/index.html"   "fase 3 - interfaccia (a11y-frontend)"
  mark "docs/DEMO.md"                           "fase 5 - demo (demo-director)"
  echo
  if [ -e src/main/java/it/visiovoice/model ]; then
    echo "I contratti esistono: i builder possono girare in parallelo."
  else
    echo "ATTENZIONE: i contratti non esistono ancora. Lancia contract-architect PRIMA di"
    echo "qualunque builder, altrimenti gli agenti paralleli divergono sui nomi dei tipi."
  fi
  echo
  echo "Ordine di lancio: docs/PLAN.md. Squadra e perimetri: docs/AGENT-TEAM.md"
}

emit_context | python -c "
import json, sys
ctx = sys.stdin.read()
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'SessionStart',
        'additionalContext': ctx,
    }
}))
" 2>/dev/null

exit 0
