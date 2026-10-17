# Glossário da apresentação

[English](glossary.md) | [Português](glossary.pt-BR.md)

O código mantém os nomes técnicos. A apresentação, o artigo e os títulos das etapas usam o **nome didático**, sempre apresentado com a analogia e com o nome técnico entre parênteses na primeira vez. Regra prática: quem está começando precisa entender o título sem ter visto o código.

| Etapa | Termo técnico (no código) | Nome didático | Analogia em uma frase |
|---|---|---|---|
| 1 | Loop agêntico | **O ciclo pensar e agir** | Um gerente que só dá ordens: o modelo decide, o seu código faz o trabalho e conta o resultado |
| 2 | Cliente do LLM | **O cérebro numa tomada** | Qualquer modelo de linguagem se encaixa, porque o resto do sistema só conhece a tomada |
| 3 | Ferramentas | **Dar mãos ao assistente** | Sem ferramentas ele só conversa; com elas consulta um pedido ou abre um ticket |
| 4 | RAG | **Resposta com consulta** | Uma prova em que se pode consultar o manual antes, e a resposta vem de uma fonte, não da memória |
| 5 | MCP | **Uma tomada universal para ferramentas** | Como o USB-C: qualquer ferramenta que fale o protocolo encaixa em qualquer agente, feita por qualquer pessoa |
| 6 | Multiagente, subagentes | **Uma equipe de especialistas com um coordenador** | Uma recepção que manda cada pergunta ao especialista certo e monta a resposta |
| 7 | Guardas (hooks) | **Travas de segurança** | Regras que o código impõe e que o modelo não vê nem pula: quem pode perguntar o quê e o que precisa ser escondido |
| 8.1 | Saída estruturada + validador | **A resposta em formulário** | Em vez de texto livre, o assistente preenche um formulário de campos fixos, e o código confere cada um |
| 8.2 | Ciclo de revisão | **Uma segunda opinião** | Um editor lê a resposta antes de ela sair, e depois de duas recusas um humano assume |
| 9.1 | Eventos, rastreamento | **A caixa-preta** | Cada passo fica registrado, para repetir o que aconteceu e quanto custou |
| 9.2 | Avaliação, golden dataset | **Uma prova com gabarito** | Um conjunto fixo de perguntas com respostas conhecidas transforma "parece bom" em um número |
| 11 | Servidor | **Abrir as portas** | O mesmo assistente atrás de um endereço web, para outros apps usarem |
| 12 | App cliente | **A cara do assistente** | A tela de chat que mostra fontes, tickets e o rastro ao vivo |

Outros termos: **escalação** é "passar o caso a um humano"; **embedding** é "um mapa onde significados parecidos ficam perto"; **groundedness** é "cada afirmação tem uma fonte?".
