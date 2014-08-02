package com.optrak.experiment

import akka.actor.{Props, ActorLogging, ActorRef}
import akka.persistence.PersistentActor
import com.optrak.experiment.Controller._
import com.optrak.experiment.StudentFSM.{StudentStateData, SInitial}
import com.optrak.opkfsm.OpkFSM.{StateModel, Msg}

/**
 * Created by oscarvarto on 2014/06/19.
 */
object FSMProcessor {
  val Name = "FSMProcessor"

  case class DeleteJournal(tellWhenComplete: ActorRef)
  case class DeletedJournal(lastActivity: Long)

  /**
   * Message from owner to be received after processing recovery
   */
  case object AreYouReady

  /**
   * message to owner saying all recovery is now complete
   */
  case object Ready


  /**
   * wrapper for Msg that says to persist it
   * @param msg
   */
  case class PersistMsg(msg: Msg)

  val PersistClassifier = "Persist"

  /**
   * message for fsm to tell it it's in recovery
   */
  case object InRecovery

  /**
   * message for fsm to tell it recovery is over
   */
  case object RecoveryFinished

  def props(controller: ActorRef, hfsm: HFSM, initial: WrappedHFSMData): Props = Props(new FSMProcessor(controller, hfsm, initial))

}

class FSMProcessor(controller: ActorRef, hfsm: HFSM, initial: WrappedHFSMData) extends PersistentActor with ActorLogging {
  import FSMProcessor._

  override def persistenceId = "FSMProcessor-id"

  var hsfmData : WrappedHFSMData = initial

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    self ! RecoveryFinished
  }

  def updateState(msg: Msg) = {

  }

  def receiveCommand: Receive = {
    case msg: Msg =>
      log.debug(s"FSMProcessor receive cmd $msg")
      // Validation.fold is a Catamorphism:
      // --> Run the first given function if failure, otherwise, the second given function.

      // Here we are "validating" the message before persisting. // TODO: Check these ideas
      // Only messages that can be handled are persisted.
      hfsm.hProcess(msg, hsfmData).fold(
        fail = { case (unhandledMsg, reason) =>
          val errorMsg = s"$hfsm cannot handle $unhandledMsg ($reason)"
          sender ! errorMsg
        },
        succ = { stModel =>
          persist(msg)(updateState) // todo Oscar's solution didn't work. It never called the updateState method. Why?
          hsfmData = stModel
          log.debug(s"sending $hsfmData to $controller")
          controller ! hsfmData
        }
      )
      log.debug(s"Last sequence is $lastSequenceNr")
    case dj: DeleteJournal =>
      log.debug(s"deleting journal last message is $lastSequenceNr")
      deleteMessages(lastSequenceNr, true)
      log.debug(s"sending DeletedJou8rnal to ${dj.tellWhenComplete}")
      dj.tellWhenComplete ! DeletedJournal(lastSequenceNr)
    case RecoveryFinished =>
      log.debug(s"$self received RecoveryFinished stateModel is $hsfmData")
      controller ! StartWith(hsfmData)
      log.debug(s"recoveryFinished sent StartWith($hsfmData)")
  }

  def receiveRecover: Receive = {
    case msg: Msg =>
      log.debug(s"fsmProcessor $self recovering with msg $msg")
      hfsm.hProcess(msg, hsfmData) foreach { stModel =>
        hsfmData = stModel
      }
  }



}

