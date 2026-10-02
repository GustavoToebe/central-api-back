# Concorrência comercial — T18

Geração diária não carrega todas as contratações em entidades antecipadamente. Percorre IDs em lotes de 100 por keyset, ordenados pelo banco, e executa uma transação REQUIRES_NEW por contratação. Contratação é a primeira trava; somente depois lê cobranças, situação atual, preço e isenção. A rotina externa suspende transação ambiente (NOT_SUPPORTED), liberando locks após cada contrato. Cancelada é ignorada. Não é transação global: falha pode deixar contratos anteriores processados; repetir aproveita unicidade/competências existentes.

Entrada pública gerarCobrancas com entidade desanexada recarrega a contratação atual sob trava. Entidade já gerenciada recebe lock preservando mudanças do chamador, que deve seguir a ordem da raiz antes das mutações. Recalcular abertas/remover/cancelar também protegem a raiz. Pagamento/isenção seguem contratação antes de cobranças e outbox. Não introduz débito automático nem chamada externa dentro dessas transações.

Testes simultâneos em PostgreSQL: dois jobs sem competências duplicadas; job com isenção sem aberta isentável; job com baixa sem perder pagamento. Testes anteriores de checkout/conciliação seguem válidos. Nenhuma cobrança real foi gerada nem baixada. Revisão cobre fluxos existentes, não certifica toda combinação de processos externos/falhas futuras.

Homologar geração diária/isenção/baixa, alteração de plano e cancelamento junto ao ambiente de teste do provedor. A raiz é o ponto comum de serialização; nova mutação comercial deve respeitá-la antes de ler/modificar cobranças.
