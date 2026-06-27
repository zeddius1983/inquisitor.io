# Open several accounts from a table

Feature: opening many accounts in one go and then funding them, parameterised over
Gherkin Scenario Outlines. The steps name neither endpoints nor request bodies, so
the model reads the application's OpenAPI description to choose the right call.

## Open every account in the Examples table

- **Given** none of the owners below has an account yet
- **When** an account is opened for each `<owner>` in `<currency>`
- **Then** every response is `201 Created`
- **And** each returned account has a numeric `id`, a `balance` of `0.00`, and the
  matching `<currency>`

Examples:

| owner     | currency |
|-----------|----------|
| Jackie C  | CNY      |
| Satoshi N | JPY      |
| Gordon R  | GBP      |

## Confirm every account exists

- **Given** all the accounts in the Examples table have been opened
- **When** each `<owner>` is looked up again
- **Then** an account exists for them with the stated `<currency>` and a `balance`
  of `0.00`

## Deposit into each account

- **Given** the accounts opened above
- **When** a single deposit of any amount within the row's `<min>`-`<max>` range is
  made into each `<owner>`'s account
- **Then** every deposit response is `201 Created`
- **And** each account's `balance` afterwards equals the deposited amount and lies
  within the row's `<min>`-`<max>` range

Examples:

| owner     | min | max  |
|-----------|-----|------|
| Jackie C  | 100 | 500  |
| Satoshi N | 1   | 1000 |
| Gordon R  | 20  | 60   |
