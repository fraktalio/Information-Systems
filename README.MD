# Information Systems in Kotlin

![fmodel](fmodel.webp)

## Data & Behaviour = Information (Algebra of the Business)

- Data (`examples/api.kt`): Commands, Events, State
- Behaviour (`examples/domain.kt`): Exhaustive pattern matching on data

## Overview

This project encodes information systems as composable algebraic structures using pure Kotlin functions.

It unifies two classical architectural styles:

- 🏛️ State-stored systems — store current state directly
- 🌀 Event-sourced systems — store past events and reconstruct state from them

Both are captured under a single algebraic model — the `System` type.

```
decide       : Command × State → Sequence<Event>
evolve       : State × Event   → State
initialState : ()              → State
```

### Algebraic View

| Concept       | Mathematical Structure    | Notes                                                                     |
|---------------|---------------------------|---------------------------------------------------------------------------|
| `mapCommand`  | **Contravariant Functor** | Maps input command types                                                  |
| `mapEvent`    | **Profunctor (dimap)**    | Contravariant in input events, covariant in output                        |
| `mapState`    | **Profunctor (dimap)**    | Contravariant in input state, covariant in output                         |
| `combine`     | **Monoidal Product**      | Combines two systems into a product system                                |
| `emptySystem` | **Monoidal Identity**     | Represents the “no-op” system (`GeneralSystem<Nothing?, Unit, Nothing?>`) |

## 📚 Learn More - [fmodel.fraktalio.com](fmodel.fraktalio.com)

![domain modeling](event-modeling.webp)
