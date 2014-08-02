package com.optrak.opkfsm

import com.optrak.experiment.Controller._
import com.optrak.opkfsm.OpkFSM.StateModel
import grizzled.slf4j.Logging

import scalaz.{Success, Failure, Validation}

/**
 * Created by tim on 2014/07/12
 *
 * Underlying premise:
 * An FSM takes a message and processes it returning an updated copy of itself (including its final state and data)
 * and an optional Msg. The optional Msg is used by the containing object or fsm to decide what to do next.
 * We cannot control the quality of messages so the whole thing is contained in a disjunction to permit external
 * level decisions as to what to do with the problem
 *
 * Individual fsms are non-hierarchical. However hierarchy is imposed by an outer fsm which maintains a stack
 * of fsms and knows how to join them together. This allows loose coupling with the outer fsm the only one that
 * needs to know how they relate - so the outer can be replaced to substitute a different combination of inners.
 *
 * On fsm termination it is assumed that the terminating fsm will return a termination message to the parent as part of the
 * response. Otherwise the response should contain only the Fsm itself.
 * 
 * In terms of types we are making it work as closely to the akka fsm as possible for maximum familiarity and to permit
 * wholesale (legal) plagiarism. With thanks to the akka team!
 *
 */

object OpkFSM {

  /**
   * base message class
   */
  trait Msg

  case class Unhandled(msg: Msg) extends Msg

  /**
   * Reason why this [[OpkFSM]] is shutting down.
   */
  type Reason = Msg

  /**
   * This captures all of the managed state of the [[OpkFSM]]: the state
   * name, the state data, and stop reason
   */
  case class StateModel[S, D](state: S, data: D, stopReason: Option[Reason] = None)

  type ValidatedStateModel[S, D] = Validation[(Msg, StateModel[_, _]), StateModel[S, D]]

  /**
   * All messages sent to the [[OpkFSM]] will be wrapped inside an
   * `Event`, which allows pattern matching to extract both state and data.
   */
  case class Event[S, D](event: Msg, state: S, data: D)


}

/**
 * Finite State Machine actor trait. Use as follows:
 *
 * <pre>
 *   object A {
 *     trait State
 *     case class One extends State
 *     case class Two extends State
 *
 *     case class Data(i : Int)
 *   }
 *
 *   class A OpkFSM[A.State, A.Data] {
 *     import A._
 *
 *     startWith(One, Data(42))
 *     when(One) {
 *         case Event(SomeMsg, Data(x)) => ...
 *         case Ev(SomeMsg) => ... // convenience when data not needed
 *     }
 *     when(Two) { ... }
 *     initialize()
 *   }
 * </pre>
 */

trait OpkFSM[S, D] extends Logging {
  import com.optrak.opkfsm.OpkFSM._

  type StateModel = OpkFSM.StateModel[S, D]
  type Event = OpkFSM.Event[S, D]
  type ValidatedStateModel = OpkFSM.ValidatedStateModel[S, D]
  type EventFunction = scala.PartialFunction[Event, ValidatedStateModel]

  /*
   * “import” so that these are visible without an import
   */
  val Event: OpkFSM.Event.type = OpkFSM.Event


  /**
   * ****************************************
   *                 DSL
   * ****************************************
   */

  /**
   * Insert a new StateFunction at the end of the processing chain for the
   * given state.
   *
   * @param state designator for the state
   * @param stateFunction partial function describing response to input
   */
  final def when(state: S)(stateFunction: EventFunction): Unit =
    register(state, stateFunction)


  /**
   * Produce transition to other state. Return this from a state function in
   * order to effect the transition.
   *
   * @param state state designator for the next state
   * @param data data for next state
   * @return new (validated) state model
   */
  final def next(state: S, data: D): ValidatedStateModel = Success(OpkFSM.StateModel(state, data))

  /**
   * Produce transition to other state. Return this from a state function in
   * order to effect the transition. This does not wrap in success but assumes
   * it's already inside a validation
   *
   * @param state state designator for the next state
   * @return new state model
   */
  final def nextNoV(state: S, data: D): StateModel = OpkFSM.StateModel(state, data)


  /**
   * implement this so we know what our final state is for the model. Used in stop
   * @return
   */
  def finalState: S

  final def stop(reason: Reason, stateData: D): ValidatedStateModel = Success(StateModel(finalState, stateData, Some(reason)))

  final def stopNoV(reason: Reason, stateData: D): StateModel = StateModel(finalState, stateData, Some(reason))


  /**
   * Set handler which is called upon reception of unhandled messages. Calling
   * this method again will overwrite the previous contents.
   *
   * The current state may be queried using ``state``.
   */
  final def whenUnhandled(stateEventFunction: EventFunction): Unit =
    handleEvent = stateEventFunction orElse handleEventDefault

  /**
   * type safe means of generating appropriate initial data
   * @param state
   * @param data
   * @return a StateModel(state, data)
   */
  def model(state: S, data: D): StateModel= StateModel(state, data)

  /**
   * this is the main external processing. Unhandled events will return StateModel(stateModel.state, stateModel.data, Some(Unhandled(msg))
   * @param msg input message
   * @param stateModel input model
   * @return Validation on the new stateModel
   */
  def process(msg: Msg, stateModel: StateModel) : ValidatedStateModel = {
    processMsg(msg, stateModel)
  }

  /*
   * ****************************************************************
   *                PRIVATE IMPLEMENTATION DETAILS
   * ****************************************************************
   */

  /*
   * State definitions
   */
  private var stateFunctions = Map[S, EventFunction]()

  private def register(name: S, function: EventFunction): Unit = {
    if (stateFunctions contains name)
      stateFunctions = stateFunctions.updated(name, stateFunctions(name) orElse function)
    else
      stateFunctions = stateFunctions + (name -> function)
  }

  /*
   * unhandled event handler
   */
  private val handleEventDefault: EventFunction = {
    case Event(value, state, data) ⇒
      logger.warn(s"unhandled event $value in state $state" )
      Failure((value, StateModel(state, data)))
  }
  private var handleEvent: EventFunction = handleEventDefault

  private def processMsg(value: Msg, currentState: StateModel): ValidatedStateModel = {
    val event: Event = Event(value, currentState.state, currentState.data)
    processEvent(currentState, event)
  }

  private[opkfsm] def processEvent(currentState: StateModel, event: Event): ValidatedStateModel = {
    val stateFunc = stateFunctions(currentState.state)
    val nextState =
      if (stateFunc isDefinedAt event)
        stateFunc(event)
      else
        handleEvent(Event(event.event, currentState.state, event.data))
    nextState
  }


}

