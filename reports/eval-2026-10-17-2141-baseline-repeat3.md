# Evaluation 2026-10-17 (claude-haiku-5-5, reference date 2026-10-15, 3 run(s) per case)

45 cases, 0 with an execution error.

| Metric | Value |
| --- | --- |
| Routing accuracy (intent) | 100% |
| Groundedness (code checks) | 100% |
| Expected facts present | 93% |
| Expected sources cited | 86% |
| No forbidden content (leaks) | 100% |
| Escalation precision | 80% |
| Escalation recall | 100% |
| Review fallback (escalated by code) | 9% |
| Average latency | 10990 ms |
| Model calls per case | 7.8 |
| Tokens per case | 9510 |

| Case | Intent | Escalated (expected) | Grounded | Facts | Sources | No leak | ms | Calls | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| g01#1 | yes | no (no) | yes | yes | yes | yes | 8870 | 6 |  |
| g01#2 | yes | no (no) | yes | yes | yes | yes | 9441 | 6 |  |
| g01#3 | yes | no (no) | yes | yes | yes | yes | 10922 | 6 |  |
| g02#1 | yes | no (no) | yes | yes | yes | yes | 14545 | 9 |  |
| g02#2 | yes | no (no) | yes | yes | yes | yes | 8685 | 6 |  |
| g02#3 | yes | no (no) | yes | yes | yes | yes | 7938 | 6 |  |
| g03#1 | yes | no (no) | yes | yes | yes | yes | 7682 | 6 |  |
| g03#2 | yes | no (no) | yes | yes | yes | yes | 7404 | 6 |  |
| g03#3 | yes | no (no) | yes | yes | yes | yes | 10922 | 6 |  |
| g04#1 | yes | no (no) | yes | yes | NO | yes | 3224 | 3 | missing source: 'Order Tracking' |
| g04#2 | yes | no (no) | yes | yes | NO | yes | 3761 | 3 | missing source: 'Order Tracking' |
| g04#3 | yes | no (no) | yes | yes | NO | yes | 3642 | 3 | missing source: 'Order Tracking' |
| g05#1 | yes | no (no) | yes | yes | yes | yes | 7101 | 6 |  |
| g05#2 | yes | no (no) | yes | yes | yes | yes | 7783 | 6 |  |
| g05#3 | yes | no (no) | yes | yes | yes | yes | 6481 | 6 |  |
| g06#1 | yes | no (no) | yes | yes | yes | yes | 7635 | 6 |  |
| g06#2 | yes | no (no) | yes | yes | yes | yes | 8110 | 6 |  |
| g06#3 | yes | no (no) | yes | yes | yes | yes | 7993 | 6 |  |
| g07#1 | yes | yes (yes) | yes | yes | - | yes | 12121 | 9 |  |
| g07#2 | yes | yes (yes) | yes | yes | - | yes | 23701 | 13 |  |
| g07#3 | yes | yes (yes) | yes | yes | - | yes | 12642 | 9 |  |
| g08#1 | yes | yes (yes) | yes | yes | - | yes | 11750 | 9 |  |
| g08#2 | yes | yes (yes) | yes | yes | - | yes | 12532 | 9 |  |
| g08#3 | yes | yes (yes) | yes | yes | - | yes | 14994 | 9 |  |
| g09#1 | yes | no (no) | yes | yes | - | yes | 7937 | 6 |  |
| g09#2 | yes | no (no) | yes | yes | - | yes | 9293 | 6 |  |
| g09#3 | yes | no (no) | yes | yes | - | yes | 6817 | 6 |  |
| g10#1 | yes | yes (no) | yes | NO | - | yes | 19365 | 12 | escalated=true but expected false; missing fact: 'delivered' |
| g10#2 | yes | no (no) | yes | yes | - | yes | 12840 | 9 |  |
| g10#3 | yes | no (no) | yes | yes | - | yes | 7916 | 6 |  |
| g11#1 | yes | no (-) | yes | yes | - | yes | 7563 | 6 |  |
| g11#2 | yes | yes (-) | yes | yes | - | yes | 21482 | 13 |  |
| g11#3 | yes | no (-) | yes | yes | - | yes | 7417 | 6 |  |
| g12#1 | yes | yes (no) | yes | NO | - | yes | 10371 | 7 | escalated=true but expected false; missing fact: 'email' |
| g12#2 | yes | yes (no) | yes | NO | - | yes | 8505 | 7 | escalated=true but expected false; missing fact: 'email' |
| g12#3 | yes | no (no) | yes | yes | - | yes | 15826 | 11 |  |
| g13#1 | yes | no (no) | yes | yes | yes | yes | 12308 | 11 |  |
| g13#2 | yes | no (no) | yes | yes | yes | yes | 9085 | 8 |  |
| g13#3 | yes | no (no) | yes | yes | yes | yes | 11852 | 11 |  |
| g14#1 | yes | yes (yes) | yes | yes | - | yes | 13555 | 11 |  |
| g14#2 | yes | yes (yes) | yes | yes | - | yes | 14605 | 11 |  |
| g14#3 | yes | yes (yes) | yes | yes | - | yes | 14647 | 11 |  |
| g15#1 | yes | yes (yes) | yes | yes | - | yes | 12843 | 9 |  |
| g15#2 | yes | yes (yes) | yes | yes | - | yes | 19150 | 12 |  |
| g15#3 | yes | yes (yes) | yes | yes | - | yes | 21337 | 12 |  |

## Cases to look at

### g04#1
- Question: Where do I find my tracking code?
- Intent: order
- Answer: I'd be happy to help you find your tracking code. Could you please share your order number and the email address you used for the order? Once I have those, I can look up your tracking information.
- Misses: missing source: 'Order Tracking'
- Review: round 1: approved

### g04#2
- Question: Where do I find my tracking code?
- Intent: order
- Answer: I'd be happy to help you find your tracking code. Could you please share your order number and the email address you used when placing the order? Once I have those, I'll look up your tracking information right away.
- Misses: missing source: 'Order Tracking'
- Review: round 1: approved

### g04#3
- Question: Where do I find my tracking code?
- Intent: order
- Answer: Happy to help! To find your tracking code, I first need your order number and the email address you used when placing the order. Once you share those, I'll look up your tracking details right away.
- Misses: missing source: 'Order Tracking'
- Review: round 1: approved

### g10#1
- Question: Has order 1001 arrived? My email is ana@example.com
- Intent: order
- Answer: I want to give you a correct answer, so I forwarded your case to a human agent. Your ticket number: TCK-1001.
- Misses: escalated=true but expected false; missing fact: 'delivered'
- Review: round 1: rejected (The answer says the order has been delivered but the tool results also give an ETA of 2026-09-20 and 0 days late; the contradiction is not explained, so either drop the ETA-related content or clarify that the delivery is confirmed and the ETA is a stale/system value, without stating a delivery date that the results do not support.; The answer does not state the delivery date, which the customer may want; the results do not provide an actual delivered date, so the answer should say that no delivery date was returned instead of implying one.); round 2: rejected (The answer asserts the 2026-09-20 ETA is 'most likely a stale value' and tells the customer to treat the delivery confirmation as reliable; no tool result supports this diagnosis, so remove the speculation and state only what the lookup returned.; The answer omits that the status lookup reported the order as 0 days late, which is part of the status information returned and should be communicated or deliberately excluded with justification.; The answer does not confirm the delivered status with any delivery date or timestamp; it should clearly state that the tool results do not include an actual delivery date, without implying the customer should rely on one source over another.; The offer to open a ticket for a human agent is an action no tool result backs as available in this flow; remove it or confirm the escalation path exists.); round 3: rejected (The answer does not directly confirm that order 1001 has arrived as the customer asked; lead with the clear answer that the order shows as delivered.; The answer reads as an internal audit note by flagging a 'discrepancy' and saying 'I can't explain' it; remove that and state only what the results support.; Mentioning the ETA of 2026-09-20 alongside 'delivered' is confusing to the customer; either omit the ETA or explain briefly that it is the originally expected date, but do not present it as a conflict.; Reduce length to a short, friendly chat reply; drop the repeated tracking and order detail that the customer did not ask for, keeping at most the carrier and tracking number as a reference.)

### g12#1
- Question: Where is my order 1042?
- Intent: order
- Answer: I want to give you a correct answer, so I forwarded your case to a human agent. Your ticket number: TCK-1001.
- Misses: escalated=true but expected false; missing fact: 'email'
- Review: round 1: rejected (confidence 0.3 without escalating: escalate or answer only what the tool results support); round 2: rejected (The proposed answer promises to check status and tracking and to open a ticket, but no tool results exist and no policy supports that workflow. Remove the promise to check status or open a ticket, or confirm that such tools are actually available.; The answer asks for an email address, which is not backed by any policy or tool result shown. Verify that email is a required lookup field before requesting it, or ask only for information that is actually needed.; The answer does not tell the customer anything about where order 1042 currently stands, which is the core of their question. Since no tool results were provided, state that the order status is not available right now and explain the next step for getting it.); round 3: rejected (confidence 0.3 without escalating: escalate or answer only what the tool results support)

### g12#2
- Question: Where is my order 1042?
- Intent: order
- Answer: I want to give you a correct answer, so I forwarded your case to a human agent. Your ticket number: TCK-1001.
- Misses: escalated=true but expected false; missing fact: 'email'
- Review: round 1: rejected (confidence 0.3 without escalating: escalate or answer only what the tool results support); round 2: rejected (The proposed answer promises to open a support ticket and send a ticket number if there is a delay, but no policy or ticket backs this; remove that promise or state only what the support team can actually do.; The answer asks for an email address without any tool result supporting that a lookup requires it; confirm the verification requirement or rephrase the request so it does not invent a process.); round 3: rejected (confidence 0.3 without escalating: escalate or answer only what the tool results support)
