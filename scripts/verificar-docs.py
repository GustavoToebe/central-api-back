"""Verifica referências locais de entrada e consistência de migrations sem serviços externos."""
from pathlib import Path
import json,re,sys
raiz=Path(__file__).resolve().parents[1]
estado=json.loads((raiz/'docs/estado-projeto.json').read_text(encoding='utf-8'))
erros=[]
entradas=[raiz/'AGENTS.md',raiz/'docs/README.md']+list((raiz/'docs/desenvolvimento').glob('*.md'))
for arquivo in entradas:
 texto=arquivo.read_text(encoding='utf-8')
 for destino in re.findall(r'\[[^\]]*\]\(([^)]+)\)',texto):
  destino=destino.strip().split(' "',1)[0].strip('<>')
  if not destino or destino.startswith(('#','https://','http://','mailto:','app://')): continue
  caminho=destino.split('#',1)[0]
  if caminho and not (arquivo.parent/caminho).exists(): erros.append(f'{arquivo.relative_to(raiz)}: link inexistente {destino}')
if estado['componente']=='backend':
 migrations=sorted((raiz/'src/main/resources/db/migration').glob('V*__*.sql'))
 ultima=migrations[-1].name
 if estado['migrations']['ultima']!=ultima or estado['migrations']['quantidade']!=len(migrations):
  erros.append('estado-projeto.json diverge das migrations')
 numero=int(re.match(r'V(\d+)',ultima).group(1))
 for arquivo in [raiz/'SCHEMA.md',raiz/'schema.sql',raiz/'scripts/gerar-schema.ps1']:
  if not re.search(r'V001[–-]V0*'+str(numero)+r'\b',arquivo.read_text(encoding='utf-8')):
   erros.append(f'{arquivo.name}: intervalo de migrations desatualizado')
if erros:
 print('\n'.join(erros));sys.exit(1)
print('Documentação: links de entrada e estado local coerentes.')
