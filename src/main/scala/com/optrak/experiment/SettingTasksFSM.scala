package com.optrak.experiment

import com.optrak.experiment.LivingFSM.FriendArrives
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{StateModel, Msg}
import org.joda.time.DateTime

/**
 * Created by oscarvarto on 2014/07/08.
 */
object SettingTasksFSM {
  val Name = "SettingTasks"

  sealed trait State

  case object ReviewingTasks extends State

  case object Final extends State

  case object StopSettingTasks extends Msg

  case class AppendTask(t: Task) extends Msg

  case class TaskModified(newValue: Task, taskIndex: Int) extends Msg

  val initTasks = Vector.tabulate(3) { i =>
    val n = i + 1
    Task(n, s"Task #$n")
  }

  case class Task(id: Int, title: String, description: String = "", done: Boolean = false, creationDate: DateTime = new DateTime)

  case class SettingTasksData(tasks: Vector[Task] = initTasks)

  type SettingTasksStateModel = StateModel[State, SettingTasksData]

}

import SettingTasksFSM._

class SettingTasksFSM extends OpkFSM[State, SettingTasksData] {
  def finalState = Final

  when(ReviewingTasks) {
    case Event(StopSettingTasks, _, data) => stop(StopSettingTasks, data)
    case Event(AppendTask(t), _, data) => next(ReviewingTasks, data.copy(tasks = data.tasks :+ t))
    case Event(TaskModified(newValue, i), _, data) => next(ReviewingTasks, data.copy(tasks = data.tasks.updated(i, newValue)))
    case Event(FriendArrives, _, data) => stop(FriendArrives, data)
  }
}
