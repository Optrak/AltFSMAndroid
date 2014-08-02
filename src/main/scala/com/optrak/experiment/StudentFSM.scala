package com.optrak.experiment

import com.optrak.experiment.GettingReadyFSM.GettingReadyData
import com.optrak.experiment.LivingFSM.{Idle, LivingData}
import com.optrak.experiment.SettingTasksFSM.{Task, ReviewingTasks, SettingTasksData}
import com.optrak.experiment.StudyingFSM.{Thinking, StudyingData}
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{Msg, StateModel}
import scalaz.{Validation, Failure, Success}
import scalaz.syntax.std.option._

/**
 * Created by tim on 13/07/14.
 */

object StudentFSM {
  val Name: String = "Student"

  // we set a state for each possible top of stack
  sealed trait State
  case object SInitial extends State
  case object SLiving extends State
  case object SStudying extends State
  case object SSettingTasks extends State
  case object SLoggedOff extends State
  case object SGettingReady extends State

  // our fsms are static objects
  val livingFSM = new LivingFSM
  val studyingFSM = new StudyingFSM
  val settingTasksFSM = new SettingTasksFSM
  val gettingReadyFSM= new GettingReadyFSM

  type StateModelVector = Vector[StateModel[_, _]]
  
  case class Logon(name: String, age: Int) extends Msg


  case class StudentData(
                          tasks: Vector[Task] = Vector.empty,
                          name: String = "nobody",
                          age: Int = 1,
                          showered: Boolean = false,
                          dressed: Boolean = false)
  case class StudentStateData(subs: StateModelVector = Vector.empty,
                              operations: List[PushPop] = List.empty,
                              studentData: StudentData = StudentData() ) {
    def clearOperations = this.copy(operations = List.empty)
    def withUpdatedHead(updatedHead: StateModel[_, _]) = this.copy(subs = updatedHead +: subs.tail)
    def push(newHead: StateModel[_, _], wrapped: WrappedFSMData) = this.copy(subs = newHead +: subs, operations = Push(wrapped) :: operations)
    def pop = this.copy(subs = subs.tail, operations = Pop :: operations)
    def withUpdatedTasks(newTasks: Vector[Task]) = this.copy(studentData = studentData.copy(tasks = newTasks))
    def withNameAndAge(name: String, age: Int) = this.copy(studentData = this.studentData.copy(name = name, age = age))
    def withReadiness(readiness: GettingReadyData) = {
      // we need to update the Living fsm on the list
      val living = subs.last.asInstanceOf[StateModel[LivingFSM.State, LivingFSM.LivingData]]
      val living2 = livingFSM.model(living.state, living.data.copy(showered = readiness.showered, dressed = readiness.dressed))
      this.copy(subs = subs.updated(subs.size - 1, living2), studentData = studentData.copy(
        showered = readiness.showered,
        dressed = readiness.dressed))
    }


    /**
   * Note the data item is being used to output the history of push/pop operations. If you want only those for the
   * most recent message then you should use the clearOperations method
   * tasks, name and age are attributes of the student - that is they apply if this fsm is active regardless
   * of which state we're in.
   */

    override def toString() = s"StudentData(\nsubs: ${subs.mkString("\n")} \noperations: ${operations.mkString("\n")} \ntasks: ${studentData.tasks.mkString("\n")}"
  }

  /**
   * this occurs because we've managed to get the wrong list state and head of list
   * @param expected
   * @param data
   */
  class BadInternalListException(expected: State, data: StudentStateData) extends Exception {
    override def toString() = s"Bad internal list - $data"
  }

  /**
   * This occurs because we've passed a message back down from a sub-fsm to the next level
   * and it doesn't know how to process it. This is a programming error so we throw
   * exception not validation error
   * @param msg the offending message
   * @param data the current data
   */
  class ReprocessingException(msg: Msg, data: StudentStateData) extends Exception {
    override def toString() = s"Reprocessing can't handle $msg $data"
  }

  def headAs[S: Manifest, D: Manifest](data: StudentStateData, stateMarker: State): StateModel[S, D] =
    data.subs.head.state match {
      case st : S => data.subs.head match {
        case h: StateModel[S, D] => h
        case other => throw new Exception(s"$other is not a StateModel[S, D] for the S and D given in the headAs[S, D] call")
      }
      case _ => throw new BadInternalListException(stateMarker, data)
  }

  case class StudentHFSMData(subs: Vector[WrappedFSMData], pushPop: List[PushPop], studentData: StudentData, state: State) extends WrappedHFSMData

}

import StudentFSM._
abstract class StudentFSM extends OpkFSM[State, StudentStateData] with HFSM {

  def finalState = SLoggedOff

  def wrapLiving(st: OpkFSM.StateModel[_, _]): WrappedFSMData
  def wrapSettingTasks(st: OpkFSM.StateModel[_, _]): WrappedFSMData
  def wrapStudying(st: OpkFSM.StateModel[_, _]): WrappedFSMData
  def wrapGettingReady(st: OpkFSM.StateModel[_, _]): WrappedFSMData

  when(SInitial) {
    case Event(Logon(name, age), _, data) => {
      val newModel = livingFSM.model(Idle, LivingData(name, age))
      next(SLiving, data.withNameAndAge(name, age).push(newModel, wrapLiving(newModel)))
    }
  }

  when(SSettingTasks) {
    case Event(msg, _, data) =>
      type S = SettingTasksFSM.State
      type D = SettingTasksFSM.SettingTasksData
      settingTasksFSM.process(msg, headAs[S, D](data, SSettingTasks)).map { processed =>
        processed.stopReason.cata ( stopReason =>
          reprocess(stopReason, nextNoV(SStudying, data.pop)),
          nextNoV(SSettingTasks, data.withUpdatedHead(processed).withUpdatedTasks(processed.data.tasks))
        )
      }
  }

  when(SStudying) {
    case Event(msg, _, data) =>
      type S = StudyingFSM.State
      type D = StudyingFSM.StudyingData
      studyingFSM.process(msg, headAs[S, D](data, SStudying)).map { processed =>
      processed.stopReason match {
          // it stopped so we're popping
        case Some(stopReason) => reprocess(stopReason, nextNoV(SLiving, data.pop))
          // no popping
        case None => processed.state match {
            // SettingTasks tells us to push to new fsm
          case StudyingFSM.SettingTasks =>
            val newModel = settingTasksFSM.model(ReviewingTasks, SettingTasksData(data.studentData.tasks))
            val newData = data.withUpdatedHead(processed).push(newModel, wrapSettingTasks(newModel))
            nextNoV(SSettingTasks, newData)
            // carry on in current position
          case _ => nextNoV(SStudying, data.withUpdatedHead(processed))
        }
      }
    }
  }

  when(SLiving) {
    case Event(msg, _, data) =>
      type S = LivingFSM.State
      type D = LivingFSM.LivingData
      livingFSM.process(msg, headAs[S, D](data, SLiving)).map { processed =>
      processed.stopReason.cata ( reason =>
        stopNoV(reason, data.pop),
        processed.state match {
          case LivingFSM.Studying =>
            val newModel= studyingFSM.model(Thinking, StudyingData())
            val newData = data.withUpdatedHead(processed).push(newModel, wrapStudying(newModel))
            nextNoV(SStudying, newData)
          case LivingFSM.GettingReady =>
            val newModel = gettingReadyFSM.model(GettingReadyFSM.Initial, GettingReadyFSM.GettingReadyData())
            val newData = data.withUpdatedHead(processed).push(newModel, wrapGettingReady(newModel))
            nextNoV(SGettingReady, newData)
          case _ => nextNoV(SLiving, data.withUpdatedHead(processed))
        }
      )
    }
  }

  when(SGettingReady) {
    case Event(msg, _, data) =>
      type S = GettingReadyFSM.State
      type D = GettingReadyFSM.GettingReadyData
      gettingReadyFSM.process(msg, headAs[S, D](data, SGettingReady)).map { processed =>
      processed.stopReason.cata ( reason => {
        val data1 = data
          .withUpdatedHead(processed)
          .withReadiness(processed.data)
        reprocess(reason, nextNoV(SLiving, data1.pop))
        },
        // no change of state
        nextNoV(SGettingReady, data.withUpdatedHead(processed).withReadiness(processed.data))
      )
    }
  }

  /**
   * this is the main external processing. Unhandled events will return StateModel(stateModel.state, stateModel.data, Some(Unhandled(msg))
   * We override to clear operations so we always return the stack of operations carried out with this process cycle
   * @param msg input message
   * @param stateModel input model
   * @return Validation on the new stateModel
   */
  override def process(msg: Msg, stateModel: StateModel): ValidatedStateModel = super.process(msg, stateModel.copy(data = stateModel.data.clearOperations))

  /**
   * internal method to allow us to send an output message to the new top of stack following a pop
   * @param msg
   * @param stateModel
   * @return
   */
  def reprocess(msg: Msg, stateModel: StateModel): StateModel = {
    logger.debug(s"prior to reprocessing $msg state ${stateModel.state} ${stateModel.data}")
    super.process(msg, stateModel) match {
      case Success(sm) => sm
      case Failure((msg, state)) => throw new ReprocessingException(msg, stateModel.data)
    }
  }

  // this is the price we pay for wrapping our [S, D] to pass through other stages
  def wrap(stateModel: StateModel): WrappedHFSMData = {
    val newSubs = stateModel.data.subs.map { sm =>
      sm.data match {
        case ld: LivingData => wrapLiving(sm)
        case ld: GettingReadyData => wrapGettingReady(sm)
        case ld: StudyingData => wrapStudying(sm)
        case ld: SettingTasksData => wrapSettingTasks(sm)
      }
    }
    StudentHFSMData(newSubs, stateModel.data.operations, stateModel.data.studentData, stateModel.state)
  }
  def unwrap(wrappedData: WrappedHFSMData): StateModel = wrappedData match {
    case StudentHFSMData(subs, operations, studentData, state) =>
      val newSubs = subs.map(wr => wr.stateModel)
      model(state, StudentStateData(newSubs, operations, studentData))
  }

  def hProcess(msg: Msg, wrappedData: WrappedHFSMData): Validation[(Msg, OpkFSM.StateModel[_, _]), WrappedHFSMData] = {
    val stateModel = unwrap(wrappedData)
    process(msg, stateModel) map(stModel => wrap(stModel)) // we return the same type of validation failure so map only required
  }


}
