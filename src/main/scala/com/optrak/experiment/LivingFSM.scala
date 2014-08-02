package com.optrak.experiment

import com.optrak.experiment.GettingReadyFSM.AmReady
import com.optrak.experiment.StudyingFSM.StopStudying
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{Event, StateModel, Msg}

import scalaz.Failure


/**
 * Created by oscarvarto on 2014/07/08.
 */
object LivingFSM extends App {
  def Name: String = "Living"


  sealed trait State
  case object Eating extends State
  case object WatchingTV extends State
  case object Studying extends State
  case object Idle extends State
  case object GettingReady extends State
  case object OutAndAbout extends State // assume when out all we can do is ComeBack
  case object Final extends State

  case class LivingData(name: String = "Oscar", age: Int = 31,
                        dressed: Boolean = false, showered: Boolean = false, friendWaiting: Boolean = false)

  case object StartEating extends Msg
  case object StopEating extends Msg
  case object StartWatchingTV extends Msg
  case object StopWatchingTV extends Msg
  case object StartStudying extends Msg
  case object GoOut extends Msg
  case object ComeBack extends Msg
  case object FriendArrives extends Msg
  case object StartGettingReady extends Msg
  case object Logoff extends Msg

  type LivingStateModel = StateModel[State, LivingData]

}

import LivingFSM._
class LivingFSM extends OpkFSM[State, LivingData] {
  def startWith = StateModel(Idle, LivingData())

  def finalState = Final

  val eating : EventFunction = {
    case Event(StopEating, _, data) => next(Idle, data)
  }

  val watchingTV: EventFunction = {
    case Event(StopWatchingTV, _, data) => next(Idle, data)
  }

  val notStudying : EventFunction = {
    case Event(StartEating, _, data) => next(Eating, data)
    case Event(StartWatchingTV, _, data) => next(WatchingTV, data)
    case Event(StartStudying, _, data) => next(Studying, data)
    case Event(StartGettingReady, _, data) => next(GettingReady, data)
    case Event(GoOut, _, data) => next(OutAndAbout, data)
    case Event(Logoff, _, data) => stop(Logoff, data)
  }

  val notOut: EventFunction = {
    case Event(FriendArrives, state, data) =>
      if (data.showered && data.dressed)
        next(OutAndAbout, data.copy(friendWaiting = false))
      else
        next(GettingReady, data.copy(friendWaiting = true))
  }

  val studying: EventFunction = {
    case Event(StopStudying, _, data) => next(Idle, data)
  }

  // illustrates automatic change of state based on data
  val gettingReady: EventFunction = {
    case Event(AmReady, _, data) =>
      if (data.friendWaiting)
        next(OutAndAbout, data)
      else
        next(Idle, data)

  }

  when(Idle) { notStudying orElse notOut }

  when(Studying) { studying orElse notOut }

  when(Eating) { eating orElse notStudying orElse notOut }

  when(WatchingTV) { watchingTV orElse notStudying orElse notOut  }

  when(OutAndAbout) {
    case Event(ComeBack, _, data) => next(Idle, data)
  }

  // illustrates automatic change of state based on data
  when(GettingReady) { gettingReady orElse notOut }

}

