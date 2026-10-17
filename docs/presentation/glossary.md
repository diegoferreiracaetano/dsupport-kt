# Presentation glossary

[English](glossary.md) | [Português](glossary.pt-BR.md)

The code keeps the technical names. The presentation, the article and the stage titles use the **didactic name**, always introduced with its analogy and with the technical name in parentheses the first time. Rule of thumb: a beginner must understand the title without having seen the code.

| Stage | Technical term (in the code) | Didactic name | Analogy in one sentence |
|---|---|---|---|
| 1 | Agentic loop | **The think-and-act loop** | A manager who only gives orders: the model decides, your code does the work and reports back |
| 2 | LLM client | **The brain behind a plug** | Any language model can be plugged in, because the rest of the system only knows the socket |
| 3 | Tools | **Giving the assistant hands** | Without tools it can only talk; with them it can look up an order or open a ticket |
| 4 | RAG | **Open-book answers** | An exam where you may consult the manual first, so the answer is based on a source, not on memory |
| 5 | MCP | **A universal socket for tools** | Like USB-C: any tool that speaks the protocol plugs into any agent, built by anyone |
| 6 | Multi-agent, sub-agents | **A team of specialists with a coordinator** | A reception desk that sends each question to the right expert and assembles the answer |
| 7 | Guards (hooks) | **Safety rails** | Rules enforced by code that the model cannot see or skip: who may be asked what, and what must be hidden |
| 8.1 | Structured output + validator | **The answer as a form** | Instead of free text the assistant fills in a form with fixed fields, so code can check each one |
| 8.2 | Reviewer loop | **A second pair of eyes** | An editor reads the answer before it goes out, and after two rejections a human takes over |
| 9.1 | Events, tracing | **The flight recorder** | Every step is written down, so you can replay what happened and what it cost |
| 9.2 | Evaluation, golden dataset | **An exam with an answer key** | A fixed set of questions with known answers turns "it seems good" into a number |
| 11 | Server | **Opening the doors** | The same assistant behind a web address, so other apps can use it |
| 12 | Client app | **The face of the assistant** | The chat screen that shows sources, tickets and the live trace |

Other terms: **escalation** is "handing the case to a human"; **embedding** is "a map where similar meanings sit close together"; **groundedness** is "does every claim have a source".
