package com.fraktalio

/**
 * Given-When-Then specification DSL for information systems.
 *
 * Provides two flavors:
 * - **Event-sourced**: [givenEvents] / [whenCommand] / [thenEvents] — asserts produced events
 * - **State-stored**: [givenState] / [whenCommand] / [thenState] — asserts resulting state
 *
 * Example (event-sourced):
 * ```
 * system.givenEvents(listOf(SomeEvent)) {
 *     whenCommand(SomeCommand)
 * } thenEvents listOf(ExpectedEvent)
 * ```
 *
 * Example (state-stored):
 * ```
 * system.givenState(initialState) {
 *     whenCommand(SomeCommand)
 * } thenState expectedState
 * ```
 */

/**
 * **Given** past [events], **when** a [command] is issued, produce new events.
 *
 * Reconstructs state by folding [events] via [IDynamicSystem.evolve],
 * then applies the command via [IDynamicSystem.decide].
 *
 * @param events the history of input events to reconstruct state from
 * @param command a lambda producing the command to execute
 * @return the sequence of output events produced by the system
 */
fun <Command, State, InEvent, OutEvent> IDynamicSystem<Command, State, InEvent, OutEvent>.givenEvents(
    events: Iterable<InEvent>,
    command: () -> Command
): Sequence<OutEvent> = decide(command(), events.fold(initialState()) { s, e -> evolve(s, e) })

/**
 * Identity function that serves as a readable **when** step in the DSL.
 *
 * @param command the command to issue
 * @return the same [command], unchanged
 */
fun <Command> whenCommand(command: Command): Command = command

/**
 * **Then** assert that the produced events match the [expected] events.
 *
 * @param expected the expected output events
 * @throws IllegalStateException if actual events do not match [expected]
 */
infix fun <OutEvent> Sequence<OutEvent>.thenEvents(expected: Iterable<OutEvent>) {
    val actual = toList()
    check(actual == expected.toList()) { "Expected: ${expected.toList()}, but got: $actual" }
}

/**
 * **Given** a current [state], **when** a [command] is issued, produce the new state.
 *
 * Delegates to [ISystem.inStateStoredSystem], which folds the events
 * produced by [ISystem.decide] via [ISystem.evolve] into the resulting state.
 *
 * @param state the current state of the system
 * @param command a lambda producing the command to execute
 * @return the new state after applying the command
 */
fun <Command, State, Event> ISystem<Command, State, Event>.givenState(
    state: State,
    command: () -> Command
): State = inStateStoredSystem()(command(), state)

/**
 * **Then** assert that the resulting state matches the [expected] state.
 *
 * @param expected the expected resulting state
 * @throws IllegalStateException if actual state does not match [expected]
 */
infix fun <State> State.thenState(expected: State) {
    check(this == expected) { "Expected: $expected, but got: $this" }
}