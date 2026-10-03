"""Confere que as cópias deliberadas entre a API da Central e a do Servirea continuam idênticas.

Uso (na pasta do back da Central):
    python scripts/verificar-copias.py

Algumas classes existem nos dois repositórios porque os dois lados precisam se comportar igual: o limite de login e o filtro de monitoramento. Corrigir só um lado quebra a
integração ou deixa uma falha aberta no outro. O script compara cada par depois de normalizar pacote e nome do produto e falha
(código 1) se algum divergir, mostrando as linhas. Sem o outro repositório ao lado (CI só deste repo) avisa e sai com sucesso;
aponte para ele com SERVIREA_API_BACK. Nome do arquivo igual nos dois lados, em qualquer pasta de src/.

Mudou uma cópia de propósito? Mude a outra igual. Se a divergência passou a ser desejada, tire o arquivo de COPIAS e explique no
docs/papeis-banco.md ou no doc do módulo.
"""
from pathlib import Path
import difflib
import os
import re
import sys

raiz = Path(__file__).resolve().parents[1]
outro = Path(os.environ.get('SERVIREA_API_BACK', raiz.parent.parent / 'servire' / 'servirea-api-back'))

# Cópias que precisam ser idênticas (nome do arquivo, sem pasta).
COPIAS = [
    # HMAC/TOTP agora vêm de servirea-comum; permanece o nonce dependente do banco.
    'IntegracaoNonceService.java',
    # Limite de login (contador no banco).
    'LimiteLogin.java', 'LimiteLoginException.java', 'LimiteLoginTest.java',
    'ContadorBanco.java', 'ContadorEmMemoria.java', 'ContadorJanelas.java', 'ContadorBancoIntegrationTest.java',
    # Monitoramento.
    'MonitoramentoFilter.java', 'RequestIdFilterTest.java',
]

if not outro.exists():
    print(f'Repositório do Servirea não encontrado em {outro}; verificação de cópias pulada.')
    sys.exit(0)


def achar(base: Path, nome: str):
    achados = [p for p in (base / 'src').rglob(nome)]
    return achados[0] if len(achados) == 1 else None


def normalizar(texto: str) -> list[str]:
    texto = texto.replace('\r\n', '\n')
    texto = re.sub(r'package [\w.]+;', '', texto)
    texto = re.sub(r'br\.com\.(?:servire|central)\.api(?:\.\w+)?', 'X', texto)
    texto = re.sub(r'(?i)servirea?|central', 'N', texto)
    return [linha.rstrip() for linha in texto.split('\n')]


problemas = []
for nome in COPIAS:
    a, b = achar(raiz, nome), achar(outro, nome)
    if a is None or b is None:
        problemas.append(f'{nome}: não encontrado (ou ambíguo) em {"central" if a is None else "servirea"}.')
        continue
    do_servirea, da_central = normalizar(b.read_text(encoding='utf-8')), normalizar(a.read_text(encoding='utf-8'))
    # Só formatação (quebra de linha, espaço, linha em branco) não conta como divergência.
    if ''.join(''.join(do_servirea).split()) == ''.join(''.join(da_central).split()):
        continue
    diff = [l for l in difflib.unified_diff([x for x in do_servirea if x.strip()], [x for x in da_central if x.strip()],
                                            'servirea', 'central', lineterm='', n=0) if l[:1] in '+-' and l[:3] not in ('+++', '---')]
    if diff:
        problemas.append(f'{nome} diverge ({len(diff)} linhas):\n    ' + '\n    '.join(l[:140] for l in diff[:8]))

if problemas:
    print('Cópias entre Servirea e Central divergentes:\n- ' + '\n- '.join(problemas))
    sys.exit(1)
print(f'{len(COPIAS)} cópias idênticas entre Central e Servirea.')
