package com.optrak.androidExperiment

import com.optrak.experiment.Controller._
import com.optrak.experiment.{StudentFSM, WrappedFSMData}
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.StateModel

/**
 * Created by oscarvarto on 2014/07/22.
 */
object MyHFSM {
  val LivingClazz = classOf[LivingActivity]
  val GettingReadyClazz = classOf[GettingReadyActivity]
  val StudyingClazz = classOf[StudyingActivity]
  val SettingTasksClazz = classOf[SettingTasksActivity]

  case class WrappedLivingData(stateModel: StateModel[_, _]) extends WrappedFSMData {
    def name: String = "Living"

    def thereClazz: ThereClazz = LivingClazz
  }

  case class WrappedStudyingData(stateModel: StateModel[_, _]) extends WrappedFSMData {
    def thereClazz: ThereClazz = StudyingClazz

    def name: String = "Studying"
  }

  case class WrappedSettingTasksData(stateModel: StateModel[_, _]) extends WrappedFSMData {
    def thereClazz: ThereClazz = SettingTasksClazz

    def name: String = "SettingTasks"
  }

  case class WrappedGettingReadyData(stateModel: StateModel[_, _]) extends WrappedFSMData {
    def thereClazz: ThereClazz = GettingReadyClazz

    def name: String = "GettingReady"
  }

}

class MyHFSM extends StudentFSM {

  import com.optrak.androidExperiment.MyHFSM._

  def wrapLiving(st: OpkFSM.StateModel[_, _]): WrappedFSMData = new WrappedLivingData(st)

  def wrapStudying(st: OpkFSM.StateModel[_, _]): WrappedFSMData = new WrappedStudyingData(st)

  def wrapSettingTasks(st: OpkFSM.StateModel[_, _]): WrappedFSMData = new WrappedSettingTasksData(st)

  def wrapGettingReady(st: OpkFSM.StateModel[_, _]): WrappedFSMData = new WrappedGettingReadyData(st)
}
