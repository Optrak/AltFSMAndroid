package com.optrak.experiment

import akka.actor._
import com.optrak.experiment.AbstractFragment.{CreatedThereActivity, CreateThereActivity, CloseThereActivity, RequestInitialisation}
import com.optrak.experiment.StudentFSM.StudentData
import com.optrak.opkfsm.OpkFSM
import com.optrak.opkfsm.OpkFSM.{Msg, StateModel}
import scala.concurrent.duration._

/**
 * Created by tim on 28/06/14.
 */

object ActivityActor {

  case class SetData(data: WrappedFSMData)
  val Name = "ActivityActor"

  def makeName(fsmName: String) = s"$Name-$fsmName"

  def props[S, D](data: WrappedFSMData,
                  fsmProcessor: ActorRef): Props =
    Props(new ActivityActor(data, fsmProcessor))

  case class CreateActivityReminder(cta: CreateThereActivity)
}

import ActivityActor._

// TODO: Update the following scaladoc, please!
/**
 * Purpose of activityactor is to hold the current appropriate state and data to give to the reactor on request and on change
 * This requires it to listen to transitions of the appropriate type.
 * It does no processing of OpkFSM data or states - this is left to the Reactor and the activity
 * //@param eventBus bus to listen for broadcast transitions
 *
 */
class ActivityActor(initialData: WrappedFSMData,
                             fsmProcessor: ActorRef)
  extends Actor with ActorLogging {

  var reactor: Option[ActorRef] = None
  var waitingFor: Option[CreateThereActivity] = None
  var data: WrappedFSMData = initialData

  def receive : Receive = {
    case s: SetData =>
      log.debug(s"$self receives SetData $s")
      data = s.data
      reactor foreach { _ ! s}

    case RequestInitialisation =>
      reactor = Some(sender)
      sender ! SetData(data)
      waitingFor foreach { wf =>
        sender ! wf
      }

    case msg: Msg =>
      log.debug(s"ActivityActor $self receives msg $msg sending to fsmProcessor $fsmProcessor")
      fsmProcessor ! msg

    case CloseThereActivity =>
      reactor foreach { r => r ! CloseThereActivity }
      log.debug(s"$self gets CloseThereActivity")
      context.stop(self)
    case cta : CreateThereActivity =>
      reactor match {
        case Some(r) =>
          r ! cta
          waitingFor = Some(cta)
          scheduleReminder(cta)
        case None =>
          waitingFor = Some(cta)
      }
    case CreatedThereActivity(clazz, id) =>
      waitingFor foreach { wf =>
        if (CreateThereActivity(clazz, id) == wf)
          waitingFor = None
      }
    case CreateActivityReminder(cta) =>
      waitingFor foreach { wf =>
        if (wf == cta)
          // we just try again
          self ! cta
      }
    case x => log.debug(s"ActivityActor receives unexpected $x")
  }

  def scheduleReminder(cta: CreateThereActivity) = {
    // now schedule a reminder
    context.system.scheduler.scheduleOnce(1 second, self, CreateActivityReminder(cta))(context.system.dispatcher, self);
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.debug(s"ActivityActor $self is beging stopped")
    super.postStop()
  }
}