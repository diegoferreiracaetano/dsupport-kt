# Diário de melhoria guiada por evals

[English](improvement-log.md) | [Português](improvement-log.pt-BR.md)

Como uma avaliação medida encontrou e corrigiu problemas reais no assistente, em uma tarde e por cerca de 30 centavos de dólar (`claude-haiku-5-5`).

## Método

15 casos de referência (`data/golden.json`), cada um rodado 3 vezes, porque um modelo de linguagem não é determinístico: com o mesmo código, três casos mudaram de resultado entre duas execuções únicas. Todos os relatórios estão em `reports/`. As métricas são calculadas por código simples, sem nenhum modelo como juiz.

## Antes e depois (15 casos × 3 execuções)

| Métrica | Antes | Depois |
|---|---|---|
| Acerto de roteamento (intenção) | 100% | 100% |
| Groundedness (toda afirmação tem lastro num resultado) | 100% | 100% |
| Sem conteúdo proibido (vazamento de identidade) | 100% | 100% |
| Fatos esperados presentes | 93% | **100%** |
| Fontes esperadas citadas | 86% | **100%** |
| Precisão de escalação | 80% | **100%** |
| Recall de escalação | 100% | 100% |
| Plano B da revisão (um humano é chamado porque nenhuma versão passou) | 9% | **0%** |
| Latência média | 11,0 s | 9,8 s |
| Tokens por execução | 9.510 | 9.053 |

Relatórios: `eval-2026-10-17-2141-baseline-repeat3.md` e `eval-2026-10-17-2156-after-fixes-repeat3.md`.

## O que os números revelaram

1. **Duas regras minhas se contradiziam.** A `confidence` da resposta era definida como "o quanto os resultados sustentam a resposta", enquanto uma checagem de código rejeitava confiança baixa a menos que o assistente escalasse. Pedir o e-mail que falta não tem resultado por trás, então sempre recebia confiança baixa, era rejeitado três vezes e terminava num encaminhamento desnecessário a um humano. Correção: a confiança agora significa "o quanto tenho certeza de que esta resposta é correta e útil", e pedir um dado que falta conta como resposta confiante.
2. **O revisor inventava objeções.** Ele reprovava "me envie seu e-mail" e "posso abrir um ticket" porque não sabia que o assistente faz essas coisas. Correção: o prompt do revisor agora julga só afirmações contra os resultados e aceita explicitamente pedir dados e oferecer um humano.
3. **Um erro de roteamento determinístico.** "Onde encontro meu código de rastreio?" sempre ia ao especialista de pedidos, nunca ao artigo da central de ajuda. Correção: o prompt do orquestrador manda perguntas de "como/onde" ao especialista de conhecimento e reserva o de pedidos para um pedido específico.
4. **Um caso de teste que era suposição minha, não requisito.** Em "Vocês vendem passagem aérea?" o caso exigia escalação. Depois das correções o assistente respondeu com honestidade e ofereceu um ticket. É um bom comportamento, então o caso passou a **não julgar a escalação**, com o motivo escrito no dataset (`note`), em vez de ser reclassificado para parecer aprovado.

## Cuidados que permanecem

- Um caso isolado falha por acaso entre 10% e 25% das vezes numa execução (`g02` e `g08` falharam uma vez e depois passaram 3 de 3). Julgue taxas sobre execuções repetidas, nunca uma execução só.
- O conjunto de referência é pequeno (15). Ele prova o método, não prova que o assistente está pronto para produção.
- A avaliação fixa a data de "hoje" (`2026-10-15`) para que "atrasado" não mude com o calendário.
