package com.optrak.experiment

import akka.actor._
import com.optrak.experiment.AbstractFragment.{CreateThereActivity, CloseThereActivity}
import com.optrak.experiment.ActivityActor.SetData
import com.optrak.experiment.FSMProcessor.DeleteJournal
import com.optrak.experiment.GettingReadyFSM.GettingReadyData
import com.optrak.experiment.LivingFSM.{Logoff, LivingData}
import com.optrak.experiment.SettingTasksFSM.SettingTasksData
import com.optrak.experiment.StudentFSM.{StudentData, StudentStateData}
import com.optrak.experiment.StudyingFSM.StudyingData
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{Msg, StateModel}
import grizzled.slf4j.Logging

/**
 * Created by oscarvarto on 2014/07/15 from Tim's tspTasks
 */
object Controller extends Logging {
  val Name = "Controller"

  type ThereClazz = Class[_] // Used to identify an instance of an Android Activity

  case object Quit

  // using vector because we want fast random access
  type ActivityStack = Vector[ActorRef]

  // Messages
  /**
   * This tells us we can start
   * @param data This is the data that tells us the structure of the underlying fsm state models.
   *             We don't care in too much detail (they are all wrapped)
   */
  case class StartWith(data: WrappedHFSMData)

  /**
   * used to simplify testing
   */
  case object Test
  case class TestResponse(stack: ActivityStack)

  case class ReactorFor(name: ThereClazz, reactorRef: ActorRef)

  def killStack(stack: ActivityStack): ActivityStack =
    if (stack.isEmpty)
      stack
    else
      killStack(killTopOfStack(stack))

  def killTopOfStack(stack: ActivityStack): ActivityStack = {
    import scalaz.syntax.std.option._
    val errorMsg = "Activity Stack cannot be empty to be able to call killTopOfStack"
    val topActivity = stack.headOption.err(errorMsg)
    logger.debug(s"kill top of stack for ${topActivity}")
    topActivity ! CloseThereActivity
    stack.tail
  }

  var eventStamp: Int = 0

  def props(initialReactor: ActorRef) = Props(new Controller(initialReactor))

}

import Controller._

class Controller(initialReactor: ActorRef) extends Actor with ActorLogging with Stash {
  import scalaz.syntax.std.option._
  var fsmProcessor: ActorRef = ActorRef.noSender
  var activityStack: ActivityStack = Vector.empty

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.become(initialising)
  }
  def activityActorCreation(wrappedData: WrappedFSMData, fsmProcessor: ActorRef): (ThereClazz, ActorRef) =  {

    val activityActorName = ActivityActor.makeName(wrappedData.name)
    val props = ActivityActor.props(wrappedData, fsmProcessor)
    val activityActor = context.actorOf(props, activityActorName)
    (wrappedData.thereClazz, activityActor)
  }

  def initialiseActorAndActivity(stack: ActivityStack, data: WrappedFSMData,fsmProcessor: ActorRef): ActorRef = {
    val aa = activityActorCreation(data, fsmProcessor)
    val newActivity = CreateThereActivity(aa._1, eventStamp)
    eventStamp = eventStamp + 1
    if (activityStack.isEmpty)
      initialReactor ! newActivity
    else
      stack.head ! newActivity
    aa._2
  }

  /**
   * this will build a stack recursively from input
   * @param data
   * @param fsmProcessor
   * @return
   */
  def buildFromRecovery(data: WrappedHFSMData, fsmProcessor: ActorRef): ActivityStack =

    data.subs.foldLeft(Vector.empty: ActivityStack)((stack, data) =>
      stack :+ initialiseActorAndActivity(stack, data, fsmProcessor)
    )

  def initialising: Receive = {
    case starter: StartWith => {

      fsmProcessor = sender
      activityStack = buildFromRecovery(starter.data, fsmProcessor)
      context.unbecome()
    }
  }

  def processUpdate(hWrapper: WrappedHFSMData) {
    log.debug(s"processUpdate with $hWrapper\nactivityStack is $activityStack")
    // play back push/pops
    activityStack = hWrapper.pushPop.reverse.foldLeft(activityStack) { (stack, data) =>
      data match {
        case Pop =>
          killTopOfStack(stack)
        case push: Push => initialiseActorAndActivity(stack, push.model, fsmProcessor) +: stack
      }
    }

    // now we need to just send stuff to the appropriate actors
    val zipped = activityStack.zip(hWrapper.subs)
    log.debug(s"zipped is $zipped in $self")
    zipped.foreach { z =>
      log.debug(s"sending ${z._2} to aa ${z._1} ")
      z._1 ! SetData(z._2)
    }
  }

  def receive = {
    case Quit  => // shutdown mid-process. Do not clear journal
      killStack(activityStack)
      context.stop(self)

    case hWrapper: WrappedHFSMData =>
      processUpdate(hWrapper)
      if (activityStack.size == 0) {
        sender ! DeleteJournal(initialReactor)
        context.stop(self)
      }
    case Test => sender ! TestResponse(activityStack)
  }

  override def postStop(): Unit = {
    super.postStop()
    log.debug("controller poststop")
  }
}

