# Information Systems in Kotlin

![fmodel](fmodel.webp)

Information systems as composable algebraic structures. Unifies state-stored (🏛️ store current state) and
event-sourced (🌀 store past events) architectures under a single model.

## The Journey: From General to Specific

The library is structured as a progression from the most general abstraction to practical implementations. Each file
builds upon the previous one through specialization:

```
┌─────────────────────────────────────────────────────────────────┐
│ IGeneralSystem<Command, InState, OutState, InEvent, OutEvent>  │
│ (5 type parameters - most general)                              │
│ • decide: (Command, InState) -> Sequence<OutEvent>              │
│ • evolve: (InState, InEvent) -> OutState                        │
│ • initialState: () -> OutState                                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ InState == OutState == State
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ IDynamicSystem<Command, State, InEvent, OutEvent>               │
│ extends IGeneralSystem<Command, State, State, InEvent, OutEvent>│
│ (4 type parameters)                                              │
│ • State types unified                                            │
│ • Can convert to EventSourcedSystem                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ InEvent == OutEvent == Event
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ ISystem<Command, State, Event>                                  │
│ extends IDynamicSystem<Command, State, Event, Event>            │
│ (3 type parameters - most common)                               │
│ • Event types unified                                            │
│ • Can convert to StateStoredSystem                               │
│ • Can convert to EventSourcedSystem (limited)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Given-When-Then DSL
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ SystemSpecification (Testing DSL)                               │
│ • givenEvents / thenEvents (event-sourced, IDynamicSystem)      │
│ • givenState / thenState (state-stored, ISystem)                │
│ • whenCommand (identity for readability)                         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Add persistence + metadata + transactions
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ IStateRepository / IEventRepository                             │
│ (Infrastructure layer - metadata + transaction support)          │
│ • Metadata never leaks into domain                               │
│ • Explicit transaction handle (Txn) for atomicity               │
│ • inTransaction / handle() extension functions                   │
└─────────────────────────────────────────────────────────────────┘
```

### 1. GeneralSystem (5 type parameters)

`src/1_GeneralSystem.kt` — The foundation

The most general form that captures all possible information systems:

```kotlin
interface IGeneralSystem<Command, InState, OutState, InEvent, OutEvent> {
    val decide: (Command, InState) -> Sequence<OutEvent>
    val evolve: (InState, InEvent) -> OutState
    val initialState: () -> OutState
}
```

This abstraction allows:

- Different input/output state types (InState ≠ OutState)
- Different input/output event types (InEvent ≠ OutEvent)
- Full functorial and profunctor transformations
- Monoidal composition via `combine`

### 2. DynamicSystem (4 type parameters)

`src/2_DynamicSystem.kt` — First specialization

Constrains state types to be equal: `InState == OutState == State`

```kotlin
interface IDynamicSystem<Command, State, InEvent, OutEvent> :
    IGeneralSystem<Command, State, State, InEvent, OutEvent>
```

This enables:

- Event-sourced systems (events can differ: InEvent ≠ OutEvent)
- State reconstruction from event sequences
- Conversion to `EventSourcedSystem` type alias

### 3. System (3 type parameters)

`src/3_System.kt` — Second specialization

Further constrains event types to be equal: `InEvent == OutEvent == Event`

```kotlin
interface ISystem<Command, State, Event> :
    IDynamicSystem<Command, State, Event, Event>
```

This is the most common form, enabling:

- Both state-stored and event-sourced architectures
- Conversion to `StateStoredSystem` type alias
- Conversion to `EventSourcedSystem` type alias (limited to InEvent == OutEvent)
- Full bidirectional system representation

Note: The constraint `InEvent == OutEvent` means this system can only handle event-sourced scenarios where the events
you read are the same type as the events you produce. For more flexible event transformations (InEvent ≠ OutEvent), use
`DynamicSystem` instead.

### 4. SystemSpecification (Testing DSL)

`src/4_SystemSpecification.kt` — Given-When-Then specification DSL

A lightweight DSL for specifying and verifying system behavior in both event-sourced and state-stored styles:

**Event-sourced** — given past events, when a command is issued, then expect new events:

```kotlin
counterSystem.givenEvents(emptyList()) {
    whenCommand(IncrementCounterCommand.Increment(5))
} thenEvents listOf(IncrementCounterEvent.Incremented(5, 5))
```

**State-stored** — given a current state, when a command is issued, then expect a new state:

```kotlin
counterSystem.givenState(CounterState(0, 0)) {
    whenCommand(IncrementCounterCommand.Increment(5))
} thenState CounterState(incValue = 5, decValue = 0)
```

| Function      | Role      | Works with                               |
|---------------|-----------|------------------------------------------|
| `givenEvents` | **Given** | `IDynamicSystem` (InEvent ≠ OutEvent)    |
| `givenState`  | **Given** | `ISystem` (state-stored)                 |
| `whenCommand` | **When**  | Any command type                         |
| `thenEvents`  | **Then**  | Asserts produced events match expected   |
| `thenState`   | **Then**  | Asserts resulting state matches expected |

### 5. StatefulSystem (Persistence layer)

`src/5_StatefulSystem.kt` — Practical implementation

Adds repository interfaces for persistence with metadata and explicit transaction support:

```kotlin
interface IStateRepository<Command, CommandMetadata, State, StateMetadata, Txn> {
    suspend fun fetchState(command: Pair<Command, CommandMetadata>, txn: Txn): Pair<State, StateMetadata>?
    suspend fun save(state: Pair<State, CommandMetadata>, txn: Txn): Pair<State, StateMetadata>
    suspend fun <T> inTransaction(block: suspend (Txn) -> T): T
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: StateStoredSystem<Command, State>
    ): Pair<State, StateMetadata>
}

interface IEventRepository<Command, CommandMetadata, InEvent, InEventMetadata, OutEvent, OutEventMetadata, Txn> {
    suspend fun fetchEvents(command: Pair<Command, CommandMetadata>, txn: Txn): Sequence<Pair<InEvent, InEventMetadata>>
    suspend fun save(
        events: Sequence<OutEvent>,
        commandMetadata: CommandMetadata,
        txn: Txn
    ): Sequence<Pair<OutEvent, OutEventMetadata>>
    suspend fun <T> inTransaction(block: suspend (Txn) -> T): T
    suspend fun process(
        command: Pair<Command, CommandMetadata>,
        system: EventSourcedSystem<Command, InEvent, OutEvent>
    ): Sequence<Pair<OutEvent, OutEventMetadata>>
}
```

This provides:

- Separation of domain logic from persistence
- Explicit transaction handle (`Txn`) for atomicity — the fetch → compute → save sequence runs within a single
  transaction
- Flexible locking strategy: pessimistic (e.g., `SELECT ... FOR UPDATE`) or optimistic (e.g.,
  `UPDATE ... WHERE version = ?`)
- Convenient `handle` extension functions
- Metadata support at infrastructure/application level

#### Metadata: Infrastructure Concern, Not Domain Concern

A key design principle: metadata exists only at the infrastructure/application boundaries and never leaks into the
domain layer.

Domain systems (`IGeneralSystem`, `IDynamicSystem`, `ISystem`) remain pure and focused on business logic:

```kotlin
decide: (Command, State) -> Sequence<Event>  // No metadata here
```

Metadata is introduced at the repository level for infrastructure concerns:

- Versioning and optimistic locking
- Audit trails (who, when, from where)
- Correlation IDs for distributed tracing
- Event sequence numbers and timestamps
- Tenant/user context for multi-tenancy

The `process` method orchestrates the flow:

1. Fetch data with metadata from storage
2. Extract pure domain data and pass to domain system (metadata-free)
3. Domain system produces new data (pure business logic)
4. Attach metadata and persist

This separation keeps domain logic testable, composable, and free from infrastructure concerns.

#### Composition Through Multiple Type Constraints

The `handle` extension functions demonstrate elegant composition at the type level:

```kotlin
suspend fun <SYSTEM, Command, State, Event, CommandMetadata, EventMetadata, Txn>
        SYSTEM.handle(command: Pair<Command, CommandMetadata>): Sequence<Pair<Event, EventMetadata>>
        where SYSTEM : IEventRepository<Command, CommandMetadata, Event, EventMetadata, Event, EventMetadata, Txn>,
              SYSTEM : ISystem<Command, State, Event> =
    process(command, inEventSourcedSystem())
```

This composition pattern:

- **Type-level composition**: Any type implementing both `IEventRepository` and `ISystem` automatically gains `handle`
  capability
- **Separation of concerns**: Repository provides persistence, System provides domain logic
- **No inheritance required**: Composition through interfaces, not class hierarchies
- **Metadata isolation**: Domain system extracted via `inEventSourcedSystem()` remains metadata-free

Example implementation:

```kotlin
class OrderSystem :
    ISystem<OrderCommand, OrderState, OrderEvent>,
    IEventRepository<OrderCommand, UserContext, OrderEvent, EventMetadata, OrderEvent, EventMetadata, Connection> {

    // Domain logic (pure)
    override val decide = { command: OrderCommand, state: OrderState -> /* ... */ }
    override val evolve = { state: OrderState, event: OrderEvent -> /* ... */ }
    override val initialState = { OrderState.empty() }

    // Infrastructure (with metadata and transactions)
    override suspend fun fetchEvents(command: Pair<OrderCommand, UserContext>, txn: Connection) = /* ... */
        override suspend fun save(events: Sequence<OrderEvent>, metadata: UserContext, txn: Connection) = /* ... */
            override suspend fun <T> inTransaction(block: suspend (Connection) -> T): T = /* ... */
}

// Composition happens automatically - handle() is now available
val result = orderSystem.handle(PlaceOrder(...) to UserContext(userId = "123"))
```

## Algebraic View

| Concept       | Mathematical Structure    | Notes                                                                     |
|---------------|---------------------------|---------------------------------------------------------------------------|
| `mapCommand`  | **Contravariant Functor** | Maps input command types                                                  |
| `mapEvent`    | **Profunctor (dimap)**    | Contravariant in input events, covariant in output                        |
| `mapState`    | **Profunctor (dimap)**    | Contravariant in input state, covariant in output                         |
| `combine`     | **Monoidal Product**      | Combines two systems into a product system                                |
| `emptySystem` | **Monoidal Identity**     | Represents the "no-op" system (`GeneralSystem<Nothing?, Unit, Nothing?>`) |

## Why Systems Thinking Matters in the AI Era

As software engineering continues to raise the level of abstraction through AI, systems thinking becomes even more
essential. While AI tools grow increasingly powerful at generating code, the real value lies in understanding how
components interact, how decisions ripple through ecosystems, and how to design resilient, adaptable systems.

This library embodies that philosophy: composable algebraic structures that let you reason about system behavior at a
higher level. When AI assists in implementation, your focus shifts to architecture, composition, and the invariants that
matter—exactly where human insight creates the most value.

## Data & Behaviour = Information (Algebra of the Business)

- Data (`examples/api.kt`): Commands, Events, State
- Behaviour (`examples/domain.kt`): Exhaustive pattern matching on data

## 📚 Learn More - [fmodel.fraktalio.com](fmodel.fraktalio.com)

![domain modeling](event-modeling.webp)
