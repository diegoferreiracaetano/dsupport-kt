# Evaluation 2026-10-17 (claude-haiku-5-5, reference date 2026-10-15, 3 run(s) per case)

45 cases, 0 with an execution error.

| Metric | Value |
| --- | --- |
| Routing accuracy (intent) | 100% |
| Groundedness (code checks) | 100% |
| Expected facts present | 100% |
| Expected sources cited | 100% |
| No forbidden content (leaks) | 100% |
| Escalation precision | 100% |
| Escalation recall | 100% |
| Review fallback (escalated by code) | 0% |
| Average latency | 9822 ms |
| Model calls per case | 7.1 |
| Tokens per case | 9053 |

| Case | Intent | Escalated (expected) | Grounded | Facts | Sources | No leak | ms | Calls | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| g01#1 | yes | no (no) | yes | yes | yes | yes | 8017 | 6 |  |
| g01#2 | yes | no (no) | yes | yes | yes | yes | 8626 | 6 |  |
| g01#3 | yes | no (no) | yes | yes | yes | yes | 7101 | 6 |  |
| g02#1 | yes | no (no) | yes | yes | yes | yes | 7698 | 6 |  |
| g02#2 | yes | no (no) | yes | yes | yes | yes | 7958 | 6 |  |
| g02#3 | yes | no (no) | yes | yes | yes | yes | 7669 | 6 |  |
| g03#1 | yes | no (no) | yes | yes | yes | yes | 12082 | 9 |  |
| g03#2 | yes | no (no) | yes | yes | yes | yes | 8090 | 6 |  |
| g03#3 | yes | no (no) | yes | yes | yes | yes | 8246 | 6 |  |
| g04#1 | yes | no (no) | yes | yes | yes | yes | 8294 | 6 |  |
| g04#2 | yes | no (no) | yes | yes | yes | yes | 12890 | 9 |  |
| g04#3 | yes | no (no) | yes | yes | yes | yes | 8564 | 6 |  |
| g05#1 | yes | no (no) | yes | yes | yes | yes | 9858 | 6 |  |
| g05#2 | yes | no (no) | yes | yes | yes | yes | 6862 | 6 |  |
| g05#3 | yes | no (no) | yes | yes | yes | yes | 9708 | 6 |  |
| g06#1 | yes | no (no) | yes | yes | yes | yes | 8094 | 6 |  |
| g06#2 | yes | no (no) | yes | yes | yes | yes | 7573 | 6 |  |
| g06#3 | yes | no (no) | yes | yes | yes | yes | 6922 | 6 |  |
| g07#1 | yes | yes (yes) | yes | yes | - | yes | 13138 | 9 |  |
| g07#2 | yes | yes (yes) | yes | yes | - | yes | 14538 | 9 |  |
| g07#3 | yes | yes (yes) | yes | yes | - | yes | 13306 | 9 |  |
| g08#1 | yes | yes (yes) | yes | yes | - | yes | 14670 | 9 |  |
| g08#2 | yes | yes (yes) | yes | yes | - | yes | 13651 | 9 |  |
| g08#3 | yes | yes (yes) | yes | yes | - | yes | 11012 | 9 |  |
| g09#1 | yes | no (no) | yes | yes | - | yes | 6959 | 6 |  |
| g09#2 | yes | no (no) | yes | yes | - | yes | 7290 | 6 |  |
| g09#3 | yes | no (no) | yes | yes | - | yes | 8443 | 6 |  |
| g10#1 | yes | no (no) | yes | yes | - | yes | 11960 | 6 |  |
| g10#2 | yes | no (no) | yes | yes | - | yes | 6840 | 6 |  |
| g10#3 | yes | no (no) | yes | yes | - | yes | 7709 | 6 |  |
| g11#1 | yes | no (-) | yes | yes | - | yes | 7611 | 6 |  |
| g11#2 | yes | no (-) | yes | yes | - | yes | 7638 | 6 |  |
| g11#3 | yes | no (-) | yes | yes | - | yes | 9543 | 6 |  |
| g12#1 | yes | no (no) | yes | yes | - | yes | 3410 | 3 |  |
| g12#2 | yes | no (no) | yes | yes | - | yes | 3419 | 3 |  |
| g12#3 | yes | no (no) | yes | yes | - | yes | 3714 | 3 |  |
| g13#1 | yes | no (no) | yes | yes | yes | yes | 8886 | 8 |  |
| g13#2 | yes | no (no) | yes | yes | yes | yes | 21597 | 14 |  |
| g13#3 | yes | no (no) | yes | yes | yes | yes | 14015 | 11 |  |
| g14#1 | yes | yes (-) | yes | yes | - | yes | 14130 | 11 |  |
| g14#2 | yes | no (-) | yes | yes | - | yes | 12332 | 10 |  |
| g14#3 | yes | no (-) | yes | yes | - | yes | 8410 | 6 |  |
| g15#1 | yes | yes (yes) | yes | yes | - | yes | 15860 | 9 |  |
| g15#2 | yes | yes (yes) | yes | yes | - | yes | 15334 | 9 |  |
| g15#3 | yes | yes (yes) | yes | yes | - | yes | 12367 | 9 |  |
