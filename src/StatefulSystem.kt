package com.fraktalio

// 1  ######################## State Stored ############################
interface IStateRepository<C, S> {
    /**
     * Fetch state
     *
     * @receiver Command of type [C]
     * @return the State of type [S] or null
     */
    suspend fun C.fetchState(): S?

    /**
     * Save state
     *
     * @receiver State of type [S]
     * @return newly saved State of type [S]
     */
    suspend fun S.save(): S
}

// Composing Domain (System) and Infrastructure (IStateRepository) into an Application

suspend fun <SYS, C, S, E> SYS.handle(command: C): S where SYS : IStateRepository<C, S>, SYS : ISystem<C, S, E> =
    asStateStoredSystem().run { this(command, command.fetchState() ?: initialState()).save() }

class StatefulStateStoredSystem<C, S, E>(
    system: ISystem<C, S, E>, stateRepository: IStateRepository<C, S>
) : ISystem<C, S, E> by system, IStateRepository<C, S> by stateRepository


// 2  ######################## Event Sourced ############################
interface IEventRepository<C, E> {
    /**
     * Fetch events
     *
     * @receiver Command of type [C]
     *
     * @return [Sequence] of Events of type [E]
     */
    suspend fun C.fetchEvents(): Sequence<E>

    /**
     * Save events
     *
     * @receiver [Sequence] of Events of type [E]
     * @return newly saved [Sequence] of Events of type [E]
     */
    suspend fun Sequence<E>.save(): Sequence<E>
}

// Composing Domain (System) and Infrastructure (IEventRepository) into an Application

suspend fun <SYS, C, S, E> SYS.handle(command: C): Sequence<E> where SYS : IEventRepository<C, E>, SYS : ISystem<C, S, E> =
    asEventSourcedSystem().run {
        this(command, command.fetchEvents()).save()
    }

class StatefulEventSourcedSystem<C, S, E>(
    system: ISystem<C, S, E>, eventRepository: IEventRepository<C, E>
) : ISystem<C, S, E> by system, IEventRepository<C, E> by eventRepository
