package com.optrak.experiment

import com.optrak.experiment.LivingFSM.FriendArrives
import com.optrak.experiment.SettingTasksFSM.StopSettingTasks
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{StateModel, Event, Msg}

import scalaz.std.option._

/**
 * Created by oscarvarto on 2014/07/08.
 */
object StudyingFSM {
  def Name: String = "Studying"

  sealed trait State

  case object Thinking extends State
  case object Reading extends State
  case object SettingTasks extends State
  case object Final extends State

  type Book = String

  trait Place

  case object Home extends Place
  case object School extends Place

  case class ChooseBook(book: Book) extends Msg
  case object StartReading extends Msg
  case object StopReading extends Msg
  case object SetTasks extends Msg
  case object StopStudying extends Msg

  case class StudyingData(currentBook: Option[Book]= None, place: Place = School)

  type StudyingStateModel = StateModel[State, StudyingData]

}
import StudyingFSM._
class StudyingFSM extends OpkFSM[State, StudyingData] {

  def finalState = Final

  when(Reading) {
    case Event(StopReading, _, data) => next(Thinking, data)
  }
  when(Thinking) {
    case Event(StartReading, _, data) =>
      if (data.currentBook != None)
        next(Reading, data)
      else
        next(Thinking, data)
  }
  when(SettingTasks) {
    case Event(StopSettingTasks, _, data) => next(Thinking, data)
  }
  whenUnhandled {
    case Event(ChooseBook(book), state, data: StudyingData) => next(state, data.copy(currentBook = Some(book)))
    case Event(StopStudying, state, data: StudyingData) => stop(StopStudying, data)
    case Event(SetTasks, state, data) => next(SettingTasks, data)
    case Event(FriendArrives, state, data) => stop(FriendArrives, data)
  }
}