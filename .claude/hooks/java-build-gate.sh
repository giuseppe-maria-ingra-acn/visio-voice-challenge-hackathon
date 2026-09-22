#!/usr/bin/env bash
# PostToolUse su Edit/Write - se il file toccato e' Java, verifica che compili ancora.
#
# Perche': l'obiettivo dichiarato e' che questa struttura produca codice formalmente
# corretto. Chiedere a un agente di "ricordarsi di compilare" non e' un controllo, e'
# una speranza. Questo hook lo rende un fatto: se non compila, l'agente viene svegliato
# con l'errore del compilatore e lo sistema prima di andare avanti.
#
# Gira in asyncRewake: non blocca la modifica successiva, ma se il build si rompe
# riporta il modello sul problema. Il costo di un falso silenzio qui e' un merge rotto
# alla terza ora, quando non c'e' tempo per indagare.

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

# Solo sorgenti Java. Tutto il resto esce subito: un hook lento su ogni .md
# e' un hook che qualcuno disattivera'.
case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac

[ -f pom.xml ] || exit 0

if ! out=$(mvn -q -DskipTests compile 2>&1); then
  {
    echo "IL BUILD JAVA E' ROTTO dopo la modifica a:"
    echo "  $file"
    echo
    echo "Errori del compilatore (ultime 40 righe):"
    printf '%s\n' "$out" | grep -E "ERROR|error:|\.java:\[" | tail -40
    echo
    echo "Sistema questo prima di proseguire. Non dichiarare fatto cio' che non compila."
  } >&2
  exit 2
fi

exit 0
