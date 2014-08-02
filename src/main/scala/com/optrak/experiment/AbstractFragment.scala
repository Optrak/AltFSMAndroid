package com.optrak.experiment

import akka.actor.{ActorRef, ActorSelection, Actor, ActorSystem}
import com.optrak.experiment.ActivityActor.SetData
import com.optrak.experiment.Controller.ThereClazz
import com.optrak.experiment.StudentFSM.StudentData
import com.optrak.opkfsm.OpkFSM.{StateModel, Msg}
import grizzled.slf4j.Logging

object AbstractFragment {
  /**
   * Message ordering the current reactor to (indirectly) create an instance of `ThereActivity`
   *
   * @param clazz Corresponds to the Android activity to be created
   */
  case class CreateThereActivity(clazz: ThereClazz, eventStamp: Int)

  /**
   * return from reactor to sender that the activity has been created
   * @param clazz
   */
  case class CreatedThereActivity(clazz: ThereClazz, eventStamp: Int)

  /** Signals this reactor to close its owner */
  case object CloseThereActivity

  /**
   * message to tell reactor that the system or whatever has closed this activity
   */
  case object ClosedFromOutside

  /**
   * sent to ActivityActor by pathSelection to ask for initialisation data
   */
  case object RequestInitialisation

  /**
   * sent to controller by pathSelection to tell it that we're the reactor for this clazz
   */
  case class ReactorInitialised(clazz: ThereClazz, reactor: ActorRef)
}

import AbstractFragment._

/**
 * Created by tim on 29/06/14.
 * AbstractFragment is usable both for mocking and as mixin to Android Activity (or Fragment)
 * An abstract fragment corresponds to an fsm in the hierarchical fsm
 * It has to have some global reference point for its akka system - as it will need to create it's own child reactor
 */
trait AbstractFragment extends Logging {
  /**
   * spawnActivity will create a new activity from clazz without other parameters.
   * @param clazz The clazz of the spawned reacotr
   */
  def spawnActivity(clazz: ThereClazz)

  /**
   * you must define and invoke createReactor in your derived class
   */
  def createReactor : ActorRef

  lazy val reactor = createReactor

  /**
   * implement to provide actorSystem faciilities to subclass
   * @return the actorSystem
   */
  def actorSystem: ActorSystem

  /**
   * stops the activity (e.g. getActivity.finish()
   */
  def stop()

  /**
   * this method is used to update the UI (or mock). It's called on the UI thread
   * A smart implementation will figure out what has changed ...
   * It's up to the implementation to check the WrappedFSMData contains the right stuff.
   * It should definitely log an error if it does not
   */
  def updateData(data: WrappedFSMData): Unit

  /**
   * comes here after activity stopped by some external agency such as screen rotation (could use proper name)
   */
  def closedFromOutside() {
    reactor ! ClosedFromOutside
  }

  /**
   * name is used to get the name of the associated activityActor etc
   * @return
   */
  def name: String

}

/**
 * AbstractReactor is mixin for Reactor to stand as intermediary between Activity and ActivityActor
 * This is an Actor and it has lifecycle corresponding to that of the associated Activity
 * It is created by the activity
 */
abstract class AbstractReactor(owner: AbstractFragment,
                                     clazz: ThereClazz,
                                     fsmName: String)
  extends Actor with Logging {

  private def activityActorSelection: ActorSelection = context.actorSelection(s"/user/${Controller.Name}/${ActivityActor.makeName(fsmName)}")

  var activityActor: Option[ActorRef] = None

  /**
   * this is mock/real call to do a Ui run on Android. Allows our reactor to carry out stuff
   * on ui thread
   * @param f function to be performed. Must not close over variable state!
   */
  def doOnUIThread( f: () => Unit ): Unit

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    activityActorSelection ! RequestInitialisation
    context.become(initialising)
  }

  def doSetData(data: WrappedFSMData) {
    logger.debug(s"reactor $self sets data in its fragment $owner")
    doOnUIThread { () => owner.updateData(data)}
  }

  val baseInitialising: Receive = {
    case s: SetData =>
      activityActor = Some(sender)
      doSetData(s.data)
      context.unbecome()
  }

  def initialising: Receive

  // it is anticipated that the activity lifecycle methods will be furnished by a mixin - with imoplementations for Android
  // and for mocks

  val baseReceive : Receive = {
    case AbstractFragment.CreateThereActivity(clazz, eventStamp) => {
      doOnUIThread { () => owner.spawnActivity(clazz) }
      sender ! CreatedThereActivity(clazz, eventStamp)
    }
    case AbstractFragment.CloseThereActivity =>
      doOnUIThread {() =>  owner.stop() }
      context.stop(self)
    case s: SetData=> doSetData(s.data)
    case msg: Msg => {
      logger.debug(s"reactor $self gets msg $msg sending on to $activityActor")
      activityActor.foreach( x=> x ! msg )
    }
    case ClosedFromOutside => {
      // activityActor foreach { aa => aa ! ClosedFromOutside } todo - why did we think we needed this?
      context.stop(self)
    }

  }

}

