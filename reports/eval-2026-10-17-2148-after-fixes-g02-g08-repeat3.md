# Evaluation 2026-10-17 (claude-haiku-5-5, reference date 2026-10-15, 3 run(s) per case)

6 cases, 0 with an execution error.

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
| Average latency | 10604 ms |
| Model calls per case | 7.5 |
| Tokens per case | 9232 |

| Case | Intent | Escalated (expected) | Grounded | Facts | Sources | No leak | ms | Calls | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| g02#1 | yes | no (no) | yes | yes | yes | yes | 9329 | 6 |  |
| g02#2 | yes | no (no) | yes | yes | yes | yes | 7758 | 6 |  |
| g02#3 | yes | no (no) | yes | yes | yes | yes | 7088 | 6 |  |
| g08#1 | yes | yes (yes) | yes | yes | - | yes | 12623 | 9 |  |
| g08#2 | yes | yes (yes) | yes | yes | - | yes | 12675 | 9 |  |
| g08#3 | yes | yes (yes) | yes | yes | - | yes | 14151 | 9 |  |
