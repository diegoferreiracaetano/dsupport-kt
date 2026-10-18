# Evaluation 2026-10-17 (claude-haiku-5-5, reference date 2026-10-15, 1 run(s) per case)

11 cases, 0 with an execution error.

| Metric | Value |
| --- | --- |
| Routing accuracy (intent) | 100% |
| Groundedness (code checks) | 100% |
| Expected facts present | 91% |
| Expected sources cited | 100% |
| No forbidden content (leaks) | 100% |
| Escalation precision | 100% |
| Escalation recall | 67% |
| Review fallback (escalated by code) | 0% |
| Average latency | 9436 ms |
| Model calls per case | 6.7 |
| Tokens per case | 8461 |

| Case | Intent | Escalated (expected) | Grounded | Facts | Sources | No leak | ms | Calls | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| g01 | yes | no (no) | yes | yes | yes | yes | 7605 | 6 |  |
| g02 | yes | no (no) | yes | NO | yes | yes | 8993 | 6 | missing fact: 'business days' |
| g03 | yes | no (no) | yes | yes | yes | yes | 8691 | 6 |  |
| g05 | yes | no (no) | yes | yes | yes | yes | 7685 | 6 |  |
| g06 | yes | no (no) | yes | yes | yes | yes | 7414 | 6 |  |
| g07 | yes | yes (yes) | yes | yes | - | yes | 12316 | 9 |  |
| g08 | yes | no (yes) | yes | yes | - | yes | 9333 | 6 | escalated=false but expected true |
| g09 | yes | no (no) | yes | yes | - | yes | 9957 | 6 |  |
| g11 | yes | no (-) | yes | yes | - | yes | 7961 | 6 |  |
| g13 | yes | no (no) | yes | yes | yes | yes | 8076 | 8 |  |
| g15 | yes | yes (yes) | yes | yes | - | yes | 15768 | 9 |  |

## Cases to look at

### g02
- Question: How long does a refund take to arrive?
- Intent: policy
- Answer: Refunds go back to your original payment method, and gift card purchases are refunded as store credit. Accepted payment methods are Visa, Mastercard, American Express, PayPal, and store gift cards. I couldn't find exact arrival times in our help center. If you share your order number and email, I can check your refund status or open a ticket with a human agent to confirm the timeframe.
- Misses: missing fact: 'business days'
- Review: round 1: approved

### g08
- Question: Is order 1046 late? My email is fernanda@example.com
- Intent: order
- Answer: Order 1046 is running late. It's currently in transit and is 14 days past its expected delivery date of October 1, 2026. Since it's delayed, I can open a ticket for a human agent to look into it. Would you like me to do that?
- Misses: escalated=false but expected true
- Review: round 1: approved
