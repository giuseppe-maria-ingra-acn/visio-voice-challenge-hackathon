#!/usr/bin/env bash
# PostToolUse su Edit/Write - difende i due invarianti che nessun agente deve poter aggirare.
#
# 1. BLOCCA  i dati di dominio scritti dentro il codice di produzione.
#    Un importo in un .java e' un numero che nessuno riesce piu' a ricondurre a una fonte,
#    e quindi che il gate di fidelity non puo' verificare. Gli importi stanno nello scenario.
#    I test possono averli: servono proprio a fissare valori noti.
#
# 2. AVVISA  quando si costruisce testo per l'utente senza dichiararne la provenance.
#    Qui avvisa e non blocca: la stessa forma sintattica compare legittimamente nella
#    definizione del record e nei test. Un blocco su un'euristica si trasforma in fretta
#    in un hook che qualcuno disattiva, e allora non protegge piu' nulla.

set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0

payload=$(cat 2>/dev/null || echo '{}')

file=$(printf '%s' "$payload" | python -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print(''); raise SystemExit
ti = d.get('tool_input') or {}
tr = d.get('tool_response') or {}
if not isinstance(tr, dict):
    tr = {}
print(ti.get('file_path') or tr.get('filePath') or '')
" 2>/dev/null)

case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac

# Normalizza i separatori di Windows per il confronto sul percorso.
norm=$(printf '%s' "$file" | tr '\\' '/')

# I test hanno il permesso di contenere valori letterali: e' il loro lavoro.
# I pattern accettano sia path assoluti sia relativi: Claude Code passa gli assoluti, ma
# un hook che si rompe quando il path e' relativo e' un hook che tace quando serve.
case "$norm" in
  */src/test/*|src/test/*) exit 0 ;;
esac
case "$norm" in
  */src/main/java/*|src/main/java/*) ;;
  *) exit 0 ;;
esac

[ -f "$norm" ] || exit 0

# --- Invariante 1: nessun dato di dominio nel codice di produzione -------------------
domain_hits=$(grep -nE '"[^"]*[0-9]+([.,][0-9]+)?[^"]*(euro|EUR|€|mesi|giorni|ISEE)' "$norm" 2>/dev/null | head -8)

if [ -n "$domain_hits" ]; then
  {
    echo "INVARIANTE VIOLATO - dato di dominio dentro il codice di produzione:"
    echo "  $file"
    echo
    printf '%s\n' "$domain_hits"
    echo
    echo "Importi, scadenze, fasce e requisiti stanno in"
    echo "src/main/resources/scenarios/*.json, nel campo sourceFacts."
    echo
    echo "Motivo: un numero scritto qui non ha una fonte a cui il gate di fidelity possa"
    echo "riancorarlo, quindi verrebbe pronunciato a Marco senza che nulla lo verifichi."
    echo "Spostalo nello scenario e leggilo da la'."
  } >&2
  exit 2
fi

# --- Invariante 2: testo per l'utente senza provenance dichiarata --------------------
if grep -qE 'new (ScriptSegment|FieldGuidance)\(' "$norm" 2>/dev/null; then
  if ! grep -qE '(Provenance\.|provenance)' "$norm" 2>/dev/null; then
    python -c "
import json
print(json.dumps({
  'hookSpecificOutput': {
    'hookEventName': 'PostToolUse',
    'additionalContext': (
      'AVVISO provenance: in questo file si costruiscono ScriptSegment o FieldGuidance '
      'ma non compare mai Provenance. Ogni frase che arriva a Marco deve dichiarare da '
      'dove viene: lui non vede lo schermo e non ha altro modo di sapere quanto fidarsi. '
      'Vedi docs/PROVENANCE-SPEC.md. Se questa e la definizione del record o un caso di '
      'test, ignora questo avviso.'
    ),
  }
}))
" 2>/dev/null
  fi
fi

exit 0
