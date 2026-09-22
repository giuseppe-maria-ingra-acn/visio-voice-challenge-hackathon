#!/usr/bin/env bash
# SubagentStop - registra quale agente ha finito e quando.
#
# Perche': uno dei deliverable e' la mappa di dove ha contribuito l'AI e dove e' servita
# revisione umana. Ricostruirla a memoria la sera prima produce un documento inventato.
# Questo log la rende un fatto: demo-director la genera da qui invece di ricordarsela.
#
# Append-only, non blocca mai, non fallisce mai in modo visibile. Un log che rompe il
# flusso di lavoro viene disattivato, e allora non registra piu' niente.

set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0

mkdir -p docs 2>/dev/null

python -c "
import sys, json, datetime
try:
    d = json.load(sys.stdin)
except Exception:
    d = {}

# Lo schema del payload puo' cambiare fra versioni: peschiamo il primo campo
# identificativo che troviamo invece di dipendere da un nome preciso.
name = '?'
for k in ('agent_name', 'subagent_type', 'agent_type', 'name', 'description'):
    v = d.get(k)
    if isinstance(v, str) and v.strip():
        name = v.strip()
        break

ts = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
line = '%s\t%s\n' % (ts, name)
try:
    with open('docs/.agent-activity.log', 'a', encoding='utf-8') as f:
        f.write(line)
except Exception:
    pass
" 2>/dev/null

exit 0
