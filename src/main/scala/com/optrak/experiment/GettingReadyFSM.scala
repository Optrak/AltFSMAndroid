package com.optrak.experiment

import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{StateModel, Msg}

/**
 * Created by tim on 14/07/14.
 * Illustrates a set of things ready via states to give automatic completion of state
 */
object GettingReadyFSM {
  def Name = "GettingReady"

  sealed trait State
  case object Initial extends State
  case object Showering extends State
  case object GettingDressed extends State
  case object Ready extends State

  case class GettingReadyData(showered: Boolean = false, dressed: Boolean = false) {
    def isReady = showered && dressed
  }
  
  case object StartShowering extends Msg
  case object StopShowering extends Msg
  case object StartGettingDressed extends Msg
  case object FinishedGettingDressed extends Msg
  case object AmReady extends Msg

  type GettingReadyStateModel = StateModel[State, GettingReadyData]
 
}
import GettingReadyFSM._
class GettingReadyFSM extends OpkFSM[State, GettingReadyData] {

  def finalState = Ready

  def doNext(newData: GettingReadyData) = if (newData.isReady)
    stop(AmReady, newData)
  else
    next(Initial, newData)

  when(Showering) {
    case Event(StopShowering, _, data) => doNext(data.copy(showered = true))
  }
  when(GettingDressed) {
    case Event(FinishedGettingDressed, _, data) => doNext(data.copy(dressed = true))
  }
  when(Initial) {
    case Event(StartShowering, _, data) => next(Showering, data)
    case Event(StartGettingDressed, _, data) => next(GettingDressed, data)
  }
}
