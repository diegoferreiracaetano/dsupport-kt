# Evals-driven improvement log

[English](improvement-log.md) | [Português](improvement-log.pt-BR.md)

How a measured evaluation found and fixed real problems in the assistant, on one afternoon and for about 30 cents of dollar (`claude-haiku-5-5`).

## Method

15 golden cases (`data/golden.json`), each run 3 times because a language model is not deterministic: with the same code, three cases changed outcome between two single runs. Every report is in `reports/`. Metrics are computed by plain code, no model judges them.

## Before and after (15 cases × 3 runs)

| Metric | Before | After |
|---|---|---|
| Routing accuracy (intent) | 100% | 100% |
| Groundedness (every claim backed by a result) | 100% | 100% |
| No forbidden content (identity leak check) | 100% | 100% |
| Expected facts present | 93% | **100%** |
| Expected sources cited | 86% | **100%** |
| Escalation precision | 80% | **100%** |
| Escalation recall | 100% | 100% |
| Review fallback (a human is called because no draft passed review) | 9% | **0%** |
| Average latency | 11.0 s | 9.8 s |
| Tokens per execution | 9,510 | 9,053 |

Reports: `eval-2026-10-17-2141-baseline-repeat3.md` and `eval-2026-10-17-2156-after-fixes-repeat3.md`.

## What the numbers revealed

1. **Two of my own rules contradicted each other.** The answer's `confidence` was defined as "how well the results support the answer", while a code check rejected low confidence unless the assistant escalated. Asking the customer for a missing email has no results behind it, so it always got low confidence, was rejected three times and ended in an unnecessary human handoff. Fix: confidence now means "how sure I am this answer is correct and helpful", and asking for a missing detail counts as a confident answer.
2. **The reviewer was inventing objections.** It rejected "please send your email" and "I can open a ticket" because it did not know the assistant can do those things. Fix: the reviewer prompt now judges only claims against the results and explicitly accepts asking for details and offering a human.
3. **A routing error that was deterministic.** "Where do I find my tracking code?" always went to the orders specialist, never to the help-center article. Fix: the orchestrator prompt sends how-to questions to the knowledge specialist and reserves orders for one specific order.
4. **A test case that was my assumption, not a requirement.** For "Do you sell airline tickets?" the golden case demanded an escalation. After the fixes the assistant answered honestly and offered a ticket instead. That is good behaviour, so the case is now unjudged for escalation, with the reason written in the dataset (`note`), rather than relabelled to look like a pass.

## Cautions that stay

- A single case fails by chance roughly 10% to 25% of the time on one run (`g02` and `g08` each failed once and then passed 3 of 3). Judge rates over repeated runs, never one execution.
- The golden set is small (15). It proves the method, it does not prove the assistant is ready for production.
- The evaluation pins the date of "today" (`2026-10-15`) so that "late" does not change with the calendar.
